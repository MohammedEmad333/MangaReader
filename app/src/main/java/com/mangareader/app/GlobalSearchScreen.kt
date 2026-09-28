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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

// ---------- prefs: the same store every other object in this package uses ----------

/**
 * One query fanned out across the searchable sources — every one of them, or just
 * the pinned ones when that chip is on. Rows appear as their batch finishes;
 * sources that error out or return nothing are simply absent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GlobalSearchScreen(
    query: String,
    results: List<GlobalResult>,
    running: Boolean,
    done: Int,
    total: Int,
    pinnedOnly: Boolean,
    onTogglePinnedOnly: (Boolean) -> Unit,
    hasResultsOnly: Boolean,
    onToggleHasResultsOnly: (Boolean) -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    /** Past queries, newest first, for the one-tap-to-rerun chips. */
    recents: List<String>,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    /**
     * Migration mode: the screen is a target picker for moving a library series
     * to another source. A tapped result is the chosen target rather than a
     * series to open, and per-source "See all" is hidden because there is
     * nothing to browse into here.
     */
    migrating: Boolean = false,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onOpenSeries: (Source, Series) -> Unit,
    /** Bumped when library state moves; refreshes the corner markers. */
    libraryTick: Int,
    scroll: ScrollMemory,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    // Once for the whole screen, not once per result. A global search can put
    // several hundred cells on screen across a dozen source rows.
    val marks = rememberEntryMarks(libraryTick)
    var field by remember(query) { mutableStateOf(query) }

    // Read once per entry into the composition: the pin set only changes over in
    // the Browse tab, which tears this screen down on the way there and back.
    val hasPinned = remember { SourcePrefs.pinned(context).isNotEmpty() }

    val withHits = results.count { it.series.isNotEmpty() }
    val titleMatches = results.sumOf { it.series.size }
    val shown = if (hasResultsOnly) results.filter { it.series.isNotEmpty() } else results
    val ordering = remember(query, shown, pinnedOnly, hasResultsOnly, mediaFilter, migrating) {
        buildList {
            add(query)
            add(pinnedOnly.toString())
            add(hasResultsOnly.toString())
            add(mediaFilter)
            add(migrating.toString())
            shown.forEach { add(it.source.id.toString()) }
        }
    }
    scroll.sync(ordering)

    Column(modifier = Modifier.fillMaxSize()) {
        GlobalSearchControls(
            migrating = migrating,
            field = field,
            onFieldChange = { field = it },
            running = running,
            onSearch = { onSearch(field.trim()) },
            onCancel = onCancel,
            pinnedOnly = pinnedOnly,
            hasPinned = hasPinned,
            onTogglePinnedOnly = onTogglePinnedOnly,
            hasResultsOnly = hasResultsOnly,
            onToggleHasResultsOnly = onToggleHasResultsOnly,
            mediaFilter = mediaFilter,
            onMediaFilterChange = onMediaFilterChange,
            done = done,
            total = total,
            withHits = withHits,
            titleMatches = titleMatches,
            onBack = onBack,
        )

        if (shown.isEmpty()) {
            GlobalSearchEmptyState(
                running = running,
                query = query,
                recents = recents,
                onRecentSearch = { recent ->
                    field = recent
                    onSearch(recent)
                },
                onRemoveRecent = onRemoveRecent,
                onClearRecents = onClearRecents,
                modifier = Modifier.weight(1f),
            )
        } else Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Fourth caller of ListScrollHandle.
            //
            // One lazy item PER SOURCE here, not per series: each draws a
            // source header and a horizontal row of covers, so this list is
            // short in items and tall in pixels. The handle seeks by item
            // index, which is still the right unit even when one item is most
            // of a screen.
            val listState = rememberRestoredListState(
                scroll,
                "global-search",
                ordering,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown) { result ->
                    GlobalSearchResultRow(
                        result = result,
                        migrating = migrating,
                        dimFor = { marks.dim(it) },
                        downloadedFor = { marks.downloaded(it) },
                        unreadFor = { marks.unreadOf(it) },
                        onOpenSource = onOpenSource,
                        onOpenSeries = onOpenSeries,
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

// ---------- library ----------
