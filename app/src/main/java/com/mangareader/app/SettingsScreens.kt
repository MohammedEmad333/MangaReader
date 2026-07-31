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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One page of settings. Order is the order of the index. */
private enum class SettingsSection(
    val title: String,
    val glyph: String,
    val summary: String
) {
    APPEARANCE("Appearance", "\uD83C\uDFA8", "Theme"),
    LIBRARY("Library", "\uD83D\uDCDA", "Cover size, categories"),
    READER("Reader", "\uD83D\uDCD6", "Reading mode, display, colour"),
    DOWNLOADS("Downloads", "\u2B07\uFE0F", "Queue, downloaded chapters"),
    BROWSE("Browse", "\uD83E\uDDED", "Extension repositories, global search"),
    DATA("Data and storage", "\uD83D\uDDC4\uFE0F", "Backups, storage use, caches"),
    PRIVACY("Security and privacy", "\uD83D\uDD12", "Incognito mode, secure screen"),
    ADVANCED("Advanced", "\uD83D\uDEE0\uFE0F", "Diagnostics, cookies, app info"),
}

/**
 * Settings.
 *
 * Mihon's shape — an index of sections, each opening onto its own page — with
 * two deliberate differences.
 *
 * **The sub-screen is not a branch of `YomuApp`'s routing chain.** That chain is
 * already an eight-arm `if / else if` encoding real navigation rules, and adding
 * eight more arms to it for the inside of one screen would be the worst place to
 * put them. Which section is open is local state here, so the chain gains a
 * single boolean, and back pops the section before it pops the screen.
 *
 * **The rows are led by emoji, not icons.** `material-icons-core` is about forty
 * glyphs and has none of palette / storage / shield / sliders, so every row here
 * would be an approximation of the wrong thing — the fourth time in this repo
 * that's come up. The bottom nav bar already uses emoji for exactly this reason.
 * Adding `material-icons-extended` would let all of it be done properly at once;
 * until someone decides that deliberately, this is at least consistent.
 *
 * Sections only exist where there's something real behind them, which is why
 * there is no Tracking page: nothing in this app tracks anything yet, and a row
 * that opens onto an apology is worse than no row.
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloadQueue: () -> Unit
) {
    // Stored as the enum's name rather than the enum: rememberSaveable's default
    // saver only takes what a Bundle takes, and a String is unambiguously that.
    // "" is the index.
    var openSection by rememberSaveable { mutableStateOf("") }
    val section = SettingsSection.entries.firstOrNull { it.name == openSection }

    // Explicitly typed: an `if` whose branches are assignments is a statement,
    // not an expression, and without the type annotation the compiler tries to
    // read it as this lambda's return value.
    val up: () -> Unit = { if (section != null) openSection = "" else onBack() }
    BackHandler { up() }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = section?.title ?: "Settings", onBack = up)
        when (section) {
            null -> SettingsIndex(onOpen = { openSection = it.name })
            SettingsSection.APPEARANCE -> AppearanceSettings()
            SettingsSection.LIBRARY -> LibrarySettings()
            SettingsSection.READER -> ReaderDefaultsSettings()
            SettingsSection.DOWNLOADS -> DownloadSettings(onOpenDownloadQueue)
            SettingsSection.BROWSE -> BrowseSettings()
            SettingsSection.DATA -> DataSettings()
            SettingsSection.PRIVACY -> PrivacySettings()
            SettingsSection.ADVANCED -> AdvancedSettings()
        }
    }
}

@Composable
private fun SettingsIndex(onOpen: (SettingsSection) -> Unit) {
    SettingsColumn {
        SettingsSection.entries.forEach { section ->
            ListItem(
                leadingContent = {
                    Text(section.glyph, style = MaterialTheme.typography.headlineSmall)
                },
                headlineContent = { Text(section.title) },
                supportingContent = { Text(section.summary) },
                modifier = Modifier.clickable { onOpen(section) }
            )
            HorizontalDivider()
        }
    }
}

// ---------- appearance ----------

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current

    SettingsColumn {
        SectionHeader("Theme")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "App theme",
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(AppTheme.mode),
                onSelect = { AppTheme.setMode(context, ThemeMode.entries[it]) }
            )
        }
        PrefNote(
            "Dark is the default and the only one with any mileage on it \u2014 every " +
                "screen in this app was built against it. The reader keeps its own " +
                "background setting either way."
        )
    }
}

// ---------- library ----------

@Composable
private fun LibrarySettings() {
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
private fun RefreshSourcePicker(
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
@Composable
private fun ReaderDefaultsSettings() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }

    fun update(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    SettingsColumn {
        SectionHeader("Layout")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Reading mode",
                options = ReaderMode.entries.map { it.label },
                selected = ReaderMode.entries.indexOf(settings.mode),
                onSelect = { update(settings.copy(mode = ReaderMode.entries[it])) }
            )
            PrefChipRow(
                label = "Rotation",
                options = ReaderRotation.entries.map { it.label },
                selected = ReaderRotation.entries.indexOf(settings.rotation),
                onSelect = { update(settings.copy(rotation = ReaderRotation.entries[it])) }
            )
            PrefSliderRow(
                label = "Side padding",
                value = settings.sidePadding.toFloat(),
                valueLabel = "${settings.sidePadding}%",
                range = 0f..25f,
                steps = 4,
                onChange = { update(settings.copy(sidePadding = it.toInt())) }
            )
        }

        SectionHeader("Screen")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Background",
                options = ReaderBackground.entries.map { it.label },
                selected = ReaderBackground.entries.indexOf(settings.background),
                onSelect = { update(settings.copy(background = ReaderBackground.entries[it])) }
            )
        }
        Spacer(Modifier.height(4.dp))
        PrefSwitchRow("Show page number", settings.showPageNumber) {
            update(settings.copy(showPageNumber = it))
        }
        PrefSwitchRow("Fullscreen", settings.fullscreen) {
            update(settings.copy(fullscreen = it))
        }
        PrefSwitchRow("Keep screen on", settings.keepScreenOn) {
            update(settings.copy(keepScreenOn = it))
        }

        SectionHeader("Colour")
        PrefSwitchRow("Grayscale", settings.grayscale) {
            update(settings.copy(grayscale = it))
        }
        PrefSwitchRow("Invert colours", settings.inverted) {
            update(settings.copy(inverted = it))
        }
        PrefSwitchRow(
            title = "Custom brightness",
            checked = settings.customBrightness,
            summary = "Off means the system brightness applies"
        ) {
            update(settings.copy(customBrightness = it))
        }
        if (settings.customBrightness) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrefSliderRow(
                    label = "Brightness",
                    value = settings.brightness,
                    valueLabel = "${(settings.brightness * 100).toInt()}%",
                    range = 0.01f..1f,
                    steps = 0,
                    onChange = { update(settings.copy(brightness = it)) }
                )
            }
        }
        PrefNote(
            "These are the values every chapter opens with. The reader's own " +
                "settings button writes to the same store, so a change made there " +
                "shows up here and the other way round."
        )
    }
}

// ---------- downloads ----------

@Composable
private fun DownloadSettings(onOpenDownloadQueue: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val use = rememberStorageUse(tick)

    val queued = DownloadQueue.items.size
    val failed = DownloadQueue.failed.size

    SettingsColumn {
        SectionHeader("Queue")
        ListItem(
            headlineContent = { Text("Download queue") },
            supportingContent = {
                Text(
                    when {
                        failed > 0 && queued > 0 -> "$queued waiting \u00b7 $failed failed"
                        failed > 0 -> "$failed failed"
                        queued == 0 -> "Nothing queued"
                        DownloadQueue.paused -> "$queued waiting \u00b7 paused"
                        queued == 1 -> "1 chapter downloading"
                        else -> "$queued chapters \u00b7 downloading"
                    }
                )
            },
            modifier = Modifier.clickable { onOpenDownloadQueue() }
        )
        HorizontalDivider()

        SectionHeader("On device")
        ListItem(
            headlineContent = { Text("Downloaded chapters") },
            supportingContent = { Text(storageLine(use?.downloadCount, use?.downloads, "chapters")) },
            trailingContent = {
                if ((use?.downloadCount ?: 0) > 0) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete all") }
                }
            }
        )
        HorizontalDivider()
        PrefNote(
            "Downloads live in the app's own storage, so only this button and " +
                "uninstalling reclaim them \u2014 the system won't evict them the way " +
                "it evicts the reading cache."
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text(
                    "Every downloaded chapter goes, including anything only readable " +
                        "offline. This can't be undone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    Downloads.deleteAll(context)
                    confirmDelete = false
                    tick++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- browse ----------

@Composable
private fun BrowseSettings() {
    val context = LocalContext.current
    var showRepos by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(SourcePrefs.pinnedOnlySearch(context)) }
    val repoCount = remember(showRepos) { ExtensionRepos.list(context).size }

    SettingsColumn {
        SectionHeader("Extensions")
        ListItem(
            headlineContent = { Text("Extension repositories") },
            supportingContent = {
                Text(
                    when (repoCount) {
                        0 -> "None added"
                        1 -> "1 repository"
                        else -> "$repoCount repositories"
                    }
                )
            },
            modifier = Modifier.clickable { showRepos = true }
        )
        HorizontalDivider()

        SectionHeader("Global search")
        PrefSwitchRow(
            title = "Pinned sources only",
            checked = pinnedOnly,
            summary = "Falls back to every source when nothing is pinned"
        ) {
            pinnedOnly = it
            SourcePrefs.setPinnedOnlySearch(context, it)
        }
        PrefNote(
            "Hiding individual sources and whole languages stays under " +
                "Browse \u2192 Sources, next to the list it filters \u2014 it needs the " +
                "loaded sources to have anything to show."
        )
    }

    if (showRepos) {
        ExtensionReposDialog(onDismiss = { showRepos = false })
    }
}

// ---------- data and storage ----------

/**
 * Storage location, backups, and what's using space.
 *
 * The order is Mihon's, and it's the right one: where things go, then how they
 * get out of the app, then how much room is left, then what to delete. The two
 * caches at the bottom are the ones the More tab used to measure inline on the
 * main thread.
 */
@Composable
private fun DataSettings() {
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
        SectionHeader("Storage location")
        ListItem(
            headlineContent = { Text(locationLabel) },
            supportingContent = {
                Text(
                    if (moving) "Moving chapters\u2026"
                    else "Chapter downloads and automatic backups"
                )
            },
            trailingContent = {
                TextButton(enabled = !moving, onClick = chooseLocation) { Text("Change") }
            },
            modifier = Modifier.clickable(enabled = !moving) { chooseLocation() }
        )
        HorizontalDivider()
        if (customDir != null && !StorageLocation.active(context)) {
            PrefNote(
                "\u26a0 ${customDir?.absolutePath} can't be written to right now, so " +
                    "downloads are going to app storage instead. Storage permission " +
                    "revoked, or the card it's on isn't mounted."
            )
        }
        if (customDir != null) {
            ListItem(
                headlineContent = { Text("Use app storage") },
                supportingContent = { Text("Back to the default, inside the app") },
                trailingContent = {
                    TextButton(
                        enabled = !moving,
                        onClick = {
                            val previous = StorageLocation.base(context)
                            val worthMoving = StorageLocation.hasStore(previous)
                            StorageLocation.clear(context)
                            refreshLocation()
                            if (worthMoving) {
                                pendingMove = previous to StorageLocation.base(context)
                            }
                        }
                    ) { Text("Reset") }
                }
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
                    onClick = { importOpen = true }
                ) { Text("Scan") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Reorganise downloads") },
            supportingContent = {
                Text(
                    if (reorganising) "Filing chapters\u2026"
                    else "File chapters from before this layout under source and series"
                )
            },
            trailingContent = {
                TextButton(
                    enabled = !reorganising && !moving,
                    onClick = { confirmReorganise = true }
                ) { Text("Run") }
            }
        )
        HorizontalDivider()
        PrefNote(
            "A \u201cYomu\u201d folder is created inside whatever you pick, holding " +
                "\u201cdownloads\u201d and \u201cbackups\u201d. Chapters are filed under " +
                "source, then series, then chapter, so the tree reads the same in a " +
                "file manager as it does in the app. Anything left in app storage " +
                "doesn\u2019t survive uninstalling; a folder you picked does."
        )

        SectionHeader("Backup and restore")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = !busy,
                onClick = { createPicker.launch(defaultBackupName()) },
                modifier = Modifier.weight(1f)
            ) { Text("Create backup") }
            OutlinedButton(
                enabled = !busy,
                onClick = { restorePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Restore backup") }
        }
        Spacer(Modifier.height(8.dp))

        SectionHeader("Automatic backup frequency")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Frequency",
                options = BackupFrequency.entries.map { it.label },
                selected = BackupFrequency.entries.indexOf(frequency),
                onSelect = {
                    val next = BackupFrequency.entries[it]
                    frequency = next
                    Backup.setFrequency(context, next)
                }
            )
        }
        PrefNote(
            if (frequency != BackupFrequency.OFF && customDir == null)
                "With the default location these land inside app storage, where a " +
                    "file manager can't reach them \u2014 fine as a safety net, no use " +
                    "for moving to another phone. Set a folder above for that."
            else
                "Keeps the five most recent, then deletes the oldest. A backup " +
                    "holds the library, categories, history, read marks, resume " +
                    "positions and every source's settings \u2014 not the downloaded " +
                    "pages themselves."
        )
        ListItem(
            headlineContent = { Text("Last automatic backup") },
            supportingContent = {
                Text(
                    if (lastBackup <= 0L) "Never"
                    else DateUtils.getRelativeTimeSpanString(
                        lastBackup,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                )
            }
        )
        HorizontalDivider()

        SectionHeader("Storage usage")
        DeviceStorageBar()

        SectionHeader("Used by Yomu")
        ListItem(
            headlineContent = { Text("Downloaded chapters") },
            supportingContent = { Text(storageLine(use?.downloadCount, use?.downloads, "chapters")) },
            trailingContent = {
                if ((use?.downloadCount ?: 0) > 0) {
                    TextButton(onClick = { confirmDownloads = true }) { Text("Delete") }
                }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear chapter cache") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.pageCache)} \u00b7 pages from chapters you read but didn't download"
                )
            },
            trailingContent = {
                TextButton(onClick = {
                    runCatching { File(context.cacheDir, "pages").deleteRecursively() }
                    tick++
                }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear cover cache") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.images)} \u00b7 covers and thumbnails"
                )
            },
            trailingContent = {
                TextButton(onClick = {
                    runCatching {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                    }
                    tick++
                }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Clear chapter lists") },
            supportingContent = {
                Text(
                    if (use == null) "Measuring\u2026"
                    else "${formatBytes(use.chapterLists)} \u00b7 what makes a series open offline"
                )
            },
            trailingContent = {
                TextButton(onClick = { confirmChapterLists = true }) { Text("Clear") }
            }
        )
        HorizontalDivider()
        PrefNote(
            "The chapter cache is the only one the system can reclaim on its own. " +
                "Clearing the cover cache just means covers are fetched again."
        )
    }

    if (askAccess) {
        AlertDialog(
            onDismissRequest = { askAccess = false },
            title = { Text("Allow access to storage?") },
            text = {
                Text(
                    "To keep downloads in a folder you choose, Yomu needs " +
                        "permission to manage files. Android grants this on its own " +
                        "settings screen rather than in a dialog, so this opens that " +
                        "screen \u2014 come back here afterwards and pick the folder."
                )
            },
            confirmButton = {
                Button(onClick = {
                    askAccess = false
                    val intent = StorageLocation.accessIntent(context)
                    if (intent != null) accessLauncher.launch(intent)
                    else permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { askAccess = false }) { Text("Cancel") }
            }
        )
    }

    if (importOpen) {
        TachiyomiImportDialog(onDismiss = { importOpen = false })
    }

    if (confirmReorganise) {
        AlertDialog(
            onDismissRequest = { confirmReorganise = false },
            title = { Text("Reorganise downloads?") },
            text = {
                Text(
                    "Chapters downloaded before this layout sit in a folder named " +
                        "after a hash, which is unreadable but works. This files them " +
                        "under source and series instead.\n\nA chapter can only be " +
                        "placed if the app still knows what it was \u2014 anything it " +
                        "can\u2019t identify is left where it is and keeps working."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmReorganise = false
                    reorganising = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { reorganiseDownloads(context) }
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
                                        "Filed ${report.moved} chapters \u00b7 " +
                                            "${report.unidentified} couldn\u2019t be " +
                                            "identified and were left alone"
                                }
                            },
                            { "Reorganise failed: ${it.message ?: it::class.java.simpleName}" }
                        )
                    }
                }) { Text("Reorganise") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReorganise = false }) { Text("Cancel") }
            }
        )
    }

    val move = pendingMove
    if (move != null) {
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Move existing downloads?") },
            text = {
                Text(
                    "Downloads and backups already written are still in the old " +
                        "folder. Moving them keeps them readable; leaving them means " +
                        "they stay on disk taking up space but stop appearing in " +
                        "Downloads. On a large library this takes a while, and moving " +
                        "to an SD card is a copy rather than a rename, so give it time."
                )
            },
            confirmButton = {
                Button(onClick = {
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
                                if (moved == 0) "Nothing needed moving"
                                else "Moved $moved folders"
                            },
                            { "Move failed: ${it.message ?: it::class.java.simpleName}" }
                        )
                    }
                }) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) { Text("Leave them") }
            }
        )
    }

    val note = message
    if (note != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Backup") },
            text = { Text(note) },
            confirmButton = { Button(onClick = { message = null }) { Text("Done") } }
        )
    }

    val restoreUri = pendingRestore
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Everything currently in the app is replaced: library, " +
                        "categories, history, read marks and source settings. This " +
                        "can't be undone, and it isn't a merge \u2014 anything added " +
                        "since the backup was made is lost. Downloaded chapters stay " +
                        "on disk either way."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            Backup.restoreFrom(context, restoreUri)
                        }
                        busy = false
                        result.fold(
                            onSuccess = {
                                // Every piece of YomuApp's state is in `remember`,
                                // including the source list and the open series, and
                                // all of it was built from the prefs that just got
                                // replaced. Restarting the Activity is the only way
                                // to be sure nothing on screen still refers to the
                                // library that existed a second ago.
                                (context as? ComponentActivity)?.recreate()
                            },
                            onFailure = {
                                message = "Restore failed: " +
                                    (it.message ?: it::class.java.simpleName)
                            }
                        )
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDownloads = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Every downloaded chapter goes. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    Downloads.deleteAll(context)
                    confirmDownloads = false
                    tick++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloads = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmChapterLists) {
        AlertDialog(
            onDismissRequest = { confirmChapterLists = false },
            title = { Text("Clear chapter lists?") },
            text = {
                Text(
                    "A downloaded chapter stays on disk, but the series it belongs to " +
                        "won't open offline again until it's been opened once with a " +
                        "connection."
                )
            },
            confirmButton = {
                Button(onClick = {
                    ChapterCache.clearAll(context)
                    confirmChapterLists = false
                    tick++
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmChapterLists = false }) { Text("Cancel") }
            }
        )
    }

    // Re-read on the way back in, so a backup written by the worker while this
    // screen was closed doesn't leave a stale "Never" on the row.
    LaunchedEffect(tick) { lastBackup = Backup.lastBackupAt(context) }
}

/** Free space on the volume the user thinks of as the phone's storage. */
@Composable
private fun DeviceStorageBar() {
    // StatFs, not File.getFreeSpace(): on internal storage the File API reports
    // the space *this app* may use, which is smaller than the volume's free
    // space by whatever the system reserves, and the number then disagrees with
    // the one Android's own Storage screen shows.
    val stats = remember {
        runCatching {
            val fs = StatFs(Environment.getExternalStorageDirectory().path)
            fs.blockCountLong * fs.blockSizeLong to fs.availableBlocksLong * fs.blockSizeLong
        }.getOrNull()
    }
    val total = stats?.first ?: 0L
    val free = stats?.second ?: 0L
    val fraction = if (total > 0L) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            Environment.getExternalStorageDirectory().path,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (total <= 0L) "Couldn't read the volume"
            else "Available: ${formatBytes(free)} / Total: ${formatBytes(total)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** `yomu_2026-07-28_2105.json` — sorts by age in any file manager. */
private fun defaultBackupName(): String =
    "yomu_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".json"

// ---------- security and privacy ----------

@Composable
private fun PrivacySettings() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(isIncognito(context)) }
    var secure by remember { mutableStateOf(AppTheme.secureScreen(context)) }

    SettingsColumn {
        SectionHeader("Privacy")
        PrefSwitchRow(
            title = "Incognito mode",
            checked = incognito,
            summary = "Pause reading-history logging"
        ) {
            incognito = it
            prefs(context).edit().putBoolean("incognito", it).apply()
        }
        PrefSwitchRow(
            title = "Secure screen",
            checked = secure,
            summary = "Blank the app in recents and block screenshots"
        ) {
            secure = it
            AppTheme.setSecureScreen(context, it)
        }
        PrefNote(
            "Secure screen takes effect immediately and is reapplied on every " +
                "launch. It can't hide anything already saved \u2014 downloads and " +
                "history are separate."
        )
    }
}

// ---------- advanced ----------

@Composable
private fun AdvancedSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var diagnosticsTitle by remember { mutableStateOf("Extension diagnostics") }
    var running by remember { mutableStateOf(false) }
    var confirmCookies by remember { mutableStateOf(false) }

    SettingsColumn {
        SectionHeader("Diagnostics")
        ListItem(
            headlineContent = { Text("Extension diagnostics") },
            supportingContent = {
                Text(
                    if (running) "Walking the loader\u2026"
                    else "Report every step of loading the installed extensions"
                )
            },
            modifier = Modifier.clickable {
                if (running) return@clickable
                running = true
                scope.launch {
                    // Classloading, on the loader's own path. Not a main-thread job.
                    val report = withContext(Dispatchers.IO) {
                        runCatching { diagnoseExtensions(context) }
                            .getOrElse { "Diagnostics failed: ${it.message ?: it::class.java.simpleName}" }
                    }
                    diagnosticsTitle = "Extension diagnostics"
                    diagnostics = report
                    running = false
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Startup timings") },
            supportingContent = {
                Text("What the first frame waited on, and the size of the prefs file")
            },
            modifier = Modifier.clickable {
                scope.launch {
                    // Stats a file and reads the whole prefs map. Warm by now,
                    // but still not a main-thread job.
                    val report = withContext(Dispatchers.IO) {
                        runCatching { StartupTimings.report(context) }
                            .getOrElse {
                                "Timings failed: ${it.message ?: it::class.java.simpleName}"
                            }
                    }
                    diagnosticsTitle = "Startup timings"
                    diagnostics = report
                }
            }
        )
        HorizontalDivider()

        SectionHeader("Network")
        ListItem(
            headlineContent = { Text("Clear cookies") },
            supportingContent = {
                Text("Signs out of every source and drops Cloudflare clearance")
            },
            modifier = Modifier.clickable { confirmCookies = true }
        )
        HorizontalDivider()

        SectionHeader("About")
        ListItem(
            headlineContent = { Text("Yomu") },
            supportingContent = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        if (BuildConfig.DEBUG) " \u00b7 debug" else ""
                )
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Sources") },
            supportingContent = { Text("Tachiyomi / Mihon extension APKs, plus local folders") }
        )
    }

    val report = diagnostics
    if (report != null) {
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text(diagnosticsTitle) },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = { diagnostics = null }) { Text("Done") } }
        )
    }

    if (confirmCookies) {
        AlertDialog(
            onDismissRequest = { confirmCookies = false },
            title = { Text("Clear cookies?") },
            text = {
                Text(
                    "Any source you're logged into will need logging in again, and a " +
                        "Cloudflare challenge you've already passed will come back. " +
                        "Useful when a source starts refusing requests that used to work."
                )
            },
            confirmButton = {
                Button(onClick = {
                    // The cookie jar is backed by the WebView's CookieManager, so
                    // this is the one call that clears both OkHttp's cookies and
                    // the WebView's - they are the same store.
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    confirmCookies = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCookies = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- storage measurement ----------

private data class StorageUse(
    val downloadCount: Int,
    val downloads: Long,
    val pageCache: Long,
    val chapterLists: Long,
    val images: Long
)

/**
 * Sizes, measured off the main thread.
 *
 * Every one of these is a recursive walk of a directory that can hold gigabytes,
 * and the More tab has been doing two of them inline in composition. On a small
 * library that's invisible; on a full one it's a stall on the frame that opens
 * the screen. Null means "still measuring", which is why every caller renders a
 * placeholder rather than a zero — a zero here would read as "nothing stored".
 */
@Composable
private fun rememberStorageUse(tick: Int): StorageUse? {
    val context = LocalContext.current
    var use by remember { mutableStateOf<StorageUse?>(null) }
    LaunchedEffect(tick, DownloadQueue.tick) {
        use = withContext(Dispatchers.IO) {
            StorageUse(
                downloadCount = runCatching { Downloads.count(context) }.getOrDefault(0),
                downloads = runCatching { Downloads.sizeBytes(context) }.getOrDefault(0L),
                pageCache = dirSize(File(context.cacheDir, "pages")),
                chapterLists = dirSize(File(context.filesDir, "chapterlists")),
                images = runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
            )
        }
    }
    return use
}

private fun dirSize(dir: File): Long = runCatching {
    if (!dir.exists()) 0L
    else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun storageLine(count: Int?, bytes: Long?, noun: String): String = when {
    count == null || bytes == null -> "Measuring\u2026"
    count == 0 -> "Nothing downloaded"
    count == 1 -> "1 chapter \u00b7 ${formatBytes(bytes)}"
    else -> "$count $noun \u00b7 ${formatBytes(bytes)}"
}

// ---------- shared rows ----------
//
// Deliberately not the reader sheet's ChipRow / SwitchRow / SliderRow, which are
// private to ReaderScreen.kt and would only need widening to be shared. The
// reader is the least-tested file in the repo right now, and re-delivering all
// 634 lines of it to change three visibility keywords is a poor trade for fifty
// lines of trivial layout. If the reader settles, these are the obvious merge.

@Composable
private fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackButton(onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
    HorizontalDivider()
}

@Composable
private fun PrefChipRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    // Scrolls rather than wraps: FlowRow is still experimental on this Compose
    // version, same as the genre chips and the reader's own sheet.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun PrefSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    onChange: (Boolean) -> Unit
) {
    // Typed explicitly: without it the lambda is inferred as a plain () -> Unit
    // and won't fit ListItem's composable slot. Bound to a local first so the
    // null check and the lambda that uses it can't disagree.
    val s = summary
    val supporting: (@Composable () -> Unit)? =
        if (s == null) null else { { Text(s) } }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        // The whole row toggles, not just the switch: a 48dp target at the far
        // edge of the screen is the hardest thing on this page to hit one-handed.
        modifier = Modifier.clickable { onChange(!checked) }
    )
    HorizontalDivider()
}

@Composable
private fun PrefSliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        steps = steps
    )
}

@Composable
private fun PrefNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    )
}
