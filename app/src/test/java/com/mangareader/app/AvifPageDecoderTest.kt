package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvifPageDecoderTest {
    private fun header(major: String, vararg compatible: String): ByteArray {
        val length = 16 + compatible.size * 4
        val boxLength = byteArrayOf(
            0, 0, (length ushr 8).toByte(), length.toByte(),
        )
        return boxLength + "ftyp".toByteArray(Charsets.US_ASCII) +
            major.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0, 0) +
            compatible.flatMap { it.toByteArray(Charsets.US_ASCII).asList() }.toByteArray()
    }

    @Test fun recognizesAvifAndSequenceMajorBrandsWithoutFileExtensions() {
        assertTrue(isAvifHeader(header("avif", "mif1")))
        assertTrue(isAvifHeader(header("avis", "msf1")))
    }

    @Test fun recognizesAvifInCompatibleBrandList() {
        assertTrue(isAvifHeader(header("mif1", "miaf", "avif")))
        assertTrue(isAvifHeader(header("msf1", "miaf", "avis")))
        assertTrue(isAvifHeader(header("mif1", "avif", "miaf")))
    }

    @Test fun doesNotReadBrandsPastEndOfFtypBox() {
        val other = header("heic", "mif1")
        assertFalse(isAvifHeader(other + "avif".toByteArray()))
        assertFalse(isAvifHeader(header("mif1", "miaf") + "avis".toByteArray()))
    }

    @Test fun rejectsCorruptTruncatedAndNonAvifHeaders() {
        assertFalse(isAvifHeader(header("heic", "mif1")))
        assertFalse(isAvifHeader(byteArrayOf(0, 0, 0, 12)))
        assertFalse(isAvifHeader(byteArrayOf(0, 0, 0, 24) + "ftypavif".toByteArray()))
        assertFalse(isAvifHeader(ByteArray(64)))
        assertFalse(isAvifHeader(byteArrayOf(0, 0, 0, 1) + "ftypavif".toByteArray() + ByteArray(16)))
    }

    @Test fun rejectsUnalignedOrTruncatedCompatibleBrands() {
        val compatible = header("mif1", "avif")
        assertFalse(isAvifHeader(compatible.copyOf(19)))
        assertFalse(isAvifHeader(compatible.copyOf(18)))
        assertTrue(isAvifHeader(compatible.copyOf(20)))
    }
}
