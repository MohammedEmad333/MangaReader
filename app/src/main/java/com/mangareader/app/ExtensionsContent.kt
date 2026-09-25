package com.mangareader.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ExtensionsContent(
    modifier: Modifier,
    reposEmpty: Boolean,
    loading: Boolean,
    error: String?,
    filter: String,
    onFilterChange: (String) -> Unit,
    installedOnly: Boolean,
    onToggleInstalledOnly: () -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    shownExtensions: List<Extension>,
    availableCount: Int,
    onDiagnose: () -> Unit,
    onInstall: (Extension) -> Unit,
    onUninstall: (String) -> Unit,
) {
    Column(modifier = modifier) {
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ErrorBanner(error)

        TextButton(
            onClick = onDiagnose,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text("Why isn't my extension showing?")
        }

        if (reposEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No repositories configured.\nAdd one in More → Browse → Extension repositories.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            return@Column
        }

        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            label = { Text("Search extensions") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (filter.isNotBlank()) {
                    TextButton(onClick = { onFilterChange("") }) {
                        Text("Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = installedOnly,
                onClick = onToggleInstalledOnly,
                label = { Text("Installed only") },
            )
            listOf("All", "Manga", "Anime").forEach { label ->
                FilterChip(
                    selected = mediaFilter == label,
                    onClick = { onMediaFilterChange(label) },
                    label = { Text(label) },
                )
            }
            Text(
                "${shownExtensions.size} of $availableCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (shownExtensions.isEmpty() && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (availableCount == 0) "Nothing in the index yet."
                    else "No extension matches that.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val updatable = shownExtensions
            .filter { it.hasUpdate }
            .sortedBy { it.name.lowercase() }
        val installed = shownExtensions
            .filter { it.isInstalled && !it.hasUpdate }
            .sortedBy { it.name.lowercase() }
        val available = shownExtensions
            .filterNot { it.isInstalled }
            .sortedBy { it.name.lowercase() }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (updatable.isNotEmpty()) {
                    item {
                        SectionHeader("Update available (${updatable.size})")
                    }
                    items(updatable) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = { onInstall(ext) },
                            onUninstall = { onUninstall(ext.pkgName) },
                        )
                    }
                }

                if (installed.isNotEmpty()) {
                    item { SectionHeader("Installed") }
                    items(installed) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = {},
                            onUninstall = { onUninstall(ext.pkgName) },
                        )
                    }
                }

                if (available.isNotEmpty()) {
                    item { SectionHeader("Available") }
                    items(available) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = { onInstall(ext) },
                        )
                    }
                }
            }

            ListScrollHandle(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
internal fun ExtensionDiagnosticsDialog(
    report: String?,
    onDismiss: () -> Unit,
) {
    if (report == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extension diagnostics") },
        text = {
            SelectionContainer {
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
