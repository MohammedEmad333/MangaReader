package com.mangareader.app

internal val releaseNotes318To318 = listOf(
    ReleaseNote(
        code = 318,
        name = "0.318",
        header = "Verify a backup before restoring",
        body = """
            • Selecting Restore backup now checks the selected Yomu JSON archive before offering to replace settings.
            • A read-only summary reports the backup version, number of preference stores, saved settings and size without displaying private values.
            • A SHA-256 fingerprint is verified again before restoring, so a changed or corrupted archive cannot silently replace the previously checked file.
            • Rejects invalid or oversized files and malformed preference values before any settings are cleared.
            • New JVM tests cover valid and corrupted archives, unexpected values and bounded reads.
        """.trimIndent(),
    ),
)
