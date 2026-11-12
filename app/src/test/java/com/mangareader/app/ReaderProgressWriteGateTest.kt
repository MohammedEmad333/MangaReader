package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressWriteGateTest {
    @Test fun savingTheSamePageOnLoadingChangesDoesNotRewritePreferences() {
        val gate = ReaderProgressWriteGate()
        assertTrue(gate.shouldPersist(0, 5))
        assertFalse(gate.shouldPersist(0, 5))
        assertFalse(gate.shouldPersist(0, 8))
        assertFalse(gate.shouldPersist(0, 8))
    }

    @Test fun realPageChangesPersistImmediatelyEvenWhenReadingBackwards() {
        val gate = ReaderProgressWriteGate()
        assertTrue(gate.shouldPersist(2, 8))
        assertTrue(gate.shouldPersist(3, 8))
        assertFalse(gate.shouldPersist(3, 8))
        assertTrue(gate.shouldPersist(1, 8))
        assertTrue(gate.shouldPersist(3, 8))
    }

    @Test fun rejectsEmptyChapterAndOutOfBoundsProgressWithoutPoisoningGate() {
        val gate = ReaderProgressWriteGate()
        assertFalse(gate.shouldPersist(0, 0))
        assertFalse(gate.shouldPersist(-1, 4))
        assertFalse(gate.shouldPersist(4, 4))
        assertTrue(gate.shouldPersist(0, 4))
        assertFalse(gate.shouldPersist(0, 4))
    }

    @Test fun samePageInDifferentChapterUsesFreshGate() {
        val firstChapter = ReaderProgressWriteGate()
        val secondChapter = ReaderProgressWriteGate()
        assertTrue(firstChapter.shouldPersist(4, 6))
        assertFalse(firstChapter.shouldPersist(4, 6))
        assertTrue(secondChapter.shouldPersist(4, 6))
    }

    @Test fun closingAtAlreadyPersistedPageAvoidsRedundantWrite() {
        val gate = ReaderProgressWriteGate()
        assertTrue(gate.shouldPersist(6, 10))
        assertFalse(gate.shouldPersist(6, 10))
        assertTrue(gate.shouldPersist(7, 10))
    }
}
