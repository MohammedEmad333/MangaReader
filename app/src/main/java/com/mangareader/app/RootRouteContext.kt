package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class RootRouteContext(
    val context: Context,
    val scope: CoroutineScope,
    val state: YomuStateBundle,
    val actions: AppActionController,
    val ui: RootUiBindings
) {
    val app get() = state.app
    val browse get() = state.browse
    val series get() = state.series
    val reader get() = state.reader
    val search get() = state.search
    val migration get() = state.migration
    val media get() = state.media
}
