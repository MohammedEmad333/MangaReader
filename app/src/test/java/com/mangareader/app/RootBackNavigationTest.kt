package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBackNavigationTest {
    @Test fun secondaryTabsReturnHomeBeforeExit() {
        for (tab in 1..4) {
            assertEquals(RootBackAction.HOME, rootBackAction(tab, 100L, 150L))
        }
    }

    @Test fun firstBackOnHomePromptsAndSecondWithinWindowExits() {
        assertEquals(RootBackAction.CONFIRM_EXIT, rootBackAction(0, 0L, 1000L))
        assertEquals(RootBackAction.EXIT, rootBackAction(0, 1000L, 2999L))
    }

    @Test fun expiredClockRewindAndBoundaryNeverExit() {
        assertEquals(RootBackAction.CONFIRM_EXIT, rootBackAction(0, 1000L, 3000L))
        assertEquals(RootBackAction.CONFIRM_EXIT, rootBackAction(0, 2000L, 1500L))
    }
}
