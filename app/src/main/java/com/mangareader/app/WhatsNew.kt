package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object WhatsNew {
    private const val KEY_SEEN = "whats_new_seen_code"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun pending(context: Context): List<ReleaseNote> {
        val p = prefs(context)
        val current = BuildConfig.VERSION_CODE

        if (!p.contains(KEY_SEEN)) {
            markSeen(context)
            val existingInstall = p.contains("library_json")
            return if (existingInstall) Changelog.since(current - 1, current) else emptyList()
        }

        val seen = p.getInt(KEY_SEEN, current)
        if (seen >= current) return emptyList()
        return Changelog.since(seen, current)
    }

    fun markSeen(context: Context) {
        prefs(context).edit().putInt(KEY_SEEN, BuildConfig.VERSION_CODE).apply()
    }
}

@Composable
internal fun WhatsNewDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    if (notes.isEmpty()) return

    val expanded = remember(notes) { mutableStateMapOf(notes.first().code to true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("What's new")
                Text(
                    if (notes.size == 1) {
                        "Yomu ${notes.first().name}"
                    } else {
                        "${notes.size} updates since your last visit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notes.forEachIndexed { index, note ->
                    val open = expanded[note.code] == true
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        tonalElevation = if (open) 2.dp else 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded[note.code] = !open }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Version ${note.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (index == 0) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                    Text(
                                        note.header,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (index == 0) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                Icon(
                                    if (open) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (open) "Collapse" else "Expand",
                                )
                            }
                            if (open) {
                                Text(
                                    note.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (index == 0) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        },
    )
}
