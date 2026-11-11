package com.mangareader.app

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtensionIndexDownloadPolicyTest {
    @Test fun readsUnicodeCatalogueWithoutTruncation() {
        val json = """[{"name":"مانجا","version":"1.0"}]"""
        assertEquals(json, ExtensionIndexDownloadPolicy.readUtf8(
            ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)),
        ))
    }

    @Test fun rejectsInvalidDeclaredSizes() {
        for (length in listOf(0L, -2L, 6L)) {
            assertThrows(IOException::class.java) {
                ExtensionIndexDownloadPolicy.readUtf8(
                    ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                    contentLength = length,
                    maxBytes = 5,
                )
            }
        }
    }

    @Test fun enforcesStreamingLimitWhenSizeUnknown() {
        assertThrows(IOException::class.java) {
            ExtensionIndexDownloadPolicy.readUtf8(
                ByteArrayInputStream(ByteArray(6) { 65 }),
                maxBytes = 5,
            )
        }
    }

    @Test fun allowsBoundarySizeAndRejectsEmptyResponse() {
        assertEquals("hello", ExtensionIndexDownloadPolicy.readUtf8(
            ByteArrayInputStream("hello".toByteArray()),
            contentLength = 5,
            maxBytes = 5,
        ))
        assertThrows(IOException::class.java) {
            ExtensionIndexDownloadPolicy.readUtf8(
                ByteArrayInputStream(byteArrayOf()),
            )
        }
    }
}
