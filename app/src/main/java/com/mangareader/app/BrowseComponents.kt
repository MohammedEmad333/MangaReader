package com.mangareader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val KEY_BROWSE_VIEW = "browse_view"

internal fun browseViewPreferenceKey(sourceId: String): String =
    if (sourceId.isBlank()) KEY_BROWSE_VIEW else KEY_BROWSE_VIEW + ":" + sourceId

internal enum class BrowseView(val key: String, val label: String) {
    COMFORTABLE("comfortable", "Comfortable grid"),
    COMPACT("compact", "Compact grid"),
    LIST("list", "List");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}

@Composable
internal fun ComfortableCell(
    series: Series,
    marks: EntryMarks,
    local: Boolean,
    onOpen: (Series) -> Unit,
) {
    val dim = marks.dim(series.id)
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onOpen(series) },
    ) {
        Box(modifier = Modifier.clip(MaterialTheme.shapes.medium)) {
            CoverImage(
                cover = series.cover,
                title = series.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .alpha(if (dim) 0.4f else 1f),
            )
            Box(modifier = Modifier.align(Alignment.TopStart).padding(7.dp)) {
                EntryBadges(
                    downloaded = marks.downloaded(series.id),
                    local = marks.badgeLocal && local,
                    unread = marks.unreadOf(series.id),
                )
            }
        }
        Text(
            text = series.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 2.dp, vertical = 7.dp)
                .alpha(if (dim) 0.4f else 1f),
        )
    }
}

@Composable
internal fun CompactCell(
    series: Series,
    marks: EntryMarks,
    local: Boolean,
    onOpen: (Series) -> Unit,
) {
    val dim = marks.dim(series.id)
    Box(
        modifier = Modifier
            .padding(6.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpen(series) },
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .alpha(if (dim) 0.4f else 1f),
        )
        Box(modifier = Modifier.align(Alignment.TopStart).padding(7.dp)) {
            EntryBadges(
                downloaded = marks.downloaded(series.id),
                local = marks.badgeLocal && local,
                unread = marks.unreadOf(series.id),
            )
        }
        Text(
            text = series.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.86f),
                        ),
                    ),
                )
                .padding(start = 9.dp, end = 9.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

@Composable
internal fun ListRow(
    series: Series,
    marks: EntryMarks,
    local: Boolean,
    onOpen: (Series) -> Unit,
) {
    val dim = marks.dim(series.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onOpen(series) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(0.7f)
                .alpha(if (dim) 0.4f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = series.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (dim) 0.4f else 1f),
        )
        Spacer(Modifier.width(8.dp))
        EntryBadges(
            downloaded = marks.downloaded(series.id),
            local = marks.badgeLocal && local,
            unread = marks.unreadOf(series.id),
        )
    }
}

internal fun formatChapterDate(millis: Long): String? {
    if (millis <= 0L) return null
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val startOfToday = now - (now % day)
    return when {
        millis >= startOfToday -> "Today"
        millis >= startOfToday - day -> "Yesterday"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
