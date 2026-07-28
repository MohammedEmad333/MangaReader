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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

// ---------- prefs: the same store every other object in this package uses ----------

@Composable
fun CoverImage(cover: Any?, title: String, modifier: Modifier = Modifier) {
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

/**
 * The launcher icon of an extension APK, or the source's initials when there's
 * no package behind it (local folders) or the package is gone.
 *
 * The PackageManager lookup is remembered per package and only runs for rows
 * the LazyColumn actually composes, so a 95-source list doesn't load 95 icons.
 */
@Composable
internal fun SourceIcon(pkgName: String?, fallback: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(pkgName) {
        pkgName?.let {
            runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
        }
    }
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
