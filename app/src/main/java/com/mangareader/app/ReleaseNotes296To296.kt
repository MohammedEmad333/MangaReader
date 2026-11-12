package com.mangareader.app

internal val releaseNotes296To296 = listOf(
    ReleaseNote(
        code = 296,
        name = "0.296",
        header = "Update multiple extensions from a single action",
        body = """
            • A new Update all button starts updates for outdated extensions shown in the Extensions list.
            • Yomu downloads and presents one APK at a time, while Android asks for installation approval.
            • After each installer closes, Yomu checks the installed version before reporting success.
            • Progress, failed updates and a final summary are visible, with an option to cancel remaining updates.
            • Individual extension installation and existing APK download protections remain available.
        """.trimIndent(),
    ),
)
