package com.mangareader.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun LibraryFilterOptions(
    downloaded: FilterState,
    local: FilterState,
    read: FilterState,
    unread: FilterState,
    started: FilterState,
    completed: FilterState,
    nsfw: FilterState,
    onDownloadedChange: (FilterState) -> Unit,
    onLocalChange: (FilterState) -> Unit,
    onReadChange: (FilterState) -> Unit,
    onUnreadChange: (FilterState) -> Unit,
    onStartedChange: (FilterState) -> Unit,
    onCompletedChange: (FilterState) -> Unit,
    onNsfwChange: (FilterState) -> Unit,
) {
    TriFilterRow("Downloaded", downloaded, onDownloadedChange)
    TriFilterRow("Local source", local, onLocalChange)
    TriFilterRow("Read", read, onReadChange)
    TriFilterRow("Unread", unread, onUnreadChange)
    TriFilterRow("Started", started, onStartedChange)
    TriFilterRow("Completed", completed, onCompletedChange)
    TriFilterRow("18+", nsfw, onNsfwChange)

    SheetNote(
        "Tap once to require, twice to exclude, three times to clear. " +
            "“Read” is the category of that name.",
    )
    SheetNote(
        "Unread, Started and Completed count chapters, so they only know about " +
            "series you have opened at least once. Anything not yet counted stays " +
            "out of those views rather than being guessed at — open a series once " +
            "and it joins them.",
    )
    SheetNote(
        "18+ goes by the source a series came from, not by the series itself, so " +
            "a source carrying both marks everything saved from it. A source " +
            "nothing has classified yet is left in when you exclude.",
    )
}

@Composable
internal fun LibrarySortOptions(
    sort: LibrarySort,
    ascending: Boolean,
    onSortChange: (LibrarySort) -> Unit,
    onAscendingChange: (Boolean) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current

    LibrarySort.entries.forEach { option ->
        SortRow(
            label = option.label,
            selected = sort == option,
            ascending = ascending,
            trailing = {
                if (option == LibrarySort.RANDOM) {
                    IconButton(
                        onClick = {
                            LibraryPrefs.reshuffle(context)
                            onChanged()
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reshuffle")
                    }
                }
            },
            onClick = {
                if (sort == option && option != LibrarySort.RANDOM) {
                    val next = !ascending
                    onAscendingChange(next)
                    LibraryPrefs.setAscending(context, next)
                } else {
                    onSortChange(option)
                    LibraryPrefs.setSort(context, option)
                }
                onChanged()
            },
        )
    }

    SheetNote(
        "Tap the active sort again to reverse it. Last read uses the history, " +
            "which keeps the 40 most recent chapters — anything older sorts to the end.",
    )
}

@Composable
internal fun LibraryDisplayOptions(
    display: LibraryDisplay,
    perRow: Int,
    badgeDownloaded: Boolean,
    badgeLocal: Boolean,
    badgeUnread: Boolean,
    showTabs: Boolean,
    showCount: Boolean,
    onDisplayChange: (LibraryDisplay) -> Unit,
    onPerRowChange: (Int) -> Unit,
    onBadgeDownloadedChange: (Boolean) -> Unit,
    onBadgeLocalChange: (Boolean) -> Unit,
    onBadgeUnreadChange: (Boolean) -> Unit,
    onShowTabsChange: (Boolean) -> Unit,
    onShowCountChange: (Boolean) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current

    SheetHeader("Display mode")
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        LibraryDisplay.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    FilterChip(
                        selected = display == mode,
                        onClick = {
                            onDisplayChange(mode)
                            LibraryPrefs.setDisplay(context, mode)
                            onChanged()
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text("Items per row", modifier = Modifier.weight(1f))
        AssistChip(
            onClick = {
                onPerRowChange(0)
                LibraryPrefs.setItemsPerRow(context, 0)
                onChanged()
            },
            label = { Text(if (perRow == 0) "Auto" else "${perRow}") },
        )
    }

    Slider(
        value = (if (perRow == 0) 1 else perRow).toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(1, 10)
            onPerRowChange(next)
            LibraryPrefs.setItemsPerRow(context, next)
            onChanged()
        },
        valueRange = 1f..10f,
        steps = 8,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    SheetHeader("Badges")
    CheckRow("Downloaded", badgeDownloaded) {
        onBadgeDownloadedChange(it)
        LibraryPrefs.setBadgeDownloaded(context, it)
        onChanged()
    }
    CheckRow("Local source", badgeLocal) {
        onBadgeLocalChange(it)
        LibraryPrefs.setBadgeLocal(context, it)
        onChanged()
    }
    CheckRow("Unread count", badgeUnread) {
        onBadgeUnreadChange(it)
        LibraryPrefs.setBadgeUnread(context, it)
        onChanged()
    }

    SheetHeader("Tabs")
    CheckRow("Show category tabs", showTabs) {
        onShowTabsChange(it)
        LibraryPrefs.setShowTabs(context, it)
        onChanged()
    }
    CheckRow("Show number of items", showCount) {
        onShowCountChange(it)
        LibraryPrefs.setShowCount(context, it)
        onChanged()
    }
}

@Composable
internal fun LibraryGroupOptions(
    group: LibraryGroup,
    onGroupChange: (LibraryGroup) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current

    LibraryGroup.entries.forEach { option ->
        SortRow(
            label = option.label,
            selected = group == option,
            ascending = true,
            showDirection = false,
            onClick = {
                onGroupChange(option)
                LibraryPrefs.setGroup(context, option)
                onChanged()
            },
        )
    }

    SheetNote(
        "Grouping by source isn't offered: a library entry stores the source id " +
            "it came from, never the source's name, so the tabs would read as raw extension ids.",
    )
}
