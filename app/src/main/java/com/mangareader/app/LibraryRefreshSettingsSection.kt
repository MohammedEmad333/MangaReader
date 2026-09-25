package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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

@Composable
internal fun LibraryRefreshSettingsSection() {
    val context = LocalContext.current
    var sourcePickerOpen by remember { mutableStateOf(false) }
    val entryCount = remember { Library.list(context).size }
    val uncountedCount = remember(LibraryRefresh.finishedAt, LibraryRefresh.running) {
        val known = SeriesIndex.all(context).keys
        Library.list(context).count { it.seriesId !in known }
    }

    remember { LibraryRefresh.loadSummary(context) }

    SectionHeader("Chapter counts")
    if (LibraryRefresh.running) {
        val total = LibraryRefresh.total
        val done = LibraryRefresh.done
        ListItem(
            headlineContent = { Text("Refreshing…") },
            supportingContent = {
                Text(
                    listOfNotNull(
                        if (total > 0) "$done of $total" else "Starting",
                        LibraryRefresh.currentTitle.ifBlank { null },
                    ).joinToString(" • "),
                )
            },
        )
        LinearProgressIndicator(
            progress = { if (total <= 0) 0f else done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(16.dp)) {
            OutlinedButton(onClick = { LibraryRefreshService.stop(context) }) {
                Text("Stop")
            }
        }
        if (LibraryRefresh.scopeLabel.isNotBlank()) {
            PrefNote(
                "Only ${LibraryRefresh.scopeLabel}. This one can't be resumed — " +
                    "it is short enough to just run again.",
            )
        }
        PrefNote(
            if (LibraryRefresh.resumed > 0) {
                "Resumed — ${LibraryRefresh.resumed} series were already counted " +
                    "and are not being fetched again. This keeps going with the app " +
                    "closed, and stopping picks up here."
            } else {
                "This keeps going with the app closed. Stopping keeps whatever it " +
                    "has already counted, and starting again picks up where it stopped " +
                    "rather than from the top."
            },
        )
    } else {
        val sweepStartedAt = remember(entryCount, LibraryRefresh.finishedAt) {
            RefreshCursor.startedAt(context)
        }
        val alreadyCounted = remember(sweepStartedAt, LibraryRefresh.finishedAt) {
            if (sweepStartedAt <= 0L) 0
            else SeriesIndex.sweptSince(context, sweepStartedAt).size
        }
        val unfinished = sweepStartedAt > 0L

        ListItem(
            headlineContent = {
                Text(if (unfinished) "Resume refresh" else "Refresh library")
            },
            supportingContent = {
                Text(
                    when {
                        unfinished -> listOfNotNull(
                            "$alreadyCounted of $entryCount counted",
                            LibraryRefresh.coversRepaired.get()
                                .takeIf { it > 0 }
                                ?.let { "$it covers fixed" },
                        ).joinToString(" • ") + " — carries on from there"

                        LibraryRefresh.finishedAt > 0L -> listOfNotNull(
                            "${LibraryRefresh.counted} counted",
                            if (LibraryRefresh.failed > 0) {
                                "${LibraryRefresh.failed} failed"
                            } else null,
                            if (LibraryRefresh.skipped > 0) {
                                "${LibraryRefresh.skipped} skipped"
                            } else null,
                            if (LibraryRefresh.resumed > 0) {
                                "${LibraryRefresh.resumed} resumed"
                            } else null,
                            LibraryRefresh.coversRepaired.get()
                                .takeIf { it > 0 }
                                ?.let { "$it covers fixed" },
                        ).joinToString(" • ")

                        else -> "Fetch chapter lists for all $entryCount series"
                    },
                )
            },
            modifier = Modifier.clickable {
                LibraryRefreshService.start(context)
            },
        )

        if (uncountedCount > 0) {
            ListItem(
                headlineContent = { Text("Refresh what's missing") },
                supportingContent = {
                    Text(
                        "$uncountedCount series have no chapter count yet — " +
                            "usually ones whose extension wasn't installed when " +
                            "the last refresh ran, or ones it failed on.",
                    )
                },
                modifier = Modifier.clickable {
                    LibraryRefreshService.startUncounted(context, uncountedCount)
                },
            )
        }

        ListItem(
            headlineContent = { Text("Refresh some sources") },
            supportingContent = {
                Text(
                    "Pick which sources to fetch instead of the whole library — " +
                        "useful when the summary above names one that failed.",
                )
            },
            modifier = Modifier.clickable { sourcePickerOpen = true },
        )

        if (unfinished) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { LibraryRefreshService.startOver(context) }) {
                    Text("Start over")
                }
            }
            PrefNote(
                "A refresh was stopped before it finished. Resuming fetches only " +
                    "the series it hadn't reached; starting over fetches every " +
                    "series again, which is what you want if the counts are old " +
                    "rather than incomplete.",
            )
        } else if (LibraryRefresh.finishedAt > 0L) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { LibraryRefresh.clearSummary(context) }) {
                    Text("Dismiss")
                }
            }
        }

        LibraryRefresh.firstError?.let { PrefNote("First error: $it") }

        val failures = remember(
            LibraryRefresh.finishedAt,
            LibraryRefresh.running,
        ) {
            LibraryRefresh.failureCounts()
        }
        if (failures.isNotEmpty()) {
            PrefNote(
                "Failures by source: " + failures.take(8).joinToString(" • ") {
                    "${SourceNames.nameOf(context, it.first)} ×${it.second}"
                } + if (failures.size > 8) " …" else "",
            )
        }

        PrefNote(
            "Unread counts, the unread badge and the Unread, Started and " +
                "Completed filters only know about series you have opened. This " +
                "fetches a chapter list for every series so they know about all " +
                "of them. It is one network request per series, so on a large " +
                "library it takes a while and is best left running on Wi‑Fi. " +
                "Nothing is downloaded — only the chapter lists.",
        )
    }

    if (sourcePickerOpen) {
        RefreshSourcePicker(
            onDismiss = { sourcePickerOpen = false },
            onStart = { ids, label ->
                sourcePickerOpen = false
                LibraryRefreshService.start(context, ids, label)
            },
        )
    }
}
