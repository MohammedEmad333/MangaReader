package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class MigrationNavigationState {
    var from by mutableStateOf<MigrateFrom?>(null)
    var target by mutableStateOf<Pair<Source, Series>?>(null)

    fun clear() {
        from = null
        target = null
    }
}
