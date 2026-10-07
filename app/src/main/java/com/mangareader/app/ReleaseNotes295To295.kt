package com.mangareader.app

internal val releaseNotes295To295 = listOf(
    ReleaseNote(
        code = 295,
        name = "0.295",
        header = "Adding local sources is clearer and easier to scan",
        body = """
            • The source setup dialog now uses clearer surfaced sections for source type, naming and folder access.
            • Local-folder status is easier to understand before and after a folder is selected.
            • The selected folder path is more readable and no longer competes with the primary action.
            • Save now uses a clearer “Save source” action while preserving the existing configuration rules.
            • Source configuration behavior and persisted folder permissions are unchanged; this release focuses on presentation and clarity.
        """.trimIndent(),
    ),
)
