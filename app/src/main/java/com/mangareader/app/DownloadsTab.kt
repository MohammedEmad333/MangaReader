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
// PullToRefreshBox lives in a SUB-PACKAGE of material3, which the wildcard
// elsewhere does not reach and which the 0.98 CI failure was about.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Downloads tab: series with chapters saved to permanent storage.
 *
 * Built from [DownloadIndex] rather than from the library, because the two are
 * different sets — a chapter can be downloaded without the series ever being
 * saved, and a saved series usually has nothing downloaded at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsTab(
    downloadTick: Int,
    /** Bumped when library state moves; refreshes the corner markers. */
    libraryTick: Int,
    scroll: ScrollMemory,
    onOpen: (DownloadedSeries) -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val marks = rememberEntryMarks(libraryTick)

    // Deletes made here don't go through the service, so they wouldn't move
    // DownloadQueue.tick; a local counter covers that without pushing a callback
    // back up to YomuApp for something no other screen cares about.
    var localTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    // Cleared from an EFFECT, not from the gesture lambda.
    //
    // 0.160 set the flag true and false in one pass, reasoning that a disk
    // re-read has no honest "refreshing" period to show. The reasoning was
    // right and the implementation was wrong: PullToRefreshBox only ever
    // composed with `false`, never OBSERVED the transition, and so never ran
    // its retract animation — the arrow stayed parked where the gesture left
    // it until something else forced a recomposition.
    //
    // Clearing here gives the widget the two frames it needs to animate out.
    // That is still not a padded delay: the re-read has already happened in
    // the recomposition this flag triggered, so nothing is being waited on.
    LaunchedEffect(refreshing) {
        if (refreshing) refreshing = false
    }

    val revision = downloadTick + localTick

    // Keyed on the revision so a chapter finishing, or a delete from anywhere
    // else, re-reads rather than showing a stale list.
    //
    // On IO, not in composition. `DownloadIndex.list` is the expensive one: a
    // directory walk per downloaded chapter for the sizes this screen shows,
    // plus — when the recovery gate is open — a ChapterCache read per library
    // entry. Every stat crosses FUSE on external storage.
    //
    // It was survivable before 0.72 only by accident: the library screen asked
    // for the same thing at startup, so by the time this tab was opened the
    // memos were warm and someone else had already paid. 0.72 stopped the
    // library asking, which was right, and left this screen paying it cold and
    // on the main thread — where it presents as the app not responding.
    //
    // `null` means "still working", which is what the empty state below reads
    // to tell loading apart from genuinely nothing downloaded. Those looked
    // identical before and one of them is not an answer.
    val loaded by produceState<List<DownloadedSeries>?>(null, revision) {
        value = withContext(Dispatchers.IO) { DownloadIndex.list(context) }
    }
    val series = loaded ?: emptyList()
    val downloadsOrdering = remember(series) { series.map { it.seriesId } }
    scroll.sync(downloadsOrdering)
    val totalSize = remember(series) { series.sumOf { it.sizeBytes } }
    var confirmDelete by remember { mutableStateOf<DownloadedSeries?>(null) }

    val queued = DownloadQueue.items.size
    val failedCount = DownloadQueue.failed.size

    Column(modifier = Modifier.fillMaxSize()) {
        DownloadsHeader(
            seriesCount = series.size,
            totalSize = totalSize,
            queued = queued,
            failedCount = failedCount,
            onOpenQueue = onOpenQueue,
        )

        if (loaded == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reading the download folder\u2026",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing downloaded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else PullToRefreshBox(
            // Instant by design: the refresh is a re-read of the download index
            // off disk, not a network call, so there is no honest "refreshing"
            // period to show. The flag is set and cleared in one pass rather
            // than padded with a delay to make the spinner look busy.
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                // TWO LAYERS OF CACHE, and 0.162 only cleared the outer one.
                //
                // DownloadIndex.list() memoises its result, so 0.160's gesture
                // re-read that and never touched the disk. 0.162 called
                // DownloadIndex.invalidate() — which forced a rebuild, and the
                // rebuild calls Downloads.isComplete() for every record, which
                // answers from ITS OWN ConcurrentHashMap memo. So the rebuild
                // re-asked a memo that still said "complete" and the disk was
                // still never consulted.
                //
                // invalidateCompletion() drops the completion and size memos
                // AND the index — it exists for exactly this, "anything that
                // moves or removes files in bulk", and a refresh is the user
                // saying that happened. Clearing the outer cache while an inner
                // one still answers is not a partial fix, it is no fix.
                Downloads.invalidateCompletion()
                localTick++
            },
            modifier = Modifier.fillMaxSize()
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Third caller of ListScrollHandle, after the library grid (0.133)
            // and the series chapter list (0.149).
            //
            // totalItems is series.size and nothing else, unlike the chapter
            // list's `visible.size + 3`: this LazyColumn has no header, no
            // actions block and no trailing spacer, so its item count IS the
            // data count. Every caller has its own version of this number and
            // getting it wrong stops the handle short of the end.
            val listState = rememberRestoredListState(
                scroll,
                "downloads",
                downloadsOrdering,
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(
                    series,
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
        } // PullToRefreshBox
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
