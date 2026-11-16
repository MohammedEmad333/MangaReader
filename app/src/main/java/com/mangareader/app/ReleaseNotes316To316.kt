package com.mangareader.app

internal val releaseNotes316To316 = listOf(
    ReleaseNote(
        code = 316,
        name = "0.316",
        header = "Safe offline chapter repair audit",
        body = """
            • Downloads now offers Check offline chapters to inspect saved files for missing or invalid manga pages.
            • Preview the number of complete, repairable and blocked chapters before queueing any work.
            • Safely re-queue only chapters with reliable page counts and unambiguous page filenames; existing valid pages stay on disk.
            • Chapters with changed, missing or ambiguous metadata are left for manual review, never deleted automatically.
            • New unit tests cover damaged chapters, duplicate files, aliases and read-only scan behavior.
        """.trimIndent(),
    ),
)
