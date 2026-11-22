package com.mangareader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-backed local index regression. No HTTP requests, source extensions, or external storage. */
@RunWith(AndroidJUnit4::class)
class OfflineDownloadsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun verifiedChapterAppearsWithoutNetworkAndMissingPageIsExcluded() {
        val before = DownloadIndexStorage.read(context)
        val chapterId = "offline-device-" + UUID.randomUUID().toString()
        val seriesId = "offline-series-" + UUID.randomUUID().toString()
        val directory = File(
            File(context.filesDir, StorageLocation.CHAPTERS),
            offlineKey(chapterId),
        )
        try {
            assertTrue(directory.mkdirs())
            File(directory, "0000").writeBytes(byteArrayOf(1, 2, 3))
            File(directory, ".complete").writeText("1")
            DownloadIndexStorage.write(
                context,
                before + DownloadIndexRecord(
                    chapterId = chapterId,
                    chapterName = "Offline test chapter",
                    sourceId = "offline-test-source",
                    seriesId = seriesId,
                    title = "Offline test series",
                    cover = "",
                ),
            )
            Downloads.invalidateCompletion()
            val visible = DownloadIndex.listIndexed(context)
            val entry = visible.single { it.seriesId == seriesId }
            assertEquals(listOf(chapterId), entry.chapters.map { it.chapterId })
            assertTrue(entry.sizeBytes > 0L)

            assertTrue(File(directory, "0000").delete())
            Downloads.invalidateCompletion()
            assertFalse(DownloadIndex.listIndexed(context).any { it.seriesId == seriesId })
        } finally {
            DownloadIndexStorage.write(context, before)
            directory.deleteRecursively()
            Downloads.invalidateCompletion()
        }
    }
}
