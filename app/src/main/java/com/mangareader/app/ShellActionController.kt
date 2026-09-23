package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ShellActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val globalSearch: GlobalSearchState,
    private val mediaState: MediaNavigationState,
    private val openSource: (Source, String, BrowseMode) -> Unit,
    private val runGlobalSearch: (String) -> Unit
) {
    fun selectTab(tab: Int) {
        appState.currentTab = tab
        if (tab == 2) {
            refreshHistory()
        }
    }

    fun removeLibrary(ids: Set<String>) {
        Library.removeAll(context, ids)
        appState.libraryTick++
    }

    fun openGlobalSearch() {
        globalSearch.open = true
        if (
            globalSearch.query.isNotBlank() &&
            globalSearch.results.isEmpty() &&
            !globalSearch.running
        ) {
            runGlobalSearch(globalSearch.query)
        }
    }

    fun addSource() {
        appState.editingConfig = SourceConfig(
            SourceManager.newId(),
            "local",
            ""
        )
        appState.showSourceDialog = true
    }

    fun editSource(config: SourceConfig) {
        appState.editingConfig = config
        appState.showSourceDialog = true
    }

    fun deleteSource(config: SourceConfig) {
        SourceManager.remove(context, config.id)
        reloadConfigs()
    }

    fun refreshExtensions() {
        scope.launch {
            appState.extensionSources = withContext(Dispatchers.IO) {
                runCatching {
                    SourceManager.listAllSources(context)
                        .filter { it.id.startsWith("tachi:") }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun deleteHistory(entry: HistoryEntry) {
        History.remove(context, entry.chapterKey)
        refreshHistory()
    }

    fun clearHistory() {
        History.list(context).forEach {
            History.remove(context, it.chapterKey)
        }
        refreshHistory()
    }

    fun refreshHistory() {
        appState.history = History.forDisplay(context)
    }

    fun openDownloadQueue() {
        appState.downloadsOpen = true
    }

    fun closeDownloadQueue() {
        appState.downloadsOpen = false
    }

    fun openSettings() {
        appState.settingsOpen = true
    }

    fun closeSettings() {
        appState.settingsOpen = false
    }

    fun showFilters() {
        appState.filtersOpen = true
    }

    fun dismissFilters() {
        appState.filtersOpen = false
    }

    fun applyFilters(source: Source) {
        dismissFilters()
        openSource(source, "", BrowseMode.FILTER)
    }

    fun showProbe() {
        appState.probeOpen = true
    }

    fun dismissProbe() {
        appState.probeOpen = false
    }

    fun setEditingConfig(config: SourceConfig?) {
        appState.editingConfig = config
    }

    fun dismissSourceDialog() {
        appState.showSourceDialog = false
        appState.editingConfig = null
    }

    fun saveSource(config: SourceConfig) {
        SourceManager.upsert(context, config)
        reloadConfigs()
        dismissSourceDialog()
    }

    fun dismissVideoScan() {
        mediaState.scan = null
    }

    fun openEmbed(url: String) {
        val page = seriesState.active?.let { series ->
            browseState.source?.seriesUrl(series)
        }
        mediaState.scan = null
        mediaState.embed = url to page.orEmpty()
    }

    fun openExternalVideo(url: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(view)
        }.onFailure {
            appState.error =
                "No app on this device can play that link"
        }
    }

    private fun reloadConfigs() {
        appState.configs = SourceManager.list(context)
    }
}
