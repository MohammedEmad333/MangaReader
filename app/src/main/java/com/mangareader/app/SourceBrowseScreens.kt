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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
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

// ---------- prefs: the same store every other object in this package uses ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    title: String,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
    supportsSearch: Boolean,
    supportsLatest: Boolean,
    supportsFilters: Boolean,
    onOpenFilters: () -> Unit,
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    query: String,
    hasNext: Boolean,
    loadingMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (Series) -> Unit,
    onBack: () -> Unit,
    /**
     * Opens a visible WebView at this source's site so the user can answer a
     * Cloudflare challenge by hand. Null for sources with no site to open —
     * local folders, and any extension that isn't an `HttpSource`.
     */
    onSolveChallenge: (() -> Unit)? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var searchField by remember(query) { mutableStateOf(query) }
    // Opens itself when a search is already running, so returning to a screen
    // showing results doesn't hide the field that produced them.
    var searchOpen by remember(title) { mutableStateOf(query.isNotBlank()) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var view by remember {
        mutableStateOf(BrowseView.from(prefs(context).getString(KEY_BROWSE_VIEW, null)))
    }
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
            navigationIcon = { BackButton(onBack) },
            actions = {
                if (supportsSearch) {
                    IconButton(onClick = {
                        // Closing while a search is live clears it, because the
                        // grid underneath is showing results and hiding the field
                        // would leave no way to tell that from the catalogue.
                        if (searchOpen && query.isNotBlank()) {
                            searchField = ""
                            onSearch("")
                        }
                        searchOpen = !searchOpen
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search"
                        )
                    }
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                Box {
                    IconButton(onClick = { viewMenuOpen = true }) {
                        // MoreVert, not a grid glyph: `material-icons-core` has no
                        // grid_view, and `Icons.Filled.List` is the one icon this
                        // repo refuses to import because it puts a property named
                        // `List` in file scope next to `List<Foo>` type usages.
                        Icon(Icons.Default.MoreVert, contentDescription = "View options")
                    }
                    DropdownMenu(
                        expanded = viewMenuOpen,
                        onDismissRequest = { viewMenuOpen = false }
                    ) {
                        BrowseView.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (option == view) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    view = option
                                    prefs(context).edit()
                                        .putString(KEY_BROWSE_VIEW, option.key).apply()
                                    viewMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        )

        // Only where there's a choice to make. A source declaring supportsLatest
        // false would show two chips that fetch the same listing.
        if (supportsLatest || supportsFilters) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (supportsLatest) {
                    // Neither reads as selected while a search is showing: the
                    // grid is neither listing at that point, and claiming
                    // otherwise is the kind of small lie that makes a screen
                    // feel broken.
                    FilterChip(
                        selected = query.isBlank() && mode == BrowseMode.POPULAR,
                        onClick = { onModeChange(BrowseMode.POPULAR) },
                        label = { Text("Popular") }
                    )
                    FilterChip(
                        selected = query.isBlank() && mode == BrowseMode.LATEST,
                        onClick = { onModeChange(BrowseMode.LATEST) },
                        label = { Text("Latest") }
                    )
                }
                if (supportsFilters) {
                    // Opens the sheet rather than switching listing directly:
                    // it only becomes the active listing once something is
                    // applied, so it reads as selected but isn't a mode toggle.
                    FilterChip(
                        selected = query.isBlank() && mode == BrowseMode.FILTER,
                        onClick = { onOpenFilters() },
                        label = { Text("Filter") }
                    )
                }
            }
        }

        if (supportsSearch && searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchField,
                    onValueChange = { searchField = it },
                    label = { Text("Search this source") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSearch(searchField.trim()) }) { Text("Go") }
            }
            if (query.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Results for \u201c$query\u201d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        searchField = ""
                        onSearch("")
                    }) { Text("Clear") }
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        // Matched on the message rather than a status code because that's all
        // that survives: the failure arrives here as an already-formatted string
        // from `Response.failureMessage()`, which is the one place that can see
        // the Cloudflare headers.
        val challengeable = onSolveChallenge != null &&
            error?.contains("Cloudflare", ignoreCase = true) == true
        ErrorBanner(
            error = error,
            actionLabel = if (challengeable) "Open in WebView" else null,
            onAction = if (challengeable) onSolveChallenge else null
        )

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
                // List is the same grid with one column, so paging, the empty
                // state and "Load more" stay on one code path instead of two.
                columns = if (view == BrowseView.LIST) GridCells.Fixed(1)
                else GridCells.Adaptive(minSize = coverMinDp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(shown) { s ->
                    when (view) {
                        BrowseView.COMFORTABLE -> ComfortableCell(s, onOpen)
                        BrowseView.COMPACT -> CompactCell(s, onOpen)
                        BrowseView.LIST -> ListRow(s, onOpen)
                    }
                }

                // Paging is manual rather than infinite-scroll: one tap per page
                // keeps request volume predictable and visible.
                if (hasNext && activeCategory == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator()
                            } else {
                                OutlinedButton(onClick = onLoadMore) { Text("Load more") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- series ----------

/** One labelled icon action under the series header. */
@Composable
private fun SeriesAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesScreen(
    series: Series,
    chapters: List<Chapter>,
    sourceId: String,
    sourceName: String,
    canDownload: Boolean,
    downloadProgress: Map<String, Int>,
    downloadTick: Int,
    downloadingAll: Boolean,
    onDownload: (Chapter) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    loading: Boolean,
    error: String?,
    readTick: Int,
    onOpen: (Int) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onLibraryChanged: () -> Unit,
    onSolveChallenge: (() -> Unit)?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var showCategories by remember { mutableStateOf(false) }
    var showAddToLibrary by remember { mutableStateOf(false) }
    var descriptionExpanded by remember(series.id) { mutableStateOf(false) }
    var confirmDeleteChapter by remember(series.id) { mutableStateOf<Chapter?>(null) }
    var inLibrary by remember(series.id) { mutableStateOf(Library.contains(context, series.id)) }

    // First unread chapter drives the Start/Resume button. Recomputed on readTick
    // so marking something read moves the target without reopening the screen.
    val resumeIndex = remember(chapters, readTick, sourceId) {
        chapters.indexOfFirst { !ReadState.isRead(context, chapterKeyOf(sourceId, it)) }
    }
    val downloadedCount = remember(chapters, downloadTick) {
        chapters.count { Downloads.isComplete(context, it.id) }
    }
    val anyProgress = remember(chapters, readTick, sourceId) {
        chapters.any {
            val k = chapterKeyOf(sourceId, it)
            ReadState.isRead(context, k) || savedPage(context, k) > 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box {
                    // Cover as a faded backdrop, then a gradient down to the
                    // background so the text at the bottom stays readable.
                    if (series.cover != null) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.20f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                )
                            )
                    )

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BackButton(onBack)
                        }

                        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                            CoverImage(
                                cover = series.cover,
                                title = series.title,
                                modifier = Modifier
                                    .width(108.dp)
                                    .aspectRatio(0.7f)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!series.author.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        series.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(series.status, sourceName.ifBlank { null })
                                        .joinToString(" \u2022 "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SeriesAction(
                                icon = if (inLibrary) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                label = if (inLibrary) "In library" else "Add to library",
                                tint = if (inLibrary) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    if (inLibrary) {
                                        Library.remove(context, series.id)
                                        inLibrary = false
                                        onLibraryChanged()
                                    } else {
                                        showAddToLibrary = true
                                    }
                                }
                            )
                            if (inLibrary) {
                                SeriesAction(
                                    icon = Icons.Default.Edit,
                                    label = "Categories",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { showCategories = true }
                                )
                            }
                            if (canDownload && chapters.isNotEmpty()) {
                                SeriesAction(
                                    icon = if (downloadingAll) Icons.Default.Clear
                                    else Icons.Default.KeyboardArrowDown,
                                    label = if (downloadingAll) "Stop" else "Download all",
                                    tint = if (downloadingAll) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        if (downloadingAll) onCancelDownloads() else onDownloadAll()
                                    }
                                )
                                if (downloadedCount > 0) {
                                    SeriesAction(
                                        icon = Icons.Default.Delete,
                                        label = "Delete ($downloadedCount)",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { onDeleteDownloads() }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (!series.description.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            series.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            if (descriptionExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (descriptionExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            if (series.genres.isNotEmpty()) {
                item {
                    // Scrolling row rather than a wrapping one: FlowRow is still
                    // an experimental layout API on this Compose version.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        series.genres.forEach { genre ->
                            SuggestionChip(onClick = { }, label = { Text(genre) })
                        }
                    }
                }
            }

            item {
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                // Same test the browse screen uses: the failure arrives as an
                // already-formatted string from `Response.failureMessage()`,
                // which is the one place that can see the Cloudflare headers.
                //
                // Worth having here and not only on browse, because these are
                // different requests that fail separately. A source can list its
                // catalogue from cached clearance and then 403 on the chapter
                // list, which left the only way to solve it on a screen the user
                // had already moved past.
                val challengeable = onSolveChallenge != null &&
                    error?.contains("Cloudflare", ignoreCase = true) == true
                ErrorBanner(
                    error = error,
                    actionLabel = if (challengeable) "Open in WebView" else null,
                    onAction = if (challengeable) onSolveChallenge else null
                )
                Text(
                    if (chapters.size == 1) "1 chapter" else "${chapters.size} chapters",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

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
                        val bits = listOfNotNull(
                            formatChapterDate(ch.dateUploaded),
                            ch.scanlator,
                            when {
                                read -> "Read"
                                resume > 0 -> "Page ${resume + 1}"
                                else -> null
                            }
                        )
                        if (bits.isNotEmpty()) {
                            Text(
                                bits.joinToString(" \u2022 "),
                                color = if (!read && resume > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (canDownload) {
                                val percent = downloadProgress[ch.id]
                                val downloaded = remember(ch.id, downloadTick) {
                                    Downloads.isComplete(context, ch.id)
                                }
                                // Queued and downloading are different states now
                                // that a queue exists, and read straight off it:
                                // a chapter can sit waiting behind twenty others.
                                val active = DownloadQueue.activeId == ch.id
                                val queued = DownloadQueue.isQueued(ch.id)
                                when {
                                    active -> Text(
                                        if (percent != null && percent > 0) "$percent%"
                                        else "\u2026",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    queued -> Text(
                                        "Queued",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // The check mark is the delete affordance.
                                    // A separate bin icon would be a third
                                    // control on a row that already has two, and
                                    // the state and the action on it are the same
                                    // thing: it's there because it's downloaded.
                                    downloaded -> IconButton(
                                        onClick = { confirmDeleteChapter = ch }
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Downloaded \u2014 delete",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    else -> TextButton(onClick = { onDownload(ch) }) {
                                        Text("Save")
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            TextButton(onClick = { onToggleRead(ch) }) {
                                Text(if (read) "Unread" else "Read")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpen(index) }
                )
                HorizontalDivider()
            }

            // Clearance so the last row isn't trapped under the button.
            item { Spacer(Modifier.height(88.dp)) }
        }

        if (chapters.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { onOpen(if (resumeIndex >= 0) resumeIndex else 0) },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                text = { Text(if (anyProgress) "Resume" else "Start") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    // Confirmed rather than immediate. The row itself opens the chapter, so a
    // control inside it that deletes on the first tap is one slipped thumb away
    // from a re-download — and unlike "Delete all", this button sits next to the
    // thing people are aiming at.
    val pendingDelete = confirmDeleteChapter
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChapter = null },
            title = { Text("Delete this download?") },
            text = {
                Text(
                    "\u201c${pendingDelete.name}\u201d is removed from storage. The " +
                        "chapter stays in the list and can be saved again, and your " +
                        "read mark and place in it are untouched."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteChapter(pendingDelete)
                    confirmDeleteChapter = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChapter = null }) { Text("Cancel") }
            }
        )
    }

    if (showCategories) {
        CategoryAssignDialog(seriesId = series.id, onDismiss = { showCategories = false })
    }

    if (showAddToLibrary) {
        AddToLibraryDialog(
            series = series,
            sourceId = sourceId,
            onDismiss = { showAddToLibrary = false },
            onSaved = {
                showAddToLibrary = false
                inLibrary = true
                onLibraryChanged()
            }
        )
    }
}

private const val KEY_BROWSE_VIEW = "browse_view"

/**
 * How the browse grid draws a result.
 *
 * Mihon's three, and they answer different questions: Comfortable is for
 * reading titles you don't know, Compact fits roughly a third more covers on
 * screen for a library you recognise by art, and List is the only one that shows
 * a long title in full. The choice is global rather than per-source \u2014 it's
 * about the screen and the eyes in front of it, not about the catalogue.
 */
internal enum class BrowseView(val key: String, val label: String) {
    COMFORTABLE("comfortable", "Comfortable grid"),
    COMPACT("compact", "Compact grid"),
    LIST("list", "List");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}

/** Cover with the title underneath it. */
@Composable
private fun ComfortableCell(series: Series, onOpen: (Series) -> Unit) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onOpen(series) }
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Cover with the title over it, under a scrim. */
@Composable
private fun CompactCell(series: Series, onOpen: (Series) -> Unit) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onOpen(series) }
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        // The scrim isn't decoration. Covers are arbitrary artwork and a title
        // drawn straight onto a pale one is unreadable; the gradient is what
        // makes white text safe over anything.
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** One row: small cover, full title. */
@Composable
private fun ListRow(series: Series, onOpen: (Series) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(series) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(0.7f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Today" / "Yesterday" / a short date, or null when the source gave no date. */
internal fun formatChapterDate(millis: Long): String? {
    if (millis <= 0L) return null
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val startOfToday = now - (now % day)
    return when {
        millis >= startOfToday -> "Today"
        millis >= startOfToday - day -> "Yesterday"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
