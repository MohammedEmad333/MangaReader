package com.mangareader.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YomuAppHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state = remember { YomuStateBundle(context) }
    val appState = state.app

    val ui = rememberRootUiBindings(context)

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
