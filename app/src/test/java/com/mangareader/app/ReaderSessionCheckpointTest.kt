package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionCheckpointTest {
    @Test fun completeCheckpointIsValid() {
        assertTrue(ReaderSessionCheckpoint("source", "series", "chapter", 0).isValid())
        assertTrue(ReaderSessionCheckpoint("source", "series", "chapter", 42).isValid())
    }

    @Test fun unreadOnlyClearsCurrentChaptersCheckpoint() {
        val checkpoint = ReaderSessionCheckpoint("source", "series", "chapter", 6)
        assertTrue(checkpointMatchesChapterKey(checkpoint, "source|chapter"))
        assertFalse(checkpointMatchesChapterKey(checkpoint, "source|other"))
        assertFalse(checkpointMatchesChapterKey(checkpoint, "other|chapter"))
        assertFalse(checkpointMatchesChapterKey(
            checkpoint.copy(chapterId = ""), "source|"
        ))
    }

    @Test fun incompleteCheckpointIsRejected() {
        assertFalse(ReaderSessionCheckpoint("", "series", "chapter", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "", "chapter", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "series", "", 2).isValid())
        assertFalse(ReaderSessionCheckpoint("source", "series", "chapter", -1).isValid())
    }
}
