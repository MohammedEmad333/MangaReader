package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPathIndexValidationTest {
    @Test fun acceptsReadableThreeLevelRelativePaths() {
        assertTrue(isSafeStoredChapterPath("Source/Series/Chapter 1"))
        assertTrue(isSafeStoredChapterPath("مصدر/سلسلة/فصل ١"))
        assertTrue(isSafeStoredDownloadFolder("A title with spaces"))
    }

    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf(
            "", "/Source/Series/Chapter", "Source/../Chapter",
            "Source/Series/..", "Source/./Chapter",
            "Source/Series/Chapter/extra", "Source//Chapter",
            "../Source/Series", "Source\\Series\\Chapter",
            "C:\\Source\\Series\\Chapter",
        ).forEach { path ->
            assertFalse("Unsafe: $path", isSafeStoredChapterPath(path))
        }
    }

    @Test fun rejectsUnsafeFolderNamesWithoutRejectingOrdinaryUnicode() {
        listOf("", ".", "..", "a/b", "a\\b", "a\u0000b", "a\nb").forEach { name ->
            assertFalse("Unsafe folder: $name", isSafeStoredDownloadFolder(name))
        }
        assertTrue(isSafeStoredDownloadFolder("Vol. 2 - الفصل الثاني"))
    }
}
