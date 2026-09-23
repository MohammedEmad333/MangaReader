package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun YomuRootRouter(
    state: YomuStateBundle,
    actions: AppActionController,
    ui: RootUiBindings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = RootRouteContext(
        context = context,
        scope = scope,
        state = state,
        actions = actions,
        ui = ui
    )

    when (
        RootTransientRoute(root)
    ) {
        TransientRouteResult.EMBED -> return
        TransientRouteResult.CONTENT -> Unit
        TransientRouteResult.NONE -> {
            if (root.series.active != null) {
                RootSeriesRoute(root)
            } else if (root.search.open) {
                RootGlobalSearchRoute(root)
            } else if (root.browse.source != null) {
                RootSourceBrowseRoute(root)
            } else {
                RootFallbackRoute(root)
            }
        }
    }

    RootOverlays(root)
}
