package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryOptionsSheet(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    var sort by remember { mutableStateOf(LibraryPrefs.sort(context)) }
    var ascending by remember { mutableStateOf(LibraryPrefs.ascending(context)) }
    var display by remember { mutableStateOf(LibraryPrefs.display(context)) }
    var perRow by remember { mutableIntStateOf(LibraryPrefs.itemsPerRow(context)) }
    var group by remember { mutableStateOf(LibraryPrefs.group(context)) }
    var badgeDl by remember { mutableStateOf(LibraryPrefs.badgeDownloaded(context)) }
    var badgeLocal by remember { mutableStateOf(LibraryPrefs.badgeLocal(context)) }
    var badgeUnread by remember { mutableStateOf(LibraryPrefs.badgeUnread(context)) }
    var showTabs by remember { mutableStateOf(LibraryPrefs.showTabs(context)) }
    var showCount by remember { mutableStateOf(LibraryPrefs.showCount(context)) }
    var fDownloaded by remember { mutableStateOf(LibraryPrefs.filterDownloaded(context)) }
    var fLocal by remember { mutableStateOf(LibraryPrefs.filterLocal(context)) }
    var fRead by remember { mutableStateOf(LibraryPrefs.filterRead(context)) }
    var fUnread by remember { mutableStateOf(LibraryPrefs.filterUnread(context)) }
    var fStarted by remember { mutableStateOf(LibraryPrefs.filterStarted(context)) }
    var fCompleted by remember { mutableStateOf(LibraryPrefs.filterCompleted(context)) }
    var fNsfw by remember { mutableStateOf(LibraryPrefs.filterNsfw(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        TabRow(selectedTabIndex = tab) {
            listOf("Filter", "Sort", "Display", "Group").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .heightIn(min = 260.dp, max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            when (tab) {
                0 -> LibraryFilterOptions(
                    downloaded = fDownloaded,
                    local = fLocal,
                    read = fRead,
                    unread = fUnread,
                    started = fStarted,
                    completed = fCompleted,
                    nsfw = fNsfw,
                    onDownloadedChange = {
                        fDownloaded = it
                        LibraryPrefs.setFilterDownloaded(context, it)
                        onChanged()
                    },
                    onLocalChange = {
                        fLocal = it
                        LibraryPrefs.setFilterLocal(context, it)
                        onChanged()
                    },
                    onReadChange = {
                        fRead = it
                        LibraryPrefs.setFilterRead(context, it)
                        onChanged()
                    },
                    onUnreadChange = {
                        fUnread = it
                        LibraryPrefs.setFilterUnread(context, it)
                        onChanged()
                    },
                    onStartedChange = {
                        fStarted = it
                        LibraryPrefs.setFilterStarted(context, it)
                        onChanged()
                    },
                    onCompletedChange = {
                        fCompleted = it
                        LibraryPrefs.setFilterCompleted(context, it)
                        onChanged()
                    },
                    onNsfwChange = {
                        fNsfw = it
                        LibraryPrefs.setFilterNsfw(context, it)
                        onChanged()
                    },
                )

                1 -> LibrarySortOptions(
                    sort = sort,
                    ascending = ascending,
                    onSortChange = { sort = it },
                    onAscendingChange = { ascending = it },
                    onChanged = onChanged,
                )

                2 -> LibraryDisplayOptions(
                    display = display,
                    perRow = perRow,
                    badgeDownloaded = badgeDl,
                    badgeLocal = badgeLocal,
                    badgeUnread = badgeUnread,
                    showTabs = showTabs,
                    showCount = showCount,
                    onDisplayChange = { display = it },
                    onPerRowChange = { perRow = it },
                    onBadgeDownloadedChange = { badgeDl = it },
                    onBadgeLocalChange = { badgeLocal = it },
                    onBadgeUnreadChange = { badgeUnread = it },
                    onShowTabsChange = { showTabs = it },
                    onShowCountChange = { showCount = it },
                    onChanged = onChanged,
                )

                else -> LibraryGroupOptions(
                    group = group,
                    onGroupChange = { group = it },
                    onChanged = onChanged,
                )
            }
        }
    }
}
