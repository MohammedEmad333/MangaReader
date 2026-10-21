package com.mangareader.app

import org.junit.Assert.assertNull
import org.junit.Test

class MigrationNavigationStateTest {
    @Test fun clearDiscardsMigrationOriginAndSelectedTarget() {
        val state = MigrationNavigationState()
        state.from = MigrateFrom("old-series", "old-source", "Original title")
        state.clear()
        assertNull(state.from)
        assertNull(state.target)
    }
}
