package com.mangareader.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPagePayloadTest {
    @Test fun rejectsServerErrorPages() {
        assertTrue(isObviouslyInvalidPage("<!doctype html><html>Forbidden</html>".toByteArray()))
        assertTrue(isObviouslyInvalidPage("  <html>Captcha</html>".toByteArray()))
        assertTrue(isObviouslyInvalidPage("{\"error\":\"blocked\"}".toByteArray()))
    }

    @Test fun rejectsPlaintextAndJsonCdnBlockResponses() {
        listOf(
            "Access Denied",
            " 403 Forbidden",
            "429 Too Many Requests",
            "Cloudflare Ray ID: test",
            "502 Bad Gateway",
            "Error 404: page not found",
            "upstream connect error or disconnect/reset before headers",
            "{ \"errors\": [\"blocked\"] }",
            "[{\"error\":\"blocked\"}]",
            " <head><title>Access denied</title>",
        ).forEach { body ->
            assertTrue("Should reject: $body", isObviouslyInvalidPage(body.toByteArray()))
        }
    }

    @Test fun rejectsEmptyOrWhitespaceOnlyPageBodies() {
        assertTrue(isObviouslyInvalidPage(byteArrayOf()))
        assertTrue(isObviouslyInvalidPage("  \n\t ".toByteArray()))
    }

    @Test fun permitsKnownBinaryImageMagicBytes() {
        listOf(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte()),
            "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray(),
            "GIF89a".toByteArray(),
            byteArrayOf(0, 0, 0, 24) + "ftypavif".toByteArray(),
            byteArrayOf(0, 0, 0, 24) + "ftypmif1".toByteArray() + ByteArray(4) + "avif".toByteArray(),
        ).forEach { image ->
            assertFalse(isObviouslyInvalidPage(image))
        }
    }

    @Test fun readsPagePrefixOnOlderAndroidApisWithoutNewJavaStreamMethods() {
        val bytes = ByteArray(256) { it.toByte() }
        ByteArrayInputStream(bytes).use { input ->
            assertArrayEquals(bytes.copyOf(128), input.readPagePrefix())
            assertEquals(128, input.available())
        }
        ByteArrayInputStream(byteArrayOf(1, 2, 3)).use { input ->
            assertArrayEquals(byteArrayOf(1, 2, 3), input.readPagePrefix())
        }
        ByteArrayInputStream(byteArrayOf()).use { input ->
            assertArrayEquals(byteArrayOf(), input.readPagePrefix())
        }
    }

    @Test fun handlesStreamsThatReturnPartialChunks() {
        val stream = object : ByteArrayInputStream("hello".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, minOf(len, 1))
        }
        stream.use { assertArrayEquals("hello".toByteArray(), it.readPagePrefix()) }
    }

    @Test fun retainsAvifAndOtherBinaryImageFiles() {
        assertFalse(isObviouslyInvalidPage(byteArrayOf(0, 0, 0, 24) + "ftypavif".toByteArray()))
        assertFalse(isObviouslyInvalidPage(byteArrayOf(-1, -40, -1, -32)))
    }
}
