package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persisted state needed before the root router is useful.
 *
 * The app historically constructed AppUiState, GlobalSearchState and
 * RootUiBindings directly from SharedPreferences during the first composition.
 * The first SharedPreferences access loads and parses the whole XML file, so a
 * large imported profile could block the UI before the first interactive frame.
 *
 * Load the small startup snapshot on IO instead. The host renders a lightweight
 * progress indicator until it is ready, then constructs the normal state graph
 * from plain in-memory values.
 */
internal data class StartupUiSnapshot(
    val configs: List<SourceConfig>,
    val history: List<HistoryEntry>,
    val searchPinnedOnly: Boolean,
    val recentSearches: List<String>,
    val libraryCategory: String?,
    val releaseNotes: List<ReleaseNote>,
)

@Composable
internal fun rememberStartupUiSnapshot(context: Context): StartupUiSnapshot? {
    val appContext = context.applicationContext
    val snapshot by produceState<StartupUiSnapshot?>(initialValue = null, appContext) {
        value = withContext(Dispatchers.IO) {
            // Both used to run synchronously before setContent. They touch disk
            // and, for an existing queue, can probe download markers/storage.
            // Restore them before exposing the snapshot so no user action can
            // race with a late queue/source migration.
            SourceManager.migrateLegacy(appContext)
            DownloadQueue.restore(appContext)

            StartupUiSnapshot(
                configs = SourceManager.list(appContext),
                history = History.forDisplay(appContext),
                searchPinnedOnly = SourcePrefs.pinnedOnlySearch(appContext),
                recentSearches = SourcePrefs.recentSearches(appContext),
                libraryCategory = LibraryPrefs.lastCategory(appContext),
                releaseNotes = WhatsNew.pending(appContext),
            )
        }
    }
    return snapshot
}
