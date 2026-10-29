package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineChapterIntegrityTest {
    private fun withChapter(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("chapter-integrity-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun completeIndexedChapterPasses() = withChapter { dir ->
        File(dir, ".complete").writeText("3")
        repeat(3) { File(dir, "%04d".format(it)).writeBytes(byteArrayOf(1, 2, 3)) }
        val report = inspectOfflineChapter(dir)
        assertTrue(report.isComplete)
        assertEquals(3, report.expectedPages)
        assertEquals(3, report.availablePages)
        assertTrue(report.missingIndices.isEmpty())
        assertTrue(report.invalidIndices.isEmpty())
    }

    @Test fun missingAndCorruptPagesInvalidateMarker() = withChapter { dir ->
        File(dir, ".complete").writeText("3")
        File(dir, "0000").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "0001").writeText("<html>Access denied</html>")
        val report = inspectOfflineChapter(dir)
        assertFalse(report.isComplete)
        assertEquals(listOf(2), report.missingIndices)
        assertEquals(listOf(1), report.invalidIndices)
    }

    @Test fun malformedMarkerNeverClaimsComplete() = withChapter { dir ->
        File(dir, ".complete").writeText("not-a-number")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        assertFalse(inspectOfflineChapter(dir).isComplete)
        assertEquals(null, inspectOfflineChapter(dir).expectedPages)
    }

    @Test fun duplicatePageIndicesAndUnexpectedNumbersAreDetected() = withChapter { dir ->
        File(dir, ".complete").writeText("2")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        File(dir, "0000.png").writeBytes(byteArrayOf(2))
        File(dir, "0001").writeBytes(byteArrayOf(3))
        File(dir, "0002").writeBytes(byteArrayOf(4))
        val report = inspectOfflineChapter(dir)
        assertFalse(report.isComplete)
        assertEquals(listOf(0, 2), report.invalidIndices)
    }

    @Test fun deletingPageAfterValidInspectionInvalidatesSubsequentInspection() = withChapter { dir ->
        File(dir, ".complete").writeText("2")
        val first = File(dir, "0000").apply { writeBytes(byteArrayOf(1, 2)) }
        val second = File(dir, "0001").apply { writeBytes(byteArrayOf(3, 4)) }
        assertTrue(inspectOfflineChapter(dir).isComplete)
        assertTrue(second.delete())
        val current = inspectOfflineChapter(dir)
        assertFalse(current.isComplete)
        assertEquals(listOf(1), current.missingIndices)
        assertTrue(first.exists())
    }

    @Test fun replacingPreviouslyValidPageWithServerErrorInvalidatesInspection() = withChapter { dir ->
        File(dir, ".complete").writeText("1")
        val image = File(dir, "0000").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(inspectOfflineChapter(dir).isComplete)
        image.writeText("<html>Blocked</html>")
        assertFalse(inspectOfflineChapter(dir).isComplete)
        assertEquals(listOf(0), inspectOfflineChapter(dir).invalidIndices)
    }

    @Test fun zeroByteImageIsReportedAsInvalidNotMissing() = withChapter { dir ->
        File(dir, ".complete").writeText("2")
        File(dir, "0000").writeBytes(byteArrayOf(1, 2))
        File(dir, "0001").createNewFile()
        val report = inspectOfflineChapter(dir)
        assertFalse(report.isComplete)
        assertTrue(report.missingIndices.isEmpty())
        assertEquals(listOf(1), report.invalidIndices)
        assertEquals(2, report.availablePages)
    }

    @Test fun duplicateIndexWithEmptyVariantIsInvalid() = withChapter { dir ->
        File(dir, ".complete").writeText("1")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        File(dir, "0000.jpg").createNewFile()
        val report = inspectOfflineChapter(dir)
        assertFalse(report.isComplete)
        assertEquals(listOf(0), report.invalidIndices)
        assertTrue(report.missingIndices.isEmpty())
    }

    @Test fun repairPlanTargetsOnlyMissingOrCorruptPages() = withChapter { dir ->
        File(dir, ".complete").writeText("4")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        File(dir, "0001").writeText("<html>blocked</html>")
        File(dir, "0003").writeBytes(byteArrayOf(3))
        val plan = planOfflinePageRepair(dir)
        assertEquals(listOf(1, 2), plan.downloadIndices)
        assertTrue(plan.ambiguousIndices.isEmpty())
        assertTrue(plan.canRepairAutomatically)
        assertTrue(File(dir, "0000").exists())
    }

    @Test fun repairPlanDoesNotOverwriteDuplicateOrOutOfRangePages() = withChapter { dir ->
        File(dir, ".complete").writeText("2")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        File(dir, "0000.jpg").writeBytes(byteArrayOf(2))
        File(dir, "0003").writeBytes(byteArrayOf(3))
        val plan = planOfflinePageRepair(dir)
        assertEquals(listOf(1), plan.downloadIndices)
        assertEquals(listOf(0, 3), plan.ambiguousIndices)
        assertFalse(plan.canRepairAutomatically)
    }

    @Test fun repairPlanRejectsUnknownExpectedCount() = withChapter { dir ->
        File(dir, "0000").writeBytes(byteArrayOf(1))
        val plan = planOfflinePageRepair(dir)
        assertFalse(plan.canRepairAutomatically)
        assertTrue(plan.downloadIndices.isEmpty())
    }

    @Test fun repairRequiresMatchingSourcePageCount() = withChapter { dir ->
        File(dir, ".complete").writeText("3")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        assertTrue(canSafelyRepairOfflineChapter(dir, 3))
        assertFalse(canSafelyRepairOfflineChapter(dir, 2))
        assertFalse(canSafelyRepairOfflineChapter(dir, 0))
        assertTrue(File(dir, "0000").exists())
    }

    @Test fun ambiguousOfflineChapterIsBlockedBeforeRepair() = withChapter { dir ->
        File(dir, ".complete").writeText("2")
        File(dir, "0000").writeBytes(byteArrayOf(1))
        File(dir, "0000.jpg").writeBytes(byteArrayOf(2))
        assertFalse(canSafelyRepairOfflineChapter(dir, 2))
    }
}
