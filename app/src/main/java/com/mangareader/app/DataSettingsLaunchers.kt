package com.mangareader.app

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class DataSettingsLaunchers(
    val chooseLocation: () -> Unit,
    val createBackup: () -> Unit,
    val restoreBackup: () -> Unit,
    val grantStorageAccess: () -> Unit,
)

@Composable
internal fun rememberDataSettingsLaunchers(
    context: Context,
    scope: CoroutineScope,
    refreshLocation: () -> Unit,
    onNeedAccess: () -> Unit,
    onMessage: (String) -> Unit,
    onPendingMove: (Pair<File, File>) -> Unit,
    onPendingRestore: (Uri) -> Unit,
    onBusyChange: (Boolean) -> Unit,
): DataSettingsLaunchers {
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshLocation() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshLocation() }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val target = StorageLocation.pathFromTreeUri(uri)
                        ?: return@withContext Triple<File?, Pair<File, File>?, String>(
                            null,
                            null,
                            "That folder isn't on this device's storage. Pick one under " +
                                "internal storage or an SD card — Drive and similar " +
                                "providers have no path behind them."
                        )
                    if (!StorageLocation.ensureWritable(target)) {
                        return@withContext Triple<File?, Pair<File, File>?, String>(
                            target,
                            null,
                            "Couldn't write to ${target.absolutePath}."
                        )
                    }

                    val previous = StorageLocation.base(appContext)
                    val worthMoving = StorageLocation.hasStore(previous)
                    StorageLocation.set(appContext, target)
                    val next = StorageLocation.base(appContext)
                    Triple(target, if (worthMoving) previous to next else null, "")
                }

                if (result.third.isNotEmpty()) {
                    onMessage(result.third)
                } else {
                    refreshLocation()
                    result.second?.let(onPendingMove)
                }
            }
        }
    }

    val createPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            onBusyChange(true)
            scope.launch {
                val result = withContext(Dispatchers.IO) { Backup.writeTo(context, uri) }
                onMessage(
                    result.fold(
                        { "Backup saved" },
                        { "Backup failed: ${it.message ?: it::class.java.simpleName}" }
                    )
                )
                onBusyChange(false)
            }
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) onPendingRestore(uri) }

    return DataSettingsLaunchers(
        chooseLocation = {
            if (StorageLocation.hasAccess(context)) treePicker.launch(null)
            else onNeedAccess()
        },
        createBackup = { createPicker.launch(defaultBackupName()) },
        restoreBackup = { restorePicker.launch(arrayOf("*/*")) },
        grantStorageAccess = {
            val intent = StorageLocation.accessIntent(context)
            if (intent != null) accessLauncher.launch(intent)
            else permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        },
    )
}
