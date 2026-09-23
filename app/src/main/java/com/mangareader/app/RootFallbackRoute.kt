package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootFallbackRoute(
    root: RootRouteContext
) {
    val appState = root.app
    when {
        appState.downloadsOpen -> {
            DownloadQueueScreen(
                onBack = { appState.downloadsOpen = false }
            )
        }

        appState.settingsOpen -> {
            SettingsScreen(
                onBack = { appState.settingsOpen = false },
                onOpenDownloadQueue = {
                    appState.downloadsOpen = true
                }
            )
        }

        else -> {
            RootMainTabsRoute(root)
        }
    }
}
