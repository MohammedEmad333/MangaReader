package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchHasResultsPreferenceTest {

    @Test
    fun globalSearchState_usesSavedHasResultsPreference() {
        val enabled = GlobalSearchState(
            initialPinnedOnly = true,
            initialHasResultsOnly = true,
            initialMediaFilter = "All",
            initialRecents = emptyList(),
        )
        val disabled = GlobalSearchState(
            initialPinnedOnly = true,
            initialHasResultsOnly = false,
            initialMediaFilter = "All",
            initialRecents = emptyList(),
        )

        assertTrue(enabled.hasResultsOnly)
        assertFalse(disabled.hasResultsOnly)
    }
}
