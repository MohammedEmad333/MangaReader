package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDestinationTest {
    @Test fun opensUpdatesTabForChapterNotification() {
        assertEquals(5, notificationDestination(openQueue = false, openUpdates = true))
    }

    @Test fun preservesDownloadQueueNotificationNavigation() {
        assertEquals(3, notificationDestination(openQueue = true, openUpdates = false))
    }

    @Test fun doesNotNavigateForNormalAppLaunch() {
        assertNull(notificationDestination(openQueue = false, openUpdates = false))
    }

    @Test fun prioritizesUpdatesWhenBothFlagsArePresent() {
        assertEquals(5, notificationDestination(openQueue = true, openUpdates = true))
    }
}
