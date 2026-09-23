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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
// PullToRefreshBox lives in a SUB-PACKAGE of material3. The wildcard above does
// NOT reach it — that is exactly the 0.98 CI failure.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

@Composable
internal fun MoreTab(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }
    var showCategories by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Stays here as well as under Settings > Security and privacy. It's the
        // one preference on this screen that gets turned on for a few chapters and
        // then off again, and a thing used that way shouldn't be three taps deep.
        // Both switches read the same key, and this tab is disposed while Settings
        // is open, so the one here re-reads on the way back rather than going stale.
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Visibility) },
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

        // Reads the queue directly, so it stays live while the service drains it.
        val queued = DownloadQueue.items.size
        val failedDownloads = DownloadQueue.failed.size
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Download) },
            headlineContent = { Text("Download queue") },
            supportingContent = {
                Text(
                    when {
                        failedDownloads > 0 && queued > 0 ->
                            "$queued waiting \u00b7 $failedDownloads failed"
                        failedDownloads > 0 -> "$failedDownloads failed"
                        queued == 0 -> "Nothing queued"
                        DownloadQueue.paused -> "$queued waiting \u00b7 paused"
                        queued == 1 -> "1 chapter downloading"
                        else -> "$queued chapters \u00b7 downloading"
                    }
                )
            },
            modifier = Modifier.clickable { onOpenDownloads() }
        )
        HorizontalDivider()

        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.List) },
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Create and delete library categories") },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()

        // Cover size, extension repositories and the downloaded-chapter totals
        // used to be rows on this screen. They live under Settings now; the two
        // storage rows in particular were walking the whole download tree during
        // composition, on the main thread, every time this tab was opened.
        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Settings) },
            headlineContent = { Text("Settings") },
            supportingContent = { Text("Appearance, reader, downloads, storage, privacy") },
            modifier = Modifier.clickable { onOpenSettings() }
        )
        HorizontalDivider()

        ListItem(
            leadingContent = { MoreIcon(Icons.Filled.Star) },
            headlineContent = { Text("About Yomu") },
            supportingContent = {
                Text("Native Kotlin manga reader \u00b7 ${BuildConfig.VERSION_NAME}")
            }
        )
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = { showCategories = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}
