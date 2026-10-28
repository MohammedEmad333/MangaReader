package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
