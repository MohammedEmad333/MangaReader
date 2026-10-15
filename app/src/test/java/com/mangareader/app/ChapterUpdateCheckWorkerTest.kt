package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateCheckWorkerTest {
    @Test fun failedSourceDiscoveryRetriesAtMostThreeTimes() {
        assertEquals(true, shouldRetryUpdateSourceDiscovery(0))
        assertEquals(true, shouldRetryUpdateSourceDiscovery(2))
        assertEquals(false, shouldRetryUpdateSourceDiscovery(3))
        assertEquals(false, shouldRetryUpdateSourceDiscovery(-1))
    }

    @Test fun cursorAdvancesOnlyForCompletedEntries() {
        assertEquals(5, scheduledCheckNextCursor(3, 2, 10))
        assertEquals(3, scheduledCheckNextCursor(3, 0, 10))
        assertEquals(1, scheduledCheckNextCursor(9, 2, 10))
    }

    @Test fun cursorHandlesEmptyLibraryAndLargeOffsets() {
        assertEquals(0, scheduledCheckNextCursor(100, 4, 0))
        assertEquals(0, scheduledCheckNextCursor(Int.MAX_VALUE, 2, 3))
    }

    @Test fun emptyAndInvalidBatchesAreSafe() {
        assertEquals(emptyList<Int>(), scheduledUpdateBatch(emptyList<Int>(), 0, 12))
        assertEquals(emptyList<Int>(), scheduledUpdateBatch(listOf(1, 2), 0, 0))
    }

    @Test fun batchWrapsWithoutRepeatedEntries() {
        assertEquals(listOf(4, 5, 1), scheduledUpdateBatch((1..5).toList(), 3, 3))
        assertEquals((1..3).toList(), scheduledUpdateBatch((1..3).toList(), 0, 12))
    }

    @Test fun staleCursorIsNormalizedAfterLibraryChanges() {
        assertEquals(listOf(3, 1), scheduledUpdateBatch((1..3).toList(), 8, 2))
    }
}
