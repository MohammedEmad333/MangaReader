package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibrarySettings() {
    val context = LocalContext.current
    var showCategories by remember { mutableStateOf(false) }
    var categoryTick by remember { mutableIntStateOf(0) }
    val sizes = listOf("small", "medium", "large")
    var coverSize by remember {
        mutableStateOf(prefs(context).getString("cover_size", "medium") ?: "medium")
    }
    val counts by produceState<Pair<Int, Int>?>(initialValue = null, categoryTick, showCategories) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            Categories.list(appContext).size to Library.list(appContext).size
        }
    }
    val categoryCount = counts?.first
    val entryCount = counts?.second

    SettingsColumn {
        SectionHeader("Display")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Cover size",
                options = sizes.map { it.replaceFirstChar { c -> c.uppercase() } },
                selected = sizes.indexOf(coverSize).coerceAtLeast(0),
                onSelect = {
                    coverSize = sizes[it]
                    prefs(context).edit().putString("cover_size", sizes[it]).apply()
                },
            )
        }
        PrefNote(
            "Applies to per-source browsing. The library grid has its own " +
                "columns setting, under Display in the library's options sheet."
        )

        SectionHeader("Categories")
        SettingsActionCard(
            title = "Edit categories",
            summary = when (categoryCount) {
                null -> "Loading…"
                0 -> "None yet"
                1 -> "1 category"
                else -> "$categoryCount categories"
            },
            onClick = { showCategories = true },
        )
        SettingsActionCard(
            title = "Saved series",
            summary = entryCount?.let { "$it in the library" } ?: "Loading…",
        )

        LibraryRefreshSettingsSection()
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = {
            showCategories = false
            categoryTick++
        })
    }
}
