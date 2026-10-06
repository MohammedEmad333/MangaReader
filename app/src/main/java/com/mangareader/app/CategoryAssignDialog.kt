package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CategoryAssignDialog(seriesId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val loaded by produceState<Pair<List<Category>, Set<String>>?>(null, seriesId) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            Categories.list(appContext) to Categories.categoriesFor(appContext, seriesId)
        }
    }
    val all = loaded?.first.orEmpty()
    var selected by remember(seriesId) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(loaded) {
        if (selected == null) selected = loaded?.second
    }
    val currentSelection = selected.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Categories")
                Text(
                    "Choose where this series appears",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            when {
                loaded == null -> Text(
                    "Loading categories…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                all.isEmpty() -> Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        "No categories yet — create some under More → Categories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    all.forEach { cat ->
                        val checked = currentSelection.contains(cat.id)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (checked) {
                                            currentSelection - cat.id
                                        } else {
                                            currentSelection + cat.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { next ->
                                        selected = if (next) {
                                            currentSelection + cat.id
                                        } else {
                                            currentSelection - cat.id
                                        }
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
            }
        },
        confirmButton = {
            Button(
                enabled = loaded != null && selected != null && !saving,
                onClick = {
                    val appContext = context.applicationContext
                    val next = selected ?: return@Button
                    saving = true
                    scope.launch {
                        val saved = runCatching {
                            withContext(Dispatchers.IO) {
                                Categories.setCategoriesFor(appContext, seriesId, next)
                            }
                        }.isSuccess
                        saving = false
                        if (saved) onDismiss()
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}
