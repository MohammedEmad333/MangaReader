package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesNavigationGenerationTest {
    @Test fun outdatedChapterResponsesCannotReplaceNewSeries() {
        val state = SeriesNavigationState()
        state.begin(Series("series-a", "A", null), SeriesOrigin.BROWSE)
        val first = state.snapshotGeneration()
        assertTrue(state.isCurrent("series-a", first))
        state.begin(Series("series-b", "B", null), SeriesOrigin.BROWSE)
        assertFalse(state.isCurrent("series-a", first))
        assertTrue(state.isCurrent("series-b", state.snapshotGeneration()))
    }

    @Test fun reopeningSameSeriesInvalidatesPreviousRequest() {
        val state = SeriesNavigationState()
        state.begin(Series("same", "Series", null), SeriesOrigin.BROWSE)
        val first = state.snapshotGeneration()
        state.begin(Series("same", "Series", null), SeriesOrigin.BROWSE)
        assertFalse(state.isCurrent("same", first))
        val second = state.snapshotGeneration()
        state.clear()
        assertFalse(state.isCurrent("same", second))
    }
}
