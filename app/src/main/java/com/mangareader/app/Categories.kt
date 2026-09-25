package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Category(val id: String, val name: String)

/**
 * User-defined categories and a mapping of series -> categories.
 * Assignments are keyed by series id (folder URI, or "<sourceId>:<url>"),
 * so they survive rescans and work across sources.
 */
object Categories {
    private const val KEY_CATS = "categories_json"
    const val DEFAULT_ID = "default"
    private const val KEY_ASSIGN = "category_assign_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    @Volatile
    private var catsRaw: String? = null

    @Volatile
    private var catsCache: List<Category>? = null

    fun list(context: Context): List<Category> {
        val raw = prefs(context).getString(KEY_CATS, null) ?: return emptyList()
        val hit = catsCache
        if (hit != null && catsRaw == raw) return hit
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Category(o.getString("id"), o.getString("name"))
            }.also {
                catsCache = it
                catsRaw = raw
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCats(context: Context, cats: List<Category>) {
        val arr = JSONArray()
        cats.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val text = arr.toString()
        prefs(context).edit().putString(KEY_CATS, text).apply()
        catsCache = cats
        catsRaw = text
    }

    fun add(context: Context, name: String) {
        addAndGet(context, name)
    }

    /** Adds a category and returns it, so callers can pre-select what they just made. */
    fun addAndGet(context: Context, name: String): Category {
        val cats = list(context).toMutableList()
        val created = Category(UUID.randomUUID().toString(), name)
        cats.add(created)
        saveCats(context, cats)
        return created
    }

    /**
     * The category everything lands in when the user doesn't pick one.
     * Created on first use so a fresh install always has somewhere to save to.
     */
    fun ensureDefault(context: Context): Category {
        val existing = list(context)
        existing.firstOrNull { it.id == DEFAULT_ID }?.let { return it }
        val created = Category(DEFAULT_ID, "Default")
        saveCats(context, listOf(created) + existing)
        return created
    }

    fun rename(context: Context, id: String, name: String) {
        saveCats(context, list(context).map { if (it.id == id) it.copy(name = name) else it })
    }

    fun remove(context: Context, id: String) {
        if (id == DEFAULT_ID) return
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
        invalidateAssignments()
    }

    // Parsing this map is cheap once and ruinous several thousand times.
    // Filtering the library by category called categoriesFor() per entry, and
    // each of those re-parsed the whole assignment object — fine at forty
    // series, an unresponsive app at four thousand.
    //
    // Keyed on the raw string rather than a dirty flag, so a write from
    // anywhere invalidates it, including a restore that replaces the prefs
    // wholesale. SharedPreferences hands back the same String instance for
    // repeated reads, so the comparison is a reference check in practice.
    @Volatile
    private var assignRaw: String? = null

    @Volatile
    private var assignCache: JSONObject? = null

    private fun invalidateAssignments() {
        assignRaw = null
        assignCache = null
    }

    /**
     * Every series with at least one category.
     *
     * The complement of this — inside the library — is what Tachiyomi calls
     * "Default": not a category anything is filed under, but the absence of one.
     */
    fun assignedSeries(context: Context): Set<String> {
        val map = assignments(context)
        val out = HashSet<String>()
        val keys = map.keys()
        while (keys.hasNext()) {
            val seriesId = keys.next()
            val arr = map.optJSONArray(seriesId) ?: continue
            if (arr.length() > 0) out.add(seriesId)
        }
        return out
    }

    private fun assignments(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_ASSIGN, null) ?: return JSONObject()
        val hit = assignCache
        if (hit != null && assignRaw == raw) return hit
        return try {
            JSONObject(raw).also {
                assignCache = it
                assignRaw = raw
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /**
     * Every series id in [catId], in one parse.
     *
     * The filter this replaces asked the question the other way round — for each
     * series, which categories is it in — which is the same answer and O(n)
     * parses to get it.
     */
    fun seriesIn(context: Context, catId: String): Set<String> {
        val map = assignments(context)
        val out = HashSet<String>()
        val keys = map.keys()
        while (keys.hasNext()) {
            val seriesId = keys.next()
            val arr = map.optJSONArray(seriesId) ?: continue
            for (i in 0 until arr.length()) {
                if (arr.optString(i) == catId) {
                    out.add(seriesId)
                    break
                }
            }
        }
        return out
    }

    fun categoriesFor(context: Context, seriesId: String): Set<String> {
        val map = assignments(context)
        if (!map.has(seriesId)) return emptySet()
        val arr = map.getJSONArray(seriesId)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    /**
     * Adds and removes categories across many series in a single write.
     *
     * [setCategoriesFor] reserialises the entire assignment object per call, so
     * running it once per selected series is the quadratic write §5 keeps
     * finding — a hundred selected entries would be a hundred growing
     * serialisations of a map that already holds thousands. This is one parse,
     * one pass, one write, one invalidation.
     *
     * A category in neither [add] nor [remove] is left exactly as it was on each
     * series, which is what makes a mixed selection editable: the caller can
     * leave the categories only *some* of the selection belongs to alone instead
     * of having to force them on or off.
     *
     * [remove] is applied after [add], so a category in both wins as a removal.
     */
    fun applyCategories(
        context: Context,
        seriesIds: Set<String>,
        add: Set<String>,
        remove: Set<String>
    ) {
        if (seriesIds.isEmpty() || (add.isEmpty() && remove.isEmpty())) return
        val map = assignments(context)
        seriesIds.forEach { seriesId ->
            // LinkedHashSet, not HashSet: assignment order is what the category
            // list is written in, and reordering it on every bulk edit would
            // make the stored JSON churn for no reason.
            val current = LinkedHashSet<String>()
            map.optJSONArray(seriesId)?.let { arr ->
                for (i in 0 until arr.length()) current.add(arr.getString(i))
            }
            current.addAll(add)
            current.removeAll(remove)
            if (current.isEmpty()) {
                map.remove(seriesId)
            } else {
                val arr = JSONArray()
                current.forEach { arr.put(it) }
                map.put(seriesId, arr)
            }
        }
        prefs(context).edit().putString(KEY_ASSIGN, map.toString()).apply()
        invalidateAssignments()
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
        invalidateAssignments()
    }
}
