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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
// PullToRefreshBox lives in a SUB-PACKAGE of material3, which the wildcard
// above does not reach. Needs naming explicitly or it resolves to nothing.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import dalvik.system.PathClassLoader
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One labelled icon action under the series header. */
@Composable
internal fun SeriesAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * The tag chips, shared by the collapsed scrolling row and the expanded
 * wrapping one.
 *
 * Extracted when the chevron learned to show every tag: two copies of a chip
 * that owns a dropdown is two places for the menu actions to drift apart, and
 * the menu is the reason a tag stopped being decoration in the first place.
 *
 * Not a Row or a FlowRow itself — the caller supplies the layout, which is the
 * only thing that differs between the two states.
 */
@Composable
internal fun GenreChips(
    genres: List<String>,
    sourceName: String,
    onSearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    genres.forEach { genre ->
        // A tag was decoration until now — a chip with an empty onClick. What it
        // actually is is a query, so tapping one offers the three things you can
        // do with a query rather than picking one and hoping.
        Box {
            var tagMenu by remember(genre) { mutableStateOf(false) }
            SuggestionChip(
                onClick = { tagMenu = true },
                label = { Text(genre) }
            )
            DropdownMenu(
                expanded = tagMenu,
                onDismissRequest = { tagMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Search $sourceName") },
                    onClick = {
                        tagMenu = false
                        onSearchTag(genre)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Global search") },
                    onClick = {
                        tagMenu = false
                        onGlobalSearchTag(genre)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy to clipboard") },
                    onClick = {
                        tagMenu = false
                        clipboard.setText(AnnotatedString(genre))
                    }
                )
            }
        }
    }
}

/**
 * The series cover, full screen and zoomable.
 *
 * A `Dialog` rather than a branch of the routing chain in `YomuApp`. That chain
 * encodes real navigation rules in an if/else and is delicate enough already —
 * §5 has three separate bugs from state living in the wrong side of it — and
 * this needs none of what a branch buys: nothing below it has to know it's open,
 * it holds no state worth surviving, and a dialog's own back handling dismisses
 * it without touching the series underneath.
 *
 * `usePlatformDefaultWidth = false` is what makes it full-bleed; without it a
 * dialog is inset to the platform's alert width and a cover in the middle of it
 * is barely larger than the one on the screen behind.
 *
 * Zoomable because a cover is one of the few images in this app worth looking at
 * closely, and `telephoto` is already a dependency the reader leans on — the
 * same call that made paged zoom cheap makes this nearly free.
 */
@Composable
internal fun CoverViewer(cover: Any?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            ZoomableAsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                // A zoomable image consumes its own pointer events, so a tap
                // detector wrapped around it never fires — the same thing that
                // moved the reader's show-controls tap off the pager and onto
                // the pages. Tapping the image is the way out.
                onClick = { onDismiss() }
            )
            // The scrim is black in both themes, so the arrow can't take its
            // colour from the scheme — on a light theme that's near-black on
            // black. Overridden rather than a second BackButton, so this stays
            // the one back affordance the app uses everywhere.
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                BackButton(onDismiss)
            }
        }
    }
}

/** Adds or removes one id. Written out because `Set` has no toggle. */
internal fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * The contextual bar shown while chapters are selected.
 *
 * Icon over label, via the same [SeriesAction] the series header uses, so the
 * two rows of actions on this screen look like the same app.
 *
 * The labels stay because the icons can't carry it alone. `material-icons-core`
 * has nothing for mark-as-read or mark-as-unread — §7's icon debt, again — so
 * read borrows `Check` and unread borrows `Clear`, and `Check` already means
 * "downloaded" three columns to the left. Bare glyphs would be a guess; with a
 * word under them they're just a target. Delete is last and coloured, so the one
 * action that can't be undone isn't adjacent to the one hit most.
 */
@Composable
internal fun ChapterSelectionBar(
    count: Int,
    canDownload: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    /** True when the button should add bookmarks rather than remove them. */
    bookmarkAdds: Boolean,
    onBookmark: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                }
                Text(
                    "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }
            // Spread, not scrolled. Four actions fit a phone width comfortably
            // and the header row directly above this one is already distributed
            // — bunching these at the left made the bar read as an overflow that
            // had more to show.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (canDownload) {
                    SeriesAction(
                        icon = Icons.Default.Download,
                        label = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onDownload
                    )
                }
                SeriesAction(
                    icon = Icons.Default.Check,
                    label = "Read",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRead
                )
                SeriesAction(
                    icon = Icons.Default.Clear,
                    label = "Unread",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onUnread
                )
                SeriesAction(
                    icon = if (bookmarkAdds) Icons.Default.BookmarkBorder
                    else Icons.Default.Bookmark,
                    label = if (bookmarkAdds) "Bookmark" else "Unbookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onBookmark(bookmarkAdds) }
                )
                SeriesAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * How far a read chapter's text fades.
 *
 * 0.45 rather than something subtler because this is now the *only* signal that
 * a chapter has been read, and it has to survive a bright phone outdoors.
 */
internal const val READ_DIM = 0.45f

/**
 * Height of the series screen's top bar, reserved in the scrolling header.
 *
 * Material3's `TopAppBar` is 64dp and does not expose it as a public constant,
 * so this is a copy of a number owned elsewhere. If the bar ever looks like it
 * overlaps the cover, or leaves a gap above it, this is why.
 */
internal val TOP_BAR_HEIGHT = 64.dp

/** How far the header scrolls before the top bar is fully opaque. */
internal val TOP_BAR_FADE_OVER = 120.dp
