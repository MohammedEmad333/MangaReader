package com.mangareader.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Per-series automatic download preference.
 *
 * Disabled by default. The refresh worker also requires a pre-existing chapter
 * cache before it considers anything "new", so enabling this can never turn the
 * first library refresh into a download of the whole backlog.
 */
internal enum class AutoDownloadLimit(val value: Int, val label: String) {
    ONE(1, "Newest 1"),
    THREE(3, "Newest 3"),
    FIVE(5, "Newest 5"),
    TEN(10, "Newest 10"),
    ALL(0, "All new");

    fun next(): AutoDownloadLimit {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun from(value: Int): AutoDownloadLimit =
            entries.firstOrNull { it.value == value } ?: THREE
    }
}

internal object AutoDownloadPrefs {
    private const val PREFS = "manga_reader"
    private const val KEY_ENABLED = "auto_download_series"
    private const val KEY_WIFI_ONLY = "auto_download_wifi_only"
    private const val KEY_LIMIT = "auto_download_limit"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(sourceId: String, seriesId: String): String =
        sourceId.length.toString() + ":" + sourceId + ":" + seriesId

    fun enabled(context: Context, sourceId: String, seriesId: String): Boolean =
        key(sourceId, seriesId) in
            (prefs(context).getStringSet(KEY_ENABLED, emptySet()) ?: emptySet())

    fun wifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    fun limit(context: Context): AutoDownloadLimit =
        AutoDownloadLimit.from(prefs(context).getInt(KEY_LIMIT, AutoDownloadLimit.THREE.value))

    fun setLimit(context: Context, value: AutoDownloadLimit) {
        prefs(context).edit().putInt(KEY_LIMIT, value.value).apply()
    }

    fun networkAllowed(context: Context): Boolean {
        if (!wifiOnly(context)) return true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun setEnabled(
        context: Context,
        sourceId: String,
        seriesId: String,
        enabled: Boolean,
    ) {
        val current = LinkedHashSet(
            prefs(context).getStringSet(KEY_ENABLED, emptySet()) ?: emptySet()
        )
        val value = key(sourceId, seriesId)
        if (enabled) current.add(value) else current.remove(value)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    /**
     * Returns chapters not represented by the previous snapshot.
     *
     * Id is the strongest identity, but extension updates sometimes rewrite URLs
     * for every chapter. The normalized name fallback prevents that from looking
     * like hundreds of brand-new chapters and flooding the queue.
     */
    fun newChapters(
        context: Context,
        previous: List<Chapter>,
        fresh: List<Chapter>,
    ): List<Chapter> {
        if (previous.isEmpty() || fresh.isEmpty()) return emptyList()

        val oldIds = previous.mapTo(HashSet()) { it.id }
        val oldNames = previous.mapTo(HashSet()) { normalize(it.name) }

        val found = fresh.filter { chapter ->
            chapter.id !in oldIds && normalize(chapter.name) !in oldNames
        }

        val max = limit(context).value
        return if (max <= 0) found else found.takeLast(max)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}
