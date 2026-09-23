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
                onBack = root.actions::closeDownloadQueue
            )
        }

        appState.settingsOpen -> {
            SettingsScreen(
                onBack = root.actions::closeSettings,
                onOpenDownloadQueue = root.actions::openDownloadQueue
            )
        }

        else -> {
            RootMainTabsRoute(root)
        }
    }
}
