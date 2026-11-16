package com.mangareader.app

internal val releaseNotes310To310 = listOf(
    ReleaseNote(
        code = 310,
        name = "0.310",
        header = "Smarter nearby page loading",
        body = """
            • Page downloads now prioritize the current viewport and nearby pages on both sides, instead of delaying an adjacent page until every distant page in the travel direction has loaded.
            • Large page jumps give a slightly wider look-ahead in the direction of travel while preserving the opposite adjacent page.
            • Outstanding downloads are not cancelled and the chapter still downloads completely; previously downloaded or offline pages remain intact.
            • New unit tests cover direction changes, fast seeks, visible page priority and old-session isolation.
        """.trimIndent(),
    ),
)
