package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPathRecoveryIdentityTest {
    @Test fun duplicateSourceFoldersDoNotPickAnArbitrarySource() {
        assertNull(uniqueDownloadFolderOwner(
            mapOf("source-a" to "Same", "source-b" to "Same"), "Same"
        ))
        assertEquals("source-a", uniqueDownloadFolderOwner(
            mapOf("source-a" to "Unique", "source-b" to "Other"), "Unique"
        ))
    }

    @Test fun sameSeriesFolderNamesInOtherSourcesAreAllowed() {
        val owners = mapOf(
            "source-a|series" to "Common",
            "source-b|series" to "Common",
        )
        assertEquals("source-a|series", uniqueDownloadFolderOwner(
            owners, "Common", "source-a|"
        ))
        assertEquals("source-b|series", uniqueDownloadFolderOwner(
            owners, "Common", "source-b|"
        ))
        assertNull(uniqueDownloadFolderOwner(owners, "Common"))
    }

    @Test fun ambiguousSeriesFoldersWithinOneSourceCannotRecoverIdentity() {
        assertNull(uniqueDownloadFolderOwner(
            mapOf(
                "source-a|one" to "Same",
                "source-a|two" to "Same",
            ),
            "Same", "source-a|"
        ))
    }

    @Test fun duplicateMarkerPathsAreIgnoredInsteadOfLastOneWinning() {
        val paths = uniqueScannedChapterPaths(listOf(
            "same-id" to "A/Series/Chapter 1",
            "same-id" to "B/Series/Chapter 1",
            "good" to "A/Series/Chapter 2",
        ))
        assertEquals(mapOf("good" to "A/Series/Chapter 2"), paths)
    }

    @Test fun repeatedMarkerAtSamePathIsUnambiguous() {
        assertEquals(mapOf("chapter" to "A/Series/Chapter"), uniqueScannedChapterPaths(
            listOf("chapter" to "A/Series/Chapter", "chapter" to "A/Series/Chapter")
        ))
    }

    @Test fun blankIdsAndUnsafePathsAreRejected() {
        assertTrue(uniqueScannedChapterPaths(listOf(
            "" to "A/Series/Chapter",
            "bad" to "A/../Chapter",
        )).isEmpty())
        assertNull(uniqueDownloadFolderOwner(mapOf("source" to "Folder"), ""))
    }
}
