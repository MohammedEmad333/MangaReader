package com.mangareader.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException

// ---------- prefs: the same store every other object in this package uses ----------

internal fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

/** Stable per-chapter key: source id + chapter id. Drives resume, read flags and history. */
internal fun chapterKeyOf(sourceId: String, chapter: Chapter): String = "$sourceId|${chapter.id}"

internal fun savedPage(context: Context, key: String): Int =
    prefs(context).getInt("pos:$key", 0)

internal fun savePage(context: Context, key: String, page: Int) {
    prefs(context).edit().putInt("pos:$key", page).apply()
}

/**
 * Forgets where a chapter was left off.
 *
 * Removed rather than written as 0, so `savedPage(...) > 0` keeps meaning
 * "started" — the distinction the Start/Resume button reads, and the reason
 * marking a chapter unread has to come through here rather than only clearing
 * the read flag.
 */
internal fun clearPage(context: Context, key: String) {
    prefs(context).edit().remove("pos:$key").apply()
}

/** [savePage] for a whole batch, in one edit. Used by the Tachiyomi import. */
internal fun savePageBulk(context: Context, pages: Map<String, Int>) {
    if (pages.isEmpty()) return
    val editor = prefs(context).edit()
    pages.forEach { (key, page) -> editor.putInt("pos:$key", page) }
    editor.apply()
}

internal fun isIncognito(context: Context): Boolean =
    prefs(context).getBoolean("incognito", false)

// ---------- global search tuning ----------

/** How many sources are queried at once. Kept low: every one is a live network call. */
internal const val GLOBAL_SEARCH_CONCURRENCY = 6

/** Per-source cap on the row of results, so one chatty source can't dominate. */
internal const val GLOBAL_SEARCH_PER_SOURCE = 12

/** One source's slice of a global search. Sources that error out are dropped. */
/** Where the currently open series was reached from; decides where back goes. */
internal enum class SeriesOrigin { BROWSE, LIBRARY, HISTORY, GLOBAL_SEARCH, DOWNLOADS }

internal class GlobalResult(val source: Source, val series: List<Series>)

/** The library series a migration is moving away from, while the picker is open. */
internal class MigrateFrom(val seriesId: String, val sourceId: String, val title: String)

/** Everything needed to jump straight back into a chapter from a history row. */
internal class ResumeTarget(
    val source: Source,
    val series: Series,
    val chapters: List<Chapter>,
    val index: Int
)

/**
 * Walks the exact same path ExtensionManager.loadInstalledSources takes, but reports
 * every step instead of swallowing failures into printStackTrace(). Diagnostic only.
 */

internal fun diagnoseExtensions(context: Context): String =
    ExtensionLoader.diagnose(context)

/**
 * The message to show when a call into an extension fails.
 *
 * **Every source call catches [Throwable], not [Exception], and this is why.**
 * An extension is a separately-compiled APK loaded through a `PathClassLoader`
 * against a *vendored* copy of the Tachiyomi source API. When the extension was
 * built against a newer API than this app ships, the mismatch does not arrive as
 * an exception — it arrives as a [LinkageError]: `NoClassDefFoundError` for a
 * model class that doesn't exist here, `NoSuchMethodError` for a method that
 * does exist but changed shape, `AbstractMethodError` for an interface that grew
 * a member. Those are `Error`, not `Exception`, so `catch (e: Exception)` lets
 * them straight through and **the app closes**.
 *
 * That is not hypothetical. An updated Elite Babes took the app down on every
 * open, and the only symptom was a process that vanished — no message, nothing
 * to read, and nothing to distinguish it from a source that was simply broken.
 *
 * Two things are deliberately still rethrown:
 *
 * - [CancellationException], because a cancelled coroutine has to finish
 *   cancelled. Closing the reader mid-load cancels a page fetch, and reporting
 *   that as a failed chapter is a bug this codebase has already had once.
 * - [VirtualMachineError] — out of memory, stack overflow. The process is
 *   already in trouble and dressing it up as "this source didn't work" hides a
 *   real problem behind a plausible-looking one.
 *
 * A version gate cannot replace this. `ExtensionLoader` does check the lib
 * version, but it reads what the extension *claims*, so it can only refuse
 * extensions that declare themselves out of range — not ones that declare a
 * version this app says it supports and then reach for something it doesn't
 * have. The honest gate is the failure itself, named and shown.
 */
internal fun sourceFailureMessage(t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    if (t is VirtualMachineError) throw t
    if (t is LinkageError) {
        // Named rather than summarised: the class or method in the message is
        // the exact piece of API the vendored source-api is missing, which is
        // the one fact needed to decide whether to implement it or to stop
        // claiming support for that lib version.
        return "This extension was built against a newer source API than this " +
            "app provides \u2014 ${t.javaClass.simpleName}: " +
            "${t.message ?: "missing symbol"}"
    }
    // Name the type when there is no message. A bare "Could not list chapters"
    // is indistinguishable from a source that legitimately has none, and it is
    // what an exception carrying a null message produces — NoSuchElementException
    // out of an empty stream, an NPE, a ClassCastException. Those are different
    // bugs and they were all rendering as the same sentence.
    //
    // The cause is included when there is one, because the outer type is often
    // a wrapper and the inner one is the answer.
    val cause = t.cause?.takeIf { it !== t }?.javaClass?.simpleName
    val type = t.javaClass.simpleName + if (cause != null) " \u2190 $cause" else ""
    return t.message?.let { "$it ($type)" } ?: "$fallback \u2014 $type"
}
