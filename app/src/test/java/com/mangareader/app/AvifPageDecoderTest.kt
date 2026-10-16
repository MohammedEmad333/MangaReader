package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvifPageDecoderTest {
    private fun header(brand: String) = byteArrayOf(0, 0, 0, 24) + brand.toByteArray(Charsets.US_ASCII)

    @Test fun recognizesAvifAndAvifSequenceWithoutFileExtensions() {
        assertTrue(isAvifHeader(header("ftypavif")))
        assertTrue(isAvifHeader(header("ftypavis")))
    }

    @Test fun ignoresNonAvifAndShortFiles() {
        assertFalse(isAvifHeader(header("ftypheic")))
        assertFalse(isAvifHeader(byteArrayOf(0, 0, 0, 12)))
        assertFalse(isAvifHeader(ByteArray(12)))
    }
}
