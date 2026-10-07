package com.mangareader.app

internal val releaseNotes293To293 = listOf(
    ReleaseNote(
        code = 293,
        name = "0.293",
        header = "Source settings now match the refreshed Yomu design",
        body = """
            • Source preference rows now use rounded surfaced cards with clearer title, summary and control hierarchy.
            • Empty source settings now use a dedicated surfaced state instead of loose text.
            • Single-choice settings now use selectable cards that make the active value easier to scan.
            • Multi-choice settings now show the selected count and use clearer selected states.
            • Text-entry settings now preserve source-provided guidance above the input field.
            • Preference persistence and source behavior are unchanged; this release focuses on consistency and readability.
        """.trimIndent(),
    ),
)
