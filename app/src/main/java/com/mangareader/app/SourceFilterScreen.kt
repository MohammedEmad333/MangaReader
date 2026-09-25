package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One row in the Sources list. Local folders and extension sources render the
 * same way, so they're flattened into this before the list is built; `config`
 * is non-null only for local folders, which is what gates the Edit/Delete menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceFilterScreen(
    rows: List<BrowseRow>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(SourcePrefs.hiddenSources(context)) }
    var enabledLangs by remember { mutableStateOf(SourcePrefs.enabledLangs(context)) }
    // Read, not written, here: 18+ is a Settings switch. It still belongs in
    // the count below, or "N of M shown" contradicts the list beside it.
    val showNsfw = remember { SourcePrefs.showNsfw(context) }

    val groups = remember(rows) {
        rows.groupBy { it.lang.ifBlank { "Other" } }
            .toList()
            .sortedBy { it.first.lowercase() }
            .sortedBy { langRank(it.first) }
    }
    val allIds = remember(rows) { rows.map { it.id } }
    val allLangs = remember(rows) { rows.map { it.lang.ifBlank { "Other" } }.distinct() }
    val allShown = hidden.isEmpty() && allLangs.all { it in enabledLangs }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sources") },
            navigationIcon = {
                BackButton(onBack)
            }
        )

        // The list of every installed source, grouped by language with a header
        // and a divider per group — the longest list in Browse and the one card
        // 87 reported. The handle reads its own item count, so the grouping
        // needs no arithmetic here.
        val sourceListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = sourceListState, modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("All sources") },
                    supportingContent = {
                        val shown = rows.count {
                            SourcePrefs.isVisible(
                                it.id, it.lang.ifBlank { "Other" }, it.isNsfw,
                                hidden, enabledLangs, showNsfw
                            )
                        }
                        Text("$shown of ${rows.size} shown")
                    },
                    trailingContent = {
                        Switch(
                            checked = allShown,
                            onCheckedChange = { on ->
                                enabledLangs = SourcePrefs.setLangsEnabled(context, allLangs, on)
                                // Turning everything on also clears individual
                                // hides, or the switch would lie about the count.
                                if (on) {
                                    hidden = SourcePrefs.setSourcesHidden(context, allIds, false)
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
            }

            groups.forEach { (lang, sources) ->
                val langOff = lang !in enabledLangs
                item {
                    ListItem(
                        headlineContent = {
                            Text(lang, style = MaterialTheme.typography.titleSmall)
                        },
                        trailingContent = {
                            Switch(
                                checked = !langOff,
                                onCheckedChange = { on ->
                                    enabledLangs = SourcePrefs.setLangEnabled(context, lang, on)
                                }
                            )
                        }
                    )
                }
                items(sources.sortedBy { it.name.lowercase() }) { row ->
                    val on = row.id !in hidden
                    ListItem(
                        leadingContent = { SourceIcon(row.iconPkg, row.name) },
                        headlineContent = { Text(row.name) },
                        trailingContent = {
                            Checkbox(
                                checked = on && !langOff,
                                // A language switched off greys out its sources
                                // rather than silently rewriting each checkbox.
                                enabled = !langOff,
                                onCheckedChange = {
                                    hidden = SourcePrefs.toggleSourceHidden(context, row.id)
                                }
                            )
                        },
                        modifier = Modifier.clickable(enabled = !langOff) {
                            hidden = SourcePrefs.toggleSourceHidden(context, row.id)
                        }
                    )
                }
                item { HorizontalDivider() }
            }
        }
        ListScrollHandle(
            state = sourceListState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        }
    }
}
