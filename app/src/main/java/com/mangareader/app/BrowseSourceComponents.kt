package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One row in the Sources list. Local folders and extension sources render the
 * same way, so they're flattened into this before the list is built; `config`
 * is non-null only for local folders, which is what gates the Edit/Delete menu.
 */
internal data class BrowseRow(
    val id: String,
    val name: String,
    val lang: String,
    val iconPkg: String?,
    val isNsfw: Boolean,
    val configurable: Boolean,
    val isAnime: Boolean,
    val config: SourceConfig?,
    val source: Source?
)

/** Local folders first, multi-language sources next, then languages A-Z. */
internal fun langRank(group: String): Int = when (group) {
    "Local" -> 0
    "Multi" -> 1
    else -> 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseSourceRow(
    row: BrowseRow,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onEditConfig: ((SourceConfig) -> Unit)? = null,
    onDeleteConfig: ((SourceConfig) -> Unit)? = null,
    onOpenSettings: ((Source) -> Unit)? = null
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    // Only local folders carry a config, and only they get the Edit/Delete menu.
    val cfg = row.config
    ListItem(
        leadingContent = { SourceIcon(row.iconPkg, row.name) },
        headlineContent = { Text(row.name) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (row.lang.isNotBlank()) Text(row.lang)
                if (row.isAnime) {
                    Text(
                        "Anime",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (row.isNsfw) NsfwBadge()
            }
        },
        modifier = Modifier.clickable { onOpen() },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val src = row.source
                if (row.configurable && src != null) {
                    IconButton(onClick = { onOpenSettings?.invoke(src) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Source settings")
                    }
                }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        // A real pin, filled when pinned and outlined when
                        // not. This was a filled-vs-dimmed Star for six releases
                        // because material-icons-core has ~40 glyphs and no
                        // PushPin; the extended pack is now a dependency and the
                        // dimming workaround goes with it. The tint still moves
                        // as well as the glyph — outlined-and-grey vs filled-and-
                        // primary is legible at a glance where shape alone isn't.
                        if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (pinned) "Unpin" else "Pin",
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cfg != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            if (onEditConfig != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { menuOpen = false; onEditConfig?.invoke(cfg) }
                                )
                            }
                            if (onDeleteConfig != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menuOpen = false; onDeleteConfig?.invoke(cfg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Which sources appear in the Sources list.
 *
 * Grouped by language, with a switch per language and a checkbox per source, the
 * way Mihon does it. Both stores hold what's switched *off*, so a source added by
 * a new extension shows up without anyone having to enable it.
 */
