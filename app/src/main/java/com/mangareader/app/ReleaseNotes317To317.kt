package com.mangareader.app

internal val releaseNotes317To317 = listOf(
    ReleaseNote(
        code = 317,
        name = "0.317",
        header = "Source Health Center",
        body = """
            • Advanced settings now has a unified source-health overview listing installed sources, unavailable sources with failed downloads, and failure counts.
            • Helpful summaries distinguish rate limits, blocked access, missing pages and connectivity problems without exposing private URLs in the overview.
            • Connection checks remain opt-in and use the existing source probe; opening the center does not make website requests.
            • Tests cover missing sources, duplicate records, failure categorization and source naming.
        """.trimIndent(),
    ),
)
