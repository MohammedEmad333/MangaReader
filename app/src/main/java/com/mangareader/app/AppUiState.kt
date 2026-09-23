package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppUiState(context: Context) {
    var currentTab by mutableIntStateOf(0)

    var configs by mutableStateOf(SourceManager.list(context))
    var extensionSources by mutableStateOf<List<Source>>(emptyList())
    var history by mutableStateOf(History.forDisplay(context))

    var showSourceDialog by mutableStateOf(false)
    var editingConfig by mutableStateOf<SourceConfig?>(null)

    var filtersOpen by mutableStateOf(false)
    var probeOpen by mutableStateOf(false)

    var downloadTick by mutableIntStateOf(0)
    var downloadsOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var challengeUrl by mutableStateOf<String?>(null)

    var readTick by mutableIntStateOf(0)
    var libraryTick by mutableIntStateOf(0)
}
