package com.mangareader.app

/**
 * A WebView session can be shared with OkHttp when the actual main-frame page
 * succeeded and the document is not a Cloudflare challenge. A cf_clearance
 * cookie is useful, but is not required when the site serves the page to the
 * WebView's native User-Agent without issuing one.
 */
internal fun shouldAcceptWebViewPage(
    httpStatusCode: Int,
    challengeDetected: Boolean,
): Boolean = httpStatusCode in 200..399 && !challengeDetected

/** Allows same-host redirects and subdomains, but never unrelated destinations. */
internal fun isSameHostOrSubdomain(expectedHost: String, actualHost: String): Boolean {
    val expected = expectedHost.trim().lowercase().removePrefix("www.")
    val actual = actualHost.trim().lowercase().removePrefix("www.")
    return expected.isNotEmpty() &&
        actual.isNotEmpty() &&
        (actual == expected || actual.endsWith(".$expected"))
}

/**
 * Runs inside the page after the main frame finishes. Cloudflare can sometimes
 * return a challenge document without a visible checkbox, so inspect both text
 * and its known challenge elements before treating a 2xx page as accessible.
 */
internal const val CLOUDFLARE_CHALLENGE_DETECT_SCRIPT = """
(function() {
    const title = document.title || "";
    const body = document.body ? document.body.innerText.slice(0, 5000) : "";
    const text = (title + " " + body).toLowerCase();
    const markers = /just a moment|checking your browser|verify you are human|performing security verification|enable javascript and cookies to continue|attention required/;
    const widget = document.querySelector(
        "#challenge-form, #cf-challenge-running, .cf-challenge, " +
        "input[name='cf-turnstile-response'], " +
        "iframe[src*='challenges.cloudflare.com'], [id^='cf-chl-']"
    );
    return markers.test(text) || !!widget;
})()
"""
