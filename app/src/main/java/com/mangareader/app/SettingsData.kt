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
        DataStorageLocationSection(
            locationLabel = locationLabel,
            moving = moving,
            customDir = customDir,
            active = StorageLocation.active(context),
            reorganising = reorganising,
            onChooseLocation = chooseLocation,
            onUseAppStorage = {
                val previous = StorageLocation.base(context)
                val worthMoving = StorageLocation.hasStore(previous)
                StorageLocation.clear(context)
                refreshLocation()
                if (worthMoving) {
                    pendingMove = previous to StorageLocation.base(context)
                }
            },
            onImport = { importOpen = true },
            onReorganise = { confirmReorganise = true },
        )

        DataBackupSettingsSection(
            busy = busy,
            frequency = frequency,
            lastBackup = lastBackup,
            customDirectorySelected = customDir != null,
            onCreateBackup = { createPicker.launch(defaultBackupName()) },
            onRestoreBackup = { restorePicker.launch(arrayOf("*/*")) },
            onFrequencyChange = {
                frequency = it
                Backup.setFrequency(context, it)
            },
        )

        DataStorageUsageSection(
            use = use,
            onDeleteDownloads = { confirmDownloads = true },
            onClearPageCache = {
                runCatching { File(context.cacheDir, "pages").deleteRecursively() }
                tick++
            },
            onClearCoverCache = {
                runCatching {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                }
                tick++
            },
            onClearChapterLists = { confirmChapterLists = true },
        )
    }

    DataSettingsDialogs(
        askAccess = askAccess,
        onDismissAccess = { askAccess = false },
        onGrantAccess = {
            askAccess = false
            val intent = StorageLocation.accessIntent(context)
            if (intent != null) {
                accessLauncher.launch(intent)
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        },
        importOpen = importOpen,
        onDismissImport = { importOpen = false },
        confirmReorganise = confirmReorganise,
        onDismissReorganise = { confirmReorganise = false },
        onConfirmReorganise = {
            confirmReorganise = false
            reorganising = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    reorganiseDownloads(context)
                }
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
                                "Filed ${report.moved} chapters · " +
                                    "${report.unidentified} couldn’t be " +
                                    "identified and were left alone"
                        }
                    },
                    {
                        "Reorganise failed: " +
                            (it.message ?: it::class.java.simpleName)
                    },
                )
            }
        },
        pendingMove = pendingMove,
        onDismissMove = { pendingMove = null },
        onConfirmMove = { move ->
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
                        if (moved == 0) {
                            "Nothing needed moving"
                        } else {
                            "Moved $moved folders"
                        }
                    },
                    {
                        "Move failed: " +
                            (it.message ?: it::class.java.simpleName)
                    },
                )
            }
        },
        message = message,
        onDismissMessage = { message = null },
        pendingRestore = pendingRestore,
        onDismissRestore = { pendingRestore = null },
        onConfirmRestore = { restoreUri ->
            pendingRestore = null
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    Backup.restoreFrom(context, restoreUri)
                }
                busy = false
                result.fold(
                    onSuccess = {
                        (context as? ComponentActivity)?.recreate()
                    },
                    onFailure = {
                        message = "Restore failed: " +
                            (it.message ?: it::class.java.simpleName)
                    },
                )
            }
        },
        confirmDownloads = confirmDownloads,
        onDismissDownloads = { confirmDownloads = false },
        onConfirmDownloads = {
            Downloads.deleteAll(context)
            confirmDownloads = false
            tick++
        },
        confirmChapterLists = confirmChapterLists,
        onDismissChapterLists = { confirmChapterLists = false },
        onConfirmChapterLists = {
            ChapterCache.clearAll(context)
            confirmChapterLists = false
            tick++
        },
    )

    // Re-read on the way back in, so a backup written by the worker while this
    // screen was closed doesn't leave a stale "Never" on the row.
    LaunchedEffect(tick) { lastBackup = Backup.lastBackupAt(context) }
}

// ---------- security and privacy ----------
