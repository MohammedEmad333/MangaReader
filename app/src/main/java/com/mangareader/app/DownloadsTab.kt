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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/** The verified local view remains usable while legacy recovery runs. */
private data class DownloadsLoadState(
    val items: List<DownloadedSeries>? = null,
    val complete: Boolean = false,
    val error: String? = null,
    val storageUnavailable: Boolean = false,
)

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
    val load by produceState(DownloadsLoadState(), revision) {
        val app = context.applicationContext
        // The selected SD card may have returned since an earlier access
        // failure. Re-resolve its location before reading any saved chapters.
        StorageLocation.invalidate()
        var indexed: List<DownloadedSeries>? = null
        var unavailable = false
        try {
            val snapshot = withContext(Dispatchers.IO) {
                DownloadIndex.listIndexed(app) to
                    (StorageLocation.chosen(app) != null && !StorageLocation.active(app))
            }
            indexed = snapshot.first
            unavailable = snapshot.second
            value = DownloadsLoadState(items = indexed, storageUnavailable = unavailable)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Legacy recovery may still reconstruct a missing local index.
        }
        try {
            val recovered = withContext(Dispatchers.IO) {
                DownloadIndex.list(app) to
                    (StorageLocation.chosen(app) != null && !StorageLocation.active(app))
            }
            value = DownloadsLoadState(
                items = recovered.first, complete = true,
                storageUnavailable = recovered.second,
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            value = DownloadsLoadState(
                items = indexed, complete = true, storageUnavailable = unavailable,
                error = if (indexed == null) {
                    "Could not read the download folder. Check storage access and retry."
                } else {
                    "Older folders could not be checked. Saved downloads remain available."
                },
            )
        }
    }
    // UI timer is independent from potentially blocking disk IO.
    val scanSlow by produceState(false, revision) {
        delay(12_000L)
        value = true
    }
    val loaded = load.items
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
    var repairScan by remember { mutableStateOf<OfflineRepairScan?>(null) }
    var checkingOffline by remember { mutableStateOf(false) }
    var repairMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            checkingOffline = checkingOffline,
            onCheckOffline = {
                if (!checkingOffline) scope.launch {
                    checkingOffline = true
                    try {
                        repairScan = withContext(Dispatchers.IO) {
                            scanStoredOfflineRepairs(context)
                        }
                    } catch (_: Exception) {
                        repairMessage = "Could not check offline chapters. Your files were not changed."
                    } finally {
                        checkingOffline = false
                    }
                }
            },
        )

        if (load.storageUnavailable) {
            Text(
                "Selected download folder unavailable. Reconnect storage or restore " +
                    "file permission; your saved files were not deleted.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val loadWarning = load.error ?: if (scanSlow && !load.complete) {
            "Reading local download folders is taking longer than expected."
        } else null
        if (loadWarning != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    loadWarning,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    StorageLocation.invalidate()
                    Downloads.invalidateCompletion()
                    localTick++
                }) { Text("Retry") }
            }
        }
        if (loaded != null && !load.complete && series.isNotEmpty()) {
            Text(
                "Showing verified saved chapters while checking older folders…",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

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

        if (loaded == null || (loaded.isEmpty() && !load.complete)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (scanSlow || load.error != null) {
                        "Download folder needs attention. Tap Retry above."
                    } else {
                        "Checking locally saved chapters…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (series.isEmpty() && offlineAnimeCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (load.storageUnavailable) {
                        "Reconnect the selected download folder to view your saved files."
                    } else "Nothing downloaded yet.",
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

    val scan = repairScan
    if (scan != null) {
        val summary = scan.summary
        AlertDialog(
            onDismissRequest = { if (!checkingOffline) repairScan = null },
            title = { Text("Offline chapter check") },
            text = {
                Text(
                    "${summary.completeChapters} complete · " +
                        "${summary.repairableChapters} repairable · " +
                        "${summary.blockedChapters} need manual review.\n" +
                        "${summary.pagesToRedownload} missing or invalid pages can be retried. " +
                        "Complete pages are kept. Ambiguous folders will not be changed."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !checkingOffline && scan.repairItems.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            checkingOffline = true
                            try {
                                // Re-audit immediately before queueing: files may
                                // have changed since the dialog was first opened.
                                val current = withContext(Dispatchers.IO) {
                                    scanStoredOfflineRepairs(context)
                                }
                                val added = DownloadQueue.enqueue(context, current.repairItems)
                                if (added > 0) DownloadService.start(context)
                                repairScan = null
                                repairMessage = if (added > 0) {
                                    "$added chapters added to the repair queue. " +
                                        "Existing valid pages will be reused."
                                } else {
                                    "No new repairs were queued. Downloads may already be complete or queued."
                                }
                            } catch (_: Exception) {
                                repairMessage = "Could not queue the repairs. No chapter files were deleted."
                                repairScan = null
                            } finally {
                                checkingOffline = false
                            }
                        }
                    },
                ) { Text("Queue safe repairs") }
            },
            dismissButton = {
                TextButton(
                    enabled = !checkingOffline,
                    onClick = { repairScan = null },
                ) { Text("Close") }
            },
        )
    }

    repairMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { repairMessage = null },
            title = { Text("Offline repair") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { repairMessage = null }) { Text("OK") }
            },
        )
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
