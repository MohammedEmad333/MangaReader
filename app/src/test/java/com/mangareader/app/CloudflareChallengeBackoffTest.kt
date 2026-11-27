package com.mangareader.app

import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeBackoff
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareChallengeBackoffTest {
    @Test
    fun failedHostIsSuppressedUntilCooldownExpires() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        backoff.clear("asurascans.com")

        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 1_000L))
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 1_001L))
        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 60_999L))
        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 61_000L))
    }

    @Test
    fun cooldownIsScopedPerHost() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        backoff.clear("asurascans.com")
        backoff.clear("example.com")
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))
        assertFalse(backoff.isCoolingDown("example.com", nowMillis = 2_000L))
    }

    @Test
    fun successfulRetryClearsCooldown() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        backoff.clear("asurascans.com")
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        backoff.clear("asurascans.com")

        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))
    }

    @Test
    fun visibleWebViewCanClearCooldownCreatedByInterceptorInstance() {
        val interceptorBackoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        val visibleWebViewBackoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        interceptorBackoff.clear("asurascans.com")
        interceptorBackoff.recordFailure("asurascans.com", nowMillis = 1_000L)
        assertTrue(interceptorBackoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))

        visibleWebViewBackoff.clearHost("asurascans.com")

        assertFalse(interceptorBackoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))
    }
}
