package com.mangareader.app

internal val releaseNotes326To326 = listOf(
    ReleaseNote(
        code = 326,
        name = "0.326",
        header = "Preview changes before restoring a backup",
        body = """
            • Before confirming restore, see an estimate of settings that will be added, changed, removed or left unchanged.
            • The comparison is read-only and does not display private setting names, values or source credentials.
            • Device-local storage locations are excluded, because restore preserves those settings.
            • The selected backup is still revalidated and fingerprint-checked before any restore writes.
            • JVM and Android tests verify counts, string-set ordering and unchanged preferences during preview.
        """.trimIndent(),
    ),
)
