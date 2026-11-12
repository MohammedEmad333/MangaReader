package com.mangareader.app

internal val releaseNotes299To299 = listOf(
    ReleaseNote(
        code = 299,
        name = "0.299",
        header = "Better feedback for slow pages, smoother reader progress",
        body = """
            • Loading pages now show their page numbers instead of an unexplained spinner.
            • Slow connections show helpful messages after 12 and 30 seconds without interrupting downloads.
            • End-of-chapter file validation runs on an IO dispatcher instead of blocking reader progress on the main thread.
            • A successful retry can still complete a previously incomplete chapter.
            • JVM tests cover slow-load thresholds and final-page validation decisions.
        """.trimIndent(),
    ),
)
