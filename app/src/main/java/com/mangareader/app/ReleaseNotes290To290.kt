package com.mangareader.app

internal val releaseNotes290To290 = listOf(
    ReleaseNote(
        code = 290,
        name = "0.290",
        header = "Global search and offline anime now use the refreshed Yomu surfaces",
        body = """
            • Global search controls now live in one rounded panel with wrapped filters, clearer progress and a cleaner migration hint.
            • Empty global-search states and recent searches now use surfaced cards instead of loose divider-heavy content.
            • Per-source global-search results are grouped into rounded cards while preserving duplicate-result protection and existing callbacks.
            • Active anime downloads now show clearer progress cards, retry/delete actions and status hierarchy.
            • Offline anime entries now use rounded playable cards with clearer format, quality and destructive-action treatment.
            • Search, migration, download, retry, delete and playback behavior are unchanged; this release focuses on presentation and scanability.
        """.trimIndent(),
    ),
)
