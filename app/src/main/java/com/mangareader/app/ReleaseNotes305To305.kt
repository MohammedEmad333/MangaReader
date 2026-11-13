package com.mangareader.app

internal val releaseNotes305To305 = listOf(
    ReleaseNote(
        code = 305,
        name = "0.305",
        header = "More reliable history deletion and bounded writing",
        body = """
            • Repeated updates from one series no longer let a history batch grow without a limit.
            • History deletion and Clear all now wait behind earlier queued reader updates, so pending writes cannot revive removed entries.
            • History mutations use the same IO worker as reading-progress history writes.
            • Storage errors are reported when removing or clearing history.
            • New tests cover duplicate updates and independent message-count and series-count limits.
        """.trimIndent(),
    ),
)
