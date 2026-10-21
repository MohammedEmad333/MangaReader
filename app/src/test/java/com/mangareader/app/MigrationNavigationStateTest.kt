package com.mangareader.app

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationNavigationStateTest {
    @Test fun beginReplacesPreviousOrigin() {
        val state = MigrationNavigationState()
        state.begin(MigrateFrom("old", "source-a", "Old"))
        state.begin(MigrateFrom("new", "source-b", "New"))
        assertEquals("new", state.from?.seriesId)
        assertNull(state.target)
    }

    @Test fun clearDiscardsMigrationOriginAndSelectedTarget() {
        val state = MigrationNavigationState()
        state.from = MigrateFrom("old-series", "old-source", "Original title")
        state.clear()
        assertNull(state.from)
        assertNull(state.target)
    }
}
