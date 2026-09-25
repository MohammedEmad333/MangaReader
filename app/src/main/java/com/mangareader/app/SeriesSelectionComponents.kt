package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun SeriesAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
internal fun ChapterSelectionBar(
    count: Int,
    canDownload: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    bookmarkAdds: Boolean,
    onBookmark: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                }
                Text(
                    "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text("All")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (canDownload) {
                    SeriesAction(
                        icon = Icons.Default.Download,
                        label = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onDownload,
                    )
                }
                SeriesAction(
                    icon = Icons.Default.Check,
                    label = "Read",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRead,
                )
                SeriesAction(
                    icon = Icons.Default.Clear,
                    label = "Unread",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onUnread,
                )
                SeriesAction(
                    icon = if (bookmarkAdds) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                    label = if (bookmarkAdds) "Bookmark" else "Unbookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onBookmark(bookmarkAdds) },
                )
                SeriesAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}
