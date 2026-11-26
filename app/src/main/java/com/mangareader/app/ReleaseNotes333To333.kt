package com.mangareader.app

internal val releaseNotes333To333 = listOf(
    ReleaseNote(
        code = 333,
        name = "0.333",
        header = "Load series details and chapters together",
        body = """
            • Fetch series metadata and chapters through one combined request when an extension supports it, reducing duplicate network calls for Asura Scans.
            • Keep older extensions on their existing details and chapter loading path.
            • Update series metadata and the chapter list atomically when opening or refreshing a series.
        """.trimIndent(),
    ),
)
