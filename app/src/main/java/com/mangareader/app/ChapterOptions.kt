package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Filter / Sort / Display for the chapter list.
 *
 * Same shape as `LibraryOptionsSheet` and reusing its rows, which is most of why
 * this was cheap. Written on tap rather than on dismiss, for the reason the
 * reader's settings sheet is: a sheet can be swiped away, and a setting lost
 * because it was closed the wrong way is a bug nobody reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterOptionsSheet(
    seriesId: String,
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    var sort by remember { mutableStateOf(ChapterPrefs.sort(context)) }
    var ascending by remember { mutableStateOf(ChapterPrefs.ascending(context)) }
    var display by remember { mutableStateOf(ChapterPrefs.display(context)) }
    var fDownloaded by remember { mutableStateOf(ChapterPrefs.filterDownloaded(context)) }
    var fUnread by remember { mutableStateOf(ChapterPrefs.filterUnread(context)) }
    var fBookmarked by remember { mutableStateOf(ChapterPrefs.filterBookmarked(context)) }
    val scanlatorOptions = remember(chapters) {
        chapters.mapNotNull { it.scanlator?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selectedScanlators by remember(seriesId, scanlatorOptions) {
        mutableStateOf(
            ChapterPrefs.scanlators(context, seriesId)
                .intersect(scanlatorOptions.toSet())
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TabRow(selectedTabIndex = tab) {
            listOf("Filter", "Sort", "Display").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Column(
            modifier = Modifier
                .heightIn(min = 220.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            when (tab) {
                0 -> {
                    TriFilterRow("Downloaded", fDownloaded) {
                        fDownloaded = it
                        ChapterPrefs.setFilterDownloaded(context, it)
                        onChanged()
                    }
                    TriFilterRow("Unread", fUnread) {
                        fUnread = it
                        ChapterPrefs.setFilterUnread(context, it)
                        onChanged()
                    }
                    TriFilterRow("Bookmarked", fBookmarked) {
                        fBookmarked = it
                        ChapterPrefs.setFilterBookmarked(context, it)
                        onChanged()
                    }
                    if (scanlatorOptions.isNotEmpty()) {
                        Text(
                            "Scanlator",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedScanlators.isEmpty(),
                                onClick = {
                                    selectedScanlators = emptySet()
                                    ChapterPrefs.setScanlators(context, seriesId, emptySet())
                                    onChanged()
                                },
                                label = { Text("All") },
                            )
                            scanlatorOptions.forEach { scanlator ->
                                FilterChip(
                                    selected = scanlator in selectedScanlators,
                                    onClick = {
                                        selectedScanlators =
                                            if (scanlator in selectedScanlators) {
                                                selectedScanlators - scanlator
                                            } else {
                                                selectedScanlators + scanlator
                                            }
                                        ChapterPrefs.setScanlators(
                                            context,
                                            seriesId,
                                            selectedScanlators,
                                        )
                                        onChanged()
                                    },
                                    label = { Text(scanlator) },
                                )
                            }
                        }
                    }
                }
                1 -> {
                    ChapterSort.entries.forEach { option ->
                        SortRow(
                            label = option.label,
                            selected = sort == option,
                            ascending = ascending,
                            onClick = {
                                // Tapping the current sort flips direction;
                                // tapping another switches to it and keeps the
                                // direction. Same rule as the library sheet, so
                                // the two behave the same under the thumb.
                                if (sort == option) {
                                    ascending = !ascending
                                    ChapterPrefs.setAscending(context, ascending)
                                } else {
                                    sort = option
                                    ChapterPrefs.setSort(context, option)
                                }
                                onChanged()
                            }
                        )
                    }
                    SheetNote(
                        "Chapters without a number or date from the source stay at " +
                            "the end, whichever way round the sort runs."
                    )
                }
                2 -> {
                    ChapterDisplay.entries.forEach { option ->
                        SortRow(
                            label = option.label,
                            selected = display == option,
                            ascending = true,
                            showDirection = false,
                            onClick = {
                                display = option
                                ChapterPrefs.setDisplay(context, option)
                                onChanged()
                            }
                        )
                    }
                    SheetNote(
                        "A chapter the source gave no number for keeps its name."
                    )
                }
            }
        }
    }
}

/**
 * The bulk-download choices, from SY's `DownloadAction`.
 *
 * [BOOKMARKED] is this app's own addition rather than SY's — bookmarks landed
 * in 0.120 and this is half of what they unblocked, the other half being the
 * Bookmarked row in the filter sheet above.
 */
enum class DownloadChoice(val label: String) {
    NEXT_1("Next chapter"),
    NEXT_5("Next 5 chapters"),
    NEXT_10("Next 10 chapters"),
    NEXT_25("Next 25 chapters"),
    UNREAD("All unread chapters"),
    BOOKMARKED("All bookmarked chapters")
}

/**
 * Which chapters a [DownloadChoice] actually queues.
 *
 * **"Next 5" means the next five that are unread *and not already on disk*, not
 * the next five rows.** That is SY's rule (`getUnreadChapters` filters on
 * `NOT_DOWNLOADED` before taking any) and it is the whole usefulness of the
 * menu: taken literally as "the next five rows", tapping it on a series you are
 * part-way through re-queues chapters you already have and looks like a button
 * that did nothing.
 *
 * **[DownloadChoice.BOOKMARKED] ignores read state, and that is deliberate.**
 * Every other choice is a form of "what should I read next", so being unread is
 * part of the question. A bookmark is a third thing that survives both reading a
 * chapter and marking it unread (see [Bookmarks]), so filtering bookmarks by
 * unread would silently skip the chapter someone bookmarked *because* they had
 * read it and wanted it kept. Not-already-downloaded still applies, for the
 * reason above.
 *
 * Runs over [chapters] in the order they are **drawn**, so the menu follows the
 * sort and filter currently on screen rather than a hidden second ordering.
 */
internal fun downloadTargets(
    context: Context,
    chapters: List<Chapter>,
    sourceId: String,
    choice: DownloadChoice
): List<Chapter> {
    // Common to every choice: queueing something already on disk is the one
    // outcome that makes the whole menu look broken.
    val notDownloaded = chapters.filter { !Downloads.isComplete(context, it.id) }
    val unread = notDownloaded.filter {
        !ReadState.isRead(context, chapterKeyOf(sourceId, it))
    }
    return when (choice) {
        DownloadChoice.NEXT_1 -> unread.take(1)
        DownloadChoice.NEXT_5 -> unread.take(5)
        DownloadChoice.NEXT_10 -> unread.take(10)
        DownloadChoice.NEXT_25 -> unread.take(25)
        DownloadChoice.UNREAD -> unread
        DownloadChoice.BOOKMARKED -> notDownloaded.filter {
            Bookmarks.isBookmarked(context, chapterKeyOf(sourceId, it))
        }
    }
}
