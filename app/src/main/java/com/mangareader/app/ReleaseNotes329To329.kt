package com.mangareader.app

internal val releaseNotes329To329 = listOf(
    ReleaseNote(
        code = 329,
        name = "0.329",
        header = "Speed up library search on large collections",
        body = """
            • Search terms are now normalized once per library arrangement and reused across all titles and source/category groups.
            • Unicode separator and Arabic/diacritic normalization patterns are compiled once instead of being recreated during title matching.
            • Arabic alef variants, ya/alif-maqsura, tatweel, accents, word-order independence and partial matches remain supported.
            • New JVM tests cover reusable compiled queries and empty Unicode-space queries.
        """.trimIndent(),
    ),
)
