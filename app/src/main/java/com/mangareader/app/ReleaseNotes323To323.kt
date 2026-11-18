package com.mangareader.app

internal val releaseNotes323To323 = listOf(
    ReleaseNote(
        code = 323,
        name = "0.323",
        header = "Check saved automatic backups",
        body = """
            • Backup settings can now scan earlier automatic backup files and show which ones are verified, invalid or unreadable.
            • The scanner uses the same JSON validation as restoration, without modifying or deleting old backups.
            • The newest verified archive is identified, and large folders are scanned with a 100-file limit.
            • Restore still re-verifies the selected file before replacing settings. Tests cover damaged files, untouched files and scan limits.
        """.trimIndent(),
    ),
)
