package com.mangareader.app

internal val releaseNotes312To312 = listOf(
    ReleaseNote(
        code = 312,
        name = "0.312",
        header = "Adaptive wide-page orientation",
        body = """
            • New Auto (portrait) mode rotates landscape manga images for portrait reading but keeps their natural orientation when the display is landscape.
            • Existing Fit and Rotate 90° modes remain unchanged; the default stays Fit.
            • Rotation is decided using image dimensions off the main thread, retaining full-resolution display and pinch zoom.
            • JVM tests cover orientation changes, invalid viewport dimensions and saved preference fallback.
        """.trimIndent(),
    ),
)
