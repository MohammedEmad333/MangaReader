package com.mangareader.app

internal val releaseNotes331To331 = listOf(
    ReleaseNote(
        code = 331,
        name = "0.331",
        header = "Open Downloads reliably without internet",
        body = """
            • Show locally verified downloaded chapters while older folder recovery runs.
            • Avoid scanning the full library when downloaded chapters already have identity markers.
            • Unfinished chapters no longer trigger unnecessary full-library recovery.
            • Show a storage warning and Retry action if offline folder inspection takes too long or fails.
            • Add regression tests for offline chapter verification and legacy recovery.
        """.trimIndent(),
    ),
)
