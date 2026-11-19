package com.mangareader.app

import java.security.MessageDigest

/**
 * A stable SHA-256 of the exact local preference stores selected for restore.
 * No preference keys, values, filenames or source credentials leave this method.
 *
 * The device-local storage path is intentionally preserved by restore and is
 * excluded. Worker-owned backup timestamps can change while a confirmation
 * dialog is open, so they are excluded as well.
 */
internal fun backupLocalStateFingerprint(
    stores: Map<String, Map<String, *>>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")

    fun append(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(byteArrayOf(
            (bytes.size ushr 24).toByte(),
            (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),
            bytes.size.toByte(),
        ))
        digest.update(bytes)
    }

    for ((storeName, values) in stores.toSortedMap()) {
        append(storeName)
        val keys = values.keys.sorted()
        for (key in keys) {
            if (storeName == "manga_reader" && key in setOf(
                    "storage_dir", "backup_last_at", "backup_last_attempt_at",
                    "backup_last_attempt_succeeded",
                )
            ) continue
            val value = values[key]
            append(key)
            when (value) {
                is String -> { append("s"); append(value) }
                is Boolean -> { append("b"); append(value.toString()) }
                is Int -> { append("i"); append(value.toString()) }
                is Long -> { append("l"); append(value.toString()) }
                is Float -> { append("f"); append(value.toRawBits().toString()) }
                is Set<*> -> {
                    append("ss")
                    // Same set with a different iteration order is identical.
                    val members = value.filterIsInstance<String>().sorted()
                    append(members.size.toString())
                    members.forEach(::append)
                }
                else -> { append("unsupported"); append(value?.javaClass?.name.orEmpty()) }
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
