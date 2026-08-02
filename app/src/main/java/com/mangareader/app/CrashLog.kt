package com.mangareader.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few uncaught exceptions, written to disk before the process dies.
 *
 * This app has no way to see a crash. It is sideloaded, there is no Play
 * Console behind it, `adb logcat` needs a cable or a paired debugging session,
 * and the failures that have cost this project the most time are precisely the
 * ones no build step can reach — 0.121 shipped green and would not start, and
 * 0.137 loaded every extension and then died on global search. Both were
 * invisible until someone sat with a device.
 *
 * So: catch it, write it, show it in Settings → Diagnostics.
 *
 * ## Why this delegates rather than swallows
 *
 * The previous handler is called afterwards, so Android still does what it
 * would have done — kills the process and shows its dialog. A handler that
 * returns without delegating leaves the process alive with a dead thread and
 * a UI that no longer updates, which is a worse failure than the crash and one
 * that looks like a freeze rather than a bug.
 *
 * ## Why the write is wrapped
 *
 * Anything thrown from inside an uncaught-exception handler replaces the
 * original exception, so a bug here would erase the very trace it exists to
 * capture. Every step is inside runCatching for that reason, and a failure to
 * write is silently accepted — losing the log is bad, losing the crash dialog
 * as well is worse.
 *
 * ## Why the thread name is recorded
 *
 * It is often the whole answer. A crash on `main` is app code; one on an
 * OkHttp dispatcher or an RxJava scheduler thread is code the app called but
 * does not own, and under R8 that distinction is the difference between a bug
 * and a missing keep rule.
 */
object CrashLog {

    private const val FILE = "crashes.txt"

    /** Keep the file small enough to read on a phone and to hold in a String. */
    private const val MAX_BYTES = 64 * 1024

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun file(context: Context) = File(context.filesDir, FILE)

    /**
     * Installs the handler. Call first in [App.onCreate], before anything that
     * could itself crash — a handler registered after the failing line is a
     * handler that catches nothing.
     */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { sw ->
            // printStackTrace walks the cause chain, which is where the useful
            // half usually is: 0.121's IllegalArgumentException said nothing,
            // and its cause named FullTypeReference.
            PrintWriter(sw).use { error.printStackTrace(it) }
        }.toString()

        val entry = buildString {
            append("=== ").append(stamp.format(Date())).append(" ===\n")
            append("version: ").append(BuildConfig.VERSION_NAME)
            append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("thread:  ").append(thread.name).append('\n')
            append(trace).append('\n')
        }

        val f = file(context)
        // Newest first. The old content is re-read and re-written rather than
        // appended to, because the entry that matters is the one you are
        // looking for right now, and scrolling past four older ones to reach it
        // on a phone is how a log stops being read.
        val existing = runCatching { if (f.exists()) f.readText() else "" }.getOrDefault("")
        val combined = (entry + existing).take(MAX_BYTES)
        f.writeText(combined)

        // Mirror to external storage, and this is not redundancy for its own
        // sake. filesDir is app-private: reading it needs a build that LAUNCHES,
        // so a crash-on-startup can only be read by shipping another release
        // first — which is the loop 0.138 and 0.139 were spent on.
        //
        // getExternalFilesDir needs no permission, is deleted with the app, and
        // lands at Android/data/com.mangareader.app/files/, which a file manager
        // on this device can reach. When a build will not start, that copy is
        // the only one anyone can get at.
        runCatching {
            context.getExternalFilesDir(null)?.let { dir ->
                File(dir, FILE).writeText(combined)
            }
        }
    }

    /** The log, newest first, or null when nothing has crashed. */
    fun read(context: Context): String? =
        runCatching {
            val f = file(context)
            if (f.exists() && f.length() > 0) f.readText() else null
        }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE).delete() } }
    }

    /** Whether to offer the row at all. */
    fun exists(context: Context): Boolean = read(context) != null
}
