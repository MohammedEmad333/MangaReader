package com.mangareader.app

import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeBackoff
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareChallengeBackoffTest {
    @Test
    fun failedHostIsSuppressedUntilCooldownExpires() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)

        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 1_000L))
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 1_001L))
        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 60_999L))
        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 61_000L))
    }

    @Test
    fun cooldownIsScopedPerHost() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        assertTrue(backoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))
        assertFalse(backoff.isCoolingDown("example.com", nowMillis = 2_000L))
    }

    @Test
    fun successfulRetryClearsCooldown() {
        val backoff = CloudflareChallengeBackoff(cooldownMillis = 60_000L)
        backoff.recordFailure("asurascans.com", nowMillis = 1_000L)

        backoff.clear("asurascans.com")

        assertFalse(backoff.isCoolingDown("asurascans.com", nowMillis = 2_000L))
    }
}
