package com.mangareader.app

import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupVerificationTest {
    private fun backup(
        stores: String = """{"manga_reader":{"library":{"t":"s","v":"[]"}}}""",
        version: Int = 1,
    ): ByteArray = """{"version":$version,"app":"0.317","createdAt":12345,"stores":$stores}"""
        .toByteArray(Charsets.UTF_8)

    @Test fun validBackupProducesReadOnlySummaryAndStableFingerprint() {
        val bytes = backup()
        val first = BackupVerification.validate(bytes)
        val second = BackupVerification.validate(bytes)
        assertEquals(1, first.report.version)
        assertEquals(1, first.report.storeCount)
        assertEquals(1, first.report.settingCount)
        assertEquals(bytes.size, first.report.byteCount)
        assertEquals(first.report.fingerprint, second.report.fingerprint)
        assertEquals(64, first.report.fingerprint.length)
        assertTrue(first.stores.containsKey("manga_reader"))
    }

    @Test fun unknownSettingsDoNotBecomeValidData() {
        val bytes = backup("""{"manga_reader":{"mystery":{"t":"future","v":1}}}""")
        assertThrows(IllegalArgumentException::class.java) {
            BackupVerification.validate(bytes)
        }
    }

    @Test fun rejectsCorruptTruncatedOrUnsupportedBackups() {
        val candidates = listOf(
            "not json".toByteArray(),
            byteArrayOf(0x1f, 0x8b.toByte(), 1),
            byteArrayOf(0xc3.toByte(), 0x28),
            """{"version":1,"stores":{""".toByteArray(),
            backup(version = 99),
            backup(stores = """{"source_a":{"x":{"t":"s","v":"a"}}}"""),
            backup(stores = """{"manga_reader":{"x":{"t":"i","v":"nope"}}}"""),
            backup(stores = """{"manga_reader":{"x":{"t":"i","v":2147483648}}}"""),
            backup(stores = """{"manga_reader":{"x":{"t":"ss","v":[1]}}}"""),
            backup(stores = """{"manga_reader":{},"source_../evil":{"x":{"t":"s","v":"bad"}}}"""),
        )
        candidates.forEach { bytes ->
            assertThrows(Exception::class.java) { BackupVerification.validate(bytes) }
        }
    }

    @Test fun ignoresUnrecognizedStoreNamesAndNeverReturnsThem() {
        val data = backup(
            stores = """{"manga_reader":{"x":{"t":"b","v":true}},"other_settings":{"token":{"t":"s","v":"secret"}}}""",
        )
        val verified = BackupVerification.validate(data)
        assertEquals(1, verified.stores.size)
        assertFalse(verified.stores.containsKey("other_settings"))
    }

    @Test fun boundedReadStopsAtLimit() {
        val bytes = backup()
        assertEquals(bytes.toList(), BackupVerification.readBounded(ByteArrayInputStream(bytes)).toList())
        val huge = ByteArray(64 * 1024 * 1024 + 1)
        assertThrows(IllegalStateException::class.java) {
            BackupVerification.readBounded(ByteArrayInputStream(huge))
        }
    }
}
