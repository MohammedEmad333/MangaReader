package com.mangareader.app

import android.content.Context
import org.json.JSONObject

internal data class LibraryRefreshSummary(
    val total: Int,
    val done: Int,
    val counted: Int,
    val resumed: Int,
    val failed: Int,
    val skipped: Int,
    val finishedAt: Long,
    val firstError: String?,
    val coversRepaired: Int,
    val failures: Map<String, Int>,
)

internal object LibraryRefreshSummaryStore {
    private const val SUMMARY_KEY = "refresh_last_summary"

    fun clear(context: Context) {
        prefs(context).edit().remove(SUMMARY_KEY).apply()
    }

    fun load(context: Context): LibraryRefreshSummary? {
        val raw = prefs(context).getString(SUMMARY_KEY, null) ?: return null
        val o = JSONObject(raw)
        val failures = buildMap {
            o.optJSONObject("failures")?.let { f ->
                for (key in f.keys()) put(key, f.optInt(key))
            }
        }
        return LibraryRefreshSummary(
            total = o.optInt("total"),
            done = o.optInt("done"),
            counted = o.optInt("counted"),
            resumed = o.optInt("resumed"),
            failed = o.optInt("failed"),
            skipped = o.optInt("skipped"),
            finishedAt = o.optLong("finishedAt"),
            firstError = if (o.isNull("firstError")) null else o.optString("firstError"),
            coversRepaired = o.optInt("coversRepaired"),
            failures = failures,
        )
    }

    fun save(context: Context, summary: LibraryRefreshSummary) {
        val failures = JSONObject()
        summary.failures.forEach { (key, value) -> failures.put(key, value) }
        val o = JSONObject()
            .put("total", summary.total)
            .put("done", summary.done)
            .put("counted", summary.counted)
            .put("resumed", summary.resumed)
            .put("failed", summary.failed)
            .put("skipped", summary.skipped)
            .put("finishedAt", summary.finishedAt)
            .put("firstError", summary.firstError ?: JSONObject.NULL)
            .put("coversRepaired", summary.coversRepaired)
            .put("failures", failures)
        prefs(context).edit().putString(SUMMARY_KEY, o.toString()).apply()
    }
}
