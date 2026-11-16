package com.mangareader.app

internal val releaseNotes319To319 = listOf(
    ReleaseNote(
        code = 319,
        name = "0.319",
        header = "Android reader and restore regression coverage",
        body = """
            • Real Android emulator tests now cover backup verification, successful restore and prevention of corrupted or changed-file restores.
            • The real paged reader is exercised with paired pages, an odd final page and correct right-to-left page placement.
            • Fast back-and-forth page seeking is checked for complete, unique download priorities.
            • Pull requests touching reader navigation or backup logic run the connected Android test suite as a separate CI gate.
        """.trimIndent(),
    ),
)
