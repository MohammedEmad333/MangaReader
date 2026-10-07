package com.mangareader.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SourceDialog(
    value: SourceConfig,
    onChange: (SourceConfig) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SourceConfig) -> Unit,
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onChange(value.copy(treeUri = uri.toString()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add source")
                Text(
                    "Connect content stored on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Source type", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("local").forEach { type ->
                                FilterChip(
                                    selected = value.type == type,
                                    onClick = { onChange(value.copy(type = type)) },
                                    label = { Text(typeLabel(type)) },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = value.label,
                    onValueChange = { onChange(value.copy(label = it)) },
                    label = { Text("Display name") },
                    supportingText = { Text("Optional. Yomu will use the source type if left blank.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (value.type == "local") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Library folder", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (value.treeUri.isBlank()) {
                                    "Choose the folder Yomu should scan for local manga and media."
                                } else {
                                    "Yomu can keep access to this folder after you close the app."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (value.treeUri.isNotBlank()) {
                                Text(
                                    Uri.decode(value.treeUri).substringAfterLast(':'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { folderPicker.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (value.treeUri.isNotBlank()) "Change folder" else "Choose folder")
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Server connection", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = value.url,
                                onValueChange = { onChange(value.copy(url = it)) },
                                label = { Text("Server URL") },
                                placeholder = { Text("http://192.168.1.10:25600") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = value.user,
                                onValueChange = { onChange(value.copy(user = it)) },
                                label = { Text("Username / email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = value.pass,
                                onValueChange = { onChange(value.copy(pass = it)) },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = value.isConfigured,
                onClick = { onSave(value.copy(label = value.label.ifBlank { typeLabel(value.type) })) },
            ) { Text("Save source") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
