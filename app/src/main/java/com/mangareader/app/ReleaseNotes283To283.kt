package com.mangareader.app

internal val releaseNotes283To283 = listOf(
    ReleaseNote(
        code = 283,
        name = "0.283",
        header = "Failed anime downloads are easier to recover",
        body = "The anime download manager now keeps terminal direct-download failures visible instead of silently discarding them. Failed items offer Retry and Delete actions, automatic retry states are labeled more clearly, and HLS retries are shown as retrying while WorkManager resumes the saved segments.",
    ),
)
