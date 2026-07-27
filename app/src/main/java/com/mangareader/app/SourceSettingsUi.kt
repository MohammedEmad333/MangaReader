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

/** One row inside the source settings dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcePrefRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { if (summary != null) Text(summary) },
        trailingContent = trailing,
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * Settings for one extension source, read from its ConfigurableSource screen.
 *
 * Every edit bumps `revision`, which re-runs SourceSettings.load and so re-reads
 * the persisted values — the Preference objects are rebuilt rather than mutated,
 * which keeps this list honest about what the extension will actually see.
 */
@Composable
internal fun SourceSettingsDialog(source: Source, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val items = remember(source.id, revision) { SourceSettings.load(context, source) }
    var editing by remember { mutableStateOf<SourcePrefItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.name) },
        text = {
            if (items.isEmpty()) {
                Text("This source doesn't expose any settings.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEach { item ->
                        when (item) {
                            is SourcePrefItem.Toggle -> SourcePrefRow(
                                title = item.title,
                                summary = item.summary,
                                onClick = {
                                    SourceSettings.apply(context, source, item, !item.checked)
                                    revision++
                                },
                                trailing = {
                                    Switch(
                                        checked = item.checked,
                                        onCheckedChange = { checked ->
                                            SourceSettings.apply(context, source, item, checked)
                                            revision++
                                        }
                                    )
                                }
                            )

                            is SourcePrefItem.Choice -> {
                                val label = SourceSettings.labelFor(item)
                                SourcePrefRow(
                                    title = item.title,
                                    summary = if (label.isNotBlank()) label else item.summary,
                                    onClick = { editing = item }
                                )
                            }

                            is SourcePrefItem.MultiChoice -> SourcePrefRow(
                                title = item.title,
                                summary = "${item.current.size} selected",
                                onClick = { editing = item }
                            )

                            is SourcePrefItem.TextEntry -> SourcePrefRow(
                                title = item.title,
                                summary = if (item.current.isNotBlank()) item.current
                                else item.summary,
                                onClick = { editing = item }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    val target = editing
    if (target != null) {
        when (target) {
            // Toggles are edited in place; they never open a second dialog.
            is SourcePrefItem.Toggle -> Unit

            is SourcePrefItem.Choice -> AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(target.title) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        target.entries.forEachIndexed { idx, label ->
                            val value = target.values.getOrNull(idx)
                            if (value != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            SourceSettings.apply(context, source, target, value)
                                            revision++
                                            editing = null
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = value == target.current,
                                        onClick = null
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            )

            is SourcePrefItem.MultiChoice -> {
                var selected by remember(target.key) { mutableStateOf(target.current) }
                AlertDialog(
                    onDismissRequest = { editing = null },
                    title = { Text(target.title) },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            target.entries.forEachIndexed { idx, label ->
                                val value = target.values.getOrNull(idx)
                                if (value != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selected = if (value in selected) selected - value
                                                else selected + value
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = value in selected,
                                            onCheckedChange = null
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(label)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            SourceSettings.apply(context, source, target, selected)
                            revision++
                            editing = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editing = null }) { Text("Cancel") }
                    }
                )
            }

            is SourcePrefItem.TextEntry -> {
                var draft by remember(target.key) { mutableStateOf(target.current) }
                AlertDialog(
                    onDismissRequest = { editing = null },
                    title = { Text(target.title) },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            SourceSettings.apply(context, source, target, draft)
                            revision++
                            editing = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editing = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

// ---------- extensions ----------
