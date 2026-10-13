package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTermsTest {
    @Test
    fun matchesArabicAlefMaqsuraAndYaVariants() {
        assertTrue(libraryTitleMatches("ليلى", librarySearchTerms("ليلي")))
        assertTrue(libraryTitleMatches("علي", librarySearchTerms("على")))
        assertFalse(libraryTitleMatches("ليلى", librarySearchTerms("حكاية")))
    }

    @Test
    fun matchesArabicAlefVariantsAndTatweel() {
        assertTrue(libraryTitleMatches("أبطال المدينة", librarySearchTerms("ابطال")))
        assertTrue(libraryTitleMatches("إسلام", librarySearchTerms("اسلام")))
        assertTrue(libraryTitleMatches("آمال", librarySearchTerms("امال")))
        assertTrue(libraryTitleMatches("الـمغامرة", librarySearchTerms("المغامرة")))
        assertFalse(libraryTitleMatches("أبطال المدينة", librarySearchTerms("المغامرة")))
    }

    @Test
    fun matchesTitlesWithoutRequiringAccentsOrArabicVowels() {
        assertTrue(libraryTitleMatches("Pokémon", librarySearchTerms("pokemon")))
        assertTrue(libraryTitleMatches("الْحُرِّيَّةُ", librarySearchTerms("الحرية")))
        assertTrue(libraryTitleMatches("Cafe", librarySearchTerms("Café")))
        assertFalse(libraryTitleMatches("Pokémon", librarySearchTerms("pocket")))
    }

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
