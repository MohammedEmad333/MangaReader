package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Full-size updates feed, independent of the Library settings dialog. */
@Composable
internal fun ChapterUpdatesTab(onOpenChapter: (ChapterUpdateEvent) -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(ChapterUpdateJournal.list(context)) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = filterChapterUpdateEvents(events, query)
    val grouped = groupChapterUpdatesByDay(visible, ::chapterUpdateDayLabel)

    Column(modifier = Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("Chapter updates", style = MaterialTheme.typography.titleLarge) },
            supportingContent = { Text(chapterUpdatesCountLabel(events.size)) },
            trailingContent = {
                TextButton(onClick = { events = ChapterUpdateJournal.list(context) }) {
                    Text("Refresh")
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search series or chapter") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (visible.isEmpty()) {
            Text(
                if (events.isEmpty()) "No chapter updates yet. Refresh your library to check for new releases."
                else "No updates match your search.",
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                grouped.forEach { (date, seriesGroups) ->
                    item(key = "day:$date") {
                        Text(
                            date,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    seriesGroups.forEach { group ->
                        item(key = "series:$date:${group.sourceId}:${group.seriesId}") {
                            ListItem(
                                headlineContent = { Text(group.seriesTitle) },
                                supportingContent = {
                                    Text("${group.events.size} new chapter(s)")
                                },
                            )
                        }
                        items(
                            group.events,
                            key = { "chapter:${it.sourceId}:${it.seriesId}:${it.chapterId}" },
                        ) { event ->
                            ListItem(
                                modifier = Modifier.clickable { onOpenChapter(event) },
                                headlineContent = { Text(event.chapterName) },
                                supportingContent = { Text(formatChapterUpdateTime(event.detectedAt)) },
                            )
                        }
                        item(key = "divider:$date:${group.sourceId}:${group.seriesId}") {
                            HorizontalDivider()
                        }
                    }
                }
            }
            TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chapter updates?") },
            text = { Text("This will not delete any series, chapters, or reading progress.") },
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
