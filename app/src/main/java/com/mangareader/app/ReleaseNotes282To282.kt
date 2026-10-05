package com.mangareader.app

internal val releaseNotes282To282 = listOf(
    ReleaseNote(
        code = 282,
        name = "0.282",
        header = "HLS anime downloads now resume after interruptions",
        body = "Segmented HLS downloads now keep completed segments when Android interrupts the worker or a temporary failure occurs. Yomu retries automatically up to two times and reuses the saved segments instead of restarting the episode from the beginning.",
    ),
)
