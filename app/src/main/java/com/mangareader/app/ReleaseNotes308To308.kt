package com.mangareader.app

internal val releaseNotes308To308 = listOf(
    ReleaseNote(
        code = 308,
        name = "0.308",
        header = "Lighter full-page image memory use",
        body = """
            • Full-resolution reader pages no longer add decoded bitmaps to Coil's shared memory cache as you turn pages.
            • Existing cached images can still be reused, and on-screen pages retain their normal resolution and zoom.
            • The page-picker's bounded thumbnails keep normal caching for quick navigation.
            • New regression tests verify the different cache policies for full pages and previews.
        """.trimIndent(),
    ),
)
