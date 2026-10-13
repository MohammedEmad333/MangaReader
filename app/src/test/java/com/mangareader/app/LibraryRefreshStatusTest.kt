package com.mangareader.app

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRefreshStatusTest {
    @Test fun noTimestampBeforeFirstRun() {
        assertNull(lastRefreshLabel(0))
        assertNull(lastRefreshLabel(-1))
    }

    @Test fun completedRefreshShowsTimestamp() {
        val label = lastRefreshLabel(1_700_000_000_000L)
        assertTrue(label?.startsWith("Last refresh finished: ") == true)
        assertTrue(label!!.length > "Last refresh finished: ".length)
    }
}
