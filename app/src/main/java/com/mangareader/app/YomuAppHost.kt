package com.mangareader.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YomuAppHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startup = rememberStartupUiSnapshot(context)

    // A responsive first frame is better than a blank/frozen one while Android
    // parses a large SharedPreferences file. Nothing below needs to exist until
    // the persisted root state is ready.
    if (startup == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val state = remember(startup) { YomuStateBundle(startup) }
    val appState = state.app

    val ui = rememberRootUiBindings(
        context = context,
        initialLibraryCategory = startup.libraryCategory,
        releaseNotes = startup.releaseNotes,
    )

    val activity = context as? ComponentActivity
    val launchedFromUpdatesNotification = remember(activity, startup) {
        activity?.intent?.getBooleanExtra(ChapterUpdateNotifier.EXTRA_OPEN_UPDATES, false) == true
    }

    // Application startup no longer restores the queue on the main thread.
    // Once the IO snapshot has restored it, resume exactly the same work the
    // old MainActivity.onCreate path resumed.
    LaunchedEffect(startup) {
        if (DownloadQueue.items.isNotEmpty() && !DownloadQueue.paused) {
            DownloadService.start(context)
        }
    }

    // Secondary root tabs return to Home. Overlay/reader handlers take priority.
    DoubleBackToExitHandler(
        activity = activity,
        currentTab = appState.currentTab,
        onHome = { appState.currentTab = 0 },
    )

    DownloadQueueIntentHandler(
        activity = activity,
        onOpenQueue = {
            appState.currentTab = 3
            appState.downloadsOpen = true
        },
        onOpenUpdates = { state.openChapterUpdatesFromNotification(context) },
    )
    ExtensionResumeObserver(
        activity = activity,
        onSourcesChanged = { appState.extensionSources = it }
    )

    val actions = remember(context, scope, state) {
        AppActionController(
            context = context,
            scope = scope,
            state = state
        )
    }

    // Restore a previous reading session through the existing history resolver.
    // History may be absent or an extension may have been removed; in that
    // case leave the normal home screen available instead of failing startup.
    LaunchedEffect(state, actions, launchedFromUpdatesNotification) {
        if (launchedFromUpdatesNotification) return@LaunchedEffect
        val checkpoint = ReaderSessionCheckpointStore.load(context)
        val entry = checkpoint?.let { findRestorableReaderHistory(it, startup.history) }
        if (entry != null && state.reader.chapterIndex == null) {
            actions.openFromHistory(entry)
        }
    }

    YomuRootRouter(
        state = state,
        actions = actions,
        ui = ui
    )
}
