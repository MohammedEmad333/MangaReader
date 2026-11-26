package com.mangareader.app

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HttpSourceMangaUpdateLockTest {

    @Test
    fun combinedDetailsAndChapterEntrypointsShareOnePerSeriesLock() = runBlocking {
        val source = ConcurrentUpdateSource()
        val manga = manga("/series/overgeared")

        coroutineScope {
            val combined = async {
                source.getMangaUpdateWithLock(manga, emptyList(), true, true)
            }
            val details = async { source.getMangaDetails(manga) }
            val chapters = async { source.getChapterList(manga) }

            combined.await()
            details.await()
            chapters.await()
        }

        assertEquals(1, source.peakConcurrent.get())
    }

    @Test
    fun differentSeriesCanStillUpdateConcurrently() = runBlocking {
        val source = ConcurrentUpdateSource()
        val first = manga("/series/overgeared")
        val second = manga("/series/another-series")

        coroutineScope {
            val a = async {
                source.getMangaUpdateWithLock(first, emptyList(), true, true)
            }
            val b = async {
                source.getMangaUpdateWithLock(second, emptyList(), true, true)
            }
            a.await()
            b.await()
        }

        assertEquals(2, source.peakConcurrent.get())
    }

    private fun manga(url: String): SManga = SMangaImpl().apply {
        this.url = url
        title = url.substringAfterLast('/')
    }

    private class ConcurrentUpdateSource : HttpSource() {
        override val name = "Lock regression source"
        override val lang = "en"
        override val supportsLatest = false
        override val baseUrl = "https://example.org"

        private val active = AtomicInteger()
        val peakConcurrent = AtomicInteger()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            val now = active.incrementAndGet()
            peakConcurrent.updateAndGet { previous -> maxOf(previous, now) }
            try {
                delay(30)
                return SMangaUpdate(manga, emptyList())
            } finally {
                active.decrementAndGet()
            }
        }

        override fun popularMangaRequest(page: Int): Request = error("Not used in this test")
        override fun popularMangaParse(response: Response): MangasPage = error("Not used in this test")
        override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
            error("Not used in this test")
        override fun searchMangaParse(response: Response): MangasPage = error("Not used in this test")
        override fun latestUpdatesRequest(page: Int): Request = error("Not used in this test")
        override fun latestUpdatesParse(response: Response): MangasPage = error("Not used in this test")
        override fun mangaDetailsParse(response: Response): SManga = error("Not used in this test")
        override fun chapterListParse(response: Response): List<SChapter> = error("Not used in this test")
        override fun chapterPageParse(response: Response): SChapter = error("Not used in this test")
        override fun pageListParse(response: Response): List<Page> = error("Not used in this test")
        override fun imageUrlParse(response: Response): String = error("Not used in this test")
    }
}
