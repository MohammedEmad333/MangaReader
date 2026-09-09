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
    /** Past queries, newest first, for the one-tap-to-rerun chips. */
    recents: List<String>,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onOpenSeries: (Source, Series) -> Unit,
    /** Bumped when library state moves; refreshes the corner markers. */
    libraryTick: Int,
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
    val shown = if (hasResultsOnly) results.filter { it.series.isNotEmpty() } else results

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Search all sources") },
            navigationIcon = { BackButton(onBack) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (running) {
                OutlinedButton(onClick = onCancel) { Text("Stop") }
            } else {
                Button(
                    enabled = field.isNotBlank(),
                    onClick = { onSearch(field.trim()) }
                ) { Text("Go") }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pinned and All are one scope choice; Has results filters what's
            // displayed without re-running anything.
            FilterChip(
                selected = pinnedOnly && hasPinned,
                enabled = hasPinned,
                onClick = { onTogglePinnedOnly(true) },
                label = { Text("Pinned") }
            )
            FilterChip(
                selected = !pinnedOnly || !hasPinned,
                onClick = { onTogglePinnedOnly(false) },
                label = { Text("All") }
            )
            FilterChip(
                selected = hasResultsOnly,
                onClick = { onToggleHasResultsOnly(!hasResultsOnly) },
                label = { Text("Has results") }
            )
        }

        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (total > 0) {
            Text(
                "Searched $done of $total sources \u00b7 $withHits with results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (shown.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    when {
                        running -> "Searching\u2026"
                        query.isBlank() -> "Type something to search every source at once."
                        else -> "No source returned a match for \u201c$query\u201d."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                // Recent queries turn a re-search into a single tap. Hidden while a
                // search is in flight \u2014 the counter above already speaks for that
                // state, and re-running mid-search would just fight the running job.
                if (!running && recents.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent searches",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearRecents) { Text("Clear") }
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recents.forEach { q ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    field = q
                                    onSearch(q)
                                },
                                label = {
                                    Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove \u201c$q\u201d",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onRemoveRecent(q) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
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
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            result.source.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (result.series.isNotEmpty()) {
                            TextButton(onClick = { onOpenSource(result.source) }) {
                                Text("See all")
                            }
                        }
                    }
                    if (result.series.isEmpty()) {
                        Text(
                            "No results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.series) { s ->
                            // Results already in the library carry their marks,
                            // which is the useful half here: a global search is
                            // usually asking whether this series exists on a
                            // source you can actually read, and half the answer
                            // is whether you already have it.
                            val dim = marks.dim(s.id)
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { onOpenSeries(result.source, s) }
                            ) {
                                Box {
                                    CoverImage(
                                        cover = s.cover,
                                        title = s.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.7f)
                                            .alpha(if (dim) 0.4f else 1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                    ) {
                                        EntryBadges(
                                            downloaded = marks.downloaded(s.id),
                                            local = false,
                                            unread = marks.unreadOf(s.id)
                                        )
                                    }
                                }
                                Text(
                                    text = s.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .alpha(if (dim) 0.4f else 1f)
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
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
