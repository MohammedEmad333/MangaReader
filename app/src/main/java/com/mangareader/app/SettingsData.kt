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
    var storageBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val use = rememberStorageUse(tick)

    var frequency by remember { mutableStateOf(Backup.frequency(context)) }
    var lastBackup by remember { mutableStateOf(Backup.lastBackupAt(context)) }

    // Re-read on every entry rather than held: All files access is granted on a
    // system screen this app doesn't own, and can be taken away on the same one
    // while the app sits in the background.
    var hasAccess by remember { mutableStateOf(StorageLocation.hasAccess(context)) }
    var customDir by remember { mutableStateOf(StorageLocation.chosen(context)) }
    var locationLabel by remember { mutableStateOf("Checking storage…") }
    var storageActive by remember { mutableStateOf(false) }
    var askAccess by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<File, File>?>(null) }
    var moving by remember { mutableStateOf(false) }
    var confirmReorganise by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var reorganising by remember { mutableStateOf(false) }

    fun refreshLocation() {
        val appContext = context.applicationContext
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                StorageLocation.invalidate()
                val access = StorageLocation.hasAccess(appContext)
                val chosen = StorageLocation.chosen(appContext)
                val label = StorageLocation.label(appContext)
                val active = StorageLocation.active(appContext)
                listOf(access, chosen, label, active)
            }
            hasAccess = snapshot[0] as Boolean
            customDir = snapshot[1] as File?
            locationLabel = snapshot[2] as String
            storageActive = snapshot[3] as Boolean
        }
    }

    val launchers = rememberDataSettingsLaunchers(
        context = context,
        scope = scope,
        refreshLocation = { refreshLocation() },
        onNeedAccess = { askAccess = true },
        onMessage = { message = it },
        onPendingMove = { pendingMove = it },
        onPendingRestore = { pendingRestore = it },
        onBusyChange = { busy = it },
    )

    SettingsColumn {
        DataStorageLocationSection(
            locationLabel = locationLabel,
            moving = moving,
            customDir = customDir,
            active = storageActive,
            reorganising = reorganising,
            onChooseLocation = launchers.chooseLocation,
            onUseAppStorage = {
                val appContext = context.applicationContext
                scope.launch {
                    val move = withContext(Dispatchers.IO) {
                        val previous = StorageLocation.base(appContext)
                        val worthMoving = StorageLocation.hasStore(previous)
                        StorageLocation.clear(appContext)
                        val next = StorageLocation.base(appContext)
                        if (worthMoving) previous to next else null
                    }
                    refreshLocation()
                    if (move != null) pendingMove = move
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
            onCreateBackup = launchers.createBackup,
            onRestoreBackup = launchers.restoreBackup,
            onFrequencyChange = {
                frequency = it
                Backup.setFrequency(context, it)
            },
        )

        DataStorageUsageSection(
            use = use,
            busy = storageBusy,
            onDeleteDownloads = { confirmDownloads = true },
            onClearPageCache = {
                storageBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                File(context.cacheDir, "pages").deleteRecursively()
                            }
                        }
                    } finally {
                        storageBusy = false
                        tick++
                    }
                }
            },
            onClearCoverCache = {
                storageBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.imageLoader.memoryCache?.clear()
                                context.imageLoader.diskCache?.clear()
                            }
                        }
                    } finally {
                        storageBusy = false
                        tick++
                    }
                }
            },
            onClearChapterLists = { confirmChapterLists = true },
        )
    }

    DataSettingsDialogs(
        askAccess = askAccess,
        onDismissAccess = { askAccess = false },
        onGrantAccess = {
            askAccess = false
            launchers.grantStorageAccess()
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
            confirmDownloads = false
            storageBusy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        Downloads.deleteAll(context.applicationContext)
                    }
                } finally {
                    storageBusy = false
                    tick++
                }
            }
        },
        confirmChapterLists = confirmChapterLists,
        onDismissChapterLists = { confirmChapterLists = false },
        onConfirmChapterLists = {
            confirmChapterLists = false
            storageBusy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ChapterCache.clearAll(context.applicationContext)
                    }
                } finally {
                    storageBusy = false
                    tick++
                }
            }
        },
    )

    // Re-read on the way back in, so a backup written by the worker while this
    // screen was closed doesn't leave a stale "Never" on the row.
    LaunchedEffect(Unit) { refreshLocation() }
    LaunchedEffect(tick) { lastBackup = Backup.lastBackupAt(context) }
}

// ---------- security and privacy ----------
