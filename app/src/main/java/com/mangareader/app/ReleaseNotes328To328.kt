package com.mangareader.app

internal val releaseNotes328To328 = listOf(
    ReleaseNote(
        code = 328,
        name = "0.328",
        header = "Keep large cover-repair bursts lightweight",
        body = """
            • Missing or moved covers are recorded in memory immediately, so the refresh sweep can see them without waiting for disk.
            • Persistence is grouped on a background writer: a batch is saved after 32 changes or a short 350 ms batch window.
            • Large library scrolls no longer enqueue one full SharedPreferences rewrite for every failed cover.
            • Duplicate suppression, the 2,000-entry safety cap and removal after a successful cover repair are preserved.
            • New JVM tests cover deduplication, capacity, clearing and snapshot isolation.
        """.trimIndent(),
    ),
)
