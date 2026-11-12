package com.mangareader.app

/**
 * Persist every real page change immediately so process death cannot discard
 * reading progress, but skip duplicate callbacks caused by streaming status,
 * recomposition or closing the reader at an already-saved page.
 *
 * A gate belongs to exactly one source/series/chapter composition. Never share
 * it across chapters, since the same numeric page is not the same progress.
 */
internal class ReaderProgressWriteGate {
    private var lastSavedPage: Int? = null

    fun shouldPersist(page: Int, pageCount: Int): Boolean {
        if (!isValidReaderPageIndex(page, pageCount) || lastSavedPage == page) {
            return false
        }
        lastSavedPage = page
        return true
    }
}
