package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPageAcceptanceTest {
    @Test
    fun successfulPageWithoutClearanceCanShareItsBrowserSession() {
        assertTrue(shouldAcceptWebViewPage(httpStatusCode = 200, challengeDetected = false))
    }

    @Test
    fun challengeDocumentIsNotAcceptedEvenWhenItReturnsOk() {
        assertFalse(shouldAcceptWebViewPage(httpStatusCode = 200, challengeDetected = true))
    }

    @Test
    fun forbiddenAndUnavailableResponsesAreNotAccepted() {
        assertFalse(shouldAcceptWebViewPage(httpStatusCode = 403, challengeDetected = false))
        assertFalse(shouldAcceptWebViewPage(httpStatusCode = 503, challengeDetected = false))
    }

    @Test
    fun unknownResponseStatusIsNotAccepted() {
        assertFalse(shouldAcceptWebViewPage(httpStatusCode = 0, challengeDetected = false))
    }

    @Test
    fun sameHostAndItsSubdomainsAreAccepted() {
        assertTrue(isSameHostOrSubdomain("asurascans.com", "asurascans.com"))
        assertTrue(isSameHostOrSubdomain("asurascans.com", "www.asurascans.com"))
        assertTrue(isSameHostOrSubdomain("www.asurascans.com", "cdn.asurascans.com"))
    }

    @Test
    fun unrelatedHostIsRejected() {
        assertFalse(isSameHostOrSubdomain("asurascans.com", "example.com"))
        assertFalse(isSameHostOrSubdomain("", "asurascans.com"))
    }

    @Test
    fun challengeScriptChecksTextAndTurnstileElements() {
        assertTrue(CLOUDFLARE_CHALLENGE_DETECT_SCRIPT.contains("just a moment"))
        assertTrue(CLOUDFLARE_CHALLENGE_DETECT_SCRIPT.contains("cf-turnstile-response"))
        assertTrue(CLOUDFLARE_CHALLENGE_DETECT_SCRIPT.contains("challenges.cloudflare.com"))
    }
}
