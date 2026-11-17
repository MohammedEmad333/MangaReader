package com.mangareader.app

internal val releaseNotes320To320 = listOf(
    ReleaseNote(
        code = 320,
        name = "0.320",
        header = "Safer backup restoration on write failure",
        body = """
            • Restoring a backup now snapshots existing preference stores before replacing any of them.
            • If writing a later store fails, Yomu attempts to roll back every touched store, including the interrupted one, instead of leaving a partially imported backup.
            • Existing preferences keep their original value types, including strings, numbers, booleans and string sets.
            • A new Android instrumentation test simulates a multi-store restore failure and verifies the original settings remain intact.
            • This protects against ordinary write errors, but does not guarantee atomic restoration across app process termination or power loss.
        """.trimIndent(),
    ),
)
