package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineChapterPageFilterTest {
    @Test fun acceptsIndexedPagesWithOrWithoutImageExtension() {
        val dir = Files.createTempDirectory("offline-pages-").toFile()
        try {
            for (name in listOf("0000", "0001", "0002.jpg", "0010.PNG", "12.avif", "15.webp")) {
                val f = File(dir, name).apply { writeBytes(byteArrayOf(1)) }
                assertTrue(name, isStoredChapterPage(f))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsHiddenMarkersPartialTransfersAndUnrelatedFiles() {
        val dir = Files.createTempDirectory("offline-marker-").toFile()
        try {
            for (name in listOf(".complete", ".chapter-id", "0001.part", "0002.tmp", "chapter.json", "notes.txt", "cover.jpg")) {
                val f = File(dir, name).apply { writeBytes(byteArrayOf(1)) }
                assertFalse(name, isStoredChapterPage(f))
            }
            assertFalse(isStoredChapterPage(File(dir, "0005").apply { createNewFile() }))
            assertFalse(isStoredChapterPage(dir))
        } finally { dir.deleteRecursively() }
    }
}
