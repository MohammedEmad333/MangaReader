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

@Composable
internal fun DataSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmDownloads by remember { mutableStateOf(false) }
    var confirmChapterLists by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val use = rememberStorageUse(tick)

    var frequency by remember { mutableStateOf(Backup.frequency(context)) }
    var lastBackup by remember { mutableStateOf(Backup.lastBackupAt(context)) }

    // Re-read on every entry rather than held: All files access is granted on a
    // system screen this app doesn't own, and can be taken away on the same one
    // while the app sits in the background.
    var hasAccess by remember { mutableStateOf(StorageLocation.hasAccess(context)) }
    var customDir by remember { mutableStateOf(StorageLocation.chosen(context)) }
    var locationLabel by remember { mutableStateOf(StorageLocation.label(context)) }
    var askAccess by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<File, File>?>(null) }
    var moving by remember { mutableStateOf(false) }
    var confirmReorganise by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var reorganising by remember { mutableStateOf(false) }

    fun refreshLocation() {
        StorageLocation.invalidate()
        hasAccess = StorageLocation.hasAccess(context)
        customDir = StorageLocation.chosen(context)
        locationLabel = StorageLocation.label(context)
    }

    // API 30+: a system settings page, which returns no result — the answer is
    // read back out of Environment when it closes, not from the result code.
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshLocation() }

    // API 29 and below, where it's still an ordinary runtime permission.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshLocation() }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val target = StorageLocation.pathFromTreeUri(uri)
            when {
                target == null -> message =
                    "That folder isn't on this device's storage. Pick one under " +
                        "internal storage or an SD card \u2014 Drive and similar " +
                        "providers have no path behind them."
                !StorageLocation.ensureWritable(target) -> message =
                    "Couldn't write to ${target.absolutePath}."
                else -> {
                    // Read before the switch: after set() the old base is gone,
                    // and with it any way to find what needs moving.
                    val previous = StorageLocation.base(context)
                    val worthMoving = StorageLocation.hasStore(previous)
                    StorageLocation.set(context, target)
                    refreshLocation()
                    if (worthMoving) pendingMove = previous to StorageLocation.base(context)
                }
            }
        }
    }

    val chooseLocation: () -> Unit = {
        if (StorageLocation.hasAccess(context)) treePicker.launch(null) else askAccess = true
    }

    val createPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { Backup.writeTo(context, uri) }
                message = result.fold(
                    { "Backup saved" },
                    { "Backup failed: ${it.message ?: it::class.java.simpleName}" }
                )
                busy = false
            }
        }
    }

    // "*/*" rather than "application/json": a backup that's been through a chat
    // app or a cloud drive comes back with whatever MIME type that service felt
    // like, and a filtered picker greys out the file the user is looking at.
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) pendingRestore = uri }

    SettingsColumn {
        SectionHeader("Storage location")
        ListItem(
            headlineContent = { Text(locationLabel) },
            supportingContent = {
                Text(
                    if (moving) "Moving chapters\u2026"
                    else "Chapter downloads and automatic backups"
                )
            },
            trailingContent = {
                TextButton(enabled = !moving, onClick = chooseLocation) { Text("Change") }
            },
            modifier = Modifier.clickable(enabled = !moving) { chooseLocation() }
        )
        HorizontalDivider()
        if (customDir != null && !StorageLocation.active(context)) {
            PrefNote(
                "\u26a0 ${customDir?.absolutePath} can't be written to right now, so " +
                    "downloads are going to app storage instead. Storage permission " +
                    "revoked, or the card it's on isn't mounted."
            )
        }
        if (customDir != null) {
            ListItem(
                headlineContent = { Text("Use app storage") },
                supportingContent = { Text("Back to the default, inside the app") },
                trailingContent = {
                    TextButton(
                        enabled = !moving,
                        onClick = {
                            val previous = StorageLocation.base(context)
                            val worthMoving = StorageLocation.hasStore(previous)
                            StorageLocation.clear(context)
                            refreshLocation()
                            if (worthMoving) {
                                pendingMove = previous to StorageLocation.base(context)
                            }
                        }
                    ) { Text("Reset") }
                }
            )
            HorizontalDivider()
        }
        ListItem(
            headlineContent = { Text("Import Tachiyomi backup") },
            supportingContent = {
                Text("Library, categories, read state and history from a .tachibk file")
            },
            trailingContent = {
                TextButton(
                    enabled = !reorganising && !moving,
                    onClick = { importOpen = true }
                ) { Text("Scan") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Reorganise downloads") },
            supportingContent = {
                Text(
                    if (reorganising) "Filing chapters\u2026"
                    else "File chapters from before this layout under source and series"
                )
            },
            trailingContent = {
                TextButton(
                    enabled = !reorganising && !moving,
                    onClick = { confirmReorganise = true }
                ) { Text("Run") }
            }
        )
        HorizontalDivider()
        PrefNote(
            "A \u201cYomu\u201d folder is created inside whatever you pick, holding " +
                "\u201cdownloads\u201d and \u201cbackups\u201d. Chapters are filed under " +
                "source, then series, then chapter, so the tree reads the same in a " +
                "file manager as it does in the app. Anything left in app storage " +
                "doesn\u2019t survive uninstalling; a folder you picked does."
        )

        SectionHeader("Backup and restore")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = !busy,
                onClick = { createPicker.launch(defaultBackupName()) },
                modifier = Modifier.weight(1f)
            ) { Text("Create backup") }
            OutlinedButton(
                enabled = !busy,
                onClick = { restorePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Restore backup") }
        }
        Spacer(Modifier.height(8.dp))

        SectionHeader("Automatic backup frequency")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Frequency",
                options = BackupFrequency.entries.map { it.label },
                selected = BackupFrequency.entries.indexOf(frequency),
                onSelect = {
                    val next = BackupFrequency.entries[it]
                    frequency = next
                    Backup.setFrequency(context, next)
                }
            )
        }
        PrefNote(
            if (frequency != BackupFrequency.OFF && customDir == null)
                "With the default location these land inside app storage, where a " +
                    "file manager can't reach them \u2014 fine as a safety net, no use " +
                    "for moving to another phone. Set a folder above for that."
            else
                "Keeps the five most recent, then deletes the oldest. A backup " +
                    "holds the library, categories, history, read marks, resume " +
                    "positions and every source's settings \u2014 not the downloaded " +
                    "pages themselves."
        )
        ListItem(
            headlineContent = { Text("Last automatic backup") },
            supportingContent = {
                Text(
                    if (lastBackup <= 0L) "Never"
                    else DateUtils.getRelativeTimeSpanString(
                        lastBackup,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                )
            }
        )
        HorizontalDivider()

        SectionHeader("Storage usage")
        DeviceStorageBar()

        SectionHeader("Used by Yomu")
        ListItem(
            headlineContent = { Text("Downloaded chapters") },
            supportingContent = { Text(storageLine(use?.downloadCount, use?.downloads, "chapters")) },
            trailingContent = {
                if ((use?.downloadCount ?: 0) > 0) {
                    TextButton(onClick = { confirmDownloads = true }) { Text("Delete") }
                }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear chapter cache") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.pageCache)} \u00b7 pages from chapters you read but didn't download"
                )
            },
            trailingContent = {
                TextButton(onClick = {
                    runCatching { File(context.cacheDir, "pages").deleteRecursively() }
                    tick++
                }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear cover cache") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.images)} \u00b7 covers and thumbnails"
                )
            },
            trailingContent = {
                TextButton(onClick = {
                    runCatching {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                    }
                    tick++
                }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear chapter lists") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.chapterLists)} \u00b7 what makes a series open offline"
                )
            },
            trailingContent = {
                TextButton(onClick = { confirmChapterLists = true }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        PrefNote(
            "The chapter cache is the only one the system can reclaim on its own. " +
                "Clearing the cover cache just means covers are fetched again."
        )
    }

    if (askAccess) {
        AlertDialog(
            onDismissRequest = { askAccess = false },
            title = { Text("Allow access to storage?") },
            text = {
                Text(
                    "To keep downloads in a folder you choose, Yomu needs " +
                        "permission to manage files. Android grants this on its own " +
                        "settings screen rather than in a dialog, so this opens that " +
                        "screen \u2014 come back here afterwards and pick the folder."
                )
            },
            confirmButton = {
                Button(onClick = {
                    askAccess = false
                    val intent = StorageLocation.accessIntent(context)
                    if (intent != null) accessLauncher.launch(intent)
                    else permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { askAccess = false }) { Text("Cancel") }
            }
        )
    }

    if (importOpen) {
        TachiyomiImportDialog(onDismiss = { importOpen = false })
    }

    if (confirmReorganise) {
        AlertDialog(
            onDismissRequest = { confirmReorganise = false },
            title = { Text("Reorganise downloads?") },
            text = {
                Text(
                    "Chapters downloaded before this layout sit in a folder named " +
                        "after a hash, which is unreadable but works. This files them " +
                        "under source and series instead.\n\nA chapter can only be " +
                        "placed if the app still knows what it was \u2014 anything it " +
                        "can\u2019t identify is left where it is and keeps working."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmReorganise = false
                    reorganising = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { reorganiseDownloads(context) }
                        reorganising = false
                        tick++
                        message = result.fold(
                            { report ->
                                when {
                                    report.moved == 0 && report.unidentified == 0 ->
                                        "Everything was already filed"
                                    report.unidentified == 0 ->
                                        "Filed ${report.moved} chapters"
                                    else ->
                                        "Filed ${report.moved} chapters \u00b7 " +
                                            "${report.unidentified} couldn\u2019t be " +
                                            "identified and were left alone"
                                }
                            },
                            { "Reorganise failed: ${it.message ?: it::class.java.simpleName}" }
                        )
                    }
                }) { Text("Reorganise") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReorganise = false }) { Text("Cancel") }
            }
        )
    }

    val move = pendingMove
    if (move != null) {
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Move existing downloads?") },
            text = {
                Text(
                    "Downloads and backups already written are still in the old " +
                        "folder. Moving them keeps them readable; leaving them means " +
                        "they stay on disk taking up space but stop appearing in " +
                        "Downloads. On a large library this takes a while, and moving " +
                        "to an SD card is a copy rather than a rename, so give it time."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingMove = null
                    moving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            StorageLocation.moveStore(move.first, move.second)
                        }
                        moving = false
                        tick++
                        message = result.fold(
                            { moved ->
                                if (moved == 0) "Nothing needed moving"
                                else "Moved $moved folders"
                            },
                            { "Move failed: ${it.message ?: it::class.java.simpleName}" }
                        )
                    }
                }) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) { Text("Leave them") }
            }
        )
    }

    val note = message
    if (note != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Backup") },
            text = { Text(note) },
            confirmButton = { Button(onClick = { message = null }) { Text("Done") } }
        )
    }

    val restoreUri = pendingRestore
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Everything currently in the app is replaced: library, " +
                        "categories, history, read marks and source settings. This " +
                        "can't be undone, and it isn't a merge \u2014 anything added " +
                        "since the backup was made is lost. Downloaded chapters stay " +
                        "on disk either way."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            Backup.restoreFrom(context, restoreUri)
                        }
                        busy = false
                        result.fold(
                            onSuccess = {
                                // Every piece of YomuApp's state is in `remember`,
                                // including the source list and the open series, and
                                // all of it was built from the prefs that just got
                                // replaced. Restarting the Activity is the only way
                                // to be sure nothing on screen still refers to the
                                // library that existed a second ago.
                                (context as? ComponentActivity)?.recreate()
                            },
                            onFailure = {
                                message = "Restore failed: " +
                                    (it.message ?: it::class.java.simpleName)
                            }
                        )
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDownloads = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Every downloaded chapter goes. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    Downloads.deleteAll(context)
                    confirmDownloads = false
                    tick++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloads = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmChapterLists) {
        AlertDialog(
            onDismissRequest = { confirmChapterLists = false },
            title = { Text("Clear chapter lists?") },
            text = {
                Text(
                    "A downloaded chapter stays on disk, but the series it belongs to " +
                        "won't open offline again until it's been opened once with a " +
                        "connection."
                )
            },
            confirmButton = {
                Button(onClick = {
                    ChapterCache.clearAll(context)
                    confirmChapterLists = false
                    tick++
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmChapterLists = false }) { Text("Cancel") }
            }
        )
    }

    // Re-read on the way back in, so a backup written by the worker while this
    // screen was closed doesn't leave a stale "Never" on the row.
    LaunchedEffect(tick) { lastBackup = Backup.lastBackupAt(context) }
}

// ---------- security and privacy ----------
