package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Category(val id: String, val name: String)

/**
 * User-defined categories and a mapping of series -> categories.
 * Assignments are keyed by series id (folder URI or "komga:<id>"),
 * so they survive rescans and work across sources.
 */
object Categories {
    private const val KEY_CATS = "categories_json"
    private const val KEY_ASSIGN = "category_assign_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun list(context: Context): List<Category> {
        val raw = prefs(context).getString(KEY_CATS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Category(o.getString("id"), o.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCats(context: Context, cats: List<Category>) {
        val arr = JSONArray()
        cats.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs(context).edit().putString(KEY_CATS, arr.toString()).apply()
    }

    fun add(context: Context, name: String) {
        val cats = list(context).toMutableList()
        cats.add(Category(UUID.randomUUID().toString(), name))
        saveCats(context, cats)
    }

    fun rename(context: Context, id: String, name: String) {
        saveCats(context, list(context).map { if (it.id == id) it.copy(name = name) else it })
    }

    fun remove(context: Context, id: String) {
        saveCats(context, list(context).filterNot { it.id == id })
        // purge this category from all assignments
        val map = assignments(context)
        val cleaned = JSONObject()
        for (key in map.keys()) {
            val arr = map.getJSONArray(key)
            val kept = JSONArray()
            for (i in 0 until arr.length()) {
                val cid = arr.getString(i)
                if (cid != id) kept.put(cid)
            }
            if (kept.length() > 0) cleaned.put(key, kept)
        }
        prefs(context).edit().putString(KEY_ASSIGN, cleaned.toString()).apply()
    }

    private fun assignments(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_ASSIGN, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun categoriesFor(context: Context, seriesId: String): Set<String> {
        val map = assignments(context)
        if (!map.has(seriesId)) return emptySet()
        val arr = map.getJSONArray(seriesId)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setCategoriesFor(context: Context, seriesId: String, catIds: Set<String>) {
        val map = assignments(context)
        if (catIds.isEmpty()) {
            map.remove(seriesId)
        } else {
            val arr = JSONArray()
            catIds.forEach { arr.put(it) }
            map.put(seriesId, arr)
        }
        prefs(context).edit().putString(KEY_ASSIGN, map.toString()).apply()
    }
}
