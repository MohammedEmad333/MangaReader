package com.mangareader.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object BackupCodec {
    fun encode(
        map: Map<String, *>,
        skip: Set<String>,
    ): JSONObject {
        val out = JSONObject()

        map.forEach { (key, value) ->
            if (key in skip) return@forEach

            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject()
                    .put("t", "ss")
                    .put(
                        "v",
                        JSONArray().apply {
                            value.filterIsInstance<String>().forEach(::put)
                        },
                    )
                else -> null
            }

            if (entry != null) out.put(key, entry)
        }

        return out
    }

    fun decodeInto(
        editor: SharedPreferences.Editor,
        entries: JSONObject,
    ): Int {
        var count = 0

        for (key in entries.keys()) {
            val entry = entries.optJSONObject(key) ?: continue

            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "s" -> editor.putString(key, entry.optString("v"))
                "ss" -> {
                    val arr = entry.optJSONArray("v") ?: JSONArray()
                    editor.putStringSet(
                        key,
                        (0 until arr.length())
                            .mapNotNull { arr.optString(it) }
                            .toSet(),
                    )
                }
                else -> continue
            }

            count++
        }

        return count
    }

    fun storeNames(
        context: Context,
        appPrefs: String,
    ): List<String> {
        val dir = File(
            context.applicationContext.applicationInfo.dataDir,
            "shared_prefs",
        )

        val found = runCatching {
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml") }
                ?.map { it.name.removeSuffix(".xml") }
                ?.filter { it == appPrefs || it.startsWith("source_") }
                .orEmpty()
        }.getOrDefault(emptyList())

        return if (appPrefs in found) found else found + appPrefs
    }
}
