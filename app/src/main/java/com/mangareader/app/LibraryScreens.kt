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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

// ---------- prefs: the same store every other object in this package uses ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTab(
    libraryTick: Int,
    error: String?,
    onOpen: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit
) {
    val context = LocalContext.current

    // Re-read on every tick so adds/removes show up immediately.
    val entries = remember(libraryTick) { Library.list(context) }
    val categories = remember(libraryTick) { Categories.list(context) }
    var activeCategory by remember { mutableStateOf<String?>(null) }

    val coverMinDp = when (prefs(context).getString("cover_size", "medium")) {
        "small" -> 88.dp
        "large" -> 140.dp
        else -> 110.dp
    }

    val shown = remember(entries, activeCategory, libraryTick) {
        val cat = activeCategory
        if (cat == null) entries
        else {
            // One lookup of the category's members, then a set test per entry.
            // The obvious spelling — categoriesFor(entry) for each entry — is a
            // full JSON parse per series and locks the app up on a large library.
            val ids = Categories.seriesIn(context, cat)
            entries.filter { it.seriesId in ids }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Library") })
        ErrorBanner(error)

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (entries.isEmpty()) "Your library is empty."
                    else "Nothing in this category yet.",
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
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = coverMinDp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown) { entry ->
                    var menuOpen by remember(entry.seriesId) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .pointerInput(entry.seriesId) {
                                detectTapGestures(
                                    onTap = { onOpen(entry) },
                                    onLongPress = { menuOpen = true }
                                )
                            }
                    ) {
                        CoverImage(
                            cover = entry.cover.ifBlank { null },
                            title = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box {
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from library") },
                                    onClick = { menuOpen = false; onRemove(entry) }
                                )
                            }
                        }
                    }
                }
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
