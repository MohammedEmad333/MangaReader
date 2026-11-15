package com.mangareader.app

internal val releaseNotes309To309 = listOf(
    ReleaseNote(
        code = 309,
        name = "0.309",
        header = "Fresh pages after re-download",
        body = """
            • Replacing a downloaded page at the same file path now refreshes the reader's image inspection and decoded display request.
            • Page-picker previews use file-versioned cache keys, avoiding stale thumbnails after a page is replaced.
            • Explicit retries force a new image request even if the file timestamp has not changed.
            • New tests verify same-path replacements, retry generations and cache-key isolation.
        """.trimIndent(),
    ),
)
