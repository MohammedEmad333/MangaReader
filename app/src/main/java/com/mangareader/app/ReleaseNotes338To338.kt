package com.mangareader.app

internal val releaseNotes338To338 = listOf(
    ReleaseNote(
        code = 338,
        name = "0.338",
        header = "Sync successful WebView sessions and speed up Cloudflare fallback",
        body = """
            • Treat a successful same-host WebView page load as a usable browser session even when Cloudflare does not issue a cf_clearance cookie.
            • Sync the shared WebView cookies and its native User-Agent before retrying source requests.
            • Shorten the hidden automatic Cloudflare challenge attempt from 30 seconds to 8 seconds.
            • Apply a 60-second per-host cooldown after a failed hidden attempt so queued image and chapter requests do not each create another WebView and wait.
        """.trimIndent(),
    ),
)
