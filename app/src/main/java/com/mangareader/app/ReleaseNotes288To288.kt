package com.mangareader.app

internal val releaseNotes288To288 = listOf(
    ReleaseNote(
        code = 288,
        name = "0.288",
        header = "Reader and source controls get the full Yomu treatment",
        body = """
            • Reader defaults now use the same rounded settings panels as the rest of Yomu, with clearer grouping for layout, screen and colour controls.
            • The Sources visibility screen now groups languages and sources into scan-friendly cards with live shown counts.
            • Source filter rows now make include, exclude, sort, text and selection states easier to understand at a glance.
            • Source browsing controls now use polished mode chips, a surfaced search area and clearer view context in the top bar.
            • The source filter dialog now has stronger hierarchy without changing how extension filter state, Reset or Apply work.
            • Existing reader preferences, source visibility rules, browsing callbacks and extension filter behavior are unchanged.
        """.trimIndent(),
    ),
)
