package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupHealthTest {
    @Test fun failedRetryKeepsLastSuccessfulBackupVisible() {
        val state = automaticBackupHealth(100L, 200L, false)
        assertTrue(state.retryRecommended)
        assertTrue(state.status.contains("last successful backup was kept"))
    }

    @Test fun firstFailedBackupMakesMissingSafetyNetExplicit() {
        val state = automaticBackupHealth(0L, 200L, false)
        assertTrue(state.retryRecommended)
        assertTrue(state.status.contains("no successful backup"))
    }

    @Test fun successfulBackupDoesNotRequestRetry() {
        val state = automaticBackupHealth(200L, 200L, true)
        assertFalse(state.retryRecommended)
    }

    @Test fun neverRunDoesNotShowSpuriousFailure() {
        val state = automaticBackupHealth(0L, 0L, false)
        assertFalse(state.retryRecommended)
        assertTrue(state.status.contains("No successful"))
    }
}
