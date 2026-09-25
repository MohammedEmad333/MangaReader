package com.mangareader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryPagerContent(
    groups: List<LibraryGroupView>,
    allEntriesEmpty: Boolean,
    pagerState: PagerState,
    selecting: Boolean,
    scroll: ScrollMemory,
    ordering: Any?,
    display: LibraryDisplay,
    perRow: Int,
    readIds: Set<String>,
    downloadedIds: Set<String>,
    badgeLocal: Boolean,
    unreadCounts: Map<String, Int>,
    selected: Set<String>,
    onOpen: (LibraryEntry) -> Unit,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        LibraryEmpty(allEmpty = true, modifier = modifier)
        return
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !selecting && groups.size > 1,
        modifier = modifier,
    ) { page ->
        val group = groups[page]
        LibraryGrid(
            shown = group.items,
            allEmpty = allEntriesEmpty,
            scrollKey = group.key,
            scroll = scroll,
            ordering = ordering,
            display = display,
            perRow = perRow,
            readIds = readIds,
            downloadedIds = downloadedIds,
            badgeLocal = badgeLocal,
            unreadCounts = unreadCounts,
            selected = selected,
            selecting = selecting,
            onOpen = onOpen,
            onToggle = onToggle,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun LibraryScreenDialogs(
    optionsOpen: Boolean,
    onDismissOptions: () -> Unit,
    onOptionsChanged: () -> Unit,
    assignOpen: Boolean,
    selected: Set<String>,
    onDismissAssign: () -> Unit,
    onAppliedAssign: () -> Unit,
) {
    if (optionsOpen) {
        LibraryOptionsSheet(
            onDismiss = onDismissOptions,
            onChanged = onOptionsChanged,
        )
    }

    if (assignOpen) {
        BulkCategoryDialog(
            seriesIds = selected,
            onDismiss = onDismissAssign,
            onApplied = onAppliedAssign,
        )
    }
}
