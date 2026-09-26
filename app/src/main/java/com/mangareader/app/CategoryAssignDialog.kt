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
import androidx.compose.runtime.produceState
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
                                        if (currentSelection.contains(cat.id)) currentSelection - cat.id
                                        else currentSelection + cat.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = currentSelection.contains(cat.id),
                                onCheckedChange = {
                                    selected = if (it) currentSelection + cat.id else currentSelection - cat.id
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
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss,
            ) { Text("Cancel") }
        }
    )
}
