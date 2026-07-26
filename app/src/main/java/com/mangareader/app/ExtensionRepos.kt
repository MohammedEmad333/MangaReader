package com.mangareader.app

import android.content.Context
import org.json.JSONArray

/**
 * Persists and manages user-defined extension repository URLs.
 */
object ExtensionRepos {
    private const val KEY_REPOS = "extension_repos_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun list(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_REPOS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, url: String) {
        val repos = list(context).toMutableList()
        if (!repos.contains(url)) {
            repos.add(url)
            save(context, repos)
        }
    }

    fun remove(context: Context, url: String) {
        save(context, list(context).filterNot { it == url })
    }

    private fun save(context: Context, repos: List<String>) {
        val arr = JSONArray()
        repos.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_REPOS, arr.toString()).apply()
    }
}
