package com.mangareader.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private data class Book(
    val fileName: String,
    val pages: List<File>
)

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<Book?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
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
    }

    val current = book
    if (current != null) {
        ReaderScreen(book = current, onClose = { book = null })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Manga Reader", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("v0.3 — pick a .cbz and read it", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Open CBZ")
                }
            }
            val e = error
            if (e != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(e, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReaderScreen(book: Book, onClose: () -> Unit) {
    var webtoon by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { book.pages.size })
    val listState = rememberLazyListState()
    val currentPage =
        if (webtoon) listState.firstVisibleItemIndex + 1
        else pagerState.currentPage + 1

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) { Text("Close") }
            Text(
                text = book.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$currentPage / ${book.pages.size}",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { webtoon = !webtoon }) {
                Text(if (webtoon) "Paged" else "Webtoon")
            }
        }
        if (webtoon) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(book.pages) { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                AsyncImage(
                    model = book.pages[index],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
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

    return Book(fileName = fileName, pages = pages)
}
