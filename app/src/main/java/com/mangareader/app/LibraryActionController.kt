package com.mangareader.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class LibraryActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val globalSearch: GlobalSearchState,
    private val migrationState: MigrationNavigationState,
    private val enrichSeries: (Source, Series) -> Unit,
    private val openChapter: (Int) -> Unit
) {
    fun bulkSetRead(ids: Set<String>, value: Boolean) {
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

    fun bulkDownload(ids: Set<String>) {
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

    fun openFromLibrary(entry: LibraryEntry) {
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.LIBRARY
        )

        scope.launch {
            appState.loading = true
            try {
                openLibraryEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        browseState.source = source
                        browseState.sourceId = source.id
                    },
                    onCachedChapters = { cached ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.chapters = cached
                        }
                    },
                    onResolved = { source, series, chapters ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.active = series
                            seriesState.chapters = chapters
                            seriesState.fetched = true
                            enrichSeries(source, series)
                        }
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
            }
            appState.loading = false
        }
    }

    fun openFromDownloads(entry: DownloadedSeries) {
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.DOWNLOADS
        )

        scope.launch {
            appState.loading = true
            try {
                openDownloadedEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        browseState.source = source
                        browseState.sourceId = source.id
                    },
                    onCachedChapters = { cached ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.chapters = cached
                        }
                    },
                    onResolved = { source, series, chapters ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.active = series
                            seriesState.chapters = chapters
                            seriesState.fetched = true
                            enrichSeries(source, series)
                        }
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
            }
            appState.loading = false
        }
    }

    fun openFromHistory(entry: HistoryEntry) {
        appState.error = null

        scope.launch {
            appState.loading = true
            try {
                openHistoryEntry(context, entry) { target ->
                    seriesState.origin = SeriesOrigin.HISTORY
                    browseState.source = target.source
                    browseState.sourceId = target.source.id
                    seriesState.active = target.series
                    seriesState.chapters = target.chapters
                    enrichSeries(target.source, target.series)
                    openChapter(target.index)
                }
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not resume"
                )
            }
            appState.loading = false
        }
    }
}
