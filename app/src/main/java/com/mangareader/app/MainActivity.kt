package com.mangareader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream

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

private data class CbzPreview(
    val fileName: String,
    val pageCount: Int,
    val firstPage: Bitmap
)

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<CbzPreview?>(null) }
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
                    runCatching { loadCbz(context, uri) }
                }
                loading = false
                result
                    .onSuccess { preview = it }
                    .onFailure { error = it.message ?: "Failed to open file" }
            }
        }
    }

    val current = preview
    if (current != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = current.firstPage.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = current.fileName + " — 1 / " + current.pageCount,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Open another")
                }
            }
        }
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
            Text("v0.2 — open a .cbz or .zip of images", style = MaterialTheme.typography.bodyMedium)
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

private fun loadCbz(context: Context, uri: Uri): CbzPreview {
    val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "file.cbz"

    fun open(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open the selected file")

    // Pass 1: list image entries
    val names = mutableListOf<String>()
    ZipInputStream(open()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && isImageEntry(entry.name)) names.add(entry.name)
            entry = zip.nextEntry
        }
    }
    if (names.isEmpty()) throw IllegalStateException("No images found — is this a CBZ/ZIP of pages?")

    val sorted = names.sortedBy { naturalSortKey(it) }
    val target = sorted.first()

    // Pass 2: decode only the first page
    var bitmap: Bitmap? = null
    ZipInputStream(open()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == target) {
                bitmap = BitmapFactory.decodeStream(zip)
                break
            }
            entry = zip.nextEntry
        }
    }
    val page = bitmap ?: throw IllegalStateException("Could not decode the first page")
    return CbzPreview(fileName = fileName, pageCount = sorted.size, firstPage = page)
}
