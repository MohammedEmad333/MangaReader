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
    var filter by rememberSaveable { mutableStateOf("") }
    var installedOnly by rememberSaveable { mutableStateOf(false) }
    var mediaFilter by rememberSaveable { mutableStateOf("All") }

    // Installing and uninstalling both finish in the system's UI, in another
    // process, so this screen can't be told when they're done — the only signal
    // it gets is the user coming back. Set on the way out, read on the next
    // resume, so an ordinary resume (unlocking the phone, switching back to the
    // app) doesn't refetch the index for nothing.
    var awaitingPackageChange by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Uninstall goes through a launcher rather than startActivity so the system
    // dialog stays in this task and this screen is told when it closes. The
    // check afterwards is the point: a refused or cancelled uninstall is
    // otherwise indistinguishable from a successful one, which is how the first
    // attempt at this shipped — tap, flicker, package still there, no idea why.
    var pendingUninstall by remember { mutableStateOf<String?>(null) }
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pkg = pendingUninstall
        pendingUninstall = null
        refreshTick++
        if (pkg != null && ExtensionManager.isInstalled(context, pkg)) {
            report = "$pkg is still installed.\n\nThe uninstall was either " +
                "cancelled, or refused by the system. If no dialog appeared at " +
                "all, this build is missing the REQUEST_DELETE_PACKAGES " +
                "permission, or the ROM blocks app-initiated uninstalls."
        }
    }
    val startUninstall: (String) -> Unit = { pkg ->
        pendingUninstall = pkg
        val launched = runCatching {
            uninstallLauncher.launch(ExtensionManager.uninstallIntent(pkg))
        }
        launched.onFailure {
            pendingUninstall = null
            report = "Couldn't open the uninstaller: ${it.message}"
        }
    }

    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPackageChange) {
                awaitingPackageChange = false
                refreshTick++
            }
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    // Client-side filter over the already-fetched index: no refetch, no network.
    val shownExtensions = remember(available, filter, installedOnly, mediaFilter) {
        val q = filter.trim()
        // The same switch as the Sources list. An 18+ extension left listed here
        // while its sources are hidden would be a one-tap route back to exactly
        // what was switched off, which makes the setting look broken.
        val showNsfw = SourcePrefs.showNsfw(context)
        available.filter { ext ->
            (showNsfw || !ext.isNsfw) &&
                (!installedOnly || ext.isInstalled) &&
                (mediaFilter == "All" ||
                    (mediaFilter == "Anime" && ext.isAnime) ||
                    (mediaFilter == "Manga" && !ext.isAnime)) &&
                (q.isBlank() ||
                    ext.name.contains(q, ignoreCase = true) ||
                    ext.pkgName.contains(q, ignoreCase = true))
        }
    }

    LaunchedEffect(repos, refreshTick) {
        if (repos.isEmpty()) {
            available = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            available = ExtensionManager.fetchAvailable(context)
            if (available.isEmpty()) error = "No extensions found in the configured repos."
        } catch (e: Throwable) {
            // Throwable: this path classloads extension packages to resolve
            // install state, so it can surface the same LinkageError family a
            // source call can. See sourceFailureMessage.
            error = sourceFailureMessage(e, "Could not reach the repository")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = installedOnly,
                    onClick = { installedOnly = !installedOnly },
                    label = { Text("Installed only") }
                )
                listOf("All", "Manga", "Anime").forEach { label ->
                    FilterChip(
                        selected = mediaFilter == label,
                        onClick = { mediaFilter = label },
                        label = { Text(label) },
                    )
                }
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

            // This caller used to hand-count its items — three optional section
            // headers plus the rows — which was the most conditional version of
            // that arithmetic in the app and the most likely to rot. The handle
            // reads the count off the list now; see ListScrollHandle.
            val extListState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = extListState, modifier = Modifier.fillMaxSize()) {
                if (updatableExts.isNotEmpty()) {
                    item { SectionHeader("Update available (${updatableExts.size})") }
                    items(updatableExts) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = {
                                awaitingPackageChange = true
                                scope.launch {
                                    ExtensionManager.install(context, ext)
                                    onInstalled()
                                }
                            },
                            onUninstall = { startUninstall(ext.pkgName) }
                        )
                    }
                }
                if (installedExts.isNotEmpty()) {
                    item { SectionHeader("Installed") }
                    items(installedExts) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = { },
                            onUninstall = { startUninstall(ext.pkgName) }
                        )
                    }
                }
                if (availableExts.isNotEmpty()) {
                    item { SectionHeader("Available") }
                    items(availableExts) { ext ->
                        ExtensionRow(
                            ext = ext,
                            onInstall = {
                                awaitingPackageChange = true
                                scope.launch {
                                    ExtensionManager.install(context, ext)
                                    onInstalled()
                                }
                            }
                        )
                    }
                }
            }
            ListScrollHandle(
                state = extListState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
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

// ---------- global search ----------
