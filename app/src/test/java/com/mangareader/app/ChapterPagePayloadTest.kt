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
