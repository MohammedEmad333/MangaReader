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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- prefs: the same store every other object in this package uses ----------

/**
 * Turns a stored cover string into something Coil can actually load.
 *
 * The two kinds disagree by design and share one `String` field: a local
 * series' cover is a filesystem path or a `content://` uri, an extension's is
 * an http URL. Wrapping the second in a `File` — which the history list did —
 * yields `/https:/host/...`, a path that cannot exist, so every
 * extension-sourced cover failed. In a debug build the failure overlay then
 * rendered that mangled path as text, which is why the rows showed "https:/hen…"
 * where the picture should be.
 */
internal fun coverModel(path: String?): Any? {
    val s = path?.trim().orEmpty()
    return when {
        s.isBlank() -> null
        // Anything with a scheme is handed over as-is; Coil resolves http,
        // https, content and file itself.
        s.startsWith("http://", ignoreCase = true) ||
            s.startsWith("https://", ignoreCase = true) ||
            s.startsWith("content://", ignoreCase = true) ||
            s.startsWith("file://", ignoreCase = true) -> s
        else -> File(s)
    }
}

@Composable
fun CoverImage(
    cover: Any?,
    title: String,
    modifier: Modifier = Modifier,
    /**
     * The library entry this cover belongs to, where it is one.
     *
     * Only the library grid passes it, and only the library grid should: this is
     * what turns a failed draw into a repair candidate, and a browse result or a
     * search hit isn't something this app stores a cover for.
     */
    seriesId: String? = null
) {
    val coverContext = LocalContext.current
    // Why Coil gave up on this cover, if it did. Keyed on the model so a
    // recycled grid cell doesn't inherit the previous entry's failure.
    var failure by remember(cover) { mutableStateOf<String?>(null) }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { state ->
                    val cause = state.result.throwable
                    failure = cause.message ?: cause::class.java.simpleName
                    // A stored cover that 404s is the only failure that says
                    // anything about the *stored string*. A timeout, an
                    // unresolvable host or a 403 is about the network or the
                    // source, and treating those as staleness would have one
                    // scroll in airplane mode queue the entire library for
                    // repair. See CoverRepair.
                    if (seriesId != null && isMissingImage(cause)) {
                        CoverRepair.report(coverContext, seriesId)
                    }
                }
            )
            // Debug builds only.
            //
            // A cover that fails to load and one the source never supplied both
            // render as the same grey box, which is exactly the ambiguity that
            // makes "no covers on this source" impossible to act on: 403, 404,
            // an unresolvable host and a format Android can't decode all look
            // identical from the outside. The URL and the reason are the two
            // facts that separate them, so show both rather than guessing at a
            // fix and shipping it blind.
            if (BuildConfig.DEBUG && failure != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = listOfNotNull(
                            (cover as? String)?.takeLast(48),
                            failure
                        ).joinToString("\n\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val HTTP_STATUS = Regex("""HTTP\s+(?:error\s+)?(\d{3})""")

/**
 * Whether a Coil failure means the image is gone rather than unreachable.
 *
 * Read off the message rather than by catching Coil's own exception type. This
 * file already proves `state.result.throwable.message` compiles; an unfamiliar
 * import does not, and with no compiler in this loop that is a CI round trip for
 * a detail this small.
 *
 * Tolerant on purpose, and the asymmetry is deliberate: a false positive costs
 * one wasted details request inside a sweep that is already making thousands,
 * while a false negative leaves a broken cover on screen indefinitely.
 */
private fun isMissingImage(cause: Throwable?): Boolean {
    val message = cause?.message ?: return false
    val code = HTTP_STATUS.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return code == 404 || code == 410
}

/** Cached miss, so a package without an icon isn't looked up again either. */
private object NoIcon

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

/**
 * An extension's launcher icon, loaded once per process.
 *
 * `remember(pkgName)` only holds it for as long as the row is composed, and a
 * `LazyColumn` throws rows away the moment they leave the screen — so scrolling
 * the sources list re-ran `getApplicationIcon` continuously. That call is an IPC
 * to the package manager plus opening another APK's resources, on the main
 * thread, and there are over a thousand extensions in that list.
 */
private fun extensionIcon(context: Context, pkgName: String): Drawable? {
    iconCache[pkgName]?.let { return if (it === NoIcon) null else it as Drawable }
    val loaded = runCatching {
        context.applicationContext.packageManager.getApplicationIcon(pkgName)
    }.getOrNull()
    iconCache[pkgName] = loaded ?: NoIcon
    return loaded
}

/**
 * The launcher icon of an extension APK, or the source's initials when there's
 * no package behind it (local folders) or the package is gone.
 *
 * The lookup itself is cached process-wide by [extensionIcon]; remembering it
 * per composed row is not enough, because scrolling disposes rows constantly.
 */
@Composable
internal fun SourceIcon(pkgName: String?, fallback: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(pkgName) { pkgName?.let { extensionIcon(context, it) } }
    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    fallback.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The one back affordance in the app.
 *
 * There were three. An `ArrowBack` icon on the series and source-filter screens,
 * a `TextButton` holding a literal "\u2190" on per-source browse, global search
 * and the WebView, and the word "Back" on the download queue and settings. Three
 * controls at three sizes for one action, and only the icon form has a real 48dp
 * target \u2014 a bare arrow glyph inside a `TextButton` is a small thing to hit in
 * the corner of the screen that's hardest to reach one-handed.
 *
 * It lives here rather than being copied into each file so that "they all match"
 * stays true by construction instead of by everyone remembering.
 */
@Composable
internal fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
    }
}

/** Section label above a run of source/extension rows. */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * Plain-language gloss for the HTTP codes that mean something specific.
 *
 * `HttpException` renders as \u201cHTTP error 522\u201d, which is accurate and tells
 * nobody anything. The 52x family in particular is worth naming: those are
 * Cloudflare saying it couldn\u2019t reach the site\u2019s own server, so the source is
 * down and no amount of retrying, clearing cookies or reinstalling the extension
 * will change it. Without that, a dead site looks exactly like a broken app.
 *
 * Matched on the message because that\u2019s all that survives \u2014 the code is
 * formatted into a string in `:source-api` and the exception type is gone by the
 * time an error reaches a screen.
 */
private fun httpHint(error: String): String? {
    val code = Regex("""HTTP error (\d{3})""")
        .find(error)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
    return when (code) {
        429 -> "Too many requests \u2014 the source is rate-limiting. Wait a minute, then retry."
        500, 502, 503 ->
            "The source\u2019s server is erroring or overloaded. Nothing to fix here; try later."
        504, 520, 521, 522, 523, 524 ->
            "Cloudflare couldn\u2019t reach the source\u2019s own server. The site is down, not " +
                "the app \u2014 wait, or use another source."
        else -> null
    }
}

/** The 18+ marker shown next to adult sources, matching the extension index flag. */
@Composable
internal fun NsfwBadge() {
    Text(
        "18+",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error
    )
}

/**
 * The error line under a screen's top bar.
 *
 * [actionLabel] and [onAction] are optional and default to nothing, so the call
 * sites that only want text are unchanged. They exist because some errors are
 * things the user can actually do something about — a Cloudflare challenge being
 * the first — and an error message with no way to act on it is a dead end.
 */
@Composable
internal fun ErrorBanner(
    error: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    if (error == null) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        val hint = httpHint(error)
        if (hint != null) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

// ---------- sources tab ----------

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

/**
 * A draggable scroll handle for a long lazy list or grid.
 *
 * **Deliberately takes numbers, not a state object.** A `LazyGridState` and a
 * `LazyListState` share no supertype that exposes what this needs, so a version
 * written against one would have to be duplicated for the other — and the card
 * this comes from is "*all* scrolls should have a scroll handle". Handing it
 * four integers and a callback means the second caller is three lines rather
 * than a second copy of this file.
 *
 * **The thumb is derived from the scroll, never driven alongside it.** Dragging
 * calls [onSeek] and then waits to be told where it ended up, exactly like the
 * library tab row does with `settledPage`. Keeping a private thumb position and
 * a scroll position in step is two things driving one value, which §5 records
 * as a feedback loop that no amount of "is it already equal" fixes.
 *
 * Hidden until something moves. A permanent handle on a screen of cover art is
 * clutter, and on a 3575-entry library it is also a lie about precision — one
 * pixel of track is several series.
 */
@Composable
internal fun ScrollHandle(
    firstVisibleIndex: Int,
    visibleItems: Int,
    totalItems: Int,
    isScrolling: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Nothing to seek through: the whole list is on screen.
    if (totalItems <= visibleItems || totalItems <= 0) return

    var dragging by remember { mutableStateOf(false) }
    // -1 means "not dragging". Held only for the duration of a drag, to carry
    // the sub-item remainder between deltas — without it a slow drag rounds to
    // the same index every frame and the handle sticks.
    var dragIndex by remember { mutableFloatStateOf(-1f) }

    val visible = isScrolling || dragging
    val handleAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "handleAlpha")

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(HANDLE_WIDTH)) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { HANDLE_HEIGHT.toPx() }
        val usable = (trackPx - thumbPx).coerceAtLeast(1f)
        val span = (totalItems - visibleItems).coerceAtLeast(1)

        val position = (if (dragIndex >= 0f) dragIndex else firstVisibleIndex.toFloat()) / span
        val offsetY = (position.coerceIn(0f, 1f) * usable).roundToInt()

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, offsetY) }
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .alpha(handleAlpha)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val base = if (dragIndex >= 0f) dragIndex else firstVisibleIndex.toFloat()
                        val next = (base + delta / usable * span).coerceIn(0f, span.toFloat())
                        dragIndex = next
                        onSeek(next.roundToInt())
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        dragIndex = -1f
                    }
                )
        )
    }
}

private val HANDLE_WIDTH = 10.dp
private val HANDLE_HEIGHT = 48.dp
