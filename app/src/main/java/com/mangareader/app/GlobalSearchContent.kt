package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchControls(
    migrating: Boolean,
    field: String,
    onFieldChange: (String) -> Unit,
    running: Boolean,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    pinnedOnly: Boolean,
    hasPinned: Boolean,
    onTogglePinnedOnly: (Boolean) -> Unit,
    hasResultsOnly: Boolean,
    onToggleHasResultsOnly: (Boolean) -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    done: Int,
    total: Int,
    withHits: Int,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(if (migrating) "Migrate to…" else "Search all sources") },
        navigationIcon = { BackButton(onBack) },
    )

    if (migrating) {
        Text(
            "Pick the source to move this series to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = onFieldChange,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (running) {
            OutlinedButton(onClick = onCancel) { Text("Stop") }
        } else {
            Button(
                enabled = field.isNotBlank(),
                onClick = onSearch,
            ) { Text("Go") }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = pinnedOnly && hasPinned,
            enabled = hasPinned,
            onClick = { onTogglePinnedOnly(true) },
            label = { Text("Pinned") },
        )
        FilterChip(
            selected = !pinnedOnly || !hasPinned,
            onClick = { onTogglePinnedOnly(false) },
            label = { Text("All") },
        )
        FilterChip(
            selected = hasResultsOnly,
            onClick = { onToggleHasResultsOnly(!hasResultsOnly) },
            label = { Text("Has results") },
        )
        if (!migrating) {
            FilterChip(
                selected = mediaFilter == "All",
                onClick = { onMediaFilterChange("All") },
                label = { Text("Both") },
            )
            FilterChip(
                selected = mediaFilter == "Manga",
                onClick = { onMediaFilterChange("Manga") },
                label = { Text("Manga") },
            )
            FilterChip(
                selected = mediaFilter == "Anime",
                onClick = { onMediaFilterChange("Anime") },
                label = { Text("Anime") },
            )
        }
    }

    if (running) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (total > 0) {
        Text(
            "Searched $done of $total sources · $withHits with results",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlobalSearchEmptyState(
    running: Boolean,
    query: String,
    recents: List<String>,
    onRecentSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            when {
                running -> "Searching…"
                query.isBlank() -> "Type something to search every source at once."
                else -> "No source returned a match for “$query”."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (!running && recents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearRecents) { Text("Clear") }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recents.forEach { q ->
                    InputChip(
                        selected = false,
                        onClick = { onRecentSearch(q) },
                        label = {
                            Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove “$q”",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveRecent(q) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlobalSearchResultRow(
    result: GlobalResult,
    migrating: Boolean,
    dimFor: (String) -> Boolean,
    downloadedFor: (String) -> Boolean,
    unreadFor: (String) -> Int?,
    onOpenSource: (Source) -> Unit,
    onOpenSeries: (Source, Series) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            result.source.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (result.source.isAnime) {
            Text(
                "Anime",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (result.series.isNotEmpty() && !migrating) {
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
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            result.series,
            key = { it.id },
            contentType = { "series" },
        ) { series ->
            val dim = dimFor(series.id)
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onOpenSeries(result.source, series) },
            ) {
                Box {
                    CoverImage(
                        cover = series.cover,
                        title = series.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .alpha(if (dim) 0.4f else 1f),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    ) {
                        EntryBadges(
                            downloaded = downloadedFor(series.id),
                            local = false,
                            unread = unreadFor(series.id),
                        )
                    }
                }
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .alpha(if (dim) 0.4f else 1f),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
}
