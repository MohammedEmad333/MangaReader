package com.mangareader.app

internal val releaseNotes304To304 = listOf(
    ReleaseNote(
        code = 304,
        name = "0.304",
        header = "Smoother, safer reading history updates",
        body = """
            • Consecutive reading-history updates for the same series are combined before writing the history file.
            • History batches keep each series' most recent update and preserve the order of reading across sources.
            • Small bounded batches reduce write churn while keeping the UI responsive.
            • Closing the reader flushes earlier history updates before refreshing the History tab.
            • Storage failures are reported without blocking navigation back to the library.
            • Reader page position and resume checkpoint still save immediately.
        """.trimIndent(),
    ),
)
