package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private data class Book(
    val fileName: String,
    val key: String,
    val pages: List<File>
)

private fun prefs(context: Context) =
    context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { SourceManager.migrateLegacy(context) }

    var sources by remember { mutableStateOf(SourceManager.list(context)) }
    fun refreshSources() { sources = SourceManager.list(context) }

    var activeConfig by remember { mutableStateOf<SourceConfig?>(null) }
    fun activeSource(): Source? = activeConfig?.let { SourceManager.build(context, it) }

    var seriesList by remember { mutableStateOf<List<Series>?>(null) }
    var scanTick by remember { mutableStateOf(0) }
    var openSeries by remember { mutableStateOf<Series?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var chapterIndex by remember { mutableStateOf(0) }
    var book by remember { mutableStateOf<Book?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // continue-reading state
    var readingSeries by remember { mutableStateOf<Series?>(null) }
    var readingConfigId by remember { mutableStateOf("") }
    var currentHistory by remember { mutableStateOf<HistoryEntry?>(null) }
    var historyState by remember { mutableStateOf(History.list(context)) }

    // dialog / add-flow state
    var showTypeChooser by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SourceConfig?>(null) }
    var folderPickTarget by remember { mutableStateOf<String?>(null) } // "new" | config id | null

    fun openSingleFile(uri: Uri) {
        loading = true
        error = null
        scope.launch {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "file"
            val result = withContext(Dispatchers.IO) { runCatching { extractPages(context, uri) } }
            loading = false
            result.onSuccess { pages ->
                readingSeries = null
                readingConfigId = ""
                chapters = emptyList()
                chapterIndex = 0
                val key = uri.toString()
                val title = name.substringBeforeLast('.')
                val initial = prefs(context).getInt("pos:" + key, 0).coerceIn(0, pages.size - 1)
                val entry = HistoryEntry(
                    key, title, "", "", "", initial, pages.size, System.currentTimeMillis()
                )
                currentHistory = entry
                History.touch(context, entry)
                book = Book(fileName = title, key = key, pages = pages)
            }.onFailure { error = it.message ?: "Failed to open file" }
        }
    }

    fun openChapterAt(list: List<Chapter>, index: Int) {
        loading = true
        error = null
        scope.launch {
            val ch = list[index]
            val src = activeSource()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (src != null) src.loadPages(ch)
                    else throw IllegalStateException("No source selected")
                }
            }
            loading = false
            result.onSuccess { pages ->
                chapters = list
                chapterIndex = index
                val initial = prefs(context).getInt("pos:" + ch.id, 0).coerceIn(0, pages.size - 1)
                val title = readingSeries?.let { it.title + " — " + ch.name } ?: ch.name
                val cover = readingSeries?.cover?.absolutePath ?: ""
                val entry = HistoryEntry(
                    ch.id, title, readingConfigId, readingSeries?.id ?: "",
                    cover, initial, pages.size, System.currentTimeMillis()
                )
                currentHistory = entry
                History.touch(context, entry)
                book = Book(fileName = ch.name, key = ch.id, pages = pages)
            }.onFailure { error = it.message ?: "Failed to open chapter" }
        }
    }

    fun openSeriesAt(s: Series) {
        val src = activeSource() ?: return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { src.listChapters(s) } }
            loading = false
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    error = "No chapters found in this series"
                } else {
                    readingSeries = s
                    readingConfigId = activeConfig?.id ?: ""
                    chapters = list
                    openSeries = s
                    if (list.size == 1) openChapterAt(list, 0)
                }
            }.onFailure { error = it.message ?: "Failed to load chapters" }
        }
    }

    fun openFromHistory(entry: HistoryEntry) {
        if (entry.sourceId.isBlank()) {
            openSingleFile(Uri.parse(entry.chapterKey))
            return
        }
        val cfg = SourceManager.list(context).find { it.id == entry.sourceId }
        if (cfg == null) { error = "That source was removed"; return }
        val src = SourceManager.build(context, cfg)
        if (src == null) { error = "Source not configured"; return }
        loading = true
        error = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val ser = src.listSeries().find { it.id == entry.seriesId }
                        ?: throw IllegalStateException("Series no longer found")
                    val chs = src.listChapters(ser)
                    val idx = chs.indexOfFirst { it.id == entry.chapterKey }
                    if (idx < 0) throw IllegalStateException("Chapter no longer found")
                    Triple(ser, chs, idx)
                }
            }
            loading = false
            res.onSuccess { (ser, chs, idx) ->
                activeConfig = cfg
                readingSeries = ser
                readingConfigId = cfg.id
                openChapterAt(chs, idx)
            }.onFailure { error = it.message ?: "Couldn't reopen" }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) openSingleFile(uri) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val target = folderPickTarget
        folderPickTarget = null
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Local folder"
            if (target == "new") {
                SourceManager.upsert(
                    context,
                    SourceConfig(SourceManager.newId(), "local", name, treeUri = uri.toString())
                )
                refreshSources()
            } else if (target != null) {
                // editing an existing local source's folder
                editing = editing?.copy(treeUri = uri.toString())
            }
        }
    }

    LaunchedEffect(book, openSeries, activeConfig) {
        if (book == null && openSeries == null && activeConfig == null) {
            historyState = History.list(context)
        }
    }

    // ---- open library scan ----
    LaunchedEffect(activeConfig?.id, scanTick) {
        val src = activeSource()
        if (src != null) {
            loading = true
            val result = withContext(Dispatchers.IO) { runCatching { src.listSeries() } }
            loading = false
            result.onSuccess { seriesList = it }
                .onFailure {
                    seriesList = emptyList()
                    error = it.message ?: "Failed to load library"
                }
        }
    }

    // ---- config dialog ----
    val edit = editing
    if (edit != null) {
        SourceDialog(
            value = edit,
            onChange = { editing = it },
            onPickFolder = {
                folderPickTarget = edit.id
                folderPicker.launch(null)
            },
            onDismiss = { editing = null },
            onSave = {
                SourceManager.upsert(context, edit)
                refreshSources()
                editing = null
            }
        )
    }

    if (showTypeChooser) {
        AlertDialog(
            onDismissRequest = { showTypeChooser = false },
            title = { Text("Add a source") },
            text = { Text("Local folder reads CBZ/ZIP on this device. Komga connects to a self-hosted server.") },
            confirmButton = {
                TextButton(onClick = {
                    showTypeChooser = false
                    folderPickTarget = "new"
                    folderPicker.launch(null)
                }) { Text("Local folder") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTypeChooser = false
                    editing = SourceConfig(SourceManager.newId(), "komga", "Komga")
                }) { Text("Komga server") }
            }
        )
    }

    // ---- navigation ----
    val currentBook = book
    when {
        currentBook != null -> {
            val saved = prefs(context)
                .getInt("pos:" + currentBook.key, 0)
                .coerceIn(0, currentBook.pages.size - 1)
            key(currentBook.key) {
                ReaderScreen(
                    book = currentBook,
                    initialPage = saved,
                    hasPrev = chapters.isNotEmpty() && chapterIndex > 0,
                    hasNext = chapters.isNotEmpty() && chapterIndex < chapters.size - 1,
                    onPrev = { openChapterAt(chapters, chapterIndex - 1) },
                    onNext = { openChapterAt(chapters, chapterIndex + 1) },
                    onProgress = { p ->
                        prefs(context).edit().putInt("pos:" + currentBook.key, p).apply()
                        currentHistory?.let {
                            val e = it.copy(page = p, updatedAt = System.currentTimeMillis())
                            currentHistory = e
                            History.touch(context, e)
                        }
                    },
                    onClose = {
                        book = null
                        if (chapters.size <= 1) openSeries = null
                    }
                )
            }
        }

        openSeries != null -> {
            SeriesScreen(
                series = openSeries!!,
                chapters = chapters,
                progressFor = { ch -> prefs(context).getInt("pos:" + ch.id, 0) },
                onOpen = { i -> openChapterAt(chapters, i) },
                onBack = {
                    openSeries = null
                    chapters = emptyList()
                }
            )
        }

        activeConfig != null -> {
            LibraryScreen(
                title = activeConfig!!.label,
                series = seriesList,
                loading = loading,
                error = error,
                onRescan = { scanTick++ },
                onOpen = { s -> openSeriesAt(s) },
                onBack = {
                    activeConfig = null
                    seriesList = null
                    error = null
                }
            )
        }

        else -> {
            SourcesManagerScreen(
                sources = sources,
                loading = loading,
                error = error,
                history = historyState,
                onOpenHistory = { openFromHistory(it) },
                onAdd = { showTypeChooser = true },
                onOpen = { cfg ->
                    if (cfg.isConfigured) {
                        error = null
                        seriesList = null
                        activeConfig = cfg
                    } else {
                        editing = cfg
                    }
                },
                onEdit = { cfg ->
                    editing = cfg
                },
                onDelete = { cfg ->
                    SourceManager.remove(context, cfg.id)
                    refreshSources()
                },
                onOpenFile = { filePicker.launch(arrayOf("*/*")) }
            )
        }
    }
}

@Composable
private fun SourcesManagerScreen(
    sources: List<SourceConfig>,
    loading: Boolean,
    error: String?,
    history: List<HistoryEntry>,
    onOpenHistory: (HistoryEntry) -> Unit,
    onAdd: () -> Unit,
    onOpen: (SourceConfig) -> Unit,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit,
    onOpenFile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yomu — Sources",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenFile) { Text("Open file") }
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (history.isNotEmpty()) {
            Text(
                "Continue reading",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(history) { h ->
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .padding(end = 10.dp)
                            .clickable { onOpenHistory(h) }
                    ) {
                        if (h.coverPath.isNotBlank()) {
                            AsyncImage(
                                model = File(h.coverPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Text(
                            h.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "p. " + (h.page + 1) + " / " + h.total,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        if (sources.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No sources yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Add a local folder of CBZ files, or connect a Komga server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(sources) { cfg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                            .clickable { onOpen(cfg) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cfg.label.ifBlank { typeLabel(cfg.type) },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when (cfg.type) {
                                    "komga" -> cfg.url.ifBlank { "not configured" }
                                    else -> typeLabel(cfg.type)
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { onEdit(cfg) },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text("Edit") }
                            TextButton(
                                onClick = { onDelete(cfg) },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) { Text("+  Add source") }
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
        title = { Text(typeLabel(value.type)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value.label,
                    onValueChange = { onChange(value.copy(label = it)) },
                    label = { Text("Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (value.type == "komga") {
                    OutlinedTextField(
                        value = value.url,
                        onValueChange = { onChange(value.copy(url = it)) },
                        label = { Text("Server URL (http://192.168…:25600)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value.user,
                        onValueChange = { onChange(value.copy(user = it)) },
                        label = { Text("Email") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value.pass,
                        onValueChange = { onChange(value.copy(pass = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                } else {
                    Text(
                        text = if (value.treeUri.isBlank()) "No folder chosen"
                        else "Folder set — tap to change",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = onPickFolder) { Text("Choose folder") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = value.isConfigured) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("←") }
            Text(
                text = title + " — " + (series?.size ?: 0),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRescan,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("⟳") }
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        if (loading && series == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (series.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) { Text("Nothing here yet — check the source settings") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(series) { s ->
                    Column(
                        modifier = Modifier
                            .padding(6.dp)
                            .clickable { onOpen(s) }
                    ) {
                        AsyncImage(
                            model = s.cover,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("←") }
            Text(
                text = series.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = chapters.size.toString() + " ch",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            itemsIndexed(chapters) { i, ch ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = ch.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val p = progressFor(ch)
                    if (p > 0) {
                        Text(
                            text = "resume at page " + (p + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webtoon by remember { mutableStateOf(prefs(context).getBoolean("mode_webtoon", false)) }
    var rtl by remember { mutableStateOf(prefs(context).getBoolean("mode_rtl", false)) }
    var showBar by remember { mutableStateOf(true) }
    var pendingJump by remember { mutableStateOf(-1) }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { book.pages.size }
    )
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage
    )
    val currentIndex =
        if (webtoon) listState.firstVisibleItemIndex
        else pagerState.currentPage

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler { onClose() }

    LaunchedEffect(currentIndex) { onProgress(currentIndex) }

    LaunchedEffect(webtoon) {
        if (pendingJump >= 0) {
            if (webtoon) listState.scrollToItem(pendingJump)
            else pagerState.scrollToPage(pendingJump)
            pendingJump = -1
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClose,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("✕") }
                    Text(
                        text = book.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = (currentIndex + 1).toString() + " / " + book.pages.size,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!webtoon) {
                        TextButton(
                            onClick = {
                                rtl = !rtl
                                prefs(context).edit().putBoolean("mode_rtl", rtl).apply()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text(if (rtl) "RTL" else "LTR") }
                    }
                    TextButton(
                        onClick = {
                            pendingJump = currentIndex
                            webtoon = !webtoon
                            prefs(context).edit().putBoolean("mode_webtoon", webtoon).apply()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text(if (webtoon) "Paged" else "Webtoon") }
                }
            }
            if (webtoon) {
                var wtScale by remember { mutableStateOf(1f) }
                var wtOffsetX by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        wtScale = (wtScale * zoom).coerceIn(1f, 3f)
                                        val maxOff = (wtScale - 1f) * size.width / 2f
                                        wtOffsetX = (wtOffsetX + pan.x).coerceIn(-maxOff, maxOff)
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = wtScale
                                scaleY = wtScale
                                translationX = wtOffsetX
                            }
                    ) {
                        items(book.pages) { file ->
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showBar = !showBar },
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            } else {
                var pagerWidth by remember { mutableStateOf(0) }
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = rtl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { pagerWidth = it.width }
                ) { index ->
                    ZoomableAsyncImage(
                        model = book.pages[index],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { offset ->
                            val w = pagerWidth
                            if (w <= 0) {
                                showBar = !showBar
                            } else {
                                val leftZone = offset.x < w / 3f
                                val rightZone = offset.x > w * 2f / 3f
                                val advance = if (rtl) leftZone else rightZone
                                val back = if (rtl) rightZone else leftZone
                                when {
                                    advance && currentIndex < book.pages.size - 1 ->
                                        scope.launch { pagerState.animateScrollToPage(currentIndex + 1) }
                                    back && currentIndex > 0 ->
                                        scope.launch { pagerState.animateScrollToPage(currentIndex - 1) }
                                    !leftZone && !rightZone -> showBar = !showBar
                                    else -> { /* boundary edge tap: ignore */ }
                                }
                            }
                        }
                    )
                }
            }
            if (showBar) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (book.pages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (currentIndex + 1).toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = currentIndex.toFloat()
                                    .coerceIn(0f, (book.pages.size - 1).toFloat()),
                                onValueChange = { v ->
                                    val target = v.toInt().coerceIn(0, book.pages.size - 1)
                                    scope.launch {
                                        if (webtoon) listState.scrollToItem(target)
                                        else pagerState.scrollToPage(target)
                                    }
                                },
                                valueRange = 0f..(book.pages.size - 1).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                book.pages.size.toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (hasPrev || hasNext) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onPrev, enabled = hasPrev) { Text("◀ Prev") }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = onNext, enabled = hasNext) { Text("Next ▶") }
                        }
                    }
                }
            }
        }
    }
}
