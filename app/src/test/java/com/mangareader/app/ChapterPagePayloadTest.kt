package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPagePayloadTest {
    @Test fun rejectsServerErrorPages() {
        assertTrue(isObviouslyInvalidPage("<!doctype html><html>Forbidden</html>".toByteArray()))
        assertTrue(isObviouslyInvalidPage("  <html>Captcha</html>".toByteArray()))
        assertTrue(isObviouslyInvalidPage("{\"error\":\"blocked\"}".toByteArray()))
    }

    @Test fun retainsAvifAndOtherBinaryImageFiles() {
        assertFalse(isObviouslyInvalidPage(byteArrayOf(0, 0, 0, 24) + "ftypavif".toByteArray()))
        assertFalse(isObviouslyInvalidPage(byteArrayOf(-1, -40, -1, -32)))
    }
}
