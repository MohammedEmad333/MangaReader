package com.mangareader.app

internal val releaseNotes300To300 = listOf(
    ReleaseNote(
        code = 300,
        name = "0.300",
        header = "Accurate reading progress after fast page swipes",
        body = """
            • Paged reading now saves the page where a swipe settles, not intermediate pages passed during a fling.
            • Prevents accidentally marking a chapter read when a fast gesture briefly passes its last page.
            • Right-to-left and left-to-right paging use the same stable progress behavior.
            • Long-strip reading keeps its existing visible-page tracking.
            • Regression tests cover fast swipes, chapter transition rows, and empty chapters.
        """.trimIndent(),
    ),
)
