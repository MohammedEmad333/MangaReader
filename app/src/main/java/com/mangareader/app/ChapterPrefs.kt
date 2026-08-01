package com.mangareader.app

import android.content.Context
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
 * How the chapter list is ordered.
 *
 * [SOURCE] is the list exactly as the extension returned it, reversed once into
 * reading order by `TachiyomiSourceAdapter.listChapters`. It is the default
 * because it is the only ordering the source itself vouches for — everything
 * below is derived from a field the source may not have filled in.
 */
enum class ChapterSort(val key: String, val label: String) {
    SOURCE("source", "By source"),
    NUMBER("number", "By chapter number"),
    UPLOAD_DATE("date", "By upload date"),
    ALPHABETICAL("alpha", "Alphabetically");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SOURCE
    }
}

/** What a chapter row is titled with. */
enum class ChapterDisplay(val key: String, val label: String) {
    NAME("name", "Chapter name"),
    NUMBER("number", "Chapter number");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: NAME
    }
}

/**
 * Chapter list filter, sort and display — **one global store, not per series.**
 *
 * `ReaderPrefs` faced this exact choice and answered it the same way, and §4 of
 * the handoff records why: a per-series store needs a second store keyed by
 * series id *and* a "use default" state distinct from every real value, which is
 * a data model decision rather than a screen. The enums carry a `key` so that
 * overlay stays additive if it is ever wanted.
 *
 * What it costs, so nobody is surprised: filtering to Unread on a webtoon leaves
 * it filtered on the next series opened.
 *
 * Reuses [FilterState] rather than declaring a second tri-state enum.
 */
object ChapterPrefs {
    private fun p(c: Context) = c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun sort(c: Context) = ChapterSort.from(p(c).getString("ch_sort", null))
    fun setSort(c: Context, v: ChapterSort) = p(c).edit().putString("ch_sort", v.key).apply()

    fun ascending(c: Context) = p(c).getBoolean("ch_sort_asc", true)
    fun setAscending(c: Context, v: Boolean) =
        p(c).edit().putBoolean("ch_sort_asc", v).apply()

    fun display(c: Context) = ChapterDisplay.from(p(c).getString("ch_display", null))
    fun setDisplay(c: Context, v: ChapterDisplay) =
        p(c).edit().putString("ch_display", v.key).apply()

    fun filterDownloaded(c: Context) = FilterState.from(p(c).getInt("ch_f_dl", 0))
    fun setFilterDownloaded(c: Context, v: FilterState) =
        p(c).edit().putInt("ch_f_dl", v.stored).apply()

    fun filterUnread(c: Context) = FilterState.from(p(c).getInt("ch_f_unread", 0))
    fun setFilterUnread(c: Context, v: FilterState) =
        p(c).edit().putInt("ch_f_unread", v.stored).apply()

    fun filterBookmarked(c: Context) = FilterState.from(p(c).getInt("ch_f_bm", 0))
    fun setFilterBookmarked(c: Context, v: FilterState) =
        p(c).edit().putInt("ch_f_bm", v.stored).apply()

    /** Drives the tint on the top bar's filter icon, the way SY tints its own. */
    fun anyFilterActive(c: Context) =
        filterDownloaded(c) != FilterState.OFF ||
            filterUnread(c) != FilterState.OFF ||
            filterBookmarked(c) != FilterState.OFF
}

/**
 * The chapter list as it should be *drawn*.
 *
 * **This is a view, and the caller must keep the full list.** `YomuApp` holds
 * `chapterList` and `openChapter` indexes into it; the series screen renders
 * this. Anything crossing that boundary goes by chapter id — see the `onOpen`
 * parameter on `SeriesScreen`. Hand an index from this list to something that
 * indexes the other one and it opens the wrong chapter, silently.
 *
 * `read` and `downloaded` are each a lookup per chapter, so this runs inside a
 * `remember` keyed on the ticks that can change either.
 *
 * A chapter with no [Chapter.NO_NUMBER] number sorts **last** under
 * [ChapterSort.NUMBER] rather than as a chapter zero, in either direction. That
 * is the point of the sentinel — see `Chapter.number`.
 */
internal fun visibleChapters(
    context: Context,
    chapters: List<Chapter>,
    sourceId: String
): List<Chapter> {
    val fDownloaded = ChapterPrefs.filterDownloaded(context)
    val fUnread = ChapterPrefs.filterUnread(context)
    val fBookmarked = ChapterPrefs.filterBookmarked(context)

    val filtered = chapters.filter { ch ->
        val downloadedOk = when (fDownloaded) {
            FilterState.OFF -> true
            FilterState.INCLUDE -> Downloads.isComplete(context, ch.id)
            FilterState.EXCLUDE -> !Downloads.isComplete(context, ch.id)
        }
        if (!downloadedOk) return@filter false
        val unreadOk = when (fUnread) {
            FilterState.OFF -> true
            FilterState.INCLUDE -> !ReadState.isRead(context, chapterKeyOf(sourceId, ch))
            FilterState.EXCLUDE -> ReadState.isRead(context, chapterKeyOf(sourceId, ch))
        }
        if (!unreadOk) return@filter false
        // Bookmarks are stored true or removed, never stored false, so the
        // absent case and the "not bookmarked" case are one lookup and there is
        // no third state to collapse here — unlike `SeriesIndex`, where absent
        // and zero mean different things.
        when (fBookmarked) {
            FilterState.OFF -> true
            FilterState.INCLUDE -> Bookmarks.isBookmarked(context, chapterKeyOf(sourceId, ch))
            FilterState.EXCLUDE -> !Bookmarks.isBookmarked(context, chapterKeyOf(sourceId, ch))
        }
    }

    val ascending = ChapterPrefs.ascending(context)
    val sorted = when (ChapterPrefs.sort(context)) {
        // Already in reading order. `reversed()` rather than a comparator,
        // because there is no key to sort on — the order is the source's.
        ChapterSort.SOURCE -> if (ascending) filtered else filtered.asReversed()
        ChapterSort.NUMBER -> filtered.sortedWith(
            // Unnumbered last in both directions: `compareBy` on a sentinel
            // would put them at whichever end -1f lands on, which flips when
            // the direction does and reads as the sort losing chapters.
            compareBy<Chapter> { it.number <= Chapter.NO_NUMBER }
                .thenBy { if (ascending) it.number else -it.number }
        )
        ChapterSort.UPLOAD_DATE -> filtered.sortedWith(
            // Same shape for a source that publishes no date: 0 is "didn't say".
            compareBy<Chapter> { it.dateUploaded <= 0L }
                .thenBy { if (ascending) it.dateUploaded else -it.dateUploaded }
        )
        ChapterSort.ALPHABETICAL ->
            filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .let { if (ascending) it else it.asReversed() }
    }
    return sorted
}

/** What to title a chapter row with, honouring [ChapterDisplay]. */
internal fun chapterLabel(chapter: Chapter, display: ChapterDisplay): String = when {
    display == ChapterDisplay.NUMBER && chapter.number > Chapter.NO_NUMBER -> {
        // Trailing ".0" dropped: sources publish 12.0 for a whole chapter and
        // 12.5 for a side story, and "Chapter 12.0" reads as a typo.
        val n = chapter.number
        if (n == n.toInt().toFloat()) "Chapter ${n.toInt()}" else "Chapter $n"
    }
    // Falls back rather than showing nothing. A source that publishes no number
    // is normal, and an empty row would look like a broken chapter.
    else -> chapter.name
}

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
                    // Three rows where the reference has four, and saying why is
                    // cheaper than being asked. A scanlator filter is a *per
                    // series* set of names, which this global store is the wrong
                    // shape to hold.
                    SheetNote(
                        "Filtering by scanlator needs a per-series setting rather " +
                            "than this one, so it isn't here yet."
                    )
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
