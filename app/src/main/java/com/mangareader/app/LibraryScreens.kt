package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ---------- library ----------

/**
 * The library grid.
 *
 * [activeCategory] is hoisted into `YomuApp` on purpose. It used to be a plain
 * `remember` in here, which meant opening a series — the routing chain replaces
 * this whole branch — destroyed it, and backing out always landed on the first
 * category rather than the one that was being looked at.
 *
 * There is no longer an "All" tab. The tabs are exactly the user's categories,
 * so a null [activeCategory] is only the pre-resolution state; the first frame
 * resolves it to a real id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTab(
    libraryTick: Int,
    error: String?,
    activeCategory: String?,
    onCategoryChange: (String?) -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onRemoveMany: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A bulk category edit changes what every tab holds and what is dimmed, but
    // not the library itself, so there is nothing for YomuApp's libraryTick to
    // say about it. This is added to that tick rather than replacing it, so
    // either can invalidate the reads below — the same shape as downloadTick.
    var localTick by remember { mutableIntStateOf(0) }
    val tick = libraryTick + localTick

    // Re-read on every tick so adds/removes show up immediately.
    val entries = remember(tick) { Library.list(context) }
    val categories = remember(tick) { Categories.list(context) }

    var coverSize by remember { mutableStateOf(prefs(context).getString("cover_size", "medium") ?: "medium") }
    val coverMinDp = when (coverSize) {
        "small" -> 88.dp
        "large" -> 140.dp
        else -> 110.dp
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignOpen by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    // "Read" is a normal user category, so this is a name match rather than a
    // new field. Resolved once per tick; the members are one parse, the same
    // shape as the category filter below — asking "is this series read" per
    // entry would be a full JSON parse per entry, which is the mistake §5
    // records for categoriesFor().
    val readIds = remember(tick, categories) {
        val readCat = categories.firstOrNull { it.name.equals("Read", ignoreCase = true) }
        if (readCat == null) emptySet<String>() else Categories.seriesIn(context, readCat.id)
    }

    // Which category each tab shows. Falls back to the first tab when the
    // remembered one has been deleted since it was last looked at.
    val tabIndex = categories.indexOfFirst { it.id == activeCategory }.let { if (it < 0) 0 else it }
    val pagerState = rememberPagerState(initialPage = tabIndex) { categories.size }

    // The pager owns the position. This is the only thing that reports it
    // outward, and it reads settledPage rather than currentPage on purpose.
    //
    // The two-way sync this replaces deadlocked itself: animateScrollToPage(3)
    // from page 0 animates *through* 1 and 2, currentPage updates at each one,
    // and reporting those intermediate values back out moved activeCategory,
    // which tripped a second effect into issuing its own animateScrollToPage
    // and cancelling the first mid-flight. Every distant tab tap landed one
    // short, on the side it came from.
    //
    // settledPage only moves when the scroll stops, so a five-tab jump reports
    // once, at the end. Nothing drives the pager from activeCategory any more —
    // restoring the tab after a series is opened and backed out of is what
    // initialPage above is for, and that is read once, before any of this runs.
    LaunchedEffect(pagerState.settledPage, categories) {
        categories.getOrNull(pagerState.settledPage)?.let {
            if (it.id != activeCategory) onCategoryChange(it.id)
        }
    }

    // Leaving selection is what back should do first, ahead of leaving the tab.
    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searchOpen) { searchOpen = false; query = "" }

    // Every tab's contents, built once per tick rather than per page.
    //
    // A local @Composable helper would have been the obvious spelling and is
    // the wrong one: the pager composes neighbouring pages, so it would run the
    // filter for pages nobody is looking at on every recomposition. One pass
    // here, a map lookup there.
    //
    // Each seriesIn() call reads the cached assignment object — the parse is
    // paid once. Asking it the other way round, categoriesFor() per entry, is a
    // full parse per series and is what locked the app up at 3567 of them.
    val perCategory: Map<String, List<LibraryEntry>> =
        remember(entries, categories, tick, query) {
            val needle = query.trim()
            val assigned by lazy { Categories.assignedSeries(context) }
            categories.associate { cat ->
                val base = if (cat.id == Categories.DEFAULT_ID) {
                    // Default isn't a category things are filed under — it's
                    // where a series sits when it's filed under nothing, which
                    // is what Tachiyomi means by it too. Series put there by
                    // hand count as well, since the category editor writes it
                    // explicitly rather than saving an empty set.
                    val explicit = Categories.seriesIn(context, cat.id)
                    entries.filter { it.seriesId !in assigned || it.seriesId in explicit }
                } else {
                    val ids = Categories.seriesIn(context, cat.id)
                    entries.filter { it.seriesId in ids }
                }
                cat.id to if (needle.isBlank()) base
                else base.filter { it.title.contains(needle, ignoreCase = true) }
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            // Contextual bar. Replaces the normal one rather than sitting under
            // it, so the grid doesn't jump by a bar's height on every long press.
            TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                    }
                },
                actions = {
                    val visible = categories.getOrNull(pagerState.currentPage)
                        ?.let { perCategory[it.id] }
                        .orEmpty()
                    IconButton(onClick = { selected = visible.map { it.seriesId }.toSet() }) {
                        Icon(Icons.Default.Check, contentDescription = "Select all")
                    }
                    IconButton(onClick = { assignOpen = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Change categories")
                    }
                    IconButton(onClick = {
                        onRemoveMany(selected)
                        selected = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove from library")
                    }
                }
            )
        } else {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        // Not an OutlinedTextField: a bordered box inside a bar
                        // is taller than the bar's own content slot and clips.
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search library") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Library")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) query = ""
                        searchOpen = !searchOpen
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search"
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                "Cover size",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
                                .forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = {
                                            if (coverSize == key) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            coverSize = key
                                            prefs(context).edit().putString("cover_size", key).apply()
                                            menuOpen = false
                                        }
                                    )
                                }
                        }
                    }
                }
            )
        }

        ErrorBanner(error)

        if (categories.isEmpty()) {
            // Nothing to tab between. One flat grid, no pager.
            LibraryGrid(
                shown = entries.filter {
                    query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
                },
                allEmpty = entries.isEmpty(),
                coverMinDp = coverMinDp,
                readIds = readIds,
                selected = selected,
                selecting = selecting,
                onOpen = onOpen,
                onToggle = { id ->
                    selected = if (id in selected) selected - id else selected + id
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, categories.size - 1),
                edgePadding = 8.dp
            ) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                // A swipe that also drags entries around is not a swipe. Held
                // off during selection so a mis-swipe can't change tab out from
                // under a half-made selection.
                userScrollEnabled = !selecting,
                modifier = Modifier.weight(1f)
            ) { page ->
                val cat = categories[page]
                LibraryGrid(
                    shown = perCategory[cat.id].orEmpty(),
                    allEmpty = entries.isEmpty(),
                    coverMinDp = coverMinDp,
                    readIds = readIds,
                    selected = selected,
                    selecting = selecting,
                    onOpen = onOpen,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (assignOpen) {
        BulkCategoryDialog(
            seriesIds = selected,
            onDismiss = { assignOpen = false },
            onApplied = {
                assignOpen = false
                selected = emptySet()
                localTick++
            }
        )
    }
}

/**
 * Category editor for a selection of any size.
 *
 * The checkboxes are tri-state because a selection usually isn't uniform: with
 * six series highlighted, "Manhwa" may hold four of them, and both a plain
 * checked box and a plain unchecked one would be a lie that silently rewrites
 * the other two on save. Indeterminate means *leave this alone*, and it is the
 * state a mixed category starts in and returns to.
 *
 * Tapping cycles On -> Off -> back to where it started. A category that began
 * mixed can therefore be forced on, forced off, or restored; one that began
 * uniform just toggles.
 */
@Composable
internal fun BulkCategoryDialog(
    seriesIds: Set<String>,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val cats = remember { Categories.list(context) }

    // One membership lookup per category against the cached assignment object,
    // then a set test per selected id. The other direction — categoriesFor()
    // per selected series — is a full parse each time.
    val initial = remember(seriesIds, cats) {
        cats.associate { cat ->
            val members = Categories.seriesIn(context, cat.id)
            val hits = seriesIds.count { it in members }
            cat.id to when (hits) {
                0 -> ToggleableState.Off
                seriesIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }
    }
    val state = remember(initial) {
        mutableStateMapOf<String, ToggleableState>().apply { putAll(initial) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (cats.isEmpty()) {
                Text("No categories yet - create some under More > Categories.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (seriesIds.size == 1) "1 entry selected"
                        else "${seriesIds.size} entries selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    cats.forEach { cat ->
                        val current = state[cat.id] ?: ToggleableState.Off
                        val start = initial[cat.id] ?: ToggleableState.Off
                        val cycle = {
                            state[cat.id] = when (current) {
                                ToggleableState.On -> ToggleableState.Off
                                ToggleableState.Off ->
                                    if (start == ToggleableState.Indeterminate) start
                                    else ToggleableState.On
                                ToggleableState.Indeterminate -> ToggleableState.On
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cycle() }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TriStateCheckbox(state = current, onClick = cycle)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(cat.name)
                                if (current == ToggleableState.Indeterminate) {
                                    Text(
                                        "Some selected - left unchanged",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = cats.isNotEmpty(),
                onClick = {
                    Categories.applyCategories(
                        context,
                        seriesIds,
                        add = state.filterValues { it == ToggleableState.On }.keys.toSet(),
                        remove = state.filterValues { it == ToggleableState.Off }.keys.toSet()
                    )
                    onApplied()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One category's grid.
 *
 * Split out of [LibraryTab] because the pager instantiates it per page, and
 * because the selection rules — tap opens, or toggles while selecting; long
 * press always starts a selection — are the same on every page and worth
 * having in one place.
 */
@Composable
private fun LibraryGrid(
    shown: List<LibraryEntry>,
    allEmpty: Boolean,
    coverMinDp: Dp,
    readIds: Set<String>,
    selected: Set<String>,
    selecting: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (shown.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (allEmpty) "Your library is empty." else "Nothing in this category yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Open a series from Browse and tap \u201cAdd to library\u201d.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = coverMinDp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.seriesId }) { entry ->
            val isSelected = entry.seriesId in selected
            // Read entries are dimmed everywhere, not only inside the Read tab:
            // the same series showing bright in Manhwa and dim in Read would be
            // a state that depends on where you're standing.
            val dim = entry.seriesId in readIds && !isSelected

            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .pointerInput(entry.seriesId, selecting) {
                        detectTapGestures(
                            onTap = {
                                if (selecting) onToggle(entry.seriesId) else onOpen(entry)
                            },
                            onLongPress = { onToggle(entry.seriesId) }
                        )
                    }
            ) {
                Box {
                    CoverImage(
                        cover = entry.cover.ifBlank { null },
                        title = entry.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .alpha(if (dim) 0.4f else 1f)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        )
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (dim) 0.4f else 1f)
                )
            }
        }
    }
}

// ---------- browse ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToLibraryDialog(
    series: Series,
    sourceId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    // Guarantees there is always at least one category to save into.
    val default = remember { Categories.ensureDefault(context) }
    var cats by remember { mutableStateOf(Categories.list(context)) }
    var selected by remember { mutableStateOf(setOf(default.id)) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                Text(
                    "Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    cats.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(cat.id)) {
                                        selected - cat.id
                                    } else {
                                        selected + cat.id
                                    }
                                }
                        ) {
                            Checkbox(
                                checked = selected.contains(cat.id),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + cat.id else selected - cat.id
                                }
                            )
                            Text(cat.name)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val created = Categories.addAndGet(context, newName.trim())
                            cats = Categories.list(context)
                            selected = selected + created.id
                            newName = ""
                        }
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Never save with zero categories; fall back to Default.
                val finalCats = if (selected.isEmpty()) setOf(default.id) else selected
                Library.add(
                    context,
                    LibraryEntry(
                        seriesId = series.id,
                        sourceId = sourceId,
                        title = series.title,
                        cover = (series.cover as? String)
                            ?: (series.cover as? java.io.File)?.absolutePath
                            ?: "",
                        addedAt = System.currentTimeMillis()
                    )
                )
                Categories.setCategoriesFor(context, series.id, finalCats)
                onSaved()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun CategoryAssignDialog(seriesId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = remember { Categories.list(context) }
    var selected by remember { mutableStateOf(Categories.categoriesFor(context, seriesId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (all.isEmpty()) {
                Text("No categories yet — create some under More → Categories.")
            } else {
                Column {
                    all.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected =
                                        if (selected.contains(cat.id)) selected - cat.id
                                        else selected + cat.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(cat.id),
                                onCheckedChange = {
                                    selected = if (it) selected + cat.id else selected - cat.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Categories.setCategoriesFor(context, seriesId, selected)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- reader ----------
