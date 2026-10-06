package com.mangareader.app

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    titleMatches: Int,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(if (migrating) "Migrate to…" else "Search all sources") },
        navigationIcon = { BackButton(onBack) },
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (migrating) {
                Text(
                    "Pick the source to move this series to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (total > 0) {
                Text(
                    globalSearchSummaryLabel(done, total, withHits, titleMatches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                when {
                    running -> "Searching…"
                    query.isBlank() -> "Type something to search every source at once."
                    else -> "No source returned a match for “$query”."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            )
        }

        if (!running && recents.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    // Third-party sources occasionally emit the same manga/anime more than
    // once. Series.id is stable sourceId:url identity, so rendering duplicate
    // ids in this LazyRow violates Compose's unique-key contract and crashes
    // during prefetch/measurement. Keep the first copy and treat duplicates as
    // the same logical search result.
    val uniqueSeries = remember(result.series) { result.series.distinctBy { it.id } }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.source.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (result.source.isAnime) {
                        Text(
                            "Anime source",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (uniqueSeries.isNotEmpty() && !migrating) {
                    TextButton(onClick = { onOpenSource(result.source) }) {
                        Text("See all")
                    }
                }
            }

            if (uniqueSeries.isEmpty()) {
                Text(
                    "No results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        uniqueSeries,
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
                                    .padding(top = 6.dp)
                                    .alpha(if (dim) 0.4f else 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun globalSearchSummaryLabel(
    done: Int,
    total: Int,
    withHits: Int,
    titleMatches: Int,
): String =
    "Searched " + done.coerceAtLeast(0) + " of " + total.coerceAtLeast(0) +
        " sources · " + withHits.coerceAtLeast(0) + " with results · " +
        titleMatches.coerceAtLeast(0) + " titles"
