package com.mangareader.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Never show the contents of source_ stores: they can contain credentials. */
internal data class BackupVerificationReport(
    val version: Int,
    val appVersion: String,
    val createdAt: Long,
    val storeCount: Int,
    val settingCount: Int,
    val byteCount: Int,
    val fingerprint: String,
)

internal data class ValidatedYomuBackup(
    val report: BackupVerificationReport,
    val stores: Map<String, JSONObject>,
)

/**
 * Same validation for read-only preview and real restore. This does not modify
 * preferences or files. A fingerprint ensures a previously previewed document
 * cannot silently change between selection and confirmation.
 */
internal object BackupVerification {
    private const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_STORES = 2048
    private const val VERSION = 1
    private const val APP_STORE = "manga_reader"

    fun readBounded(stream: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (result.size().toLong() + count > maxBytes) {
                error("Backup is too large to verify safely (64 MB limit).")
            }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    fun validate(bytes: ByteArray): ValidatedYomuBackup {
        require(bytes.size <= MAX_BYTES) { "Backup is too large to verify safely (64 MB limit)." }
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            error(
                "That's a Tachiyomi backup (.tachibk), not a Yomu one. " +
                    "Use \"Import Tachiyomi backup\" instead."
            )
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            error("Backup is not valid UTF-8 text.")
        }
        if (!text.trimStart().startsWith("{")) {
            error("That isn't a Yomu backup; select a .json file created by Yomu.")
        }
        val root = JSONObject(text)
        val version = root.optInt("version", -1)
        require(version >= 1) { "This doesn't look like a Yomu backup." }
        require(version <= VERSION) { "That backup is from a newer version of Yomu." }
        val stores = root.optJSONObject("stores") ?: error("Backup has no data in it.")
        require(stores.length() <= MAX_STORES) { "Backup has too many preference stores." }
        require(stores.optJSONObject(APP_STORE) != null) { "Backup has no Yomu settings store." }

        val accepted = LinkedHashMap<String, JSONObject>()
        var count = 0
        for (name in stores.keys()) {
            if (name != APP_STORE && !name.startsWith("source_")) continue
            // Do not let user-supplied JSON address arbitrary preference paths.
            if (name.length > 200 || name.any { it == '/' || it == '\\' || it.code < 32 }) {
                error("Backup contains an invalid preference store name.")
            }
            val entries = stores.optJSONObject(name) ?: error("Invalid preference store in backup.")
            for (key in entries.keys()) {
                val entry = entries.optJSONObject(key)
                    ?: error("Backup contains an invalid setting.")
                val value = entry.opt("v")
                val valid = when (entry.optString("t")) {
                    "b" -> value is Boolean
                    "i" -> value is Number && value.toDouble().isFinite() &&
                        value.toDouble() % 1.0 == 0.0 &&
                        value.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
                    "l" -> value is Number && value.toDouble().isFinite() &&
                        value.toDouble() % 1.0 == 0.0 &&
                        value.toDouble() >= Long.MIN_VALUE.toDouble() &&
                        value.toDouble() < 9223372036854775808.0
                    "f" -> value is Number && value.toDouble().isFinite() &&
                        value.toDouble() in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble()
                    "s" -> value is String
                    "ss" -> value is JSONArray &&
                        (0 until value.length()).all { value.opt(it) is String }
                    else -> continue // Unknown future preference types are ignored on restore.
                }
                require(valid) { "Backup contains an invalid value for a saved setting." }
                count++
            }
            accepted[name] = entries
        }
        require(count > 0) { "Backup was readable but empty." }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return ValidatedYomuBackup(
            report = BackupVerificationReport(
                version = version,
                appVersion = root.optString("app").take(30),
                createdAt = root.optLong("createdAt", 0L),
                storeCount = accepted.size,
                settingCount = count,
                byteCount = bytes.size,
                fingerprint = fingerprint,
            ),
            stores = accepted,
        )
    }
}
