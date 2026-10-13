package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
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
internal fun ChapterUpdatesSettingsSection() {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(ChapterUpdateJournal.list(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    SectionHeader("New chapters")
    ListItem(
        headlineContent = { Text("Detected chapter updates") },
        supportingContent = {
            Text(chapterUpdatesCountLabel(events.size))
        },
        trailingContent = {
            TextButton(onClick = {
                events = ChapterUpdateJournal.list(context)
                showHistory = true
            }) { Text("View") }
        },
    )

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Chapter updates") },
            text = {
                if (events.isEmpty()) {
                    Text("No updates yet. Refresh your library to check for new chapters.")
                } else {
                    LazyColumn {
                        items(
                            items = events,
                            key = { "${it.sourceId}:${it.seriesId}:${it.chapterId}" },
                        ) { event ->
                            ListItem(
                                headlineContent = { Text(event.seriesTitle) },
                                supportingContent = {
                                    Column {
                                        Text(event.chapterName)
                                        Text(formatChapterUpdateTime(event.detectedAt))
                                    }
                                },
                            )
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
