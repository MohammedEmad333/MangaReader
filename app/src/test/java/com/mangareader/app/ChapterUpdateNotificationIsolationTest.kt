package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterUpdateNotificationIsolationTest {
    @Test fun successfulNotificationReturnsTrue() {
        var invoked = false
        assertTrue(safelyPostChapterUpdate { invoked = true })
        assertTrue(invoked)
    }

    @Test fun notificationErrorDoesNotEscape() {
        assertFalse(safelyPostChapterUpdate { throw SecurityException("permission revoked") })
        assertFalse(safelyPostChapterUpdate { throw IllegalStateException("channel unavailable") })
    }
}
