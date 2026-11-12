package com.mangareader.app

internal val releaseNotes297To297 = listOf(
    ReleaseNote(
        code = 297,
        name = "0.297",
        header = "Safer manga chapter switching and page retries",
        body = """
            • Reader screen state now follows the full source, series and chapter identity, even when different series reuse chapter IDs.
            • Retry requests are ignored when they no longer belong to the chapter currently displayed.
            • Late chapter results cannot be shown for a different active series.
            • Regression tests cover cross-series chapter identity and invalid retry attempts.
            • Existing page loading, retry and offline-reading behavior is preserved.
        """.trimIndent(),
    ),
)
