package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZonesTest {
    @Test fun normalZonesPreserveExistingThirds() {
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(0.1f, true, ReaderTapZoneSize.NORMAL, false))
        assertEquals(ReaderTapAction.CONTROLS, readerTapAction(0.5f, true, ReaderTapZoneSize.NORMAL, false))
        assertEquals(ReaderTapAction.NEXT, readerTapAction(0.9f, true, ReaderTapZoneSize.NORMAL, false))
    }

    @Test fun narrowAndWideZonesAdjustEdges() {
        assertEquals(ReaderTapAction.CONTROLS, readerTapAction(0.25f, true, ReaderTapZoneSize.NARROW, false))
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(0.25f, true, ReaderTapZoneSize.WIDE, false))
        assertEquals(ReaderTapAction.CONTROLS, readerTapAction(0.75f, true, ReaderTapZoneSize.NARROW, false))
        assertEquals(ReaderTapAction.NEXT, readerTapAction(0.75f, true, ReaderTapZoneSize.WIDE, false))
    }

    @Test fun rtlFlipsDirectionsButNotCentre() {
        assertEquals(ReaderTapAction.NEXT, readerTapAction(0f, true, ReaderTapZoneSize.NORMAL, true))
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(1f, true, ReaderTapZoneSize.NORMAL, true))
        assertEquals(ReaderTapAction.CONTROLS, readerTapAction(0.5f, true, ReaderTapZoneSize.NORMAL, true))
    }

    @Test fun disabledAndInvalidTapsNeverTurnPages() {
        assertEquals(ReaderTapAction.CONTROLS, readerTapAction(0f, false, ReaderTapZoneSize.WIDE, false))
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 2f)) {
            assertEquals(ReaderTapAction.CONTROLS, readerTapAction(invalid, true, ReaderTapZoneSize.NORMAL, false))
        }
    }

    @Test fun unknownStoredPreferenceFallsBackToOriginalLayout() {
        assertEquals(ReaderTapZoneSize.NORMAL, ReaderTapZoneSize.from(null))
        assertEquals(ReaderTapZoneSize.NORMAL, ReaderTapZoneSize.from("unexpected"))
        assertEquals(ReaderTapZoneSize.WIDE, ReaderTapZoneSize.from("wide"))
    }
}
