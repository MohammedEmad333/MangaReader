package com.mangareader.app

internal val releaseNotes277To277 = listOf(
    ReleaseNote(
        code = 277,
        name = "0.277",
        header = "Safer anime downloads",
        body = "Anime downloads now avoid duplicate offline and direct-download requests, HLS downloads use unique WorkManager jobs, and direct video files avoid destination-name collisions when another quality or episode file already uses the same name.",
    ),
)
