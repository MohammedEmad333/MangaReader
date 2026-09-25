package com.mangareader.app

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    ListItem(
        headlineContent = { Text(locationLabel) },
        supportingContent = {
            Text(
                if (moving) "Moving chapters…"
                else "Chapter downloads and automatic backups",
            )
        },
        trailingContent = {
            TextButton(
                enabled = !moving,
                onClick = onChooseLocation,
            ) {
                Text("Change")
            }
        },
        modifier = Modifier.clickable(
            enabled = !moving,
            onClick = onChooseLocation,
        ),
    )
    HorizontalDivider()

    if (customDir != null && !active) {
        PrefNote(
            "⚠ ${customDir.absolutePath} can't be written to right now, so " +
                "downloads are going to app storage instead. Storage permission " +
                "revoked, or the card it's on isn't mounted.",
        )
    }

    if (customDir != null) {
        ListItem(
            headlineContent = { Text("Use app storage") },
            supportingContent = { Text("Back to the default, inside the app") },
            trailingContent = {
                TextButton(
                    enabled = !moving,
                    onClick = onUseAppStorage,
                ) {
                    Text("Reset")
                }
            },
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
                onClick = onImport,
            ) {
                Text("Scan")
            }
        },
    )
    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Reorganise downloads") },
        supportingContent = {
            Text(
                if (reorganising) "Filing chapters…"
                else "File chapters from before this layout under source and series",
            )
        },
        trailingContent = {
            TextButton(
                enabled = !reorganising && !moving,
                onClick = onReorganise,
            ) {
                Text("Run")
            }
        },
    )
    HorizontalDivider()

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
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onFrequencyChange: (BackupFrequency) -> Unit,
) {
    SectionHeader("Backup and restore")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
    Spacer(Modifier.height(8.dp))

    SectionHeader("Automatic backup frequency")
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        PrefChipRow(
            label = "Frequency",
            options = BackupFrequency.entries.map { it.label },
            selected = BackupFrequency.entries.indexOf(frequency),
            onSelect = { onFrequencyChange(BackupFrequency.entries[it]) },
        )
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

    ListItem(
        headlineContent = { Text("Last automatic backup") },
        supportingContent = {
            Text(
                if (lastBackup <= 0L) {
                    "Never"
                } else {
                    DateUtils.getRelativeTimeSpanString(
                        lastBackup,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                },
            )
        },
    )
    HorizontalDivider()
}
