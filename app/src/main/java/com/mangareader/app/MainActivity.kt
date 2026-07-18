package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

private data class LibBook(
    val name: String,
    val uri: Uri,
    val cover: File?
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
    var books by remember { mutableStateOf<List<LibBook>?>(null) }
    var scanTick by remember { mutableStateOf(0) }
    var book by remember { mutableStateOf<Book?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun openBookUri(uri: Uri) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { extractCbz(context, uri) }
            }
            loading = false
            result
                .onSuccess { book = it }
                .onFailure { error = it.message ?: "Failed to open file" }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) openBookUri(uri)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs(context).edit().putString("library_uri", uri.toString()).apply()
            books = null
            libraryUri = uri
        }
    }

    LaunchedEffect(libraryUri, scanTick) {
        val lib = libraryUri
        if (lib != null) {
            loading = true
            books = withContext(Dispatchers.IO) {
                runCatching { scanLibrary(context, lib) }.getOrDefault(emptyList())
            }
            loading = false
        }
    }

    val current = book
    if (current != null) {
        val saved = prefs(context)
            .getInt("pos:" + current.key, 0)
            .coerceIn(0, current.pages.size - 1)
        ReaderScreen(
            book = current,
            initialPage = saved,
            onProgress = { p ->
                prefs(context).edit().putInt("pos:" + current.key, p).apply()
            },
            onClose = { book = null }
        )
    } else if (libraryUri == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Manga Reader", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("v0.6 — immersive reading polish", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { folderPicker.launch(null) }) {
                Text("Choose library folder")
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
                    text = "Library — " + (books?.size ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { scanTick++ },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⟳") }
                TextButton(
                    onClick = { folderPicker.launch(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Folder") }
                TextButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("File") }
            }
            val e = error
            if (e != null) {
                Text(
                    e,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (loading && books == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else if (books.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) { Text("No .cbz / .zip files found in this folder") }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    items(books.orEmpty()) { b ->
                        Column(
                            modifier = Modifier
                                .padding(6.dp)
                                .clickable { openBookUri(b.uri) }
                        ) {
                            AsyncImage(
                                model = b.cover,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = b.name,
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
private fun ReaderScreen(
    book: Book,
    initialPage: Int,
    onProgress: (Int) -> Unit,
    onClose: () -> Unit
) {
    var webtoon by remember { mutableStateOf(false) }
    var rtl by remember { mutableStateOf(false) }
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
                            onClick = { rtl = !rtl },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text(if (rtl) "RTL" else "LTR") }
                    }
                    TextButton(
                        onClick = {
                            pendingJump = currentIndex
                            webtoon = !webtoon
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
        }
    }
}

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

private fun isImageEntry(name: String): Boolean {
    val lower = name.lowercase()
    val base = lower.substringAfterLast('/')
    if (base.startsWith(".") || lower.startsWith("__macosx")) return false
    return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
}

private val digitRegex = Regex("\\d+")

private fun naturalSortKey(name: String): String =
    digitRegex.replace(name.lowercase()) { it.value.padStart(8, '0') }

private fun scanLibrary(context: Context, treeUri: Uri): List<LibBook> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val found = mutableListOf<DocumentFile>()
    fun walk(dir: DocumentFile) {
        for (f in dir.listFiles()) {
            if (f.isDirectory) {
                walk(f)
            } else {
                val n = (f.name ?: "").lowercase()
                if (n.endsWith(".cbz") || n.endsWith(".zip")) found.add(f)
            }
        }
    }
    walk(root)
    return found
        .sortedBy { naturalSortKey(it.name ?: "") }
        .map { d ->
            LibBook(
                name = d.name ?: "book",
                uri = d.uri,
                cover = ensureCover(context, d.uri)
            )
        }
}

private fun ensureCover(context: Context, bookUri: Uri): File? {
    val coversDir = File(context.cacheDir, "covers")
    coversDir.mkdirs()
    val coverFile = File(coversDir, bookUri.toString().hashCode().toString() + ".jpg")
    if (coverFile.exists()) return coverFile
    try {
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(bookUri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageEntry(entry.name)) names.add(entry.name)
                    entry = zip.nextEntry
                }
            }
        }
        val target = names.minByOrNull { naturalSortKey(it) } ?: return null
        var bmp: Bitmap? = null
        context.contentResolver.openInputStream(bookUri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == target) {
                        bmp = BitmapFactory.decodeStream(zip)
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val full = bmp ?: return null
        val targetW = 300
        val small = if (full.width > targetW) {
            val h = (full.height.toFloat() * targetW / full.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(full, targetW, h, true)
        } else full
        coverFile.outputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (small !== full) full.recycle()
        return coverFile
    } catch (e: Exception) {
        return null
    }
}

private fun extractCbz(context: Context, uri: Uri): Book {
    val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "book.cbz"

    val pagesDir = File(context.cacheDir, "current_book")
    pagesDir.deleteRecursively()
    pagesDir.mkdirs()

    val tmp = File(context.cacheDir, "current.cbz")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { out -> input.copyTo(out) }
    } ?: throw IllegalStateException("Cannot open the selected file")

    val pages = mutableListOf<File>()
    ZipFile(tmp).use { zip ->
        val entries = zip.entries().toList()
            .filter { !it.isDirectory && isImageEntry(it.name) }
            .sortedBy { naturalSortKey(it.name) }
        if (entries.isEmpty()) {
            throw IllegalStateException("No images found — is this a CBZ/ZIP of pages?")
        }
        entries.forEachIndexed { index, entry ->
            val ext = entry.name.substringAfterLast('.', "jpg")
            val outFile = File(pagesDir, "page_" + index.toString().padStart(4, '0') + "." + ext)
            zip.getInputStream(entry).use { ins ->
                outFile.outputStream().use { outs -> ins.copyTo(outs) }
            }
            pages.add(outFile)
        }
    }
    tmp.delete()

    return Book(fileName = fileName, key = uri.toString(), pages = pages)
}
