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
import androidx.compose.runtime.saveable.rememberSaveable
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

// ---------- prefs: the same store every other object in this package uses ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseTab(
    configs: List<SourceConfig>,
    extensions: List<Source>,
    scroll: ScrollMemory,
    onGlobalSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenConfig: (SourceConfig) -> Unit,
    onOpenExtension: (Source) -> Unit,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit,
    onExtensionsChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The pager owns the position and nothing drives it back the other way —
    // see §5, "Two things driving one position will fight". Tabs only ask it to
    // animate; `tab` is read out of it. There is no outward report to make here,
    // so there is no second driver to introduce.
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tab = pagerState.currentPage

    // Both re-read from prefs whenever this tab re-enters the composition, which
    // a bottom-nav switch or backing out of a source always causes.
    var pinnedIds by remember { mutableStateOf(SourcePrefs.pinned(context)) }
    val lastUsedId = remember { SourcePrefs.lastUsed(context) }
    var settingsFor by remember { mutableStateOf<Source?>(null) }
    var showSourceFilter by remember { mutableStateOf(false) }
    var mediaFilter by rememberSaveable { mutableStateOf("All") }
    // Re-read on every entry into the composition, same as the pin set: the
    // filter screen is the only thing that changes them and it lives here.
    var hiddenIds by remember { mutableStateOf(SourcePrefs.hiddenSources(context)) }
    var enabledLangs by remember { mutableStateOf(SourcePrefs.enabledLangs(context)) }
    // Re-read on every re-entry, like pinnedIds above it: a bottom-nav switch
    // always disposes this tab, so changing the switch in Settings and coming
    // back is enough. No invalidation to wire.
    val showNsfw = remember { SourcePrefs.showNsfw(context) }

    val rows = remember(configs, extensions) {
        configs.map { cfg ->
            BrowseRow(
                id = cfg.id,
                name = cfg.label.ifBlank { typeLabel(cfg.type) },
                lang = if (cfg.isConfigured) "Local" else "Local \u2014 not configured",
                iconPkg = null,
                isNsfw = false,
                configurable = false,
                isAnime = false,
                config = cfg,
                source = null
            )
        } + extensions.map { src ->
            BrowseRow(
                id = src.id,
                name = src.name,
                lang = src.lang,
                iconPkg = src.iconPkg,
                isNsfw = src.isNsfw,
                configurable = SourceSettings.isConfigurable(src),
                isAnime = src.isAnime,
                config = null,
                source = src
            )
        }
    }

    // Everything below works off the visible set; `rows` stays whole so the
    // filter screen can still list what's been switched off.
    val visibleRows = rows.filter {
        SourcePrefs.isVisible(
            it.id, it.lang.ifBlank { "Other" }, it.isNsfw, hiddenIds, enabledLangs, showNsfw
        ) && when (mediaFilter) {
            "Anime" -> it.isAnime
            "Manga" -> !it.isAnime
            else -> true
        }
    }

    val lastUsedRow = visibleRows.firstOrNull { it.id == lastUsedId }
    val pinnedRows = visibleRows.filter { it.id in pinnedIds }.sortedBy { it.name.lowercase() }

    // Pinned sources are lifted out of their language group rather than shown in
    // both places, so scrolling the list never shows the same source twice.
    // Two stable sortedBy passes rather than a multi-selector compareBy: same
    // rank-major, name-minor order, without leaning on vararg lambda inference.
    val groups = visibleRows.filterNot { it.id in pinnedIds }
        .groupBy { it.lang.ifBlank { "Other" } }
        .toList()
        .sortedBy { it.first.lowercase() }
        .sortedBy { langRank(it.first) }

    // Opening a source sets `activeSource`, which hands the composition to the
    // per-source browse branch and destroys this whole tab — so the list below
    // loses its position on the way out and lands at the top on the way back.
    // Fifth instance of the hoisting bug in §5.
    //
    // §5 says to enumerate every list under a destructive branch rather than
    // only the reported one, so: `ExtensionsScreen` on page 1 has the same bug
    // and is deliberately not fixed here. Its list is fetched async and is empty
    // on first composition, so a `LazyListState` seeded with a stored index
    // clamps to 0 before the data lands and restores nothing. It needs the state
    // built after the first non-empty list rather than another parameter, which
    // is a different piece of work — and shipping the parameter alone would look
    // fixed while doing nothing.
    //
    // The signature is what the order is actually built from. Pinning, hiding a
    // source or a language, or installing an extension all reorder the list, and
    // a position from before that means nothing afterwards.
    //
    // `lastUsedId` is deliberately NOT in it, and 0.86 shipped with it in and
    // did nothing at all as a result: `openSource` calls
    // `SourcePrefs.setLastUsed`, so opening a source changes it, so the
    // signature changed on exactly the trip this store exists to survive and
    // cleared itself every time. This is the same call the library's `counts`
    // already loses — see §4 — and it goes the same way: what it costs is one
    // row moving between the Last used section and its language group, which is
    // a stale anchor, not the wholesale reorder a position genuinely can't
    // survive.
    val sourcesOrdering = listOf(rows.size, pinnedIds, hiddenIds, enabledLangs)
    scroll.sync(sourcesOrdering)

    if (showSourceFilter) {
        SourceFilterScreen(
            rows = rows,
            onBack = {
                showSourceFilter = false
                // Pick up whatever was changed in there.
                hiddenIds = SourcePrefs.hiddenSources(context)
                enabledLangs = SourcePrefs.enabledLangs(context)
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Browse") },
            actions = {
                // Only on Sources: the Extensions tab has its own filter field.
                if (tab == 0) {
                    IconButton(onClick = onGlobalSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search all sources")
                    }
                    IconButton(onClick = { showSourceFilter = true }) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "Choose which sources show"
                        )
                    }
                }
            }
        )
        TabRow(selectedTabIndex = tab) {
            listOf("Sources", "Extensions").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { scope.launch { pagerState.scrollToPage(index) } },
                    text = { Text(label) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page == 0) {
                BrowseSourcesPage(
                    mediaFilter = mediaFilter,
                    onMediaFilterChange = { mediaFilter = it },
                    lastUsedRow = lastUsedRow,
                    pinnedRows = pinnedRows,
                    groups = groups,
                    visibleRowsEmpty = visibleRows.isEmpty(),
                    pinnedIds = pinnedIds,
                    onTogglePin = { row ->
                        pinnedIds = SourcePrefs.togglePin(context, row.id)
                    },
                    onOpen = { row ->
                        row.config?.let(onOpenConfig)
                        row.source?.let(onOpenExtension)
                    },
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onOpenSettings = { settingsFor = it },
                    onAdd = onAdd,
                    scroll = scroll,
                    ordering = sourcesOrdering,
                )
            } else {
                ExtensionsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onInstalled = onExtensionsChanged
                )
            }
        }
    }

    val settingsSource = settingsFor
    if (settingsSource != null) {
        SourceSettingsDialog(
            source = settingsSource,
            onDismiss = { settingsFor = null }
        )
    }
}
