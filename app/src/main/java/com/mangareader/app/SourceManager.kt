package com.mangareader.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * One saved connection to a backend. Multiple can exist at once
 * (e.g. two Komga servers + a local folder). The `type` decides
 * which Source implementation gets built from it.
 */
data class SourceConfig(
    val id: String,
    val type: String,          // "local" | "komga"
    val label: String,
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val treeUri: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("label", label)
        put("url", url)
        put("user", user)
        put("pass", pass)
        put("treeUri", treeUri)
    }

    val isConfigured: Boolean
        get() = when (type) {
            "local" -> treeUri.isNotBlank()
            else -> false
        }

    companion object {
        fun fromJson(o: JSONObject) = SourceConfig(
            id = o.getString("id"),
            type = o.getString("type"),
            label = o.optString("label"),
            url = o.optString("url"),
            user = o.optString("user"),
            pass = o.optString("pass"),
            treeUri = o.optString("treeUri")
        )
    }
}

fun typeLabel(type: String): String = when (type) {
    "local" -> "Local folder"
    else -> type
}

object SourceManager {
    private const val KEY = "sources_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun list(context: Context): List<SourceConfig> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SourceConfig.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- extension source cache ----------

    /** The adapters handed out last time, and the LoadResult list they wrap. */
    private var cachedAdapters: List<Source>? = null
    private var cachedFrom: List<ExtensionLoader.LoadResult>? = null

    /**
     * The adapter-wrapped extension sources.
     *
     * Two layers of caching sit under this call. [ExtensionLoader.loadAllCached]
     * avoids re-classloading the APKs, and the reference check below avoids
     * re-wrapping its results — when the loader returns the very same list
     * instance, the adapters from last time are still valid, so callers keep
     * getting stable Source identities instead of a fresh object per call.
     *
     * The adapters are built with the application context on purpose. They
     * outlive any one Activity now that they're held in a static cache, and
     * TachiyomiSourceAdapter only ever uses the context for `cacheDir`.
     */
    @Synchronized
    fun extensionSources(context: Context): List<Source> {
        val appCtx = context.applicationContext
        val results = ExtensionLoader.loadAllCached(appCtx)

        val cached = cachedAdapters
        if (cached != null && cachedFrom === results) return cached

        val adapters = results
            .flatMap { result ->
                result.sources
                    .filterIsInstance<CatalogueSource>()
                    .map { TachiyomiSourceAdapter(it, appCtx, result.pkgName, result.isNsfw) }
            }

        cachedAdapters = adapters
        cachedFrom = results
        return adapters
    }

    /** Forces the next [extensionSources] call to reload and re-wrap everything. */
    @Synchronized
    fun invalidateExtensions() {
        cachedAdapters = null
        cachedFrom = null
        ExtensionLoader.invalidate()
        // The cover header table is keyed on the hosts of these sources, so it
        // is stale for exactly as long as this cache is. Dropping it here means
        // a newly installed extension's covers carry its Referer immediately
        // rather than after the next process start.
        CoverHeaders.invalidate()
    }

    /**
     * Returns all configured local sources plus any dynamically loaded APK
     * extensions.
     *
     * The local half is rebuilt every call and stays that way: it's a
     * SharedPreferences read plus a couple of object constructions, and it has
     * to reflect edits made in the Sources screen immediately. The extension
     * half is the expensive one, and that's what [extensionSources] caches.
     */
    fun listAllSources(context: Context): List<Source> {
        // 1. Build the active local sources from saved configs — cheap, always fresh.
        val activeSources = list(context).mapNotNull { build(context, it) }

        // 2. Cached, adapter-wrapped APK extension sources.
        //    Named extSources, not extensionSources: a local val with the same
        //    name as the function would be referencing itself in its initializer.
        val extSources = extensionSources(context)

        // 3. Combine them together into a single list
        return activeSources + extSources
    }

    fun save(context: Context, items: List<SourceConfig>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(context: Context, config: SourceConfig) {
        val items = list(context).toMutableList()
        val idx = items.indexOfFirst { it.id == config.id }
        if (idx >= 0) items[idx] = config else items.add(config)
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Build the live Source for a config, or null if not usable. */
    fun build(context: Context, config: SourceConfig): Source? = when (config.type) {
        "local" ->
            if (config.treeUri.isNotBlank()) LocalSource(config.id, context, Uri.parse(config.treeUri))
            else null
        else -> null
    }

    /** One-time import of v0.9's single-source settings into the new list. */
    fun migrateLegacy(context: Context) {
        val p = prefs(context)
        if (p.getString(KEY, null) != null) return
        val seed = mutableListOf<SourceConfig>()
        p.getString("library_uri", null)?.let {
            seed.add(SourceConfig(newId(), "local", "Local folder", treeUri = it))
        }
        if (seed.isNotEmpty()) save(context, seed)
    }
}
