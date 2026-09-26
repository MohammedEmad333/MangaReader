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
internal fun BulkCategoryDialog(
    seriesIds: Set<String>,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val loaded by produceState<Pair<List<Category>, Map<String, ToggleableState>>?>(null, seriesIds) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            val categories = Categories.list(appContext)
            val initial = categories.associate { cat ->
                val members = Categories.seriesIn(appContext, cat.id)
                val hits = seriesIds.count { it in members }
                cat.id to when (hits) {
                    0 -> ToggleableState.Off
                    seriesIds.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
            }
            categories to initial
        }
    }
    val cats = loaded?.first.orEmpty()
    val initial = loaded?.second.orEmpty()
    val state = remember(loaded) {
        mutableStateMapOf<String, ToggleableState>().apply { putAll(initial) }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
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
                enabled = loaded != null && cats.isNotEmpty() && !saving,
                onClick = {
                    val appContext = context.applicationContext
                    val add = state.filterValues { it == ToggleableState.On }.keys.toSet()
                    val remove = state.filterValues { it == ToggleableState.Off }.keys.toSet()
                    saving = true
                    scope.launch {
                        val saved = runCatching {
                            withContext(Dispatchers.IO) {
                                Categories.applyCategories(appContext, seriesIds, add, remove)
                            }
                        }.isSuccess
                        saving = false
                        if (saved) onApplied()
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- browse ----------
