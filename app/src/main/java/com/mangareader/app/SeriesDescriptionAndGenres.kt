package com.mangareader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeriesDescriptionAndGenres(
    series: Series,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    sourceName: String,
    onSearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit,
) {
    if (!series.description.isNullOrBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                series.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse"
                } else {
                    "Expand"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    } else if (series.genres.isNotEmpty()) {
        Icon(
            if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) {
                "Collapse tags"
            } else {
                "Expand tags"
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 8.dp),
        )
    }

    SeriesDiscoveryActions(
        series = series,
        onGlobalSearch = onGlobalSearchTag,
    )

    if (series.genres.isNotEmpty()) {
        val tagModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)

        if (expanded) {
            FlowRow(
                modifier = tagModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChips(
                    genres = series.genres,
                    sourceName = sourceName,
                    onSearchTag = onSearchTag,
                    onGlobalSearchTag = onGlobalSearchTag,
                )
            }
        } else {
            Row(
                modifier = tagModifier.horizontalScroll(
                    rememberScrollState(),
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChips(
                    genres = series.genres,
                    sourceName = sourceName,
                    onSearchTag = onSearchTag,
                    onGlobalSearchTag = onGlobalSearchTag,
                )
            }
        }
    }
}


@Composable
private fun SeriesDiscoveryActions(
    series: Series,
    onGlobalSearch: (String) -> Unit,
) {
    val similar = remember(series) { SeriesDiscovery.similarQuery(series) }
    val author = series.author?.trim()?.takeIf { it.isNotEmpty() }
    val artist = series.artist?.trim()?.takeIf { it.isNotEmpty() }

    if (similar == null && author == null && artist == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        similar?.let { query ->
            AssistChip(
                onClick = { onGlobalSearch(query) },
                label = { Text("Find similar") },
            )
        }
        author?.let { name ->
            AssistChip(
                onClick = { onGlobalSearch(name) },
                label = { Text("Author: $name", maxLines = 1) },
            )
        }
        if (artist != null && artist != author) {
            AssistChip(
                onClick = { onGlobalSearch(artist) },
                label = { Text("Artist: $artist", maxLines = 1) },
            )
        }
    }
}
