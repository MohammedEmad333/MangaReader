package com.mangareader.app

internal val releaseNotes286To286 = listOf(
    ReleaseNote(
        code = 286,
        name = "0.286",
        header = "Queue and data screens join the new Yomu design",
        body = """
            • The chapter download queue now uses clearer status cards, compact progress states and grouped controls.
            • Failed downloads are easier to scan, retry and dismiss without losing the surrounding queue context.
            • The Settings index now uses the same rounded card language as the rest of Yomu.
            • Data & storage pages now group storage locations, backups, cache usage and maintenance actions into cleaner surfaces.
            • Queue, backup, storage and download behavior are unchanged; this release focuses on presentation and hierarchy.
        """.trimIndent(),
    ),
)
