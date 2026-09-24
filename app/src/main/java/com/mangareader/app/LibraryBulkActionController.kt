package com.mangareader.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class LibraryBulkActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val globalSearch: GlobalSearchState,
    private val migrationState: MigrationNavigationState
) {
    fun setRead(ids: Set<String>, value: Boolean) {
        if (ids.isEmpty()) return

        scope.launch {
            val result = setLibrarySeriesRead(context, ids, value)
            appState.libraryTick++

            val verb = if (value) "read" else "unread"
            val message = buildString {
                append("Marked ${result.changed} series $verb")
                if (result.skipped > 0) {
                    append(
                        " · ${result.skipped} skipped " +
                            "(not downloadable)"
                    )
                }
            }

            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun download(ids: Set<String>) {
        if (ids.isEmpty()) return

        scope.launch {
            val result = queueLibraryDownloads(context, ids)
            appState.downloadTick++

            val message = buildString {
                append(
                    if (result.added > 0) {
                        "Queued ${result.added} " +
                            if (result.added == 1) {
                                "chapter"
                            } else {
                                "chapters"
                            }
                    } else {
                        "Nothing to download — already downloaded or queued"
                    }
                )
                if (result.skipped > 0) {
                    append(
                        " · ${result.skipped} skipped " +
                            "(no chapter list)"
                    )
                }
            }

            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun migrate(
        from: MigrateFrom,
        toSource: Source,
        toSeries: Series
    ) {
        scope.launch {
            val migrated = migrateLibrarySeries(
                context,
                from,
                toSource,
                toSeries
            )
            migrationState.clear()

            if (migrated) {
                globalSearch.cancel()
                globalSearch.open = false
                browseState.series = null
                seriesState.tagReturn = null
                seriesState.active = null
                browseState.clearSource()
                appState.currentTab = 0
                appState.libraryTick++

                Toast.makeText(
                    context,
                    "Migrated to ${toSource.name}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                appState.error = "Couldn't migrate this series"
            }
        }
    }
}
