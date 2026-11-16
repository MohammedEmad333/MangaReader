package com.mangareader.app

internal val releaseNotes314To314 = listOf(
    ReleaseNote(
        code = 314,
        name = "0.314",
        header = "Two-page landscape tablet reader",
        body = """
            • Optional side-by-side pages on landscape tablets 840dp wide or larger; phones and vertical strip keep the previous layout.
            • Both reading directions show consecutive original manga pages in reading order.
            • Seeking, resuming, and saving progress map paired spreads back to their original chapter page numbers.
            • Regression tests cover odd chapter lengths, device eligibility, and logical progress mapping.
        """.trimIndent(),
    ),
)
