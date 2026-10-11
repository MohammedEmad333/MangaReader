package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionCheckpointTest {
    @Test fun completeCheckpointIsValid() {
        assertTrue(ReaderSessionCheckpoint("source", "series", "chapter", 0).isValid())
        assertTrue(ReaderSessionCheckpoint("source", "series", "chapter", 42).isValid())
    }

    @Test fun incompleteCheckpointIsRejected() {
        assertFalse(ReaderSessionCheckpoint("", "series", "chapter", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "", "chapter", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "series", "", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "series", "chapter", -1).isValid())
    }
}
