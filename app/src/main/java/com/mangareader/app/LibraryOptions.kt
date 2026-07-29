package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The library options sheet: Filter, Sort, Display, Group.
 *
 * Every control writes its pref immediately and calls [onChanged], which bumps
 * the library's tick. Nothing is staged and there is no Apply button — the grid
 * behind the sheet updates as each row is tapped, which is the whole point of a
 * sheet rather than a dialog.
 *
 * **What isn't here, and why.** Mihon's version of this sheet also offers
 * Unread, Started, Completed and Bookmarked filters, and sorts by total
 * chapters, unread count, latest chapter and fetch date. All of those need a
 * chapter list per series. `ChapterCache` only holds one for a series that has
 * actually been opened, as a file per series — so answering "how many unread"
 * across a 3567-entry library means either a network fetch per series or
 * thousands of file reads on the library screen. They are left out rather than
 * shipped as switches that do nothing; see the reader-settings note in §4 for
 * the same call made once already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryOptionsSheet(
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    // Local mirrors of the prefs. The pref is still the store — these exist so
    // the sheet repaints on tap without waiting for a round trip through the
    // library's tick.
    var sort by remember { mutableStateOf(LibraryPrefs.sort(context)) }
    var ascending by remember { mutableStateOf(LibraryPrefs.ascending(context)) }
    var display by remember { mutableStateOf(LibraryPrefs.display(context)) }
    var perRow by remember { mutableIntStateOf(LibraryPrefs.itemsPerRow(context)) }
    var group by remember { mutableStateOf(LibraryPrefs.group(context)) }
    var badgeDl by remember { mutableStateOf(LibraryPrefs.badgeDownloaded(context)) }
    var badgeLocal by remember { mutableStateOf(LibraryPrefs.badgeLocal(context)) }
    var showTabs by remember { mutableStateOf(LibraryPrefs.showTabs(context)) }
    var showCount by remember { mutableStateOf(LibraryPrefs.showCount(context)) }
    var fDownloaded by remember { mutableStateOf(LibraryPrefs.filterDownloaded(context)) }
    var fLocal by remember { mutableStateOf(LibraryPrefs.filterLocal(context)) }
    var fRead by remember { mutableStateOf(LibraryPrefs.filterRead(context)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TabRow(selectedTabIndex = tab) {
            listOf("Filter", "Sort", "Display", "Group").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Column(
            modifier = Modifier
                .heightIn(min = 260.dp, max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            when (tab) {
                0 -> {
                    TriFilterRow("Downloaded", fDownloaded) {
                        fDownloaded = it
                        LibraryPrefs.setFilterDownloaded(context, it)
                        onChanged()
                    }
                    TriFilterRow("Local source", fLocal) {
                        fLocal = it
                        LibraryPrefs.setFilterLocal(context, it)
                        onChanged()
                    }
                    TriFilterRow("Read", fRead) {
                        fRead = it
                        LibraryPrefs.setFilterRead(context, it)
                        onChanged()
                    }
                    SheetNote(
                        "Tap once to require, twice to exclude, three times to clear. " +
                            "\u201cRead\u201d is the category of that name."
                    )
                }

                1 -> {
                    LibrarySort.entries.forEach { option ->
                        SortRow(
                            label = option.label,
                            selected = sort == option,
                            ascending = ascending,
                            trailing = {
                                if (option == LibrarySort.RANDOM) {
                                    IconButton(onClick = {
                                        LibraryPrefs.reshuffle(context)
                                        onChanged()
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reshuffle")
                                    }
                                }
                            },
                            onClick = {
                                if (sort == option && option != LibrarySort.RANDOM) {
                                    // Tapping the active sort flips direction,
                                    // which is the only way to reach descending.
                                    ascending = !ascending
                                    LibraryPrefs.setAscending(context, ascending)
                                } else {
                                    sort = option
                                    LibraryPrefs.setSort(context, option)
                                }
                                onChanged()
                            }
                        )
                    }
                    SheetNote(
                        "Tap the active sort again to reverse it. Last read uses the " +
                            "history, which keeps the 40 most recent chapters — anything " +
                            "older sorts to the end."
                    )
                }

                2 -> {
                    SheetHeader("Display mode")
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LibraryDisplay.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { mode ->
                                    FilterChip(
                                        selected = display == mode,
                                        onClick = {
                                            display = mode
                                            LibraryPrefs.setDisplay(context, mode)
                                            onChanged()
                                        },
                                        label = { Text(mode.label) }
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
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("Items per row", modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = {
                                perRow = 0
                                LibraryPrefs.setItemsPerRow(context, 0)
                                onChanged()
                            },
                            label = { Text(if (perRow == 0) "Auto" else "$perRow") }
                        )
                    }
                    // Zero is Auto and lives on the chip, so the slider starts
                    // at one. Dragging it off Auto is what sets a fixed count.
                    Slider(
                        value = (if (perRow == 0) 1 else perRow).toFloat(),
                        onValueChange = {
                            perRow = it.roundToInt().coerceIn(1, 10)
                            LibraryPrefs.setItemsPerRow(context, perRow)
                            onChanged()
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SheetHeader("Badges")
                    CheckRow("Downloaded", badgeDl) {
                        badgeDl = it
                        LibraryPrefs.setBadgeDownloaded(context, it)
                        onChanged()
                    }
                    CheckRow("Local source", badgeLocal) {
                        badgeLocal = it
                        LibraryPrefs.setBadgeLocal(context, it)
                        onChanged()
                    }

                    SheetHeader("Tabs")
                    CheckRow("Show category tabs", showTabs) {
                        showTabs = it
                        LibraryPrefs.setShowTabs(context, it)
                        onChanged()
                    }
                    CheckRow("Show number of items", showCount) {
                        showCount = it
                        LibraryPrefs.setShowCount(context, it)
                        onChanged()
                    }
                }

                else -> {
                    listOf(LibraryGroup.CATEGORIES, LibraryGroup.UNGROUPED).forEach { option ->
                        SortRow(
                            label = option.label,
                            selected = group == option,
                            ascending = true,
                            showDirection = false,
                            onClick = {
                                group = option
                                LibraryPrefs.setGroup(context, option)
                                onChanged()
                            }
                        )
                    }
                    SheetNote(
                        "Grouping by source isn't offered: a library entry stores the " +
                            "source id it came from, never the source's name, so the tabs " +
                            "would read as raw extension ids."
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun TriFilterRow(label: String, state: FilterState, onChange: (FilterState) -> Unit) {
    val toggle = when (state) {
        FilterState.OFF -> ToggleableState.Off
        FilterState.INCLUDE -> ToggleableState.On
        FilterState.EXCLUDE -> ToggleableState.Indeterminate
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(state.next()) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TriStateCheckbox(state = toggle, onClick = { onChange(state.next()) })
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label)
            if (state == FilterState.EXCLUDE) {
                Text(
                    "Excluded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SortRow(
    label: String,
    selected: Boolean,
    ascending: Boolean,
    showDirection: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.width(32.dp)) {
            if (selected) {
                if (showDirection) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = if (ascending) "Ascending" else "Descending",
                        tint = MaterialTheme.colorScheme.primary,
                        // One glyph, turned over, rather than two icons: the
                        // extended icon pack isn't a dependency here.
                        modifier = Modifier.rotate(if (ascending) 180f else 0f)
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(label, modifier = Modifier.weight(1f))
        trailing()
    }
}
