package com.mangareader.app

internal val releaseNotes313To313 = listOf(
    ReleaseNote(
        code = 313,
        name = "0.313",
        header = "Split view for wide manga spreads",
        body = """
            • Optional Split wide page mode lets you inspect the first and second halves of a landscape manga spread.
            • RTL manga show the right half first; LTR manga show the left half first.
            • Both halves share the same original page and saved progress, without creating two decoded image copies or files.
            • Tests cover spread aspect ratios, invalid images, direction and asynchronous image inspection.
        """.trimIndent(),
    ),
)
