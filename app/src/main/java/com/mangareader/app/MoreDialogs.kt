package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExtensionReposDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<String>?>(null) }
    var newRepo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        repos = withContext(Dispatchers.IO) { ExtensionRepos.list(appContext) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extension repositories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Add repository",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Paste the raw index.min.json URL. Added extensions appear under Browse → Extensions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newRepo,
                                onValueChange = { newRepo = it },
                                label = { Text("Repo index URL") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = repos != null && newRepo.isNotBlank() && !saving,
                                onClick = {
                                    val appContext = context.applicationContext
                                    val url = newRepo.trim()
                                    saving = true
                                    scope.launch {
                                        repos = withContext(Dispatchers.IO) {
                                            ExtensionRepos.add(appContext, url)
                                            ExtensionRepos.list(appContext)
                                        }
                                        newRepo = ""
                                        saving = false
                                    }
                                },
                            ) { Text(if (saving) "Saving…" else "Add") }
                        }
                    }
                }

                when {
                    repos == null -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Loading repositories…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    repos!!.isEmpty() -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("No repositories yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Add one above to install extensions from an external source.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Saved repositories",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        repos.orEmpty().forEach { url ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            clipboard.setText(AnnotatedString(url))
                                            android.widget.Toast.makeText(
                                                context,
                                                "Copied repo URL",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }) { Text("Copy") }
                                        TextButton(
                                            enabled = !saving,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                            onClick = {
                                                val appContext = context.applicationContext
                                                saving = true
                                                scope.launch {
                                                    repos = withContext(Dispatchers.IO) {
                                                        ExtensionRepos.remove(appContext, url)
                                                        ExtensionRepos.list(appContext)
                                                    }
                                                    saving = false
                                                }
                                            },
                                        ) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = onDismiss) { Text("Done") }
        },
    )
}
