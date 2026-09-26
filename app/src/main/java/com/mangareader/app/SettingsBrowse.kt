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
import androidx.compose.runtime.produceState
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
internal fun BrowseSettings() {
    val context = LocalContext.current
    var showRepos by remember { mutableStateOf(false) }
    var showUnclassified by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(SourcePrefs.pinnedOnlySearch(context)) }
    var showNsfw by remember { mutableStateOf(SourcePrefs.showNsfw(context)) }
    val repoCount = remember(showRepos) { ExtensionRepos.list(context).size }
    val unclassifiedCount by produceState<Int?>(initialValue = null, showUnclassified) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            val known = SourceNsfw.all(appContext)
            Library.list(appContext)
                .asSequence()
                .map { it.sourceId }
                .distinct()
                .count { it !in known }
        }
    }

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

        SectionHeader("Content")
        PrefSwitchRow(
            title = "Show 18+ sources",
            checked = showNsfw,
            summary = "Hides adult sources and extensions from Browse and search"
        ) {
            showNsfw = it
            SourcePrefs.setShowNsfw(context, it)
        }
        PrefNote(
            "This hides them from the Sources list, the Extensions list and " +
                "global search. It does not remove anything already in your " +
                "library, and it isn't a lock \u2014 the switch is right here."
        )
        ListItem(
            headlineContent = { Text("Unclassified sources") },
            supportingContent = {
                Text(
                    when (unclassifiedCount) {
                        null -> "Checking library…"
                        0 -> "Every source in your library is classified"
                        1 -> "1 source in your library, 18+ unknown"
                        else -> "$unclassifiedCount sources in your library, 18+ unknown"
                    }
                )
            },
            modifier = Modifier.clickable { showUnclassified = true }
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
    if (showUnclassified) {
        UnclassifiedSourcesDialog(onDismiss = { showUnclassified = false })
    }
}

/**
 * Sources in the library that nothing has ever classified as 18+ or not.
 *
 * **Why a screen has to exist at all.** [SourceNsfw] learns from
 * `SourceManager.listAllSources` (what is installed) and the repo index (what is
 * installable). A source that is neither — a fork's built-in, or an extension
 * delisted since the backup was taken — is in no list anything can read, and its
 * entries sit in the library permanently unclassified. The library's 18+ filter
 * treats unknown as "the condition doesn't hold", so excluding 18+ leaves those
 * entries in. Correct, and useless if there is no way to say otherwise.
 *
 * A Tachiyomi backup's field 101 is the one other place such a source survives,
 * and `TachiyomiImport` records *names* from it — but `BackupSource` carries a
 * name and an id and no content flag, so it cannot answer this. Nothing can.
 * Hence a person.
 */
@Composable
private fun UnclassifiedSourcesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Snapshot once on open, but build it on IO: a restored library can contain
    // thousands of entries, and grouping it is not composition work.
    val rows by produceState<List<Triple<String, String, Int>>?>(initialValue = null) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            val known = SourceNsfw.all(appContext)
            Library.list(appContext)
                .groupingBy { it.sourceId }
                .eachCount()
                .filterKeys { it !in known }
                .map { (id, count) ->
                    Triple(id, SourceNames.nameOf(appContext, id), count)
                }
                .sortedByDescending { it.third }
        }
    }
    // Only ids the user actually touched. An untouched switch must not record
    // "not 18+" — that is a claim nobody made, and storing it would take the
    // source off this list without anyone having answered.
    var flags by remember { mutableStateOf(emptyMap<String, Boolean>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unclassified sources") },
        text = {
            when {
                rows == null -> Text("Loading sources…")
                rows!!.isEmpty() -> Text("Every source in your library has been classified.")
                else -> {
                    Column {
                        Text(
                            "These sources aren't installed and aren't in any repository, " +
                                "so nothing can tell whether they're 18+. Switch on the " +
                                "ones that are and the library's 18+ filter will cover them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                            items(rows!!) { (id, name, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name)
                                        Text(
                                            if (count == 1) "1 in library" else "$count in library",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = flags[id] ?: false,
                                        onCheckedChange = { value ->
                                            flags = flags + (id to value)
                                            SourceNsfw.record(context, mapOf(id to value))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
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
