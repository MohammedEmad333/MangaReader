package com.mangareader.app

internal val releaseNotes279To279 = listOf(
    ReleaseNote(
        code = 279,
        name = "0.279",
        header = "More reliable HLS downloads",
        body = "Long HLS anime downloads now wait for a network connection and run as foreground data-sync work with a low-priority progress notification, making them much less likely to be interrupted when Yomu is backgrounded or the screen is off.",
    ),
)
