package com.mangareader.app

internal val releaseNotes322To322 = listOf(
    ReleaseNote(
        code = 322,
        name = "0.322",
        header = "Backup recovery and retry",
        body = """
            • Automatic backups now record whether the latest attempt succeeded without replacing the timestamp of the last successful backup when it fails.
            • Backup settings explain a failed automatic attempt and offer an explicit Retry action.
            • A manual retry uses the same verified automatic-backup writer, reports success or failure and keeps older backups intact on failure.
            • Unit tests cover the health summary for successful, failed and never-run backups.
        """.trimIndent(),
    ),
)
