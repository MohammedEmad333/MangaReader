package com.mangareader.app

internal val releaseNotes307To307 = listOf(
    ReleaseNote(
        code = 307,
        name = "0.307",
        header = "Lighter page-picker thumbnails",
        body = """
            • The reader's page-picker now asks the image loader to decode thumbnails at their actual display size, rather than requesting full-resolution pages.
            • Thumbnail requests are bounded at 512 pixels per edge on unusually high-density screens.
            • The full-size reading and zoomable page views are unchanged.
            • New regression tests cover normal, extreme and invalid display densities.
        """.trimIndent(),
    ),
)
