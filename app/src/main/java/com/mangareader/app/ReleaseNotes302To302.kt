package com.mangareader.app

internal val releaseNotes302To302 = listOf(
    ReleaseNote(
        code = 302,
        name = "0.302",
        header = "Safer page display after changing reader settings",
        body = """
            • Async page inspection results now belong to both their image and wide-page display mode.
            • Quickly changing a page or rotating an image cannot reuse a previous mode's inspection.
            • Page loading continues to happen without blocking the reader interface.
            • Added regression tests for stale inspection results and reader setting changes.
        """.trimIndent(),
    ),
)
