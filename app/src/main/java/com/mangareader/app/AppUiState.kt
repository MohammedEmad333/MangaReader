package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppUiState(
    initialConfigs: List<SourceConfig>,
    initialHistory: List<HistoryEntry>,
) {
    var currentTab by mutableIntStateOf(0)

    var configs by mutableStateOf(initialConfigs)
    var extensionSources by mutableStateOf<List<Source>>(emptyList())
    var history by mutableStateOf(initialHistory)

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
