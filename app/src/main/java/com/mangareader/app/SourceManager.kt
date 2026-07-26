package com.mangareader.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
            "komga" -> url.isNotBlank()
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
    "komga" -> "Komga server"
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

    /** Returns all configured local/komga sources plus any dynamically loaded APK extensions. */
    fun listAllSources(context: Context): List<Source> {
        // 1. Build the active local/komga sources from saved configs (Fixed 'const val' to 'val')
        val activeSources = list(context).mapNotNull { build(context, it) }
        
        // 2. Load the dynamic APK extension sources
        val extensionSources = ExtensionManager.loadInstalledSources(context)
        
        // 3. Combine them together into a single list
        return activeSources + extensionSources
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
            if (config.treeUri.isNotBlank()) LocalSource(context, Uri.parse(config.treeUri))
            else null
        "komga" ->
            if (config.url.isNotBlank())
                KomgaSource(config.url, config.user, config.pass, context.cacheDir)
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
        val ku = p.getString("komga_url", "") ?: ""
        if (ku.isNotBlank()) {
            seed.add(
                SourceConfig(
                    newId(), "komga", "Komga",
                    url = ku,
                    user = p.getString("komga_user", "") ?: "",
                    pass = p.getString("komga_pass", "") ?: ""
                )
            )
        }
        if (seed.isNotEmpty()) save(context, seed)
    }
}
