package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTermsTest {
    @Test
    fun matchesWordsRegardlessOfOrderOrCase() {
        assertTrue(libraryTitleMatches("The Last Adventure", librarySearchTerms("adventure last")))
        assertTrue(libraryTitleMatches("The Last Adventure", librarySearchTerms("  LAST   adventure  ")))
        assertFalse(libraryTitleMatches("The Last Adventure", librarySearchTerms("last winter")))
    }

    @Test
    fun unicodeSpacesSeparateSearchWords() {
        assertTrue(libraryTitleMatches("The Last Adventure", librarySearchTerms("last\u00a0adventure")))
        assertTrue(libraryTitleMatches("The Last Adventure", librarySearchTerms("last\u3000adventure")))
    }

    @Test
    fun emptyQueryMatchesEveryTitle() {
        assertTrue(libraryTitleMatches("Any title", librarySearchTerms("  ")))
    }

    @Test
    fun singleWordStillMatchesPartialTitle() {
        assertTrue(libraryTitleMatches("Naruto Shippuden", librarySearchTerms("ship")))
    }
}
