package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
// PullToRefreshBox lives in a SUB-PACKAGE of material3. The wildcard above does
// NOT reach it — that is exactly the 0.98 CI failure.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    history: List<HistoryEntry>,
    loading: Boolean,
    error: String?,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    /** Bumped when library state moves; refreshes the corner markers. */
    libraryTick: Int,
    onClearAll: () -> Unit,
    /**
     * Re-reads the history file. Unlike the series screen's refresh this is a
     * disk read rather than a network call, so there is no honest "refreshing"
     * period and the flag below is set and cleared in one pass rather than
     * padded to make the spinner look busy.
     */
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    var mediaFilter by rememberSaveable { mutableStateOf("All") }
    val shownHistory = remember(history, mediaFilter) {
        history.filter {
            when (mediaFilter) {
                "Anime" -> it.mediaType == "anime"
                "Manga" -> it.mediaType != "anime"
                else -> true
            }
        }
    }

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

    // Once for the screen. History caps at 40 entries so the per-row cost would
    // be survivable here, which is exactly the reasoning that put an O(library)
    // read inside a row three times already — the shared helper is free.
    val marks = rememberEntryMarks(libraryTick)
    var confirmRemove by remember { mutableStateOf<HistoryEntry?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        HistoryHeader(
            historyCount = history.size,
            loading = loading,
            error = error,
            mediaFilter = mediaFilter,
            onMediaFilterChange = { mediaFilter = it },
            onClearAll = { confirmClearAll = true },
        )

        if (shownHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (history.isEmpty()) "Nothing read or watched yet."
                    else "Nothing in this history filter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRefresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The caller card 47 missed on its first pass. History caps at 40
            // entries, so this is the shortest list to carry a handle — but it
            // is also the one where every row is a full ListItem with a cover,
            // so forty of them is a long scroll in pixels.
            //
            // totalItems is history.size: no headers, no spacer, so the list's
            // item count is the data count.
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(shownHistory) { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        dim = marks.dim(entry.seriesId),
                        downloaded = marks.downloaded(entry.seriesId),
                        badgeLocal = marks.badgeLocal && entry.sourceId.isLocalSourceId(),
                        unread = marks.unreadOf(entry.seriesId),
                        onOpen = { onOpen(entry) },
                        onRemove = { confirmRemove = entry },
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

    HistoryDialogs(
        pendingRemove = confirmRemove,
        clearAllOpen = confirmClearAll,
        onDismissRemove = { confirmRemove = null },
        onConfirmRemove = {
            onDelete(it)
            confirmRemove = null
        },
        onDismissClearAll = { confirmClearAll = false },
        onConfirmClearAll = {
            onClearAll()
            confirmClearAll = false
        },
    )

}
