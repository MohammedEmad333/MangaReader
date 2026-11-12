package com.mangareader.app

internal val releaseNotes303To303 = listOf(
    ReleaseNote(
        code = 303,
        name = "0.303",
        header = "More efficient reading progress saves",
        body = """
            • Reader progress saves are deduplicated when a loading update or recomposition reports the same page again.
            • Every actual settled-page change still persists immediately for reliable resume after closing or restarting the app.
            • Page-completion checks still run when streaming or retry status changes.
            • New regression tests cover backwards navigation, empty chapters, page bounds and switching chapters.
        """.trimIndent(),
    ),
)
