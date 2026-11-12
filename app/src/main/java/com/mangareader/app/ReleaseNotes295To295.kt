package com.mangareader.app

internal val releaseNotes295To295 = listOf(
    ReleaseNote(
        code = 295,
        name = "0.295",
        header = "Extension updates are easier to find across repositories",
        body = """
            • When an extension appears in several repositories, Yomu now displays its newest available version.
            • Update buttons are no longer hidden by older duplicate catalogue entries.
            • Repository order stays predictable when available versions match.
            • Adult-content filtering remains effective if any repository marks a duplicate extension as adult content.
            • APK download validation and cached-file cleanup from recent maintenance releases remain in place.
        """.trimIndent(),
    ),
)
