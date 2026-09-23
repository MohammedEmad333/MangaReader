package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class MediaNavigationState {
    var scan by mutableStateOf<VideoScan?>(null)
    var embed by mutableStateOf<Pair<String, String>?>(null)
    var media by mutableStateOf<List<String>?>(null)
    var scanning by mutableStateOf(false)

    fun clearScan() {
        scan = null
        scanning = false
    }

    fun clearPlayer() {
        embed = null
        media = null
    }
}
