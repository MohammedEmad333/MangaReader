package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootOverlays(
    root: RootRouteContext
) {
    val appState = root.app
    val browseState = root.browse
    val mediaState = root.media
    val actions = root.actions
    val scan = mediaState.scan
    if (mediaState.scanning || scan != null) {
        ChapterVideoDialog(
            scanning = mediaState.scanning,
            scan = scan,
            isAnime = browseState.source?.isAnime == true,
            onDismiss = actions::dismissVideoScan,
            onOpenEmbed = actions::openEmbed,
            onOpenVideo = actions::openExternalVideo
        )
    }

    MainOverlayDialogs(
        activeSource = browseState.source,
        probeOpen = appState.probeOpen,
        onDismissProbe = actions::dismissProbe,
        filtersOpen = appState.filtersOpen,
        onApplyFilters = actions::applyFilters,
        onDismissFilters = actions::dismissFilters,
        whatsNewOpen = root.ui.whatsNewOpen,
        releaseNotes = root.ui.releaseNotes,
        onDismissWhatsNew = root.ui.onDismissWhatsNew,
        showSourceDialog = appState.showSourceDialog,
        editingConfig = appState.editingConfig,
        onEditingConfigChange = actions::setEditingConfig,
        onDismissSourceDialog = actions::dismissSourceDialog,
        onSaveSource = actions::saveSource
    )
}
