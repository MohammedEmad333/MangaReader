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
internal fun ExtensionsScreen(
    modifier: Modifier = Modifier,
    scroll: ScrollMemory,
    onInstalled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Parse the saved repository list off the UI thread before starting the
    // catalogue fetch. This screen is recreated when Browse re-enters
    // composition, so doing the JSON read in remember() would tax every visit.
    val repos by produceState<List<String>?>(initialValue = null) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) { ExtensionRepos.list(appContext) }
    }
    var available by remember { mutableStateOf<List<Extension>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var diagnosticsRunning by remember { mutableStateOf(false) }
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
        if (pkg != null) {
            val appContext = context.applicationContext
            scope.launch {
                val stillInstalled = withContext(Dispatchers.IO) {
                    ExtensionManager.isInstalled(appContext, pkg)
                }
                if (stillInstalled) {
                    report = "$pkg is still installed.\n\nThe uninstall was either " +
                        "cancelled, or refused by the system. If no dialog appeared at " +
                        "all, this build is missing the REQUEST_DELETE_PACKAGES " +
                        "permission, or the ROM blocks app-initiated uninstalls."
                }
            }
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
        val repoSnapshot = repos ?: return@LaunchedEffect
        if (repoSnapshot.isEmpty()) {
            available = emptyList()
            loadedOnce = true
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
        loadedOnce = true
    }

    ExtensionsContent(
        modifier = modifier,
        reposEmpty = repos?.isEmpty() == true,
        loading = loading,
        loadedOnce = loadedOnce,
        error = error,
        filter = filter,
        onFilterChange = { filter = it },
        installedOnly = installedOnly,
        onToggleInstalledOnly = { installedOnly = !installedOnly },
        mediaFilter = mediaFilter,
        onMediaFilterChange = { mediaFilter = it },
        shownExtensions = shownExtensions,
        availableCount = available.size,
        scroll = scroll,
        diagnosticsRunning = diagnosticsRunning,
        onDiagnose = {
            if (!diagnosticsRunning) {
                diagnosticsRunning = true
                scope.launch {
                    val appContext = context.applicationContext
                    report = withContext(Dispatchers.IO) {
                        runCatching { diagnoseExtensions(appContext) }
                            .getOrElse {
                                "Diagnostics failed: ${it.message ?: it::class.java.simpleName}"
                            }
                    }
                    diagnosticsRunning = false
                }
            }
        },
        onInstall = { ext ->
            awaitingPackageChange = true
            scope.launch {
                ExtensionManager.install(context, ext)
                onInstalled()
            }
        },
        onUninstall = startUninstall,
    )


    ExtensionDiagnosticsDialog(
        report = report,
        onDismiss = { report = null },
    )

}
