package com.mangareader.app

internal val releaseNotes324To324 = listOf(
    ReleaseNote(
        code = 324,
        name = "0.324",
        header = "Restore an older verified backup safely",
        body = """
            • A previously verified automatic backup can now be selected directly from the read-only backup audit.
            • Review restore rechecks the file and opens the existing confirmation showing its fingerprint and warning that settings will be replaced.
            • A selected archive is checked again before any settings are restored; changed or inaccessible files are rejected.
            • Only direct published backup files inside the current folder can be selected, with tests for path traversal and symlinks.
        """.trimIndent(),
    ),
)
