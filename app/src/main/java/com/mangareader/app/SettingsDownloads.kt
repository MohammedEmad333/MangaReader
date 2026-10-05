package com.mangareader.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun DownloadSettings(onOpenDownloadQueue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var autoDownloadWifiOnly by remember {
        mutableStateOf(AutoDownloadPrefs.wifiOnly(context))
    }
    var autoDownloadLimit by remember {
        mutableStateOf(AutoDownloadPrefs.limit(context))
    }
    val use = rememberStorageUse(tick)

    val queued = DownloadQueue.items.size
    val failed = DownloadQueue.failed.size

    SettingsColumn {
        SectionHeader("Queue")
        SettingsActionCard(
            title = "Download queue",
            summary = when {
                failed > 0 && queued > 0 -> "$queued waiting · $failed failed"
                failed > 0 -> "$failed failed"
                queued == 0 -> "Nothing queued"
                DownloadQueue.paused -> "$queued waiting · paused"
                queued == 1 -> "1 chapter downloading"
                else -> "$queued chapters · downloading"
            },
            onClick = onOpenDownloadQueue,
        )

        SectionHeader("Automatic downloads")
        PrefSwitchRow(
            title = "Wi-Fi only",
            checked = autoDownloadWifiOnly,
            summary = "Applies to per-series auto-download when library refresh finds new chapters.",
            onChange = { value ->
                autoDownloadWifiOnly = value
                AutoDownloadPrefs.setWifiOnly(context, value)
            },
        )
        SettingsActionCard(
            title = "New chapters per series",
            summary = autoDownloadLimit.label,
            onClick = {
                val next = autoDownloadLimit.next()
                autoDownloadLimit = next
                AutoDownloadPrefs.setLimit(context, next)
            },
        )
        PrefNote(
            "Auto-download is enabled per series from its ⋮ menu. The first refresh " +
                "after enabling establishes a baseline and never downloads the old backlog."
        )

        SectionHeader("On device")
        SettingsActionCard(
            title = "Downloaded chapters",
            summary = if (deleting) {
                "Deleting…"
            } else {
                storageLine(use?.downloadCount, use?.downloads, "chapters")
            },
            trailing = {
                when {
                    deleting -> TextButton(onClick = {}, enabled = false) { Text("Deleting…") }
                    (use?.downloadCount ?: 0) > 0 -> {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete all") }
                    }
                }
            },
        )
        PrefNote(
            "Downloads live in the app's own storage, so only this button and " +
                "uninstalling reclaim them — the system won't evict them the way " +
                "it evicts the reading cache."
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text(
                    "Every downloaded chapter goes, including anything only readable " +
                        "offline. This can't be undone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    deleting = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Downloads.deleteAll(context.applicationContext)
                            }
                        } finally {
                            deleting = false
                            tick++
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

internal data class StorageUse(
    val downloadCount: Int,
    val downloads: Long,
    val pageCache: Long,
    val chapterLists: Long,
    val images: Long,
)

@Composable
internal fun rememberStorageUse(tick: Int): StorageUse? {
    val context = LocalContext.current
    var use by remember { mutableStateOf<StorageUse?>(null) }
    LaunchedEffect(tick, DownloadQueue.tick) {
        use = withContext(Dispatchers.IO) {
            StorageUse(
                downloadCount = runCatching { Downloads.count(context) }.getOrDefault(0),
                downloads = runCatching { Downloads.sizeBytes(context) }.getOrDefault(0L),
                pageCache = dirSize(File(context.cacheDir, "pages")),
                chapterLists = dirSize(File(context.filesDir, "chapterlists")),
                images = runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L),
            )
        }
    }
    return use
}

private fun dirSize(dir: File): Long = runCatching {
    if (!dir.exists()) 0L
    else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

internal fun storageLine(count: Int?, bytes: Long?, noun: String): String = when {
    count == null || bytes == null -> "Measuring…"
    count == 0 -> "Nothing downloaded"
    count == 1 -> "1 chapter · ${formatBytes(bytes)}"
    else -> "$count $noun · ${formatBytes(bytes)}"
}
