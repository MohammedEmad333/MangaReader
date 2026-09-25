package com.mangareader.app

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
    ListItem(
        headlineContent = { Text("Downloaded chapters") },
        supportingContent = {
            Text(storageLine(use?.downloadCount, use?.downloads, "chapters"))
        },
        trailingContent = {
            if ((use?.downloadCount ?: 0) > 0) {
                TextButton(onClick = onDeleteDownloads, enabled = !busy) { Text("Delete") }
            }
        },
    )
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Clear chapter cache") },
        supportingContent = {
            Text(
                if (use == null) {
                    "Measuring…"
                } else {
                    "${formatBytes(use.pageCache)} · pages from chapters you read but didn't download"
                },
            )
        },
        trailingContent = {
            TextButton(onClick = onClearPageCache, enabled = !busy) { Text("Clear") }
        },
    )
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Clear cover cache") },
        supportingContent = {
            Text(
                if (use == null) {
                    "Measuring…"
                } else {
                    "${formatBytes(use.images)} · covers and thumbnails"
                },
            )
        },
        trailingContent = {
            TextButton(onClick = onClearCoverCache, enabled = !busy) { Text("Clear") }
        },
    )
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Clear chapter lists") },
        supportingContent = {
            Text(
                if (use == null) {
                    "Measuring…"
                } else {
                    "${formatBytes(use.chapterLists)} · what makes a series open offline"
                },
            )
        },
        trailingContent = {
            TextButton(onClick = onClearChapterLists, enabled = !busy) { Text("Clear") }
        },
    )
    HorizontalDivider()

    PrefNote(
        "The chapter cache is the only one the system can reclaim on its own. " +
            "Clearing the cover cache just means covers are fetched again.",
    )
}
