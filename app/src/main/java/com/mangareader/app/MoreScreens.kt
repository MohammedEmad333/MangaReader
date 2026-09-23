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

// ---------- prefs: the same store every other object in this package uses ----------

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

    // Once for the screen. History caps at 40 entries so the per-row cost would
    // be survivable here, which is exactly the reasoning that put an O(library)
    // read inside a row three times already — the shared helper is free.
    val marks = rememberEntryMarks(libraryTick)
    var confirmRemove by remember { mutableStateOf<HistoryEntry?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            if (history.isNotEmpty()) {
                TextButton(onClick = { confirmClearAll = true }) { Text("Clear all") }
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)
        HorizontalDivider()

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing read yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                items(history) { entry ->
                    // History holds entries for series that may since have been
                    // removed from the library, so most of these carry nothing.
                    val dim = marks.dim(entry.seriesId)
                    ListItem(
                        leadingContent = {
                            CoverImage(
                                cover = coverModel(entry.coverPath),
                                title = entry.title,
                                modifier = Modifier
                                    .width(64.dp)
                                    .aspectRatio(0.7f)
                                    .alpha(if (dim) 0.4f else 1f)
                            )
                        },
                        headlineContent = {
                            Text(
                                entry.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(if (dim) 0.4f else 1f)
                            )
                        },
                        supportingContent = {
                            // Beside the page position rather than over the
                            // cover: 40dp is the smallest thumbnail in the app
                            // and a chip on it would hide most of the art.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (entry.total > 0) "Page ${entry.page + 1} of ${entry.total}"
                                    else "Page ${entry.page + 1}"
                                )
                                EntryBadges(
                                    downloaded = marks.downloaded(entry.seriesId),
                                    local = marks.badgeLocal &&
                                        !entry.sourceId.startsWith("tachi:"),
                                    unread = marks.unreadOf(entry.seriesId)
                                )
                            }
                        },
                        modifier = Modifier.clickable { onOpen(entry) },
                        trailingContent = {
                            // An icon, matching the Downloads tab and the chapter
                            // rows. The word was wider than the thing it acted on
                            // and pushed the title into two lines on most entries.
                            IconButton(onClick = { confirmRemove = entry }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove from history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            ListScrollHandle(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        } // PullToRefreshBox
    }

    // Both of these ask first. Removing one entry is small and recoverable only
    // by re-reading the chapter; Clear all wipes the lot and had no guard at
    // all, which made the most destructive control on the screen the one that
    // needed the fewest taps.
    val pendingRemove = confirmRemove
    if (pendingRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from history?") },
            text = {
                Text(
                    "\u201c${pendingRemove.title}\u201d leaves the history list. " +
                        "The chapter, your read mark and your place in it are " +
                        "untouched."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDelete(pendingRemove)
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all history?") },
            text = {
                Text(
                    "Every entry is removed. Read marks and saved pages are kept, " +
                        "so nothing about your progress changes \u2014 only the list " +
                        "of what you opened recently."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onClearAll()
                    confirmClearAll = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- more ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreTab(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }
    var showCategories by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Stays here as well as under Settings > Security and privacy. It's the
        // one preference on this screen that gets turned on for a few chapters and
        // then off again, and a thing used that way shouldn't be three taps deep.
        // Both switches read the same key, and this tab is disposed while Settings
        // is open, so the one here re-reads on the way back rather than going stale.
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Visibility) },
            headlineContent = { Text("Incognito mode") },
            supportingContent = { Text("Pause reading-history logging") },
            trailingContent = {
                Switch(
                    checked = incognito,
                    onCheckedChange = { checked ->
                        incognito = checked
                        prefs(context).edit().putBoolean("incognito", checked).apply()
                    }
                )
            }
        )
        HorizontalDivider()

        // Reads the queue directly, so it stays live while the service drains it.
        val queued = DownloadQueue.items.size
        val failedDownloads = DownloadQueue.failed.size
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Download) },
            headlineContent = { Text("Download queue") },
            supportingContent = {
                Text(
                    when {
                        failedDownloads > 0 && queued > 0 ->
                            "$queued waiting \u00b7 $failedDownloads failed"
                        failedDownloads > 0 -> "$failedDownloads failed"
                        queued == 0 -> "Nothing queued"
                        DownloadQueue.paused -> "$queued waiting \u00b7 paused"
                        queued == 1 -> "1 chapter downloading"
                        else -> "$queued chapters \u00b7 downloading"
                    }
                )
            },
            modifier = Modifier.clickable { onOpenDownloads() }
        )
        HorizontalDivider()

        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.List) },
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Create and delete library categories") },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()

        // Cover size, extension repositories and the downloaded-chapter totals
        // used to be rows on this screen. They live under Settings now; the two
        // storage rows in particular were walking the whole download tree during
        // composition, on the main thread, every time this tab was opened.
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Settings) },
            headlineContent = { Text("Settings") },
            supportingContent = { Text("Appearance, reader, downloads, storage, privacy") },
            modifier = Modifier.clickable { onOpenSettings() }
        )
        HorizontalDivider()

        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Star) },
            headlineContent = { Text("About Yomu") },
            supportingContent = {
                Text("Native Kotlin manga reader \u00b7 ${BuildConfig.VERSION_NAME}")
            }
        )
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = { showCategories = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}
