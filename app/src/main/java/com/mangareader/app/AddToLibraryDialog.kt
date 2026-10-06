package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddToLibraryDialog(
    series: Series,
    sourceId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var categoryBusy by remember { mutableStateOf(false) }
    var defaultCategory by remember { mutableStateOf<Category?>(null) }
    var cats by remember { mutableStateOf<List<Category>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            val default = Categories.ensureDefault(appContext)
            default to Categories.list(appContext)
        }
        defaultCategory = loaded.first
        cats = loaded.second
        if (selected.isEmpty()) selected = setOf(loaded.first.id)
    }

    AlertDialog(
        onDismissRequest = { if (!saving && !categoryBusy) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add to library")
                Text(
                    "Choose where this series belongs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }

                Text(
                    "Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cats.orEmpty().forEach { cat ->
                        val checked = cat.id in selected
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (checked) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (checked) selected - cat.id else selected + cat.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { next ->
                                        selected = if (next) selected + cat.id else selected - cat.id
                                    },
                                )
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (checked) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = cats != null && newName.isNotBlank() && !saving && !categoryBusy,
                        onClick = {
                            val appContext = context.applicationContext
                            val name = newName.trim()
                            categoryBusy = true
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val created = Categories.addAndGet(appContext, name)
                                        created to Categories.list(appContext)
                                    }
                                }.getOrNull()
                                categoryBusy = false
                                if (result != null) {
                                    val (created, nextCategories) = result
                                    cats = nextCategories
                                    selected = selected + created.id
                                    newName = ""
                                }
                            }
                        },
                    ) {
                        Text(if (categoryBusy) "Adding…" else "Add")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = defaultCategory != null && cats != null && !saving && !categoryBusy,
                onClick = {
                    val defaultId = defaultCategory?.id ?: return@Button
                    val finalCats = if (selected.isEmpty()) setOf(defaultId) else selected
                    val entry = LibraryEntry(
                        seriesId = series.id,
                        sourceId = sourceId,
                        title = series.title,
                        cover = (series.cover as? String)
                            ?: (series.cover as? java.io.File)?.absolutePath
                            ?: "",
                        addedAt = System.currentTimeMillis(),
                    )
                    val appContext = context.applicationContext
                    saving = true
                    scope.launch {
                        val saved = runCatching {
                            withContext(Dispatchers.IO) {
                                Library.add(appContext, entry)
                                Categories.setCategoriesFor(appContext, series.id, finalCats)
                            }
                        }.isSuccess
                        saving = false
                        if (saved) onSaved()
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving && !categoryBusy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}
