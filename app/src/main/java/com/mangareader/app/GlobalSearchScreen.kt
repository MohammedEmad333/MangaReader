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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchScreen(
    query: String,
    results: List<GlobalResult>,
    running: Boolean,
    done: Int,
    total: Int,
    pinnedOnly: Boolean,
    onTogglePinnedOnly: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onOpenSeries: (Source, Series) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var field by remember(query) { mutableStateOf(query) }

    // Read once per entry into the composition: the pin set only changes over in
    // the Browse tab, which tears this screen down on the way there and back.
    val hasPinned = remember { SourcePrefs.pinned(context).isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Search all sources") },
            navigationIcon = { TextButton(onClick = onBack) { Text("←") } }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = pinnedOnly && hasPinned,
                enabled = hasPinned,
                onClick = { onTogglePinnedOnly(!pinnedOnly) },
                label = { Text("Pinned sources only") }
            )
            if (!hasPinned) {
                Text(
                    "Pin sources in Browse to narrow this",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (total > 0) {
            Text(
                "Searched $done of $total sources \u00b7 ${results.size} with results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        running -> "Searching\u2026"
                        query.isBlank() -> "Type something to search every source at once."
                        else -> "No source returned a match for \u201c$query\u201d."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(results) { result ->
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
                        TextButton(onClick = { onOpenSource(result.source) }) {
                            Text("See all")
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.series) { s ->
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { onOpenSeries(result.source, s) }
                            ) {
                                CoverImage(
                                    cover = s.cover,
                                    title = s.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.7f)
                                )
                                Text(
                                    text = s.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

// ---------- library ----------
