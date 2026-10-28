package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceBrowseHealthTest {
    @Test
    fun emptySnapshotHasNoRecordedObservation() {
        val health = SourceBrowseHealth()
        assertNull(health.lastSuccessAt)
        assertNull(health.lastFailureAt)
        assertNull(health.lastError)
        assertEquals(0, health.successes)
        assertEquals(0, health.failures)
    }

    @Test
    fun subsequentSuccessPreservesFailureHistoryButClearsCurrentError() {
        val failed = SourceBrowseHealth(
            lastFailureAt = 100L,
            lastError = "Timeout",
            failures = 1,
        )
        val recovered = failed.copy(lastSuccessAt = 200L, lastError = null, successes = 1)
        assertEquals(100L, recovered.lastFailureAt)
        assertEquals(200L, recovered.lastSuccessAt)
        assertNull(recovered.lastError)
        assertEquals(1, recovered.failures)
        assertEquals(1, recovered.successes)
    }

    @Test
    fun diagnosticsHandlesNoObservationsAndLargeCounts() {
        assertEquals(SourceHealthDiagnostics(0L, null, "Not checked", null), sourceHealthDiagnostics(null))
        val maximum = SourceBrowseHealth(
            successes = Int.MAX_VALUE,
            failures = Int.MAX_VALUE,
            lastSuccessAt = 123L,
        )
        val summary = sourceHealthDiagnostics(maximum)
        assertEquals(4294967294L, summary.totalRequests)
        assertEquals(50, summary.successPercent)
        assertEquals("Last request successful", summary.currentStatus)
    }

    @Test
    fun diagnosticsDistinguishesLatestFailureAndNeverClaimsLiveAvailability() {
        val snapshot = SourceBrowseHealth(
            lastSuccessAt = 100L, lastFailureAt = 200L,
            lastError = "Connection refused", successes = 3, failures = 1,
        )
        val summary = sourceHealthDiagnostics(snapshot)
        assertEquals(4L, summary.totalRequests)
        assertEquals(75, summary.successPercent)
        assertEquals("Last request failed", summary.currentStatus)
        assertEquals(200L, summary.lastAttemptAt)
    }
}
