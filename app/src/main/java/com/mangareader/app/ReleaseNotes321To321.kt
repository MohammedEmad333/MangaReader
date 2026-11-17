package com.mangareader.app

internal val releaseNotes321To321 = listOf(
    ReleaseNote(
        code = 321,
        name = "0.321",
        header = "Verified automatic backup publishing",
        body = """
            • Automatic backups now validate their saved JSON and re-check bytes written to disk before they become visible for restoration.
            • Incomplete or corrupted writes do not replace a good archive or cause older backups to be pruned.
            • Distinct filenames prevent two backups created within the same minute from overwriting each other.
            • Retention remains five recent validly published archive filenames; unrelated files and incomplete leftovers are left alone.
            • New unit tests cover corrupted temporary files, write interruptions, repeated timestamps and successful cleanup.
        """.trimIndent(),
    ),
)
