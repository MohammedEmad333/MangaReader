package com.mangareader.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable

@Composable
internal fun RootOverlays(
    root: RootRouteContext
) {
    val context = root.context
    val appState = root.app
    val browseState = root.browse
    val seriesState = root.series
    val mediaState = root.media
    val actions = root.actions
    val scan = mediaState.scan
    if (mediaState.scanning || scan != null) {
        ChapterVideoDialog(
            scanning = mediaState.scanning,
            scan = scan,
            onDismiss = { mediaState.scan = null },
            onOpenEmbed = { url ->
                val page = seriesState.active?.let { series ->
                    browseState.source?.seriesUrl(series)
                }
                mediaState.scan = null
                mediaState.embed = url to page.orEmpty()
            },
            onOpenVideo = { url ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(view) }
                    .onFailure {
                        appState.error =
                            "No app on this device can play that link"
                    }
            }
        )
    }

    MainOverlayDialogs(
        activeSource = browseState.source,
        probeOpen = appState.probeOpen,
        onDismissProbe = { appState.probeOpen = false },
        filtersOpen = appState.filtersOpen,
        onApplyFilters = { source ->
            appState.filtersOpen = false
            actions.openSource(source, "", BrowseMode.FILTER)
        },
        onDismissFilters = { appState.filtersOpen = false },
        whatsNewOpen = root.ui.whatsNewOpen,
        releaseNotes = root.ui.releaseNotes,
        onDismissWhatsNew = root.ui.onDismissWhatsNew,
        showSourceDialog = appState.showSourceDialog,
        editingConfig = appState.editingConfig,
        onEditingConfigChange = { appState.editingConfig = it },
        onDismissSourceDialog = {
            appState.showSourceDialog = false
            appState.editingConfig = null
        },
        onSaveSource = { saved ->
            SourceManager.upsert(context, saved)
            appState.configs = SourceManager.list(context)
            appState.showSourceDialog = false
            appState.editingConfig = null
        }
    )
}
