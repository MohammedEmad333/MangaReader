package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal enum class DownloadsSortMode(val label: String) {
    SIZE("Size"),
    TITLE("Title"),
    CHAPTERS("Chapters"),
}

internal enum class DownloadsMediaFilter(val label: String) {
    ALL("All"),
    MANGA("Manga"),
    ANIME("Anime"),
}

internal data class DownloadsViewState(
    val query: String = "",
    val sortMode: DownloadsSortMode = DownloadsSortMode.SIZE,
    val descending: Boolean = true,
    val mediaFilter: DownloadsMediaFilter = DownloadsMediaFilter.ALL,
)

internal object DownloadsViewPrefs {
    private const val PREFS = "downloads_view"
    private const val QUERY = "query"
    private const val SORT = "sort"
    private const val DESCENDING = "descending"
    private const val MEDIA = "media"

    fun load(context: android.content.Context): DownloadsViewState {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return DownloadsViewState(
            query = p.getString(QUERY, "").orEmpty(),
            sortMode = runCatching {
                DownloadsSortMode.valueOf(p.getString(SORT, null).orEmpty())
            }.getOrDefault(DownloadsSortMode.SIZE),
            descending = p.getBoolean(DESCENDING, true),
            mediaFilter = runCatching {
                DownloadsMediaFilter.valueOf(p.getString(MEDIA, null).orEmpty())
            }.getOrDefault(DownloadsMediaFilter.ALL),
        )
    }

    fun save(context: android.content.Context, state: DownloadsViewState) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(QUERY, state.query)
            .putString(SORT, state.sortMode.name)
            .putBoolean(DESCENDING, state.descending)
            .putString(MEDIA, state.mediaFilter.name)
            .apply()
    }
}

internal fun filterAndSortDownloads(
    series: List<DownloadedSeries>,
    query: String,
    sortMode: DownloadsSortMode,
    descending: Boolean,
    mediaFilter: DownloadsMediaFilter = DownloadsMediaFilter.ALL,
): List<DownloadedSeries> {
    val needle = query.trim()
    val filtered = series.filter { entry ->
        val matchesMedia = when (mediaFilter) {
            DownloadsMediaFilter.ALL -> true
            DownloadsMediaFilter.MANGA -> !entry.sourceId.isAnimeExtensionSourceId()
            DownloadsMediaFilter.ANIME -> entry.sourceId.isAnimeExtensionSourceId()
        }
        val matchesQuery = needle.isEmpty() ||
            entry.title.contains(needle, ignoreCase = true) ||
            entry.chapters.any { it.name.contains(needle, ignoreCase = true) }
        matchesMedia && matchesQuery
    }
    val sorted = when (sortMode) {
        DownloadsSortMode.SIZE -> filtered.sortedBy { it.sizeBytes }
        DownloadsSortMode.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
        DownloadsSortMode.CHAPTERS -> filtered.sortedBy { it.chapters.size }
    }
    return if (descending) sorted.asReversed() else sorted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsTab(
    downloadTick: Int,
    libraryTick: Int,
    scroll: ScrollMemory,
    onOpen: (DownloadedSeries) -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val marks = rememberEntryMarks(libraryTick)

    var localTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val initialView = remember { DownloadsViewPrefs.load(context) }
    var searchQuery by rememberSaveable { mutableStateOf(initialView.query) }
    var sortModeName by rememberSaveable { mutableStateOf(initialView.sortMode.name) }
    var sortDescending by rememberSaveable { mutableStateOf(initialView.descending) }
    var mediaFilterName by rememberSaveable { mutableStateOf(initialView.mediaFilter.name) }

    LaunchedEffect(refreshing) {
        if (refreshing) refreshing = false
    }

    val revision = downloadTick + localTick
    val loaded by produceState<List<DownloadedSeries>?>(null, revision) {
        value = withContext(Dispatchers.IO) { DownloadIndex.list(context) }
    }
    val offlineAnimeCount = remember(revision) { AnimeOfflineIndex.list(context).size }
    val series = loaded ?: emptyList()
    val sortMode = runCatching { DownloadsSortMode.valueOf(sortModeName) }
        .getOrDefault(DownloadsSortMode.SIZE)
    val mediaFilter = runCatching { DownloadsMediaFilter.valueOf(mediaFilterName) }
        .getOrDefault(DownloadsMediaFilter.ALL)

    LaunchedEffect(searchQuery, sortMode, sortDescending, mediaFilter) {
        DownloadsViewPrefs.save(
            context,
            DownloadsViewState(searchQuery, sortMode, sortDescending, mediaFilter),
        )
    }

    val visibleSeries = remember(series, searchQuery, sortMode, sortDescending, mediaFilter) {
        filterAndSortDownloads(series, searchQuery, sortMode, sortDescending, mediaFilter)
    }
    val downloadsOrdering = remember(visibleSeries) { visibleSeries.map { it.seriesId } }
    scroll.sync(downloadsOrdering)
    val totalSummary = remember(series) { summarizeDownloads(series) }
    val visibleSummary = remember(visibleSeries) { summarizeDownloads(visibleSeries) }
    var confirmDelete by remember { mutableStateOf<DownloadedSeries?>(null) }

    val queued = DownloadQueue.items.size
    val failedCount = DownloadQueue.failed.size

    Column(modifier = Modifier.fillMaxSize()) {
        DownloadsHeader(
            seriesCount = totalSummary.seriesCount,
            downloadCount = totalSummary.downloadCount,
            totalSize = totalSummary.sizeBytes,
            queued = queued,
            failedCount = failedCount,
            onOpenQueue = onOpenQueue,
        )

        if (loaded != null && (series.isNotEmpty() || offlineAnimeCount > 0)) {
            DownloadsTools(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                sortMode = sortMode,
                onSortModeChange = { sortModeName = it.name },
                descending = sortDescending,
                onDescendingChange = { sortDescending = it },
                mediaFilter = mediaFilter,
                onMediaFilterChange = { mediaFilterName = it.name },
                onClear = {
                    searchQuery = ""
                    sortModeName = DownloadsSortMode.SIZE.name
                    sortDescending = true
                    mediaFilterName = DownloadsMediaFilter.ALL.name
                },
                shownCount = visibleSeries.size,
                totalCount = series.size,
                shownDownloadCount = visibleSummary.downloadCount,
                shownSize = visibleSummary.sizeBytes,
            )
        }

        if (loaded == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reading the download folder…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (series.isEmpty() && offlineAnimeCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing downloaded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                Downloads.invalidateCompletion()
                localTick++
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val listState = rememberRestoredListState(
                    scroll,
                    "downloads",
                    downloadsOrdering,
                )
                if (visibleSeries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (mediaFilter == DownloadsMediaFilter.ANIME && offlineAnimeCount > 0) {
                                "Offline anime downloads are shown above."
                            } else {
                                "No downloads match the current search or filter."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(
                        visibleSeries,
                        key = { it.seriesId },
                        contentType = { "downloaded-series" },
                    ) { entry ->
                        DownloadsSeriesRow(
                            entry = entry,
                            dim = marks.dim(entry.seriesId),
                            badgeLocal = marks.badgeLocal && entry.sourceId.isLocalSourceId(),
                            unread = marks.unreadOf(entry.seriesId),
                            onOpen = { onOpen(entry) },
                            onDelete = { confirmDelete = entry },
                        )
                    }
                }
                ListScrollHandle(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    DownloadsDeleteDialog(
        entry = confirmDelete,
        onDismiss = { confirmDelete = null },
        onConfirm = { entry ->
            DownloadIndex.deleteSeries(context, entry)
            localTick++
            confirmDelete = null
        },
    )
}

internal data class DownloadsSummary(
    val seriesCount: Int,
    val downloadCount: Int,
    val sizeBytes: Long,
)

internal fun summarizeDownloads(series: List<DownloadedSeries>): DownloadsSummary =
    DownloadsSummary(
        seriesCount = series.size,
        downloadCount = series.sumOf { it.chapters.size },
        sizeBytes = series.sumOf { it.sizeBytes },
    )
