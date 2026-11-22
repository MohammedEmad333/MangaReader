package com.mangareader.app

internal val releaseNotes332To332 = listOf(
    ReleaseNote(
        code = 332,
        name = "0.332",
        header = "Reconnect offline download storage more reliably",
        body = """
            • Recheck the selected download location whenever Downloads is opened or Retry is tapped, including after an SD card is reconnected.
            • Keep verified local chapters readable without depending on a network request.
            • Add an Android device-backed test that lists a saved chapter and hides it immediately when its page is removed.
        """.trimIndent(),
    ),
)
