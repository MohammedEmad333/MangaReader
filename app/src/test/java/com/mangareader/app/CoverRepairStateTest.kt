package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverRepairStateTest {
    @Test fun newReportsAreVisibleImmediatelyAndDuplicatesAreIgnored() {
        val state = CoverRepairState(limit = 10)

        assertTrue(state.report("series-a"))
        assertFalse(state.report("series-a"))
        assertEquals(setOf("series-a"), state.snapshot())
    }

    @Test fun capDoesNotEvictExistingRepairRequests() {
        val state = CoverRepairState(limit = 2)

        assertTrue(state.report("series-a"))
        assertTrue(state.report("series-b"))
        assertFalse(state.report("series-c"))
        assertEquals(setOf("series-a", "series-b"), state.snapshot())
    }

    @Test fun clearRemovesOnlySuccessfullyRepairedSeries() {
        val state = CoverRepairState(
            initialIds = listOf("series-a", "series-b", "series-c"),
            limit = 10,
        )

        assertTrue(state.clear(setOf("series-a", "series-c", "not-reported")))
        assertFalse(state.clear(setOf("series-a", "not-reported")))
        assertEquals(setOf("series-b"), state.snapshot())
    }

    @Test fun blankIdsAreIgnored() {
        val state = CoverRepairState(limit = 10)

        assertFalse(state.report(""))
        assertFalse(state.report("   "))
        assertTrue(state.snapshot().isEmpty())
    }

    @Test fun snapshotsDoNotChangeWhenLaterReportsArrive() {
        val state = CoverRepairState(limit = 10)
        state.report("series-a")
        val earlier = state.snapshot()

        state.report("series-b")

        assertEquals(setOf("series-a"), earlier)
        assertEquals(setOf("series-a", "series-b"), state.snapshot())
    }
}
