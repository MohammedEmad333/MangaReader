package com.mangareader.app

internal val releaseNotes327To327 = listOf(
    ReleaseNote(
        code = 327,
        name = "0.327",
        header = "Keep new changes safe during backup confirmation",
        body = """
            • Restoring a backup now checks whether the selected local settings changed since the restore preview.
            • If your library, reading position or other settings were updated while the confirmation was open, Yomu refuses the old confirmation and asks you to review the impact again.
            • Device-local storage paths and automatic-backup status stay on this installation, even when restoring older archives.
            • No private settings are included in the displayed error; the comparison uses an in-memory SHA-256 fingerprint.
            • New JVM and Android tests verify unchanged restores, stale previews and stable string-set ordering.
        """.trimIndent(),
    ),
)
