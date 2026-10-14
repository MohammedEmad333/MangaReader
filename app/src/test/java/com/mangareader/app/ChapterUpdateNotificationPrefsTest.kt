package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterUpdateNotificationPrefsTest {
    @Test fun alertsRequireExplicitOptIn() {
        assertFalse(shouldNotifyChapterUpdates(false, true, 1))
    }

    @Test fun alertsRequireNotificationPermission() {
        assertFalse(shouldNotifyChapterUpdates(true, false, 2))
    }

    @Test fun alertsRequireNewReleases() {
        assertFalse(shouldNotifyChapterUpdates(true, true, 0))
        assertFalse(shouldNotifyChapterUpdates(true, true, -1))
    }

    @Test fun optedInPermittedNewReleasesAreEligible() {
        assertTrue(shouldNotifyChapterUpdates(true, true, 2))
    }
}
