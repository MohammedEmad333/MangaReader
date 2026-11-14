package com.mangareader.app

import coil.request.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageMemoryPolicyTest {
    @Test fun fullSizeImagesMayReuseCacheButNeverWriteDecodedBitmaps() {
        val policy = readerImageMemoryCachePolicy(ReaderImageUsage.FULL_PAGE)
        assertEquals(CachePolicy.READ_ONLY, policy)
        assertTrue(policy.readEnabled)
        assertFalse(policy.writeEnabled)
    }

    @Test fun smallPagePickerPreviewsKeepNormalMemoryCache() {
        val policy = readerImageMemoryCachePolicy(ReaderImageUsage.PAGE_PREVIEW)
        assertEquals(CachePolicy.ENABLED, policy)
        assertTrue(policy.readEnabled)
        assertTrue(policy.writeEnabled)
    }

    @Test fun rapidPageSwitchingKeepsFullPagePolicyIndependentFromPreview() {
        repeat(100) {
            assertFalse(readerImageMemoryCachePolicy(ReaderImageUsage.FULL_PAGE).writeEnabled)
        }
        assertTrue(readerImageMemoryCachePolicy(ReaderImageUsage.PAGE_PREVIEW).writeEnabled)
    }
}
