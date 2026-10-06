package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Source visibility controls grouped by language. Visibility semantics are kept
 * separate from presentation so disabling a language still leaves each source's
 * individual hidden state intact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceFilterScreen(
    rows: List<BrowseRow>,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(SourcePrefs.hiddenSources(context)) }
    var enabledLangs by remember { mutableStateOf(SourcePrefs.enabledLangs(context)) }
    val showNsfw = remember { SourcePrefs.showNsfw(context) }

    val groups = remember(rows) {
        rows.groupBy { it.lang.ifBlank { "Other" } }
            .toList()
            .sortedBy { it.first.lowercase() }
            .sortedBy { langRank(it.first) }
    }
    val allIds = remember(rows) { rows.map { it.id } }
    val allLangs = remember(rows) { rows.map { it.lang.ifBlank { "Other" } }.distinct() }
    val allShown = hidden.isEmpty() && allLangs.all { it in enabledLangs }
    val shown = rows.count {
        SourcePrefs.isVisible(
            it.id,
            it.lang.ifBlank { "Other" },
            it.isNsfw,
            hidden,
            enabledLangs,
            showNsfw,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Sources", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$shown of ${rows.size} shown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = { BackButton(onBack) },
        )

        val sourceListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = sourceListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        ListItem(
                            headlineContent = {
                                Text("All sources", style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(
                                    "$shown of ${rows.size} available in Browse",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = allShown,
                                    onCheckedChange = { on ->
                                        enabledLangs = SourcePrefs.setLangsEnabled(context, allLangs, on)
                                        if (on) {
                                            hidden = SourcePrefs.setSourcesHidden(context, allIds, false)
                                        }
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                groups.forEach { (lang, sources) ->
                    val langOff = lang !in enabledLangs
                    val shownInGroup = sources.count {
                        SourcePrefs.isVisible(
                            it.id,
                            it.lang.ifBlank { "Other" },
                            it.isNsfw,
                            hidden,
                            enabledLangs,
                            showNsfw,
                        )
                    }

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 1.dp,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(lang, style = MaterialTheme.typography.titleMedium)
                                },
                                supportingContent = {
                                    Text(
                                        if (langOff) {
                                            "Disabled · ${sources.size} sources"
                                        } else {
                                            "$shownInGroup of ${sources.size} visible"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = !langOff,
                                        onCheckedChange = { on ->
                                            enabledLangs = SourcePrefs.setLangEnabled(context, lang, on)
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }

                    items(sources.sortedBy { it.name.lowercase() }) { row ->
                        val on = row.id !in hidden
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .alpha(if (langOff) 0.45f else 1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            ListItem(
                                leadingContent = { SourceIcon(row.iconPkg, row.name) },
                                headlineContent = {
                                    Text(row.name, style = MaterialTheme.typography.titleSmall)
                                },
                                supportingContent = if (row.isNsfw) {
                                    {
                                        Text(
                                            "18+ source",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = on && !langOff,
                                        enabled = !langOff,
                                        onCheckedChange = {
                                            hidden = SourcePrefs.toggleSourceHidden(context, row.id)
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable(enabled = !langOff) {
                                    hidden = SourcePrefs.toggleSourceHidden(context, row.id)
                                },
                            )
                        }
                    }
                }
            }

            ListScrollHandle(
                state = sourceListState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
