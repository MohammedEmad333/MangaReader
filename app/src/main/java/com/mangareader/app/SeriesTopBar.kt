package com.mangareader.app

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesTopBar(
    title: String,
    canDownload: Boolean,
    chapters: List<Chapter>,
    visibleChapters: List<Chapter>,
    sourceId: String,
    onDownloadBatch: (List<Chapter>) -> Unit,
    filtersActive: Boolean,
    onOpenChapterOptions: () -> Unit,
    onRefresh: () -> Unit,
    onFindVideos: (Chapter) -> Unit,
    inLibrary: Boolean,
    onEditCategories: () -> Unit,
    onMigrate: () -> Unit,
    seriesUrl: String?,
    onBack: () -> Unit,
    barAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var showDownloadMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var preparingDownloads by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(barAlpha),
            )
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (canDownload && visibleChapters.isNotEmpty()) {
                Box {
                    IconButton(
                        enabled = !preparingDownloads,
                        onClick = { showDownloadMenu = true },
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download chapters")
                    }
                    DropdownMenu(
                        expanded = showDownloadMenu,
                        onDismissRequest = { showDownloadMenu = false },
                    ) {
                        DownloadChoice.entries.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice.label) },
                                enabled = !preparingDownloads,
                                onClick = {
                                    showDownloadMenu = false
                                    val rows = visibleChapters.toList()
                                    preparingDownloads = true
                                    scope.launch {
                                        val targets = withContext(Dispatchers.IO) {
                                            downloadTargets(
                                                appContext,
                                                rows,
                                                sourceId,
                                                choice,
                                            )
                                        }
                                        onDownloadBatch(targets)
                                        preparingDownloads = false
                                    }
                                },
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onOpenChapterOptions) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter, sort and display chapters",
                    tint = if (filtersActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }

            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            showOptionsMenu = false
                            onRefresh()
                        },
                    )

                    visibleChapters.firstOrNull()?.let { scanTarget ->
                        DropdownMenuItem(
                            text = { Text("Find videos") },
                            onClick = {
                                showOptionsMenu = false
                                onFindVideos(scanTarget)
                            },
                        )
                    }

                    if (inLibrary) {
                        DropdownMenuItem(
                            text = { Text("Edit categories") },
                            onClick = {
                                showOptionsMenu = false
                                onEditCategories()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Migrate to another source") },
                            onClick = {
                                showOptionsMenu = false
                                onMigrate()
                            },
                        )
                    }

                    if (!seriesUrl.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                showOptionsMenu = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, "$title\n$seriesUrl")
                                }
                                context.startActivity(Intent.createChooser(send, "Share series"))
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = barAlpha),
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = barAlpha),
        ),
        modifier = modifier,
    )
}
