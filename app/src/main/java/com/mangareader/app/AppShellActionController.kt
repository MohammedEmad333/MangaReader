package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppShellActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val openSource: (Source, String, BrowseMode) -> Unit
) {
    fun selectTab(tab: Int) {
        // History is already refreshed when reader/media state changes. Re-reading
        // it on every bottom-nav tap causes needless disk work and a visible
        // recomposition when simply returning to the tab.
        appState.currentTab = tab
    }

    fun removeLibrary(ids: Set<String>) {
        if (ids.isEmpty()) return
        val appContext = context.applicationContext
        val snapshot = ids.toSet()
        scope.launch {
            withContext(Dispatchers.IO) {
                Library.removeAll(appContext, snapshot)
            }
            appState.libraryTick++
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
        val appContext = context.applicationContext
        scope.launch {
            val configs = withContext(Dispatchers.IO) {
                SourceManager.remove(appContext, config.id)
                SourceManager.list(appContext)
            }
            appState.configs = configs
        }
    }

    fun refreshExtensions() {
        scope.launch {
            appState.extensionSources = withContext(Dispatchers.IO) {
                runCatching {
                    SourceManager.listAllSources(context)
                        .filter { it.id.isExtensionSourceId() }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun deleteHistory(entry: HistoryEntry) {
        val appContext = context.applicationContext
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                History.remove(appContext, entry.chapterKey)
                History.forDisplay(appContext)
            }
            appState.history = refreshed
        }
    }

    fun clearHistory() {
        val appContext = context.applicationContext
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                History.clear(appContext)
                History.forDisplay(appContext)
            }
            appState.history = refreshed
        }
    }

    fun refreshHistory() {
        val appContext = context.applicationContext
        scope.launch {
            appState.history = withContext(Dispatchers.IO) {
                History.forDisplay(appContext)
            }
        }
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
        val appContext = context.applicationContext
        scope.launch {
            val configs = withContext(Dispatchers.IO) {
                SourceManager.upsert(appContext, config)
                SourceManager.list(appContext)
            }
            appState.configs = configs
            dismissSourceDialog()
        }
    }

}
