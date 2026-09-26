package com.mangareader.app

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                }
            )
        }
        PrefNote(
            "Applies to per-source browsing. The library grid has its own "
                + "columns setting, under Display in the library's options sheet."
        )

        SectionHeader("Categories")
        ListItem(
            headlineContent = { Text("Edit categories") },
            supportingContent = {
                Text(
                    when (categoryCount) {
                        null -> "Loading…"
                        0 -> "None yet"
                        1 -> "1 category"
                        else -> "$categoryCount categories"
                    }
                )
            },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Saved series") },
            supportingContent = {
                Text(entryCount?.let { "$it in the library" } ?: "Loading…")
            }
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
