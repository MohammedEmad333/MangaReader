package com.mangareader.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun DataStorageUsageSection(
    use: StorageUse?,
    busy: Boolean,
    onDeleteDownloads: () -> Unit,
    onClearPageCache: () -> Unit,
    onClearCoverCache: () -> Unit,
    onClearChapterLists: () -> Unit,
) {
    SectionHeader("Storage usage")
    DeviceStorageBar()

    SectionHeader("Used by Yomu")
    StorageUsageCard(
        title = "Downloaded chapters",
        summary = storageLine(use?.downloadCount, use?.downloads, "chapters"),
        actionLabel = if ((use?.downloadCount ?: 0) > 0) "Delete" else null,
        actionEnabled = !busy,
        onAction = onDeleteDownloads,
    )
    StorageUsageCard(
        title = "Chapter cache",
        summary = if (use == null) {
            "Measuring…"
        } else {
            "${formatBytes(use.pageCache)} · pages from chapters you read but didn't download"
        },
        actionLabel = "Clear",
        actionEnabled = !busy,
        onAction = onClearPageCache,
    )
    StorageUsageCard(
        title = "Cover cache",
        summary = if (use == null) {
            "Measuring…"
        } else {
            "${formatBytes(use.images)} · covers and thumbnails"
        },
        actionLabel = "Clear",
        actionEnabled = !busy,
        onAction = onClearCoverCache,
    )
    StorageUsageCard(
        title = "Chapter lists",
        summary = if (use == null) {
            "Measuring…"
        } else {
            "${formatBytes(use.chapterLists)} · what makes a series open offline"
        },
        actionLabel = "Clear",
        actionEnabled = !busy,
        onAction = onClearChapterLists,
    )

    PrefNote(
        "The chapter cache is the only one the system can reclaim on its own. " +
            "Clearing the cover cache just means covers are fetched again.",
    )
}

@Composable
private fun StorageUsageCard(
    title: String,
    summary: String,
    actionLabel: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = if (actionLabel == null) null else {
                {
                    TextButton(onClick = onAction, enabled = actionEnabled) {
                        Text(actionLabel)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
