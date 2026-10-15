package com.mangareader.app

import androidx.work.ExistingPeriodicWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateScheduleRecoveryTest {
    @Test fun startupDoesNotResetExistingPeriod() {
        assertEquals(
            ExistingPeriodicWorkPolicy.KEEP,
            chapterUpdateExistingWorkPolicy(preserveExisting = true),
        )
    }

    @Test fun userPreferenceChangesUpdateExistingConstraints() {
        assertEquals(
            ExistingPeriodicWorkPolicy.UPDATE,
            chapterUpdateExistingWorkPolicy(preserveExisting = false),
        )
    }
}
