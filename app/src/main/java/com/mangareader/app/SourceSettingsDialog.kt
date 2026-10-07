package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun SourceSettingsDialog(source: Source, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val items = remember(source.id, revision) { SourceSettings.load(context, source) }
    var editing by remember { mutableStateOf<SourcePrefItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.name)
                Text(
                    "Source settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            if (items.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        "This source doesn't expose any settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Changes are saved immediately and apply only to this source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

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
                                        },
                                    )
                                },
                            )

                            is SourcePrefItem.Choice -> {
                                val label = SourceSettings.labelFor(item)
                                SourcePrefRow(
                                    title = item.title,
                                    summary = if (label.isNotBlank()) label else item.summary,
                                    onClick = { editing = item },
                                )
                            }

                            is SourcePrefItem.MultiChoice -> SourcePrefRow(
                                title = item.title,
                                summary = "${item.current.size} selected",
                                onClick = { editing = item },
                            )

                            is SourcePrefItem.TextEntry -> SourcePrefRow(
                                title = item.title,
                                summary = item.current.ifBlank { item.summary.orEmpty() },
                                onClick = { editing = item },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    when (val target = editing) {
        null, is SourcePrefItem.Toggle -> Unit

        is SourcePrefItem.Choice -> AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(target.title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    target.entries.forEachIndexed { idx, label ->
                        val value = target.values.getOrNull(idx) ?: return@forEachIndexed
                        SourceChoiceRow(
                            label = label,
                            selected = value == target.current,
                            onClick = {
                                SourceSettings.apply(context, source, target, value)
                                revision++
                                editing = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )

        is SourcePrefItem.MultiChoice -> {
            var selected by remember(target.key) { mutableStateOf(target.current) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(target.title) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        target.entries.forEachIndexed { idx, label ->
                            val value = target.values.getOrNull(idx) ?: return@forEachIndexed
                            SourceMultiChoiceRow(
                                label = label,
                                checked = value in selected,
                                onClick = {
                                    selected = if (value in selected) selected - value else selected + value
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        SourceSettings.apply(context, source, target, selected)
                        revision++
                        editing = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                },
            )
        }

        is SourcePrefItem.TextEntry -> {
            var draft by remember(target.key) { mutableStateOf(target.current) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(target.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!target.summary.isNullOrBlank()) {
                            Text(
                                target.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        SourceSettings.apply(context, source, target, draft)
                        revision++
                        editing = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun SourceChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SourceMultiChoiceRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
