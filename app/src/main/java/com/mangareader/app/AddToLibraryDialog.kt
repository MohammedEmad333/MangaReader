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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToLibraryDialog(
    series: Series,
    sourceId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var categoryBusy by remember { mutableStateOf(false) }
    var defaultCategory by remember { mutableStateOf<Category?>(null) }
    var cats by remember { mutableStateOf<List<Category>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var newName by remember { mutableStateOf("") }

    // ensureDefault() may write the whole category list, and list() may parse it.
    // Neither belongs in composition on a large imported library.
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
                    cats.orEmpty().forEach { cat ->
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
                        }
                    ) { Text(if (categoryBusy) "Adding…" else "Add") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = defaultCategory != null && cats != null && !saving && !categoryBusy,
                onClick = {
                    // Never save with zero categories; fall back to Default.
                    val defaultId = defaultCategory?.id ?: return@Button
                    val finalCats = if (selected.isEmpty()) setOf(defaultId) else selected
                    val entry = LibraryEntry(
                        seriesId = series.id,
                        sourceId = sourceId,
                        title = series.title,
                        cover = (series.cover as? String)
                            ?: (series.cover as? java.io.File)?.absolutePath
                            ?: "",
                        addedAt = System.currentTimeMillis()
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
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(
                enabled = !saving && !categoryBusy,
                onClick = onDismiss,
            ) { Text("Cancel") }
        }
    )
}
