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
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- prefs: the same store every other object in this package uses ----------

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

/** Stable per-chapter key: source id + chapter id. Drives resume, read flags and history. */
private fun chapterKeyOf(sourceId: String, chapter: Chapter): String = "$sourceId|${chapter.id}"

private fun savedPage(context: Context, key: String): Int =
    prefs(context).getInt("pos:$key", 0)

private fun savePage(context: Context, key: String, page: Int) {
    prefs(context).edit().putInt("pos:$key", page).apply()
}

private fun isIncognito(context: Context): Boolean =
    prefs(context).getBoolean("incognito", false)

/** Everything needed to jump straight back into a chapter from a history row. */
private class ResumeTarget(
    val source: Source,
    val series: Series,
    val chapters: List<Chapter>,
    val index: Int,
    val pages: List<File>
)

/**
 * Walks the exact same path ExtensionManager.loadInstalledSources takes, but reports
 * every step instead of swallowing failures into printStackTrace(). Diagnostic only.
 */

private fun diagnoseExtensions(context: Context): String =
    ExtensionLoader.diagnose(context)

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SourceManager.migrateLegacy(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YomuApp()
                }
            }
        }
    }
}

// ---------- root ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(0) }
    var configs by remember { mutableStateOf(SourceManager.list(context)) }
    var extensionSources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var history by remember { mutableStateOf(History.list(context)) }

    var showSourceDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<SourceConfig?>(null) }

    // navigation state
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSource by remember { mutableStateOf<Source?>(null) }
    var seriesList by remember { mutableStateOf<List<Series>?>(null) }
    var activeSeries by remember { mutableStateOf<Series?>(null) }
    var chapterList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var activeChapterIdx by remember { mutableStateOf<Int?>(null) }
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // bumped whenever a read flag / resume position changes, to re-read prefs in lists
    var readTick by remember { mutableIntStateOf(0) }

    // Re-scan installed extensions every time the app comes back to the foreground,
    // so returning from the system installer picks up the new package. Fires on
    // first launch too, which is why this replaces the old one-shot LaunchedEffect.
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    extensionSources = withContext(Dispatchers.IO) {
                        runCatching { ExtensionManager.loadInstalledSources(context) }
                            .getOrDefault(emptyList())
                    }
                }
            }
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    fun openSource(source: Source) {
        activeSourceId = source.id
        activeSource = source
        seriesList = null
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                seriesList = withContext(Dispatchers.IO) { source.listSeries() }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not scan this source"
                seriesList = emptyList()
            }
            isLoading = false
        }
    }

    fun openSourceConfig(config: SourceConfig) {
        val built = SourceManager.build(context, config)
        if (built == null) {
            errorMessage = "\"${config.label}\" isn't configured yet"
            return
        }
        openSource(built)
    }

    fun openSeries(series: Series) {
        val src = activeSource ?: return
        activeSeries = series
        chapterList = emptyList()
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) { src.listChapters(series) }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not list chapters"
            }
            isLoading = false
        }
    }

    fun openChapter(index: Int) {
        val src = activeSource ?: return
        val chapter = chapterList.getOrNull(index) ?: return
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val loaded = withContext(Dispatchers.IO) { src.loadPages(chapter) }
                pages = loaded
                activeChapterIdx = index
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not open this chapter"
            }
            isLoading = false
        }
    }

    fun openFromHistory(entry: HistoryEntry) {
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val target = withContext(Dispatchers.IO) {
                    val src = SourceManager.listAllSources(context)
                        .firstOrNull { it.id == entry.sourceId }
                        ?: throw IllegalStateException("That source no longer exists")
                    val series = src.listSeries().firstOrNull { it.id == entry.seriesId }
                        ?: throw IllegalStateException("That series is no longer in the library")
                    val chapters = src.listChapters(series)
                    val idx = chapters.indexOfFirst {
                        chapterKeyOf(entry.sourceId, it) == entry.chapterKey
                    }
                    if (idx < 0) throw IllegalStateException("That chapter is gone")
                    ResumeTarget(src, series, chapters, idx, src.loadPages(chapters[idx]))
                }
                activeSource = target.source
                activeSourceId = target.source.id
                activeSeries = target.series
                chapterList = target.chapters
                pages = target.pages
                activeChapterIdx = target.index
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not resume"
            }
            isLoading = false
        }
    }

    // ---- routing ----

    val chapterIdx = activeChapterIdx
    val readerChapter = chapterIdx?.let { chapterList.getOrNull(it) }

    if (chapterIdx != null && readerChapter != null && pages.isNotEmpty()) {
        val srcId = activeSourceId ?: ""
        val series = activeSeries
        val chKey = chapterKeyOf(srcId, readerChapter)
        val total = pages.size

        // key() rebuilds the pager state when the chapter changes
        key(chKey) {
            ReaderScreen(
                pages = pages,
                initialPage = savedPage(context, chKey).coerceIn(0, total - 1),
                hasPrev = chapterIdx > 0,
                hasNext = chapterIdx < chapterList.size - 1,
                onPrev = { openChapter(chapterIdx - 1) },
                onNext = { openChapter(chapterIdx + 1) },
                onProgress = { page ->
                    savePage(context, chKey, page)
                    if (page >= total - 1) ReadState.setRead(context, chKey, true)
                    if (!isIncognito(context)) {
                        History.touch(
                            context,
                            HistoryEntry(
                                chapterKey = chKey,
                                title = listOfNotNull(series?.title, readerChapter.name)
                                    .joinToString(" · "),
                                sourceId = srcId,
                                seriesId = series?.id ?: "",
                                coverPath = when (val c = series?.cover) {
                                    is java.io.File -> c.absolutePath
                                    is String -> c
                                    else -> ""
                                },
                                page = page,
                                total = total,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onClose = {
                    activeChapterIdx = null
                    pages = emptyList()
                    history = History.list(context)
                    readTick++
                }
            )
        }
    } else if (activeSeries != null) {
        SeriesScreen(
            series = activeSeries!!,
            chapters = chapterList,
            sourceId = activeSourceId ?: "",
            loading = isLoading,
            error = errorMessage,
            readTick = readTick,
            onOpen = { openChapter(it) },
            onToggleRead = { chapter ->
                val k = chapterKeyOf(activeSourceId ?: "", chapter)
                ReadState.setRead(context, k, !ReadState.isRead(context, k))
                readTick++
            },
            onBack = {
                activeSeries = null
                chapterList = emptyList()
                errorMessage = null
            }
        )
    } else if (activeSource != null) {
        LibraryScreen(
            title = activeSource!!.name,
            series = seriesList,
            loading = isLoading,
            error = errorMessage,
            onRescan = { activeSource?.let { openSource(it) } },
            onOpen = { openSeries(it) },
            onBack = {
                activeSource = null
                activeSourceId = null
                seriesList = null
                errorMessage = null
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        label = { Text("Sources") },
                        icon = { Text("📚") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            currentTab = 1
                            history = History.list(context)
                        },
                        label = { Text("History") },
                        icon = { Text("🕒") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        label = { Text("More") },
                        icon = { Text("⚙️") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    0 -> SourcesTab(
                        configs = configs,
                        extensions = extensionSources,
                        error = errorMessage,
                        onAdd = {
                            editingConfig = SourceConfig(SourceManager.newId(), "local", "")
                            showSourceDialog = true
                        },
                        onOpenConfig = { openSourceConfig(it) },
                        onOpenExtension = { openSource(it) },
                        onEdit = {
                            editingConfig = it
                            showSourceDialog = true
                        },
                        onDelete = {
                            SourceManager.remove(context, it.id)
                            configs = SourceManager.list(context)
                        },
                        onExtensionsChanged = {
                            scope.launch {
                                extensionSources = withContext(Dispatchers.IO) {
                                    runCatching { ExtensionManager.loadInstalledSources(context) }
                                        .getOrDefault(emptyList())
                                }
                            }
                        }
                    )
                    1 -> HistoryScreen(
                        history = history,
                        loading = isLoading,
                        error = errorMessage,
                        onOpen = { openFromHistory(it) },
                        onDelete = {
                            History.remove(context, it.chapterKey)
                            history = History.list(context)
                        },
                        onClearAll = {
                            History.list(context).forEach { History.remove(context, it.chapterKey) }
                            history = History.list(context)
                        }
                    )
                    2 -> MoreTab()
                }
            }
        }
    }

    val editing = editingConfig
    if (showSourceDialog && editing != null) {
        SourceDialog(
            value = editing,
            onChange = { editingConfig = it },
            onDismiss = {
                showSourceDialog = false
                editingConfig = null
            },
            onSave = { saved ->
                SourceManager.upsert(context, saved)
                configs = SourceManager.list(context)
                showSourceDialog = false
                editingConfig = null
            }
        )
    }
}

// ---------- shared ----------

@Composable
fun CoverImage(cover: Any?, title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ---------- sources tab ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesTab(
    configs: List<SourceConfig>,
    extensions: List<Source>,
    error: String?,
    onAdd: () -> Unit,
    onOpenConfig: (SourceConfig) -> Unit,
    onOpenExtension: (Source) -> Unit,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit,
    onExtensionsChanged: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My sources") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Extensions") })
        }
        ErrorBanner(error)

        if (tab == 0) {
            if (configs.isEmpty() && extensions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No sources yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd) { Text("Add a source") }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(configs) { cfg ->
                        var menuOpen by remember(cfg.id) { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(cfg.label.ifBlank { typeLabel(cfg.type) }) },
                            supportingContent = {
                                Text(
                                    if (cfg.isConfigured) typeLabel(cfg.type)
                                    else typeLabel(cfg.type) + " — not configured"
                                )
                            },
                            modifier = Modifier.clickable { onOpenConfig(cfg) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = { menuOpen = false; onEdit(cfg) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = { menuOpen = false; onDelete(cfg) }
                                        )
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }

                    if (extensions.isNotEmpty()) {
                        item {
                            Text(
                                "Installed extensions",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(extensions) { src ->
                            ListItem(
                                headlineContent = { Text(src.name) },
                                supportingContent = { Text("Extension") },
                                modifier = Modifier.clickable { onOpenExtension(src) }
                            )
                            HorizontalDivider()
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
                            ) { Text("Add a source") }
                        }
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
}

// ---------- extensions ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionsScreen(modifier: Modifier = Modifier, onInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repos by remember { mutableStateOf(ExtensionRepos.list(context)) }
    var available by remember { mutableStateOf<List<Extension>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var newRepo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newRepo,
                onValueChange = { newRepo = it },
                label = { Text("Repo index URL") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = newRepo.isNotBlank(),
                onClick = {
                    ExtensionRepos.add(context, newRepo.trim())
                    repos = ExtensionRepos.list(context)
                    newRepo = ""
                }
            ) { Text("Add") }
        }

        repos.forEach { url ->
            ListItem(
                headlineContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    TextButton(onClick = {
                        ExtensionRepos.remove(context, url)
                        repos = ExtensionRepos.list(context)
                    }) { Text("Remove") }
                }
            )
        }

        HorizontalDivider()
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)

        TextButton(
            onClick = { report = diagnoseExtensions(context) },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("Why isn't my extension showing?") }

        if (repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Add a repository URL to browse extensions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(available) { ext ->
                    ListItem(
                        headlineContent = { Text(ext.name) },
                        supportingContent = { Text("v${ext.versionName} · ${ext.pkgName}") },
                        trailingContent = {
                            if (ext.isInstalled) {
                                Text(
                                    "Installed",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        ExtensionManager.install(context, ext)
                                        onInstalled()
                                    }
                                }) { Text("Install") }
                            }
                        }
                    )
                    HorizontalDivider()
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

// ---------- library ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    title: String,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
    onRescan: () -> Unit,
    onOpen: (Series) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val coverMinDp = when (prefs(context).getString("cover_size", "medium")) {
        "small" -> 88.dp
        "large" -> 140.dp
        else -> 110.dp
    }

    val categories = remember { Categories.list(context) }
    var activeCategory by remember { mutableStateOf<String?>(null) }

    val shown = remember(series, activeCategory) {
        val all = series ?: emptyList()
        val cat = activeCategory
        if (cat == null) all
        else all.filter { Categories.categoriesFor(context, it.id).contains(cat) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            actions = { TextButton(onClick = onRescan) { Text("Rescan") } }
        )
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeCategory == null,
                    onClick = { activeCategory = null },
                    label = { Text("All") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = activeCategory == cat.id,
                        onClick = { activeCategory = cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!loading) {
                    Text(
                        if (series.isNullOrEmpty()) "Nothing found in this source."
                        else "No series in this category.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = coverMinDp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(shown) { s ->
                    Column(
                        modifier = Modifier
                            .padding(6.dp)
                            .clickable { onOpen(s) }
                    ) {
                        CoverImage(
                            cover = s.cover,
                            title = s.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                        )
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- series ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesScreen(
    series: Series,
    chapters: List<Chapter>,
    sourceId: String,
    loading: Boolean,
    error: String?,
    readTick: Int,
    onOpen: (Int) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var showCategories by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.padding(end = 8.dp)) { Text("←") }
            CoverImage(
                cover = series.cover,
                title = series.title,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(0.7f)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(series.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    "${chapters.size} chapters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showCategories = true }) { Text("Tags") }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(chapters) { index, ch ->
                val key = chapterKeyOf(sourceId, ch)
                val read = remember(key, readTick) { ReadState.isRead(context, key) }
                val resume = remember(key, readTick) { savedPage(context, key) }
                ListItem(
                    headlineContent = {
                        Text(
                            ch.name,
                            color = if (read) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        if (read) {
                            Text("Read")
                        } else if (resume > 0) {
                            Text("Page ${resume + 1}", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { onToggleRead(ch) }) {
                            Text(if (read) "Unread" else "Read")
                        }
                    },
                    modifier = Modifier.clickable { onOpen(index) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showCategories) {
        CategoryAssignDialog(seriesId = series.id, onDismiss = { showCategories = false })
    }
}

@Composable
private fun CategoryAssignDialog(seriesId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = remember { Categories.list(context) }
    var selected by remember { mutableStateOf(Categories.categoriesFor(context, seriesId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (all.isEmpty()) {
                Text("No categories yet — create some under More → Categories.")
            } else {
                Column {
                    all.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected =
                                        if (selected.contains(cat.id)) selected - cat.id
                                        else selected + cat.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(cat.id),
                                onCheckedChange = {
                                    selected = if (it) selected + cat.id else selected - cat.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Categories.setCategoriesFor(context, seriesId, selected)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- reader ----------

@Composable
private fun ReaderScreen(
    pages: List<File>,
    initialPage: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { onProgress(pagerState.currentPage) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showControls = !showControls })
                }
        ) { page ->
            val file = pages.getOrNull(page)
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) { Text("Close") }
                    Text(
                        "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        TextButton(onClick = onPrev, enabled = hasPrev) { Text("Prev") }
                        TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
                    }
                }
            }
        }
    }
}

// ---------- history ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    history: List<HistoryEntry>,
    loading: Boolean,
    error: String?,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)
        HorizontalDivider()

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing read yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    ListItem(
                        leadingContent = {
                            CoverImage(
                                cover = entry.coverPath
                                    .takeIf { it.isNotBlank() }
                                    ?.let { File(it) },
                                title = entry.title,
                                modifier = Modifier
                                    .width(40.dp)
                                    .aspectRatio(0.7f)
                            )
                        },
                        headlineContent = {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                if (entry.total > 0) "Page ${entry.page + 1} of ${entry.total}"
                                else "Page ${entry.page + 1}"
                            )
                        },
                        modifier = Modifier.clickable { onOpen(entry) },
                        trailingContent = {
                            TextButton(onClick = { onDelete(entry) }) { Text("Remove") }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------- more ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreTab() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }
    var coverSize by remember {
        mutableStateOf(prefs(context).getString("cover_size", "medium") ?: "medium")
    }
    var showCategories by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        ListItem(
            headlineContent = { Text("Incognito mode") },
            supportingContent = { Text("Pause reading-history logging") },
            trailingContent = {
                Switch(
                    checked = incognito,
                    onCheckedChange = { checked ->
                        incognito = checked
                        prefs(context).edit().putBoolean("incognito", checked).apply()
                    }
                )
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Cover size") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small", "medium", "large").forEach { size ->
                        FilterChip(
                            selected = coverSize == size,
                            onClick = {
                                coverSize = size
                                prefs(context).edit().putString("cover_size", size).apply()
                            },
                            label = { Text(size.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Create and delete library categories") },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("About Yomu") },
            supportingContent = { Text("Native Kotlin manga reader") }
        )
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = { showCategories = false })
    }
}

@Composable
private fun CategoryManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var cats by remember { mutableStateOf(Categories.list(context)) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                cats.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            Categories.remove(context, cat.id)
                            cats = Categories.list(context)
                        }) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            Categories.add(context, newName.trim())
                            cats = Categories.list(context)
                            newName = ""
                        }
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

// ---------- source dialog ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDialog(
    value: SourceConfig,
    onChange: (SourceConfig) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SourceConfig) -> Unit
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onChange(value.copy(treeUri = uri.toString()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("local", "komga").forEach { t ->
                        FilterChip(
                            selected = value.type == t,
                            onClick = { onChange(value.copy(type = t)) },
                            label = { Text(typeLabel(t)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = value.label,
                    onValueChange = { onChange(value.copy(label = it)) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.type == "local") {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (value.treeUri.isNotBlank()) "Change folder" else "Choose folder")
                    }
                    if (value.treeUri.isNotBlank()) {
                        Text(
                            Uri.decode(value.treeUri).substringAfterLast(':'),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = value.url,
                        onValueChange = { onChange(value.copy(url = it)) },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.10:25600") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.user,
                        onValueChange = { onChange(value.copy(user = it)) },
                        label = { Text("Username / email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.pass,
                        onValueChange = { onChange(value.copy(pass = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = value.isConfigured,
                onClick = { onSave(value.copy(label = value.label.ifBlank { typeLabel(value.type) })) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
