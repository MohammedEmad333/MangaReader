package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job

internal class MediaNavigationState {
    var scan by mutableStateOf<VideoScan?>(null)
    var embed by mutableStateOf<Pair<String, String>?>(null)
    var media by mutableStateOf<List<String>?>(null)
    var scanning by mutableStateOf(false)
    var scanJob: Job? = null
    private var scanRequestId = 0L

    fun beginScan(): Long {
        scanJob?.cancel()
        scanJob = null
        scan = null
        scanning = true
        scanRequestId += 1
        return scanRequestId
    }

    fun isCurrentScan(requestId: Long): Boolean = requestId == scanRequestId

    fun cancelScan() {
        scanRequestId += 1
        scanJob?.cancel()
        scanJob = null
        clearScan()
    }

    fun clearScan() {
        scan = null
        scanning = false
    }

    fun clearPlayer() {
        embed = null
        media = null
    }
}
