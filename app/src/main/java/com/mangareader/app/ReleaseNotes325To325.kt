package com.mangareader.app

internal val releaseNotes325To325 = listOf(
    ReleaseNote(
        code = 325,
        name = "0.325",
        header = "Safer backup archive restore checks",
        body = """
            • Backup archive scans now refuse to show symlinked backup files as verified restore candidates.
            • Android device tests verify that reviewing and cancelling a restore leaves local settings and saved archives unchanged.
            • If a selected backup changes after verification, the restore fails before replacing local preferences.
            • Additional unit and Android tests cover unsafe paths, missing files, partial archives and damaged backups.
        """.trimIndent(),
    ),
)
