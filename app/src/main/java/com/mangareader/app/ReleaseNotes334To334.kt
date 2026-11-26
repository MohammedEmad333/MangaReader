package com.mangareader.app

internal val releaseNotes334To334 = listOf(
    ReleaseNote(
        code = 334,
        name = "0.334",
        header = "Serialize extension updates for each series",
        body = """
            • Route combined details-and-chapters requests through the same per-series mutex used by details-only and chapters-only calls.
            • Prevent concurrent requests for the same Asura Scans manga while allowing different series to load independently.
            • Add regression tests for mixed update entry points and per-series concurrency.
        """.trimIndent(),
    ),
)
