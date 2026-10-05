package com.mangareader.app

internal val releaseNotes281To281 = listOf(
    ReleaseNote(
        code = 281,
        name = "0.281",
        header = "Direct anime downloads recover after a failure",
        body = "Direct MP4/MKV/WebM downloads now preserve their request headers and automatically retry once after Android DownloadManager reports a failure, reducing dropped downloads after temporary network or server errors.",
    ),
)
