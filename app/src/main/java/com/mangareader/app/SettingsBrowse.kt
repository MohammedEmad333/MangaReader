package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BrowseSettings() {
    val context = LocalContext.current
    var showRepos by remember { mutableStateOf(false) }
    var showUnclassified by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(SourcePrefs.pinnedOnlySearch(context)) }
    var showNsfw by remember { mutableStateOf(SourcePrefs.showNsfw(context)) }
    val repoCount by produceState<Int?>(initialValue = null, showRepos) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) { ExtensionRepos.list(appContext).size }
    }
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
        SettingsActionCard(
            title = "Extension repositories",
            summary = when (repoCount) {
                null -> "Checking repositories…"
                0 -> "None added"
                1 -> "1 repository"
                else -> "$repoCount repositories"
            },
            onClick = { showRepos = true },
        )

        SectionHeader("Content")
        PrefSwitchRow(
            title = "Show 18+ sources",
            checked = showNsfw,
            summary = "Hides adult sources and extensions from Browse and search",
        ) {
            showNsfw = it
            SourcePrefs.setShowNsfw(context, it)
        }
        PrefNote(
            "This hides them from the Sources list, the Extensions list and " +
                "global search. It does not remove anything already in your " +
                "library, and it isn't a lock — the switch is right here."
        )
        SettingsActionCard(
            title = "Unclassified sources",
            summary = when (unclassifiedCount) {
                null -> "Checking library…"
                0 -> "Every source in your library is classified"
                1 -> "1 source in your library, 18+ unknown"
                else -> "$unclassifiedCount sources in your library, 18+ unknown"
            },
            onClick = { showUnclassified = true },
        )

        SectionHeader("Global search")
        PrefSwitchRow(
            title = "Pinned sources only",
            checked = pinnedOnly,
            summary = "Falls back to every source when nothing is pinned",
        ) {
            pinnedOnly = it
            SourcePrefs.setPinnedOnlySearch(context, it)
        }
        PrefNote(
            "Hiding individual sources and whole languages stays under " +
                "Browse → Sources, next to the list it filters — it needs the " +
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

@Composable
private fun UnclassifiedSourcesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                            items(rows!!) { (id, name, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name)
                                        Text(
                                            if (count == 1) "1 in library" else "$count in library",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = flags[id] ?: false,
                                        onCheckedChange = { value ->
                                            flags = flags + (id to value)
                                            SourceNsfw.record(context, mapOf(id to value))
                                        },
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
        },
    )
}
