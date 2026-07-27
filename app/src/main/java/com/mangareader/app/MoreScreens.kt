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
internal fun HistoryScreen(
    history: List<HistoryEntry>,
    loading: Boolean,
    error: String?,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ErrorBanner(error)
        HorizontalDivider()

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing read yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    ListItem(
                        leadingContent = {
                            CoverImage(
                                cover = entry.coverPath
                                    .takeIf { it.isNotBlank() }
                                    ?.let { File(it) },
                                title = entry.title,
                                modifier = Modifier
                                    .width(40.dp)
                                    .aspectRatio(0.7f)
                            )
                        },
                        headlineContent = {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                if (entry.total > 0) "Page ${entry.page + 1} of ${entry.total}"
                                else "Page ${entry.page + 1}"
                            )
                        },
                        modifier = Modifier.clickable { onOpen(entry) },
                        trailingContent = {
                            TextButton(onClick = { onDelete(entry) }) { Text("Remove") }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------- more ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreTab() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }
    var coverSize by remember {
        mutableStateOf(prefs(context).getString("cover_size", "medium") ?: "medium")
    }
    var showCategories by remember { mutableStateOf(false) }
    var showBrowseSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        ListItem(
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

        ListItem(
            headlineContent = { Text("Cover size") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small", "medium", "large").forEach { size ->
                        FilterChip(
                            selected = coverSize == size,
                            onClick = {
                                coverSize = size
                                prefs(context).edit().putString("cover_size", size).apply()
                            },
                            label = { Text(size.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Create and delete library categories") },
            modifier = Modifier.clickable { showCategories = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Browse") },
            supportingContent = { Text("Extension repositories") },
            modifier = Modifier.clickable { showBrowseSettings = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("About Yomu") },
            supportingContent = { Text("Native Kotlin manga reader") }
        )
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = { showCategories = false })
    }

    if (showBrowseSettings) {
        ExtensionReposDialog(onDismiss = { showBrowseSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionReposDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var repos by remember { mutableStateOf(ExtensionRepos.list(context)) }
    var newRepo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extension repositories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a repo index URL (the raw index.min.json). Extensions from " +
                        "added repos appear under Browse \u2192 Extensions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newRepo,
                        onValueChange = { newRepo = it },
                        label = { Text("Repo index URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newRepo.isNotBlank(),
                        onClick = {
                            ExtensionRepos.add(context, newRepo.trim())
                            repos = ExtensionRepos.list(context)
                            newRepo = ""
                        }
                    ) { Text("Add") }
                }

                if (repos.isEmpty()) {
                    Text(
                        "No repositories yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        repos.forEach { url ->
                            ListItem(
                                headlineContent = {
                                    Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    TextButton(onClick = {
                                        ExtensionRepos.remove(context, url)
                                        repos = ExtensionRepos.list(context)
                                    }) { Text("Remove") }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun CategoryManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var cats by remember { mutableStateOf(Categories.list(context)) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                cats.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            Categories.remove(context, cat.id)
                            cats = Categories.list(context)
                        }) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            Categories.add(context, newName.trim())
                            cats = Categories.list(context)
                            newName = ""
                        }
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

// ---------- source dialog ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceDialog(
    value: SourceConfig,
    onChange: (SourceConfig) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SourceConfig) -> Unit
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onChange(value.copy(treeUri = uri.toString()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("local").forEach { t ->
                        FilterChip(
                            selected = value.type == t,
                            onClick = { onChange(value.copy(type = t)) },
                            label = { Text(typeLabel(t)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = value.label,
                    onValueChange = { onChange(value.copy(label = it)) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.type == "local") {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (value.treeUri.isNotBlank()) "Change folder" else "Choose folder")
                    }
                    if (value.treeUri.isNotBlank()) {
                        Text(
                            Uri.decode(value.treeUri).substringAfterLast(':'),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = value.url,
                        onValueChange = { onChange(value.copy(url = it)) },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.10:25600") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.user,
                        onValueChange = { onChange(value.copy(user = it)) },
                        label = { Text("Username / email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value.pass,
                        onValueChange = { onChange(value.copy(pass = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = value.isConfigured,
                onClick = { onSave(value.copy(label = value.label.ifBlank { typeLabel(value.type) })) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
