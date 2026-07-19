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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    var libraryUri by remember {
        mutableStateOf(prefs(context).getString("library_uri", null)?.let(Uri::parse))
    }
    var activeSource by remember {
        mutableStateOf(prefs(context).getString("active_source", "local") ?: "local")
    }
    var komgaTick by remember { mutableStateOf(0) }
    var showKomgaDialog by remember { mutableStateOf(false) }

    val source: Source? = remember(libraryUri, activeSource, komgaTick) {
        if (activeSource == "komga") {
            val p = prefs(context)
            val url = p.getString("komga_url", "") ?: ""
            if (url.isBlank()) null
            else KomgaSource(
                baseUrl = url,
                user = p.getString("komga_user", "") ?: "",
                pass = p.getString("komga_pass", "") ?: "",
                cacheDir = context.cacheDir
            )
        } else {
            libraryUri?.let { LocalSource(context, it) }
        }
    }

    var seriesList by remember { mutableStateOf<List<Series>?>(null) }
    var scanTick by remember { mutableStateOf(0) }
    var openSeries by remember { mutableStateOf<Series?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var chapterIndex by remember { mutableStateOf(0) }
    var book by remember { mutableStateOf<Book?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun switchSource(target: String) {
        activeSource = target
        prefs(context).edit().putString("active_source", target).apply()
        seriesList = null
        openSeries = null
        chapters = emptyList()
        error = null
    }

    fun openChapterAt(list: List<Chapter>, index: Int) {
        loading = true
        error = null
        scope.launch {
            val ch = list[index]
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val src = source
                    if (src != null) src.loadPages(ch)
                    else extractPages(context, (ch.handle as? Uri) ?: Uri.parse(ch.id))
                }
            }
            loading = false
            result.onSuccess { pages ->
                chapters = list
                chapterIndex = index
                book = Book(fileName = ch.name, key = ch.id, pages = pages)
            }.onFailure { error = it.message ?: "Failed to open chapter" }
        }
    }

    fun openSeriesAt(s: Series) {
        val src = source ?: return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { src.listChapters(s) }
            }
            loading = false
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    error = "No chapters found in this series"
                } else {
                    chapters = list
                    openSeries = s
                    if (list.size == 1) openChapterAt(list, 0)
                }
            }.onFailure { error = it.message ?: "Failed to load chapters" }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "file"
            val ch = Chapter(
                id = uri.toString(),
                name = name.substringBeforeLast('.'),
                handle = uri
            )
            openSeries = null
            openChapterAt(listOf(ch), 0)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs(context).edit().putString("library_uri", uri.toString()).apply()
            seriesList = null
            openSeries = null
            libraryUri = uri
            if (activeSource != "local") switchSource("local")
        }
    }

    LaunchedEffect(source, scanTick) {
        val src = source
        if (src != null) {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching { src.listSeries() }
            }
            loading = false
            result.onSuccess { seriesList = it }
                .onFailure {
                    seriesList = emptyList()
                    error = it.message ?: "Failed to load library"
                }
        }
    }

    if (showKomgaDialog) {
        KomgaDialog(
            context = context,
            onDismiss = { showKomgaDialog = false },
            onSaved = {
                showKomgaDialog = false
                komgaTick++
                switchSource("komga")
            }
        )
    }

    val currentBook = book
    if (currentBook != null) {
        val saved = prefs(context)
            .getInt("pos:" + currentBook.key, 0)
            .coerceIn(0, currentBook.pages.size - 1)
        key(currentBook.key) {
            ReaderScreen(
                book = currentBook,
                initialPage = saved,
                hasPrev = chapterIndex > 0,
                hasNext = chapterIndex < chapters.size - 1,
                onPrev = { openChapterAt(chapters, chapterIndex - 1) },
                onNext = { openChapterAt(chapters, chapterIndex + 1) },
                onProgress = { p ->
                    prefs(context).edit().putInt("pos:" + currentBook.key, p).apply()
                },
                onClose = {
                    book = null
                    if (chapters.size <= 1) openSeries = null
                }
            )
        }
    } else if (openSeries != null) {
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
    } else if (source == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Manga Reader", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("v0.8 — two sources, one seam", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { folderPicker.launch(null) }) {
                Text("Choose library folder")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = { showKomgaDialog = true }) {
                Text("Connect Komga server")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Text("Open single CBZ")
            }
            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            val e = error
            if (e != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(e, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.name + " — " + (seriesList?.size ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { scanTick++ },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⟳") }
                if (activeSource == "local") {
                    TextButton(
                        onClick = { folderPicker.launch(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Folder") }
                    TextButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("File") }
                    TextButton(
                        onClick = {
                            val url = prefs(context).getString("komga_url", "") ?: ""
                            if (url.isBlank()) showKomgaDialog = true
                            else switchSource("komga")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Komga") }
                } else {
                    TextButton(
                        onClick = { showKomgaDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("⚙") }
                    TextButton(
                        onClick = { switchSource("local") },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Local") }
                }
            }
            val e = error
            if (e != null) {
                Text(
                    e,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (loading && seriesList == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else if (seriesList.isNullOrEmpty()) {
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
                    items(seriesList.orEmpty()) { s ->
                        Column(
                            modifier = Modifier
                                .padding(6.dp)
                                .clickable { openSeriesAt(s) }
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
}

@Composable
private fun KomgaDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val p = prefs(context)
    var url by remember { mutableStateOf(p.getString("komga_url", "") ?: "") }
    var user by remember { mutableStateOf(p.getString("komga_user", "") ?: "") }
    var pass by remember { mutableStateOf(p.getString("komga_pass", "") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Komga server") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL (http://192.168…:25600)") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Email") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                p.edit()
                    .putString("komga_url", url.trim())
                    .putString("komga_user", user.trim())
                    .putString("komga_pass", pass)
                    .apply()
                onSaved()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = rtl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { index ->
                    ZoomableAsyncImage(
                        model = book.pages[index],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { showBar = !showBar }
                    )
                }
            }
            if (showBar && (hasPrev || hasNext)) {
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
