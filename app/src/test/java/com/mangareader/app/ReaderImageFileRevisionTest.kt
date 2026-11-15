package com.mangareader.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderImageFileRevisionTest {
    @Test fun samePathReplacementChangesRevisionAndPreviewCacheKey() {
        val file = File.createTempFile("reader-cache-revision", ".img")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val before = requireNotNull(readerImageFileRevision(file))
            assertEquals(before, readerImageFileRevision(file))
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val after = requireNotNull(readerImageFileRevision(file))
            assertEquals(before.file, after.file)
            assertNotEquals(before, after)
            assertNotEquals(before.cacheKey("preview-72x96"), after.cacheKey("preview-72x96"))
        } finally {
            file.delete()
        }
    }

    @Test fun explicitRetryBreaksTiesWhenFileMetadataIsUnchanged() {
        val file = File("/missing/test-page")
        val first = requireNotNull(readerImageFileRevision(file, retryGeneration = 0))
        val retried = requireNotNull(readerImageFileRevision(file, retryGeneration = 1))
        assertEquals(first.file, retried.file)
        assertEquals(first.length, retried.length)
        assertEquals(first.lastModified, retried.lastModified)
        assertFalse(first == retried)
        assertNotEquals(first.cacheKey("page"), retried.cacheKey("page"))
    }

    @Test fun variantsAndDecodeAttemptsCannotReuseEachOthersBitmaps() {
        val file = File("/missing/test-page")
        val revision = requireNotNull(readerImageFileRevision(file))
        assertNotEquals(revision.cacheKey("zoom"), revision.cacheKey("rotated"))
        assertNotEquals(revision.cacheKey("page"), revision.cacheKey("preview-72x96"))
        assertNotEquals(revision.cacheKey("page", 0), revision.cacheKey("page", 1))
        assertNull(readerImageFileRevision(null))
    }
}
