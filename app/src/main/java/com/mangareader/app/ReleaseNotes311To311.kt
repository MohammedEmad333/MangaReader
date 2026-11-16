package com.mangareader.app

internal val releaseNotes311To311 = listOf(
    ReleaseNote(
        code = 311,
        name = "0.311",
        header = "Customizable reader tap zones",
        body = """
            • Choose narrow, normal or wide tap zones in paged reader settings.
            • The middle area always toggles reader controls, with page-turn directions correctly mirrored for right-to-left mode.
            • Existing readers keep the original thirds layout by default, and overrides can be saved per series.
            • Unit tests cover both directions, boundary behavior, invalid touches and preference fallbacks.
        """.trimIndent(),
    ),
)
