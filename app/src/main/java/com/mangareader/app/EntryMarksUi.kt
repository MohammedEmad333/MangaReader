package com.mangareader.app

import android.content.Context
import android.graphics.drawable.Drawable
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- entry markers, shared by every screen that lists series ----------

/**
 * The corner markers a series cell can carry, gathered **once per screen**.
 *
 * Every field here is a whole-store read: `DownloadIndex.seriesIds` parses an
 * index file, `SeriesIndex.all` parses one JSON string, `Categories.seriesIn`
 * parses the assignment map. Asking any of them per row is §5's "an import is a
 * load test" — the category chips did exactly that and froze the app at 3567
 * entries. So this is built at the top of a screen and handed down.
 *
 * Absent from [unread] means **un-counted, not zero**, which is why the map only
 * ever holds positive counts and the badge draws nothing for a miss. A grid of
 * `0` badges reads as "you have read everything", which is a plausible enough
 * lie to be believed rather than reported.
 */
internal class EntryMarks(
    val readIds: Set<String>,
    val downloadedIds: Set<String>,
    val unread: Map<String, Int>,
    val badgeLocal: Boolean
) {
    fun dim(seriesId: String) = seriesId in readIds
    fun downloaded(seriesId: String) = seriesId in downloadedIds
    fun unreadOf(seriesId: String) = unread[seriesId]

    companion object {
        val NONE = EntryMarks(emptySet(), emptySet(), emptyMap(), false)
    }
}

/**
 * Reads the three stores behind [EntryMarks], honouring the same badge
 * preferences the library screen uses — one setting for "show me download
 * badges" rather than one per screen.
 *
 * [tick] is whatever the calling screen bumps when library state moves.
 */
@Composable
internal fun rememberEntryMarks(tick: Int): EntryMarks {
    val context = LocalContext.current
    return remember(tick) {
        val badgeDl = LibraryPrefs.badgeDownloaded(context)
        val badgeUnread = LibraryPrefs.badgeUnread(context)
        // "Read" is an ordinary user category matched by name, exactly as the
        // library grid matches it. No new field, one parse.
        val readCat = Categories.list(context)
            .firstOrNull { it.name.equals("Read", ignoreCase = true) }
        EntryMarks(
            readIds = if (readCat == null) emptySet()
            else Categories.seriesIn(context, readCat.id),
            downloadedIds = if (badgeDl) DownloadIndex.seriesIds(context) else emptySet(),
            unread = if (badgeUnread) {
                SeriesIndex.all(context)
                    .mapValues { (_, c) -> c.unread }
                    .filterValues { it > 0 }
            } else emptyMap(),
            badgeLocal = LibraryPrefs.badgeLocal(context)
        )
    }
}

/**
 * The corner markers themselves. Nothing is drawn when all are off, so a cell
 * that has none carries no box.
 *
 * Was private to `LibraryScreens.kt` until 0.113. Same rendering everywhere on
 * purpose: a `DL` chip should mean the same thing in Browse as it does in the
 * Library, and two implementations would drift.
 */
@Composable
internal fun EntryBadges(downloaded: Boolean, local: Boolean, unread: Int? = null) {
    val showUnread = unread != null && unread > 0
    if (!downloaded && !local && !showUnread) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        // First, because it's the one that changes and the one being looked for.
        if (showUnread) MiniBadge("$unread", MaterialTheme.colorScheme.primary)
        if (downloaded) MiniBadge("DL", MaterialTheme.colorScheme.tertiary)
        if (local) MiniBadge("Local", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
internal fun MiniBadge(text: String, colour: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}
