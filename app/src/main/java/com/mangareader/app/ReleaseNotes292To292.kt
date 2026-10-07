package com.mangareader.app

internal val releaseNotes292To292 = listOf(
    ReleaseNote(
        code = 292,
        name = "0.292",
        header = "Video stream pickers are clearer and easier to scan",
        body = """
            • Episode/chapter stream discovery now uses surfaced loading, empty and result states instead of plain text blocks.
            • Direct video streams and embedded players are visually separated with clearer primary actions.
            • Embedded media links now use compact playable cards and clearer fallback messaging when JavaScript hides the actual stream URL.
            • Long source notes remain available in a quieter diagnostic style without overpowering the main actions.
            • Existing scanning, embed opening and playback callbacks are unchanged; this release focuses on presentation and selection clarity.
        """.trimIndent(),
    ),
)
