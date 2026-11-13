package com.mangareader.app

internal val releaseNotes306To306 = listOf(
    ReleaseNote(
        code = 306,
        name = "0.306",
        header = "Reliable history list identity",
        body = """
            • History rows from different extensions no longer share the same list key when their series IDs match.
            • Scroll position tracking uses the same source-qualified row identity as the visible list.
            • Stable keys survive chapter and reading-progress updates within a series.
            • Added regression tests for matching series IDs across sources and delimiter collisions.
        """.trimIndent(),
    ),
)
