package com.mangareader.app

internal val releaseNotes315To315 = listOf(
    ReleaseNote(
        code = 315,
        name = "0.315",
        header = "More resilient chapter downloads",
        body = """
            • Temporary connection errors, DNS interruptions and timeouts get one bounded chapter retry after page-level retries are exhausted.
            • Already saved page files are reused, so the app does not download completed pages again.
            • Invalid images, broken chapter handles and other permanent failures still appear in Failed downloads without looping.
            • Pause, skip and cancel remain immediately cancellable, with JVM tests for retry limits and failure categories.
        """.trimIndent(),
    ),
)
