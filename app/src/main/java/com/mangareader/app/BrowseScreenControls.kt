package com.mangareader.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreenControls(
    title: String,
    supportsSearch: Boolean,
    supportsLatest: Boolean,
    supportsFilters: Boolean,
    searchOpen: Boolean,
    query: String,
    searchField: String,
    onSearchFieldChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    onOpenFilters: () -> Unit,
    view: BrowseView,
    onViewChange: (BrowseView) -> Unit,
    onRescan: () -> Unit,
    onDiagnose: () -> Unit,
    onBack: () -> Unit,
) {
    var viewMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (supportsSearch) {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription =
                            if (searchOpen) "Close search" else "Search",
                    )
                }
            }

            IconButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }

            Box {
                IconButton(onClick = { viewMenuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "View options",
                    )
                }
                DropdownMenu(
                    expanded = viewMenuOpen,
                    onDismissRequest = { viewMenuOpen = false },
                ) {
                    BrowseView.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (option == view) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                    )
                                }
                            },
                            onClick = {
                                onViewChange(option)
                                viewMenuOpen = false
                            },
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text("Connection probe") },
                        onClick = {
                            viewMenuOpen = false
                            onDiagnose()
                        },
                    )
                }
            }
        },
    )

    if (supportsLatest || supportsFilters) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = query.isBlank() && mode == BrowseMode.POPULAR,
                onClick = { onModeChange(BrowseMode.POPULAR) },
                label = { Text("Popular") },
            )

            if (supportsLatest) {
                FilterChip(
                    selected = query.isBlank() && mode == BrowseMode.LATEST,
                    onClick = { onModeChange(BrowseMode.LATEST) },
                    label = { Text("Latest") },
                )
            }

            if (supportsFilters) {
                FilterChip(
                    selected = query.isBlank() && mode == BrowseMode.FILTER,
                    onClick = onOpenFilters,
                    label = { Text("Filter") },
                )
            }
        }
    }

    if (supportsSearch && searchOpen) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchField,
                onValueChange = onSearchFieldChange,
                label = { Text("Search this source") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch) {
                Text("Go")
            }
        }

        if (query.isNotBlank()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Results for “$query”",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearSearch) {
                    Text("Clear")
                }
            }
        }
    }
}
