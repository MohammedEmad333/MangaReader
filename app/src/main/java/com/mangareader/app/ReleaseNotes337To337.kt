package com.mangareader.app

internal val releaseNotes337To337 = listOf(
    ReleaseNote(
        code = 337,
        name = "0.337",
        header = "Require Cloudflare clearance before retrying a series",
        body = """
            • Remove the manual Done bypass that reopened the series even when no cf_clearance cookie had been issued.
            • Show whether Cloudflare clearance is actually present in the WebView cookie store.
            • Return to Yomu and retry chapter loading only after the clearance cookie is detected and the WebView User-Agent is recorded.
            • Add a Reload page action for cases where the protected page loads but the verification widget does not appear.
        """.trimIndent(),
    ),
)
