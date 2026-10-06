package com.mangareader.app

internal val releaseNotes291To291 = listOf(
    ReleaseNote(
        code = 291,
        name = "0.291",
        header = "Chapter lists and extension repositories now match the refreshed Yomu design",
        body = """
            • Chapter and episode rows now use rounded surfaced cards instead of divider-heavy list items.
            • Selected chapters keep a clearer highlighted state while swipe-to-read and swipe-to-bookmark behavior stays intact.
            • Downloaded, queued and active chapter states remain visible inside the refreshed row layout.
            • Extension repositories now use clearer add, loading, empty and saved-repository cards.
            • Copy and remove repository actions are easier to scan, with destructive removal styled separately.
            • Reading, playback, bookmark, download and repository persistence behavior is unchanged; this release focuses on presentation consistency.
        """.trimIndent(),
    ),
)
