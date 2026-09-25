package com.mangareader.app

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a stored cover string into something Coil can actually load.
 */
internal fun coverModel(path: String?): Any? {
    val s = path?.trim().orEmpty()
    return when {
        s.isBlank() -> null
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
    seriesId: String? = null,
) {
    val coverContext = LocalContext.current
    var failure by remember(cover) { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { state ->
                    val cause = state.result.throwable
                    if (BuildConfig.DEBUG) {
                        failure = cause.message ?: cause::class.java.simpleName
                    }
                    if (seriesId != null && isMissingImage(cause)) {
                        CoverRepair.report(coverContext, seriesId)
                    }
                },
            )

            if (BuildConfig.DEBUG && failure != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = listOfNotNull(
                            (cover as? String)?.takeLast(48),
                            failure,
                        ).joinToString("\n\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal val HTTP_STATUS = Regex("""HTTP\s+(?:error\s+)?(\d{3})""")

internal fun isMissingImage(cause: Throwable?): Boolean {
    val message = cause?.message ?: return false
    val code = HTTP_STATUS.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return code == 404 || code == 410
}

/** Cached miss, so a package without an icon isn't looked up again either. */
internal object NoIcon

internal val iconCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

internal fun extensionIcon(context: Context, pkgName: String): Drawable? {
    iconCache[pkgName]?.let { return if (it === NoIcon) null else it as Drawable }
    val loaded = runCatching {
        context.applicationContext.packageManager.getApplicationIcon(pkgName)
    }.getOrNull()
    iconCache[pkgName] = loaded ?: NoIcon
    return loaded
}

@Composable
internal fun SourceIcon(
    pkgName: String?,
    fallback: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var icon by remember(pkgName) {
        mutableStateOf(pkgName?.let { iconCache[it] as? Drawable })
    }

    // PackageManager icon lookup can cross a Binder boundary and decode an
    // installed app resource. Doing that from composition makes the first fast
    // scroll through Sources/Extensions pay the cost on the UI thread. Keep
    // cached hits synchronous, but move the cold lookup to IO and update the
    // row when it is ready.
    LaunchedEffect(pkgName) {
        if (pkgName == null || iconCache[pkgName] === NoIcon) return@LaunchedEffect
        icon = (iconCache[pkgName] as? Drawable) ?: withContext(Dispatchers.IO) {
            extensionIcon(context.applicationContext, pkgName)
        }
    }

    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    fallback.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
