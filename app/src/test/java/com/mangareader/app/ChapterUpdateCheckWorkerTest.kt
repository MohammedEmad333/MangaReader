package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateCheckWorkerTest {
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
