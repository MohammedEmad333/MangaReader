package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBackTargetTest {
    @Test fun closesTopmostReaderLayerFirst() {
        assertEquals(ReaderBackTarget.SETTINGS, readerBackTarget(true, true, true))
        assertEquals(ReaderBackTarget.CHAPTERS, readerBackTarget(false, true, true))
        assertEquals(ReaderBackTarget.CONTROLS, readerBackTarget(false, false, true))
        assertEquals(ReaderBackTarget.READER, readerBackTarget(false, false, false))
    }
}
