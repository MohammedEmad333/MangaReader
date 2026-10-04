package com.mangareader.app

internal val releaseNotes278To278 = listOf(
    ReleaseNote(
        code = 278,
        name = "0.278",
        header = "Safer HLS downloads",
        body = "Anime downloads now recognize more signed HLS manifest URLs and verify that servers actually return a valid HLS playlist before saving anything offline. HTML challenge/error pages are rejected instead of appearing as broken completed downloads.",
    ),
)
