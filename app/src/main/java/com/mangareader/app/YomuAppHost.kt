package com.mangareader.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
    DoubleBackToExitHandler(activity)

    DownloadQueueIntentHandler(
        activity = activity,
        onOpenQueue = {
            appState.currentTab = 3
            appState.downloadsOpen = true
        }
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

    YomuRootRouter(
        state = state,
        actions = actions,
        ui = ui
    )
}
