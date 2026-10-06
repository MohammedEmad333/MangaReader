package com.mangareader.app

internal val releaseNotes289To289 = listOf(
    ReleaseNote(
        code = 289,
        name = "0.289",
        header = "Dialogs and update flows now match the new Yomu design",
        body = """
            • Add to Library now presents the selected series, category choices and new-category flow with clearer surfaced states.
            • Category assignment uses rounded selectable rows so the current category membership is easier to scan.
            • What's New now groups each release into expandable cards instead of divider-heavy rows, with the newest update emphasized.
            • The cancel-all-downloads confirmation now makes the destructive action and resume behavior clearer.
            • Tachiyomi backup import now has polished file selection, preview, progress, error and success states before anything is written.
            • Existing category persistence, download cancellation and Tachiyomi import behavior are unchanged; this release focuses on clarity and consistency.
        """.trimIndent(),
    ),
)
