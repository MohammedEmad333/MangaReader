package com.mangareader.app

internal val releaseNotes339To339 = listOf(
    ReleaseNote(
        code = 339,
        name = "0.339",
        header = "Clear the Cloudflare retry cooldown after WebView success",
        body = """
            • Share the per-host Cloudflare challenge cooldown between the OkHttp interceptor and visible WebView recovery.
            • Clear the host cooldown as soon as the visible WebView confirms a successful page or receives cf_clearance, before returning to the series.
            • Prevent the immediate chapter retry from being rejected by a stale 60-second cooldown created by the earlier hidden attempt.
            • Add a regression test proving a separate WebView-facing instance clears the interceptor's host cooldown.
        """.trimIndent(),
    ),
)
