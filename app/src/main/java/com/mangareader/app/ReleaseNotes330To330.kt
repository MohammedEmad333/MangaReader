package com.mangareader.app

internal val releaseNotes330To330 = listOf(
    ReleaseNote(
        code = 330,
        name = "0.330",
        header = "Lighter last-read sorting in the library",
        body = """
            • Sorting by last read now builds its timestamp lookup in one pass, without grouping all history entries into temporary lists.
            • Older imported histories with multiple chapters still use the newest timestamp.
            • Series with the same ID in different sources remain separate; standalone files are excluded.
            • New JVM regression tests cover duplicate timestamps, source identity, empty and standalone history.
        """.trimIndent(),
    ),
)
