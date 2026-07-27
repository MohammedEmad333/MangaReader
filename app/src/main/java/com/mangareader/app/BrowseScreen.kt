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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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

// ---------- prefs: the same store every other object in this package uses ----------

/**
 * One row in the Sources list. Local folders and extension sources render the
 * same way, so they're flattened into this before the list is built; `config`
 * is non-null only for local folders, which is what gates the Edit/Delete menu.
 */
internal data class BrowseRow(
    val id: String,
    val name: String,
    val lang: String,
    val iconPkg: String?,
    val isNsfw: Boolean,
    val configurable: Boolean,
    val config: SourceConfig?,
    val source: Source?
)

/** Local folders first, multi-language sources next, then languages A-Z. */
internal fun langRank(group: String): Int = when (group) {
    "Local" -> 0
    "Multi" -> 1
    else -> 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseSourceRow(
    row: BrowseRow,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onEditConfig: ((SourceConfig) -> Unit)? = null,
    onDeleteConfig: ((SourceConfig) -> Unit)? = null,
    onOpenSettings: ((Source) -> Unit)? = null
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    // Only local folders carry a config, and only they get the Edit/Delete menu.
    val cfg = row.config
    ListItem(
        leadingContent = { SourceIcon(row.iconPkg, row.name) },
        headlineContent = { Text(row.name) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (row.lang.isNotBlank()) Text(row.lang)
                if (row.isNsfw) NsfwBadge()
            }
        },
        modifier = Modifier.clickable { onOpen() },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val src = row.source
                if (row.configurable && src != null) {
                    IconButton(onClick = { onOpenSettings?.invoke(src) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Source settings")
                    }
                }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (pinned) "Unpin" else "Pin",
                        // Filled vs dimmed rather than filled vs outlined: the
                        // outlined variants live in material-icons-extended and
                        // this module only pulls in material-icons-core.
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                if (cfg != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            if (onEditConfig != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { menuOpen = false; onEditConfig?.invoke(cfg) }
                                )
                            }
                            if (onDeleteConfig != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menuOpen = false; onDeleteConfig?.invoke(cfg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Which sources appear in the Sources list.
 *
 * Grouped by language, with a switch per language and a checkbox per source, the
 * way Mihon does it. Both stores hold what's switched *off*, so a source added by
 * a new extension shows up without anyone having to enable it.
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
    var disabledLangs by remember { mutableStateOf(SourcePrefs.disabledLangs(context)) }

    val groups = remember(rows) {
        rows.groupBy { it.lang.ifBlank { "Other" } }
            .toList()
            .sortedBy { it.first.lowercase() }
            .sortedBy { langRank(it.first) }
    }
    val allIds = remember(rows) { rows.map { it.id } }
    val allShown = hidden.isEmpty() && disabledLangs.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sources") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("All sources") },
                    supportingContent = {
                        Text("${rows.count { it.id !in hidden && it.lang !in disabledLangs }} of ${rows.size} shown")
                    },
                    trailingContent = {
                        Switch(
                            checked = allShown,
                            onCheckedChange = { on ->
                                hidden = SourcePrefs.setSourcesHidden(context, allIds, !on)
                                if (on) {
                                    disabledLangs.toList().forEach {
                                        disabledLangs = SourcePrefs.toggleLangDisabled(context, it)
                                    }
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
            }

            groups.forEach { (lang, sources) ->
                val langOff = lang in disabledLangs
                item {
                    ListItem(
                        headlineContent = {
                            Text(lang, style = MaterialTheme.typography.titleSmall)
                        },
                        trailingContent = {
                            Switch(
                                checked = !langOff,
                                onCheckedChange = {
                                    disabledLangs = SourcePrefs.toggleLangDisabled(context, lang)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseTab(
    configs: List<SourceConfig>,
    extensions: List<Source>,
    onGlobalSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenConfig: (SourceConfig) -> Unit,
    onOpenExtension: (Source) -> Unit,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit,
    onExtensionsChanged: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    // Both re-read from prefs whenever this tab re-enters the composition, which
    // a bottom-nav switch or backing out of a source always causes.
    var pinnedIds by remember { mutableStateOf(SourcePrefs.pinned(context)) }
    val lastUsedId = remember { SourcePrefs.lastUsed(context) }
    var settingsFor by remember { mutableStateOf<Source?>(null) }
    var showSourceFilter by remember { mutableStateOf(false) }
    // Re-read on every entry into the composition, same as the pin set: the
    // filter screen is the only thing that changes them and it lives here.
    var hiddenIds by remember { mutableStateOf(SourcePrefs.hiddenSources(context)) }
    var disabledLangs by remember { mutableStateOf(SourcePrefs.disabledLangs(context)) }

    val rows = remember(configs, extensions) {
        configs.map { cfg ->
            BrowseRow(
                id = cfg.id,
                name = cfg.label.ifBlank { typeLabel(cfg.type) },
                lang = if (cfg.isConfigured) "Local" else "Local \u2014 not configured",
                iconPkg = null,
                isNsfw = false,
                configurable = false,
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
                config = null,
                source = src
            )
        }
    }

    // Everything below works off the visible set; `rows` stays whole so the
    // filter screen can still list what's been switched off.
    val visibleRows = rows.filter {
        SourcePrefs.isVisible(it.id, it.lang.ifBlank { "Other" }, hiddenIds, disabledLangs)
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

    if (showSourceFilter) {
        SourceFilterScreen(
            rows = rows,
            onBack = {
                showSourceFilter = false
                // Pick up whatever was changed in there.
                hiddenIds = SourcePrefs.hiddenSources(context)
                disabledLangs = SourcePrefs.disabledLangs(context)
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
                        Icon(Icons.Default.Menu, contentDescription = "Choose which sources show")
                    }
                }
            }
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Sources") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Extensions") })
        }

        if (tab == 0) {
            val openRow: (BrowseRow) -> Unit = { row ->
                row.config?.let { onOpenConfig(it) }
                row.source?.let { onOpenExtension(it) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (lastUsedRow != null) {
                    item { SectionHeader("Last used") }
                    item {
                        BrowseSourceRow(
                            row = lastUsedRow,
                            pinned = lastUsedRow.id in pinnedIds,
                            onOpen = { openRow(lastUsedRow) },
                            onTogglePin = {
                                pinnedIds = SourcePrefs.togglePin(context, lastUsedRow.id)
                            },
                            onEditConfig = onEdit,
                            onDeleteConfig = onDelete,
                            onOpenSettings = { settingsFor = it }
                        )
                    }
                }

                if (pinnedRows.isNotEmpty()) {
                    item { SectionHeader("Pinned") }
                    items(pinnedRows) { row ->
                        BrowseSourceRow(
                            row = row,
                            pinned = true,
                            onOpen = { openRow(row) },
                            onTogglePin = { pinnedIds = SourcePrefs.togglePin(context, row.id) },
                            onEditConfig = onEdit,
                            onDeleteConfig = onDelete,
                            onOpenSettings = { settingsFor = it }
                        )
                    }
                }

                groups.forEach { (lang, rowsInGroup) ->
                    item { SectionHeader(lang) }
                    items(rowsInGroup.sortedBy { it.name.lowercase() }) { row ->
                        BrowseSourceRow(
                            row = row,
                            pinned = false,
                            onOpen = { openRow(row) },
                            onTogglePin = { pinnedIds = SourcePrefs.togglePin(context, row.id) },
                            onEditConfig = onEdit,
                            onDeleteConfig = onDelete,
                            onOpenSettings = { settingsFor = it }
                        )
                    }
                }

                if (visibleRows.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No sources yet. Add a local folder, or install " +
                                    "extensions from the Extensions tab.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAdd,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add a local source") }
                    }
                }
            }
        } else {
            ExtensionsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                onInstalled = onExtensionsChanged
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionsScreen(modifier: Modifier = Modifier, onInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // repos is read-only on this screen now — the editor lives in
    // More → Browse → Extension repos. It's still state because the fetch below
    // keys on it, and it re-reads from prefs whenever this screen re-enters the
    // composition (which a bottom-nav tab switch always causes).
    val repos by remember { mutableStateOf(ExtensionRepos.list(context)) }
    var available by remember { mutableStateOf<List<Extension>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var installedOnly by remember { mutableStateOf(false) }

    // Client-side filter over the already-fetched index: no refetch, no network.
    val shownExtensions = remember(available, filter, installedOnly) {
        val q = filter.trim()
        available.filter { ext ->
            (!installedOnly || ext.isInstalled) &&
                (q.isBlank() ||
                    ext.name.contains(q, ignoreCase = true) ||
                    ext.pkgName.contains(q, ignoreCase = true))
        }
    }

    LaunchedEffect(repos) {
        if (repos.isEmpty()) {
            available = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            available = ExtensionManager.fetchAvailable(context)
            if (available.isEmpty()) error = "No extensions found in the configured repos."
        } catch (e: Exception) {
            error = e.message ?: "Could not reach the repository"
        }
        loading = false
    }

    Column(modifier = modifier) {
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)

        TextButton(
            onClick = { report = diagnoseExtensions(context) },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("Why isn't my extension showing?") }

        if (repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No repositories configured.\nAdd one in More → Browse → Extension repositories.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Search extensions") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotBlank()) {
                        TextButton(onClick = { filter = "" }) { Text("Clear") }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = installedOnly,
                    onClick = { installedOnly = !installedOnly },
                    label = { Text("Installed only") }
                )
                Text(
                    "${shownExtensions.size} of ${available.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (shownExtensions.isEmpty() && !loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (available.isEmpty()) "Nothing in the index yet."
                        else "No extension matches that.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val updatableExts = shownExtensions.filter { it.hasUpdate }
                .sortedBy { it.name.lowercase() }
            val installedExts = shownExtensions.filter { it.isInstalled && !it.hasUpdate }
                .sortedBy { it.name.lowercase() }
            val availableExts = shownExtensions.filterNot { it.isInstalled }
                .sortedBy { it.name.lowercase() }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (updatableExts.isNotEmpty()) {
                    item { SectionHeader("Update available (${updatableExts.size})") }
                    items(updatableExts) { ext ->
                        ExtensionRow(ext) {
                            scope.launch {
                                ExtensionManager.install(context, ext)
                                onInstalled()
                            }
                        }
                    }
                }
                if (installedExts.isNotEmpty()) {
                    item { SectionHeader("Installed") }
                    items(installedExts) { ext ->
                        ExtensionRow(ext) { }
                    }
                }
                if (availableExts.isNotEmpty()) {
                    item { SectionHeader("Available") }
                    items(availableExts) { ext ->
                        ExtensionRow(ext) {
                            scope.launch {
                                ExtensionManager.install(context, ext)
                                onInstalled()
                            }
                        }
                    }
                }
            }
        }
    }

    val shownReport = report
    if (shownReport != null) {
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("Extension diagnostics") },
            text = {
                SelectionContainer {
                    Text(
                        shownReport,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = { Button(onClick = { report = null }) { Text("Close") } }
        )
    }
}

/**
 * One extension in the index. Installed rows pull the real launcher icon from
 * the installed package; rows that aren't installed yet have no package to read
 * one from, so they fall back to initials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionRow(ext: Extension, onInstall: () -> Unit) {
    ListItem(
        leadingContent = {
            SourceIcon(if (ext.isInstalled) ext.pkgName else null, ext.name)
        },
        headlineContent = { Text(ext.name) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    listOf(
                        ext.lang,
                        // Show what an update would move you from and to.
                        if (ext.hasUpdate) "${ext.installedVersion} \u2192 ${ext.versionName}"
                        else ext.versionName
                    ).filter { it.isNotBlank() }.joinToString(" ")
                )
                if (ext.isNsfw) NsfwBadge()
            }
        },
        trailingContent = {
            if (ext.isInstalled && !ext.hasUpdate) {
                Text(
                    "Installed",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                // Updating is the same flow as installing: the system installer
                // treats a higher versionCode on the same package as an upgrade.
                TextButton(onClick = onInstall) {
                    Text(if (ext.hasUpdate) "Update" else "Install")
                }
            }
        }
    )
}

// ---------- global search ----------
