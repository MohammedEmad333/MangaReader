package com.mangareader.app

internal val releaseNotes298To298 = listOf(
    ReleaseNote(
        code = 298,
        name = "0.298",
        header = "Reader completion no longer skips missing pages",
        body = """
            • Chapters are marked as read only after their final page is reached and all chapter pages are usable.
            • Missing, empty, interrupted or invalid downloaded images no longer make an incomplete chapter look finished.
            • Page-by-page reading progress still resumes normally; retry controls remain available for failed pages.
            • JVM regression tests cover missing files, invalid image bodies, and unfinished loading.
        """.trimIndent(),
    ),
)
