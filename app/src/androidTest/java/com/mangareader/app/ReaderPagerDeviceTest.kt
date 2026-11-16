package com.mangareader.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.LaunchedEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Render actual ReaderPagedPages on an emulator. Missing files are intentional:
 * the app's own readable placeholders give testable page labels without network.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPagerDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fivePageTabletLayoutShowsPairsAndOddFinalPage() {
        compose.setContent {
            MaterialTheme {
                val pager = rememberPagerState(initialPage = 1, pageCount = { 5 })
                ReaderPagedPages(
                    pagerState = pager,
                    modifier = Modifier.fillMaxSize(),
                    mode = ReaderMode.PAGED_LTR,
                    pages = List(5) { null },
                    stillLoading = false,
                    headRows = 1,
                    chapters = emptyList(),
                    chapterIndex = 0,
                    chapterName = "Local test chapter",
                    hasPrev = false,
                    hasNext = false,
                    onPrev = {},
                    onNext = {},
                    colorFilter = null,
                    textColor = Color.Black,
                    onTap = {},
                    widePageMode = ReaderWidePageMode.FIT,
                    twoPageTablet = true,
                    onRetryPage = {},
                )
            }
        }
        compose.onNodeWithText("Page 1 couldn't be loaded. Check your connection and try again.").assertExists()
        compose.onNodeWithText("Page 2 couldn't be loaded. Check your connection and try again.").assertExists()
        // Actual emulator touch gesture advances the real horizontal pager.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Page 3 couldn't be loaded. Check your connection and try again.").assertExists()
        compose.onNodeWithText("Page 4 couldn't be loaded. Check your connection and try again.").assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Page 5 couldn't be loaded. Check your connection and try again.").assertExists()
    }

    @Test fun rtlSpreadDisplaysFirstLogicalPageOnRight() {
        compose.setContent {
            MaterialTheme {
                val pager = rememberPagerState(initialPage = 1, pageCount = { 4 })
                ReaderPagedPages(
                    pagerState = pager,
                    modifier = Modifier.fillMaxSize(),
                    mode = ReaderMode.PAGED_RTL,
                    pages = List(4) { null },
                    stillLoading = false,
                    headRows = 1,
                    chapters = emptyList(),
                    chapterIndex = 0,
                    chapterName = "RTL test",
                    hasPrev = false,
                    hasNext = false,
                    onPrev = {},
                    onNext = {},
                    colorFilter = null,
                    textColor = Color.Black,
                    onTap = {},
                    widePageMode = ReaderWidePageMode.FIT,
                    twoPageTablet = true,
                    onRetryPage = {},
                )
            }
        }
        val first = compose.onNodeWithText("Page 1 couldn't be loaded. Check your connection and try again.")
            .fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Page 2 couldn't be loaded. Check your connection and try again.")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("RTL logical page 1 should be to the right", first.center.x > second.center.x)
    }

    @Test fun repeatedSeekPriorityNeverDuplicatesOrLosesPages() {
        val chapterKey = "emulator-rapid-navigation"
        val token = ReaderPagePriority.begin(chapterKey, 0)
        try {
            listOf(45, 3, 39, 6, 25, 1, 49, 10).forEach { target ->
                ReaderPagePriority.update(chapterKey, target)
                ReaderPagePriority.updateVisible(chapterKey, listOf(target, (target + 1) % 50))
                val order = prioritizedPageOrder(
                    pageCount = 50,
                    visiblePage = target,
                    visiblePages = ReaderPagePriority.visiblePages(chapterKey),
                    direction = ReaderPagePriority.direction(chapterKey),
                    stride = ReaderPagePriority.stride(chapterKey),
                )
                assertEquals(50, order.size)
                assertEquals(50, order.distinct().size)
                assertEquals((0 until 50).toSet(), order.toSet())
                assertEquals(target, order.first())
            }
        } finally {
            ReaderPagePriority.finish(chapterKey, token)
        }
        assertEquals(1, ReaderPagePriority.stride(chapterKey))
    }
}
