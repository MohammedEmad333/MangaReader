package com.mangareader.app

internal val releaseNotes335To335 = listOf(
    ReleaseNote(
        code = 335,
        name = "0.335",
        header = "Diagnose series and chapter loading",
        body = """
            • Extend Connection probe beyond the homepage and Popular list to test a real series page, the combined details-and-chapters request, and one non-premium chapter page list.
            • Report the manga URL, response status, Cloudflare markers, elapsed time, returned chapter count, and page-list result without exposing cookie values.
            • Bound the combined fetch and page-list checks so a stalled source produces a diagnostic timeout instead of an endless probe.
        """.trimIndent(),
    ),
)
