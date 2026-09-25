package com.mangareader.app

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- downloads ----------

@Composable
internal fun DownloadSettings(onOpenDownloadQueue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val use = rememberStorageUse(tick)

    val queued = DownloadQueue.items.size
    val failed = DownloadQueue.failed.size

    SettingsColumn {
        SectionHeader("Queue")
        ListItem(
            headlineContent = { Text("Download queue") },
            supportingContent = {
                Text(
                    when {
                        failed > 0 && queued > 0 -> "$queued waiting \u00b7 $failed failed"
                        failed > 0 -> "$failed failed"
                        queued == 0 -> "Nothing queued"
                        DownloadQueue.paused -> "$queued waiting \u00b7 paused"
                        queued == 1 -> "1 chapter downloading"
                        else -> "$queued chapters \u00b7 downloading"
                    }
                )
            },
            modifier = Modifier.clickable { onOpenDownloadQueue() }
        )
        HorizontalDivider()

        SectionHeader("On device")
        ListItem(
            headlineContent = { Text("Downloaded chapters") },
            supportingContent = {
                Text(
                    if (deleting) "Deleting…" else storageLine(
                        use?.downloadCount,
                        use?.downloads,
                        "chapters",
                    ),
                )
            },
            trailingContent = {
                if (deleting) {
                    TextButton(onClick = {}, enabled = false) { Text("Deleting…") }
                } else if ((use?.downloadCount ?: 0) > 0) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete all") }
                }
            }
        )
        HorizontalDivider()
        PrefNote(
            "Downloads live in the app's own storage, so only this button and " +
                "uninstalling reclaim them \u2014 the system won't evict them the way " +
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

// ---------- browse ----------

// ---------- storage measurement ----------

internal data class StorageUse(
    val downloadCount: Int,
    val downloads: Long,
    val pageCache: Long,
    val chapterLists: Long,
    val images: Long
)

/**
 * Sizes, measured off the main thread.
 *
 * Every one of these is a recursive walk of a directory that can hold gigabytes,
 * and the More tab has been doing two of them inline in composition. On a small
 * library that's invisible; on a full one it's a stall on the frame that opens
 * the screen. Null means "still measuring", which is why every caller renders a
 * placeholder rather than a zero — a zero here would read as "nothing stored".
 */
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
                images = runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
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
    count == null || bytes == null -> "Measuring\u2026"
    count == 0 -> "Nothing downloaded"
    count == 1 -> "1 chapter \u00b7 ${formatBytes(bytes)}"
    else -> "$count $noun \u00b7 ${formatBytes(bytes)}"
}
