package com.mangareader.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun GlobalSearchRoute(
    query: String,
    results: List<GlobalResult>,
    running: Boolean,
    done: Int,
    total: Int,
    pinnedOnly: Boolean,
    onTogglePinnedOnly: (Boolean) -> Unit,
    hasResultsOnly: Boolean,
    onToggleHasResultsOnly: (Boolean) -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    recents: List<String>,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: (Source) -> Unit,
    migrating: Boolean,
    onOpenSeries: (Source, Series) -> Unit,
    libraryTick: Int,
    scroll: ScrollMemory,
    onBack: () -> Unit,
    migrateFrom: MigrateFrom?,
    migrateTarget: Pair<Source, Series>?,
    onDismissMigration: () -> Unit,
    onConfirmMigration: (MigrateFrom, Source, Series) -> Unit
) {
    GlobalSearchScreen(
        query = query,
        results = results,
        running = running,
        done = done,
        total = total,
        pinnedOnly = pinnedOnly,
        onTogglePinnedOnly = onTogglePinnedOnly,
        hasResultsOnly = hasResultsOnly,
        onToggleHasResultsOnly = onToggleHasResultsOnly,
        mediaFilter = mediaFilter,
        onMediaFilterChange = onMediaFilterChange,
        recents = recents,
        onRemoveRecent = onRemoveRecent,
        onClearRecents = onClearRecents,
        onSearch = onSearch,
        onCancel = onCancel,
        onOpenSource = onOpenSource,
        migrating = migrating,
        onOpenSeries = onOpenSeries,
        libraryTick = libraryTick,
        scroll = scroll,
        onBack = onBack
    )

    migrateTarget?.let { (targetSource, targetSeries) ->
        val from = migrateFrom
        AlertDialog(
            onDismissRequest = onDismissMigration,
            title = { Text("Migrate series") },
            text = {
                Text(
                    "Move “${from?.title}” to ${targetSource.name}? " +
                        "Your categories and read progress move with it, and the " +
                        "old entry is removed."
                )
            },
            confirmButton = {
                Button(
                    enabled = from != null,
                    onClick = {
                        if (from != null) {
                            onConfirmMigration(from, targetSource, targetSeries)
                        }
                    }
                ) { Text("Migrate") }
            },
            dismissButton = {
                TextButton(onClick = onDismissMigration) { Text("Cancel") }
            }
        )
    }
}
