package com.example.yomu

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import coil.compose.AsyncImage

// ---- Data Models ----

data class Source(val id: String, val name: String, val type: String)
data class SourceConfig(
    val id: String = "",
    val label: String = "",
    val type: String = "local",
    val treeUri: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null
)
data class Series(val id: String, val title: String, val cover: String?)
data class Chapter(val id: String, val name: String)
data class Book(val pages: List<String>)
data class HistoryEntry(val id: String, val title: String, val page: Int, val total: Int)
private data class HistoryPayload(
    val activeSrc: Source,
    val ser: Series,
    val chs: List<Chapter>,
    val idx: Int
)

// ---- Helper Functions ----

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("yomu_prefs", Context.MODE_PRIVATE)

private fun typeLabel(type: String): String = when (type.lowercase()) {
    "local" -> "Local Storage"
    "komga" -> "Komga Server"
    else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

// ---- Activity & Root Application ----

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

@Composable
fun YomuApp() {
    var currentTab by remember { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf(listOf<Source>()) }
    var configs by remember { mutableStateOf(listOf<SourceConfig>()) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf(SourceConfig()) }
    
    // Navigation states
    var activeSource by remember { mutableStateOf<Source?>(null) }
    var activeSeries by remember { mutableStateOf<Series?>(null) }
    var seriesList by remember { mutableStateOf<List<Series>?>(null) }
    var chapterList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var activeChapterIdx by remember { mutableStateOf<Int?>(null) }
    var currentBook by remember { mutableStateOf<Book?>(null) }
    var history by remember { mutableStateOf(listOf<HistoryEntry>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Root Navigation Layout
    if (activeChapterIdx != null && currentBook != null) {
        ReaderScreen(
            book = currentBook!!,
            initialPage = 0,
            hasPrev = activeChapterIdx!! > 0,
            hasNext = activeChapterIdx!! < chapterList.size - 1,
            onPrev = { activeChapterIdx = activeChapterIdx!! - 1 },
            onNext = { activeChapterIdx = activeChapterIdx!! + 1 },
            onProgress = { page ->
                // Handle progress tracking
            },
            onClose = { activeChapterIdx = null; currentBook = null }
        )
    } else if (activeSeries != null) {
        SeriesScreen(
            series = activeSeries!!,
            chapters = chapterList,
            progressFor = { 0 },
            onOpen = { idx ->
                activeChapterIdx = idx
                // Load book placeholder
                currentBook = Book(pages = listOf("https://via.placeholder.com/600x800"))
            },
            onBack = { activeSeries = null }
        )
    } else if (activeSource != null) {
        LibraryScreen(
            title = activeSource!!.name,
            series = seriesList,
            loading = isLoading,
            error = errorMessage,
            onRescan = { /* Trigger rescan */ },
            onOpen = { ser ->
                activeSeries = ser
                chapterList = listOf(Chapter("1", "Chapter 1"), Chapter("2", "Chapter 2"))
            },
            onBack = { activeSource = null }
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
                        onClick = { currentTab = 1 },
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
                    0 -> SourcesManagerScreen(
                        sources = sources,
                        configs = configs,
                        onAdd = {
                            editingConfig = SourceConfig()
                            showSourceDialog = true
                        },
                        onOpen = { src ->
                            activeSource = src
                            seriesList = listOf(Series("1", "Sample Manga", null))
                        },
                        onEdit = { cfg ->
                            editingConfig = cfg
                            showSourceDialog = true
                        }
                    )
                    1 -> HistoryScreen(
                        history = history,
                        onOpen = { /* Open history item */ },
                        onDelete = { entry -> history = history.filterNot { it.id == entry.id } },
                        onClearAll = { history = emptyList() }
                    )
                    2 -> MoreTab()
                }
            }
        }
    }

    if (showSourceDialog) {
        SourceDialog(
            value = editingConfig,
            onChange = { editingConfig = it },
            onPickFolder = { /* Folder picker trigger */ },
            onDismiss = { showSourceDialog = false },
            onSave = {
                val newSource = Source(
                    id = editingConfig.id.ifBlank { System.currentTimeMillis().toString() },
                    name = editingConfig.label.ifBlank { "New Source" },
                    type = editingConfig.type
                )
                sources = sources + newSource
                configs = configs + editingConfig.copy(id = newSource.id)
                showSourceDialog = false
            }
        )
    }
}

// ---- Shared Composables ----

@Composable
fun CoverImage(cover: String?, title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
fun SourcesManagerScreen(
    sources: List<Source>,
    configs: List<SourceConfig>,
    onAdd: () -> Unit,
    onOpen: (Source) -> Unit,
    onEdit: (SourceConfig) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("My Sources") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Extensions") }
            )
        }

        if (selectedTabIndex == 0) {
            if (sources.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No sources added yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAdd) {
                        Text("Add Source")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(sources) { src ->
                        val cfg = configs.find { it.id == src.id }
                        ListItem(
                            headlineContent = { Text(src.name) },
                            supportingContent = {
                                Text(
                                    cfg?.label?.ifBlank { typeLabel(cfg.type) }
                                        ?: typeLabel(src.type)
                                )
                            },
                            modifier = Modifier.clickable { onOpen(src) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (cfg != null) {
                                        IconButton(onClick = { onEdit(cfg) }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Edit Source"
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Extensions Catalog",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onAdd) {
                        Text("Configure New Source")
                    }
                }
            }
        }
    }
}

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("←") }
            },
            actions = {
                TextButton(onClick = onRescan) { Text("Refresh") }
            }
        )
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (series == null && !loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No series found")
            }
        } else if (series != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = coverMinDp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(series) { s ->
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

@Composable
private fun SeriesScreen(
    series: Series,
    chapters: List<Chapter>,
    progressFor: (Chapter) -> Int,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
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
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(series.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${chapters.size} chapters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(chapters) { index, ch ->
                val progress = progressFor(ch)
                ListItem(
                    headlineContent = { Text(ch.name) },
                    supportingContent = {
                        if (progress > 0) {
                            Text("Page ${progress + 1}", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable { onOpen(index) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderScreen(
    book: Book,
    initialPage: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val pagerState = rememberPagerState(initialPage = initialPage) { book.pages.size }
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        onProgress(pagerState.currentPage)
    }

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
                    awaitEachGesture {
                        awaitFirstDown()
                        showControls = !showControls
                    }
                }
        ) { page ->
            val file = book.pages.getOrNull(page)
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
                modifier = Modifier.fillMaxWidth(),
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
                        text = "${pagerState.currentPage + 1} / ${book.pages.size}",
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

@Composable
private fun HistoryScreen(
    history: List<HistoryEntry>,
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
                TextButton(onClick = onClearAll) { Text("Clear All") }
            }
        }
        HorizontalDivider()
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No reading history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text("Page ${entry.page + 1} of ${entry.total}") },
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

@Composable
private fun MoreTab() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        ListItem(
            headlineContent = { Text("Incognito Mode") },
            supportingContent = { Text("Pause reading history logging") },
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
            headlineContent = { Text("About Yomu") },
            supportingContent = { Text("Version 1.0.0 — Modern Jetpack Compose Reader") }
        )
    }
}

@Composable
private fun SourceDialog(
    value: SourceConfig,
    onChange: (SourceConfig) -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (value.type == "local") "Local Source" else "Configure Komga") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value.label,
                    onValueChange = { onChange(value.copy(label = it)) },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.type == "local") {
                    OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                        Text(if (value.treeUri != null) "Change Folder" else "Select Folder")
                    }
                } else if (value.type == "komga") {
                    OutlinedTextField(
                        value = value.url ?: "",
                        onValueChange = { onChange(value.copy(url = it)) },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.X:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.username ?: "",
                        onValueChange = { onChange(value.copy(username = it)) },
                        label = { Text("Username / Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.password ?: "",
                        onValueChange = { onChange(value.copy(password = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
