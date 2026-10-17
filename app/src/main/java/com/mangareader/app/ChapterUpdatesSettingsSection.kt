package com.mangareader.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Read-only updates history. Chapter navigation is added in a later PR. */
@Composable
internal fun ChapterUpdatesSettingsSection(onOpenUpdateSeries: (ChapterUpdateEvent) -> Unit) {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(ChapterUpdateJournal.list(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(ChapterUpdateNotificationPrefs.enabled(context))
    }
    var updateSearch by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        ChapterUpdateNotificationPrefs.setEnabled(context, granted)
    }

    SectionHeader("New chapters")
    PrefSwitchRow(
        title = "Notify when new chapters are detected",
        checked = notificationsEnabled,
        summary = "Off by default. Alerts are shown only for newly discovered chapters.",
        onChange = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                !ChapterUpdateNotifier.permissionGranted(context)
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationsEnabled = enabled
                ChapterUpdateNotificationPrefs.setEnabled(context, enabled)
            }
        },
    )
    ListItem(
        headlineContent = { Text("Detected chapter updates") },
        supportingContent = {
            Text(chapterUpdatesCountLabel(events.size))
        },
        trailingContent = {
            TextButton(onClick = {
                events = ChapterUpdateJournal.list(context)
                updateSearch = ""
                showHistory = true
            }) { Text("View") }
        },
    )

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Chapter updates") },
            text = {
                Column {
                    OutlinedTextField(
                        value = updateSearch,
                        onValueChange = { updateSearch = it },
                        label = { Text("Find series or chapter") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val visible = filterChapterUpdateEvents(events, updateSearch)
                    if (visible.isEmpty()) {
                        Text(
                            if (events.isEmpty()) "No updates yet. Refresh your library to check for new chapters."
                            else "No updates match your search.",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        LazyColumn {
                            items(
                                items = visible,
                                key = { "${it.sourceId}:${it.seriesId}:${it.chapterId}" },
                            ) { event ->
                                ListItem(
                                    modifier = Modifier.clickable {
                                        showHistory = false
                                        onOpenUpdateSeries(event)
                                    },
                                    headlineContent = { Text(event.seriesTitle) },
                                    supportingContent = {
                                        Column {
                                            Text(event.chapterName)
                                            Text(SourceNames.nameOf(context, event.sourceId))
                                            Text(formatChapterUpdateTime(event.detectedAt))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("Close") }
            },
            dismissButton = {
                if (events.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
                }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chapter updates?") },
            text = { Text("This removes the update history, not your library or reading progress.") },
            confirmButton = {
                TextButton(onClick = {
                    ChapterUpdateJournal.clear(context)
                    events = emptyList()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

internal fun formatChapterUpdateTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))

internal fun chapterUpdatesCountLabel(count: Int): String =
    if (count <= 0) "No new chapters detected yet" else "$count recent chapter updates"

/** Filter the Updates feed by read status without changing persisted event order. */
internal enum class ChapterUpdateReadFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    READ("Read"),
}

/** Search and read-status filters compose; neither changes the stored journal. */
internal fun filterChapterUpdateEvents(
    events: List<ChapterUpdateEvent>,
    query: String,
    readFilter: ChapterUpdateReadFilter = ChapterUpdateReadFilter.ALL,
): List<ChapterUpdateEvent> {
    val needle = query.trim()
    return events.filter { event ->
        val statusMatches = when (readFilter) {
            ChapterUpdateReadFilter.ALL -> true
            ChapterUpdateReadFilter.UNREAD -> !event.isRead
            ChapterUpdateReadFilter.READ -> event.isRead
        }
        statusMatches && (
            needle.isEmpty() ||
                event.seriesTitle.contains(needle, ignoreCase = true) ||
                event.chapterName.contains(needle, ignoreCase = true) ||
                event.sourceId.contains(needle, ignoreCase = true)
        )
    }
}
