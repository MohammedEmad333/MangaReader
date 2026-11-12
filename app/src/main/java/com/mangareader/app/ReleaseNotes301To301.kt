package com.mangareader.app

internal val releaseNotes301To301 = listOf(
    ReleaseNote(
        code = 301,
        name = "0.301",
        header = "Smoother page display and more reliable resume",
        body = """
            • Image header checks and wide-page dimension inspection now run off the main reader thread.
            • Normal page modes skip unnecessary image-dimension decoding.
            • Marking a chapter unread also clears the matching session-resume checkpoint, without touching other chapters.
            • Offline chapters remain protected from reader cache re-download actions.
            • Added regression tests for image inspection and checkpoint matching.
        """.trimIndent(),
    ),
)
