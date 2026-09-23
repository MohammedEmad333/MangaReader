package com.mangareader.app

import android.content.Context

internal class YomuStateBundle(
    context: Context
) {
    val app = AppUiState(context)
    val browse = SourceBrowseState()
    val series = SeriesNavigationState()
    val reader = ReaderSessionState()
    val search = GlobalSearchState(context)
    val migration = MigrationNavigationState()
    val media = MediaNavigationState()
}
