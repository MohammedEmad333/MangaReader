package com.mangareader.app

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun DataStorageLocationSection(
    locationLabel: String,
    moving: Boolean,
    customDir: File?,
    active: Boolean,
    reorganising: Boolean,
    onChooseLocation: () -> Unit,
    onUseAppStorage: () -> Unit,
    onImport: () -> Unit,
    onReorganise: () -> Unit,
) {
    SectionHeader("Storage location")

    DataActionCard(
        title = locationLabel,
        summary = if (moving) "Moving chapters…" else "Chapter downloads and automatic backups",
        actionLabel = "Change",
        enabled = !moving,
        onAction = onChooseLocation,
        onRowClick = onChooseLocation,
    )

    if (customDir != null && !active) {
        PrefNote(
            "⚠ ${customDir.absolutePath} can't be written to right now, so " +
                "downloads are going to app storage instead. Storage permission " +
                "revoked, or the card it's on isn't mounted.",
        )
    }

    if (customDir != null) {
        DataActionCard(
            title = "Use app storage",
            summary = "Back to the default, inside the app",
            actionLabel = "Reset",
            enabled = !moving,
            onAction = onUseAppStorage,
        )
    }

    DataActionCard(
        title = "Import Tachiyomi backup",
        summary = "Library, categories, read state and history from a .tachibk file",
        actionLabel = "Scan",
        enabled = !reorganising && !moving,
        onAction = onImport,
    )

    DataActionCard(
        title = "Reorganise downloads",
        summary = if (reorganising) {
            "Filing chapters…"
        } else {
            "File chapters from before this layout under source and series"
        },
        actionLabel = "Run",
        enabled = !reorganising && !moving,
        onAction = onReorganise,
    )

    PrefNote(
        "A “Yomu” folder is created inside whatever you pick, holding " +
            "“downloads” and “backups”. Chapters are filed under source, then " +
            "series, then chapter, so the tree reads the same in a file manager " +
            "as it does in the app. Anything left in app storage doesn't survive " +
            "uninstalling; a folder you picked does.",
    )
}

@Composable
internal fun DataBackupSettingsSection(
    busy: Boolean,
    frequency: BackupFrequency,
    lastBackup: Long,
    customDirectorySelected: Boolean,
    backupHealth: AutomaticBackupHealth?,
    checkingBackupHealth: Boolean,
    lastBackupAttempt: Long,
    backupFailure: AutomaticBackupFailure?,
    onCheckBackups: () -> Unit,
    onRetryAutomaticBackup: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onFrequencyChange: (BackupFrequency) -> Unit,
) {
    SectionHeader("Backup and restore")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = !busy,
                onClick = onCreateBackup,
                modifier = Modifier.weight(1f),
            ) {
                Text("Create backup")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = onRestoreBackup,
                modifier = Modifier.weight(1f),
            ) {
                Text("Restore backup")
            }
        }
    }

    SectionHeader("Automatic backup frequency")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            PrefChipRow(
                label = "Frequency",
                options = BackupFrequency.entries.map { it.label },
                selected = BackupFrequency.entries.indexOf(frequency),
                onSelect = { onFrequencyChange(BackupFrequency.entries[it]) },
            )
        }
    }

    PrefNote(
        if (frequency != BackupFrequency.OFF && !customDirectorySelected) {
            "With the default location these land inside app storage, where a " +
                "file manager can't reach them — fine as a safety net, no use " +
                "for moving to another phone. Set a folder above for that."
        } else {
            "Keeps the five most recent, then deletes the oldest. A backup holds " +
                "the library, categories, history, read marks, resume positions " +
                "and every source's settings — not the downloaded pages themselves."
        },
    )

    DataActionCard(
        title = "Last automatic backup attempt",
        summary = when {
            lastBackupAttempt <= 0L -> "Not attempted on this installation"
            backupFailure != null -> backupFailure.help
            else -> "Last attempt completed successfully"
        },
    )

    DataActionCard(
        title = "Most recent verified automatic backup",
        summary = when {
            checkingBackupHealth -> "Checking backup integrity…"
            backupHealth == null -> "Not checked yet"
            backupHealth.folderUnreadable -> "Backup folder could not be read. Check storage."
            backupHealth.verifiedAt != null -> {
                val relative = DateUtils.getRelativeTimeSpanString(
                    backupHealth.verifiedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
                "$relative · verified on disk" +
                    if (backupHealth.newestArchiveInvalid) {
                        " · A newer backup is invalid; the earlier valid one is kept."
                    } else ""
            }
            backupHealth.checkedArchives == 0 -> "No automatic backup file found"
            else -> "No valid backup in the last ${backupHealth.checkedArchives} checked files"
        },
        actionLabel = "Check",
        enabled = !busy && !checkingBackupHealth,
        onAction = onCheckBackups,
    )

    if (backupHealth != null && !checkingBackupHealth &&
        (backupHealth.invalidArchives > 0 || backupHealth.moreArchivesToCheck)) {
        PrefNote(
            "${backupHealth.invalidArchives} invalid among " +
                "${backupHealth.checkedArchives} checked archives. " +
                if (backupHealth.moreArchivesToCheck) {
                    "Older files were not scanned. Nothing was deleted."
                } else {
                    "Nothing was deleted."
                },
        )
    }

    DataActionCard(
        title = "Back up now",
        summary = "Safely try a new automatic backup in the configured storage location. " +
            "Valid existing files stay untouched if this attempt fails.",
        actionLabel = if (busy) "Working" else "Retry",
        enabled = !busy && !checkingBackupHealth,
        onAction = onRetryAutomaticBackup,
    )

    if (lastBackup > 0L && backupHealth?.verifiedAt == null) {
        PrefNote("The previous success timestamp is not proof that a backup is " +
            "still accessible. Use Check to verify the files stored now.")
    }
}

@Composable
private fun DataActionCard(
    title: String,
    summary: String,
    actionLabel: String? = null,
    enabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    onRowClick: (() -> Unit)? = null,
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
            trailingContent = if (actionLabel == null || onAction == null) null else {
                {
                    TextButton(onClick = onAction, enabled = enabled) {
                        Text(actionLabel)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = if (onRowClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onRowClick)
            } else {
                Modifier
            },
        )
    }
}
