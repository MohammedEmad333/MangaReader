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
internal fun LibrarySettings() {
    val context = LocalContext.current
    var showCategories by remember { mutableStateOf(false) }
    var sourcePickerOpen by remember { mutableStateOf(false) }
    var categoryTick by remember { mutableIntStateOf(0) }
    val sizes = listOf("small", "medium", "large")
    var coverSize by remember {
        mutableStateOf(prefs(context).getString("cover_size", "medium") ?: "medium")
    }
    val categoryCount = remember(categoryTick, showCategories) { Categories.list(context).size }
    val entryCount = remember { Library.list(context).size }
    // How many series the index still knows nothing about. Both reads are
    // memoised on their raw pref strings, and this recomputes when a sweep ends
    // — which is exactly when the answer changes.
    val uncountedCount = remember(LibraryRefresh.finishedAt, LibraryRefresh.running) {
        val known = SeriesIndex.all(context).keys
        Library.list(context).count { it.seriesId !in known }
    }
    // The last sweep's numbers, if this process didn't run it. Here rather than
    // in onCreate: this is the only screen that shows them, and the startup path
    // is not the place for a read nothing on the first frame needs. Guarded
    // internally, so calling it on every recomposition of this screen is free.
    remember { LibraryRefresh.loadSummary(context) }

    SettingsColumn {
        SectionHeader("Display")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Cover size",
                options = sizes.map { it.replaceFirstChar { c -> c.uppercase() } },
                selected = sizes.indexOf(coverSize).coerceAtLeast(0),
                onSelect = {
                    coverSize = sizes[it]
                    prefs(context).edit().putString("cover_size", sizes[it]).apply()
                }
            )
        }
        PrefNote(
            "Applies to per-source browsing. The library grid has its own "
                + "columns setting, under Display in the library's options sheet."
        )

        SectionHeader("Categories")
        ListItem(
            headlineContent = { Text("Edit categories") },
            supportingContent = {
                Text(
                    when (categoryCount) {
                        0 -> "None yet"
                        1 -> "1 category"
                        else -> "$categoryCount categories"
                    }
                )
            },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Saved series") },
            supportingContent = { Text("$entryCount in the library") }
        )

        SectionHeader("Chapter counts")
        if (LibraryRefresh.running) {
            val total = LibraryRefresh.total
            val done = LibraryRefresh.done
            ListItem(
                headlineContent = { Text("Refreshing\u2026") },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            if (total > 0) "$done of $total" else "Starting",
                            LibraryRefresh.currentTitle.ifBlank { null }
                        ).joinToString(" \u2022 ")
                    )
                }
            )
            LinearProgressIndicator(
                progress = { if (total <= 0) 0f else done.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Row(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(onClick = { LibraryRefreshService.stop(context) }) {
                    Text("Stop")
                }
            }
            if (LibraryRefresh.scopeLabel.isNotBlank()) {
                PrefNote("Only ${LibraryRefresh.scopeLabel}. This one can't be resumed " +
                    "\u2014 it is short enough to just run again.")
            }
            PrefNote(
                if (LibraryRefresh.resumed > 0) {
                    "Resumed \u2014 ${LibraryRefresh.resumed} series were already " +
                        "counted and are not being fetched again. This keeps going " +
                        "with the app closed, and stopping picks up here."
                } else {
                    "This keeps going with the app closed. Stopping keeps whatever " +
                        "it has already counted, and starting again picks up where " +
                        "it stopped rather than from the top."
                }
            )
        } else {
            // Both keyed on finishedAt so they re-read when a sweep ends, which
            // is the moment the cursor is either cleared or left behind.
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
                            // The covers line belongs in *both* arms. It was
                            // added to the finished one alone, and `unfinished`
                            // wins this `when` after every stop — so the one
                            // number that says whether a repair pass did
                            // anything was unreachable in exactly the state you
                            // read it from.
                            unfinished -> listOfNotNull(
                                "$alreadyCounted of $entryCount counted",
                                LibraryRefresh.coversRepaired.get()
                                    .takeIf { it > 0 }
                                    ?.let { "$it covers fixed" }
                            ).joinToString(" \u2022 ") + " \u2014 carries on from there"
                            // `resumed` belongs here even though it is zero on
                            // most runs. The end-of-sweep check is
                            // `counted + failed + skipped == total - resumed`,
                            // and with `resumed` absent from this row the one
                            // input it needs is invisible after a sweep ends —
                            // which makes the tempting move computing it from
                            // the other three, turning the identity into x == x
                            // and destroying the whole check.
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
                                // Outside the arithmetic above, deliberately.
                                // A repaired cover is not a fourth outcome
                                // alongside counted/failed/skipped — the same
                                // series is counted *and* possibly repaired, so
                                // adding this to that sum would break the one
                                // identity this row exists to let you check.
                                LibraryRefresh.coversRepaired.get()
                                    .takeIf { it > 0 }
                                    ?.let { "$it covers fixed" }
                            ).joinToString(" \u2022 ")
                            else -> "Fetch chapter lists for all $entryCount series"
                        }
                    )
                },
                modifier = Modifier.clickable { LibraryRefreshService.start(context) }
            )
            // Offered only when nothing is running: two sweeps would fight over
            // the same counters and the same foreground service.
            if (!LibraryRefresh.running && uncountedCount > 0) {
                ListItem(
                    headlineContent = { Text("Refresh what's missing") },
                    supportingContent = {
                        Text(
                            "$uncountedCount series have no chapter count yet \u2014 " +
                                "usually ones whose extension wasn't installed when " +
                                "the last refresh ran, or ones it failed on."
                        )
                    },
                    modifier = Modifier.clickable {
                        LibraryRefreshService.startUncounted(context, uncountedCount)
                    }
                )
            }
            if (!LibraryRefresh.running) {
                ListItem(
                    headlineContent = { Text("Refresh some sources") },
                    supportingContent = {
                        Text(
                            "Pick which sources to fetch instead of the whole " +
                                "library \u2014 useful when the summary above names " +
                                "one that failed."
                        )
                    },
                    modifier = Modifier.clickable { sourcePickerOpen = true }
                )
            }
            if (unfinished) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { LibraryRefreshService.startOver(context) }) {
                        Text("Start over")
                    }
                }
                PrefNote(
                    "A refresh was stopped before it finished. Resuming fetches " +
                        "only the series it hadn't reached; starting over fetches " +
                        "every series again, which is what you want if the counts " +
                        "are old rather than incomplete."
                )
            } else if (LibraryRefresh.finishedAt > 0L) {
                // The summary is process-lifetime snapshot state with nothing
                // persisting it, so without a dismiss it sits on this row until
                // something kills the app and the plain "fetch chapter lists"
                // copy never comes back. Clearing it is also what removes the
                // first-error note below.
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { LibraryRefresh.clearSummary(context) }) {
                        Text("Dismiss")
                    }
                }
            }
            LibraryRefresh.firstError?.let { PrefNote("First error: $it") }
            // Which sources are failing, not just how many series did. One error
            // string names one series; this names the thing to go and look at,
            // across the whole library, in one pass.
            //
            // Shown by name since 0.94. It read out raw ids before, and the
            // justification written here — that the id is what identifies a
            // source in the Extensions tab — was rationalising a limitation:
            // `tachi:6202325652827735606 \u00d712` tells you a source is broken
            // and gives you no way to find out which. Nothing stored a name
            // anywhere until SourceNames.
            val failures = remember(LibraryRefresh.finishedAt, LibraryRefresh.running) {
                LibraryRefresh.failureCounts()
            }
            if (failures.isNotEmpty()) {
                PrefNote(
                    // Named, not `tachi:6202325652827735606`. The tally exists to
                    // point at the source that is failing, and an id points at
                    // nothing — SourceNames is the map that makes it legible.
                    "Failures by source: " + failures.take(8).joinToString(" \u2022 ") {
                        "${SourceNames.nameOf(context, it.first)} \u00d7${it.second}"
                    } + if (failures.size > 8) " \u2026" else ""
                )
            }
            PrefNote(
                "Unread counts, the unread badge and the Unread, Started and " +
                    "Completed filters only know about series you have opened. " +
                    "This fetches a chapter list for every series so they know " +
                    "about all of them. It is one network request per series, so " +
                    "on a large library it takes a while and is best left running " +
                    "on Wi\u2011Fi. Nothing is downloaded \u2014 only the chapter lists."
            )
        }
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = {
            showCategories = false
            categoryTick++
        })
    }

    if (sourcePickerOpen) {
        RefreshSourcePicker(
            onDismiss = { sourcePickerOpen = false },
            onStart = { ids, label ->
                sourcePickerOpen = false
                LibraryRefreshService.start(context, ids, label)
            }
        )
    }
}

/**
 * Picks which sources a refresh should cover.
 *
 * Built from the *library*, not from `SourceManager`: a source with nothing
 * saved from it has nothing to refresh, and an entry whose extension has since
 * been uninstalled still needs to be listed — it is exactly the kind of thing
 * someone comes here to retry. That also keeps this off the classloading path,
 * which is not something to do from a dialog.
 */
@Composable
internal fun RefreshSourcePicker(
    onDismiss: () -> Unit,
    onStart: (Set<String>, String) -> Unit
) {
    val context = LocalContext.current
    // One parse, not one per recomposition of a checkbox.
    val rows = remember {
        Library.list(context)
            .groupingBy { it.sourceId }
            .eachCount()
            .map { (id, count) -> Triple(id, SourceNames.nameOf(context, id), count) }
            .sortedByDescending { it.third }
    }
    var picked by remember { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refresh some sources") },
        text = {
            if (rows.isEmpty()) {
                Text("Nothing in the library yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(rows) { (id, name, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = if (id in picked) picked - id else picked + id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = id in picked, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "$name ($count)",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = {
                    val label = rows.filter { it.first in picked }
                        .let { chosen ->
                            if (chosen.size == 1) chosen.first().second
                            else "${chosen.size} sources"
                        }
                    onStart(picked, label)
                }
            ) {
                Text(
                    if (picked.isEmpty()) "Refresh"
                    else "Refresh ${rows.filter { it.first in picked }.sumOf { it.third }}"
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- reader ----------

/**
 * The same [ReaderSettings] the reader's own sheet edits, in the same store.
 *
 * Worth having in both places rather than only in the reader: this is where the
 * values can be changed *before* opening a chapter, which is the only way to
 * pick a reading mode without first landing in the wrong one.
 */
