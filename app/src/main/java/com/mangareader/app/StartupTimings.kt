package com.mangareader.app

import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the first frame waited on.
 *
 * The board item is "the app takes some time to open", and reading the code got
 * as far as naming suspects and no further — which is where three handoffs have
 * now left it. This exists to replace the argument with a number.
 *
 * ### Why these marks and not a systrace
 *
 * A trace needs a machine attached. This runs on the device that has the
 * problem, reports into a dialog next to Extension diagnostics, and costs one
 * `elapsedRealtime()` pair per measured call.
 *
 * ### First call only
 *
 * [once] records a name the first time it is seen and never again. That is the
 * whole point: `Library.list` and `SeriesIndex.all` are both memoised, so every
 * call after the first is a map lookup, and averaging those in would hide the
 * one call that actually blocked the frame. A second call falls straight
 * through to [block] with no timing at all.
 *
 * ### The offset matters as much as the duration
 *
 * Each mark also records how long after process start it *began*. A 6-second
 * parse starting at +200 ms and a 6-second parse starting at +9 s are different
 * bugs: the first is the cold read, the second means something else went first
 * and this was waiting on it.
 */
object StartupTimings {

    private class Mark(val offsetMs: Long, val durationMs: Long)

    private val marks = ConcurrentHashMap<String, Mark>()

    /** First-seen order, which is also timeline order. */
    private val order = CopyOnWriteArrayList<String>()

    /**
     * True process start, not first-class-load. Available unconditionally —
     * `minSdk` is 24 and this landed in 24.
     */
    private val processStart: Long = Process.getStartElapsedRealtime()

    /**
     * Times [block] if [name] has not been timed before, and returns whatever it
     * returns either way.
     *
     * Safe to wrap around anything: the timing is recorded in a `finally`, so a
     * throwing block is still measured and the exception still propagates.
     */
    fun <T> once(name: String, block: () -> T): T {
        if (marks.containsKey(name)) return block()
        val t0 = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            val done = SystemClock.elapsedRealtime()
            marks.putIfAbsent(name, Mark(t0 - processStart, done - t0))
            order.addIfAbsent(name)
        }
    }

    /** The dialog body. Run this off the main thread — it stats and reads prefs. */
    fun report(context: Context): String = buildString {
        append("Milliseconds, first call only.\n")
        append("\"at\" is time after process start.\n\n")

        if (order.isEmpty()) {
            append("Nothing recorded yet.\n")
            append("Open the Library tab, then come back.\n")
        } else {
            for (name in order) {
                val mark = marks[name] ?: continue
                append(name).append('\n')
                append("    ").append(mark.durationMs).append(" ms")
                append("   (at +").append(mark.offsetMs).append(" ms)\n")
            }
            val measured = order.sumOf { marks[it]?.durationMs ?: 0L }
            append("\nMeasured total: ").append(measured).append(" ms\n")
            append("Anything above that is elsewhere.\n")
        }

        append('\n')
        appendPrefsSize(context)
    }

    /**
     * The size of the shared prefs file and its biggest keys.
     *
     * Twelve call sites share `manga_reader`, and Android loads and parses the
     * whole file on first access — so if one blob dominates it, every unrelated
     * read pays for it too. The app can stat its own data dir, so this needs no
     * root and no adb.
     */
    private fun StringBuilder.appendPrefsSize(context: Context) {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/manga_reader.xml")
        if (!file.exists()) {
            append("manga_reader.xml: not found\n")
            return
        }
        append("manga_reader.xml: ").append(formatSize(file.length())).append("\n\n")

        val biggest = runCatching {
            context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
                .all
                .entries
                .map { it.key to ((it.value as? String)?.length ?: 0) }
                .sortedByDescending { it.second }
                .take(5)
        }.getOrDefault(emptyList())

        if (biggest.isNotEmpty()) {
            append("Largest keys:\n")
            for ((key, chars) in biggest) {
                append("    ").append(key).append("  ")
                append(formatSize(chars.toLong())).append('\n')
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
