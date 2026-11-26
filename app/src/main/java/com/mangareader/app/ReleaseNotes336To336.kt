package com.mangareader.app

internal val releaseNotes336To336 = listOf(
    ReleaseNote(
        code = 336,
        name = "0.336",
        header = "Open Cloudflare challenges on the exact series page",
        body = """
            • The series screen now opens the extension's exact manga URL in the visible WebView when Cloudflare blocks chapter loading, rather than opening only the source homepage.
            • After the challenge is solved, the existing recovery flow automatically reopens the series and retries its details and chapters.
            • Connection probe stops after a confirmed series-page challenge instead of issuing a duplicate request that waits through a second headless challenge timeout.
            • Give the combined request enough time for the Cloudflare interceptor to return its original HTTP error when the challenge cannot be solved headlessly.
        """.trimIndent(),
    ),
)
