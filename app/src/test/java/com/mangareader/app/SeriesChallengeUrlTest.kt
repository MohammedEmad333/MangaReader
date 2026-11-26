package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesChallengeUrlTest {
    @Test
    fun prefersTheExactSeriesUrlOverTheHomepage() {
        assertEquals(
            "https://asurascans.com/comics/overgeared-7de80a82",
            challengeUrlForSeries(
                seriesUrl = "https://asurascans.com/comics/overgeared-7de80a82",
                siteUrl = "https://asurascans.com",
            ),
        )
    }

    @Test
    fun fallsBackToHomepageWhenSeriesUrlIsUnavailable() {
        assertEquals(
            "https://asurascans.com",
            challengeUrlForSeries(
                seriesUrl = null,
                siteUrl = "https://asurascans.com",
            ),
        )
    }

    @Test
    fun returnsNullWhenNeitherUrlIsAvailable() {
        assertNull(challengeUrlForSeries(seriesUrl = null, siteUrl = null))
    }

    @Test
    fun ignoresBlankUrls() {
        assertEquals(
            "https://asurascans.com",
            challengeUrlForSeries(seriesUrl = " ", siteUrl = "https://asurascans.com"),
        )
    }
}
