package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun MoreTab(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(prefs(context).getBoolean("incognito", false)) }
    var showCategories by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("More", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Quick controls and app settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MoreCard(
            icon = Icons.Filled.Visibility,
            title = "Incognito mode",
            subtitle = "Pause reading-history logging",
            trailing = {
                Switch(
                    checked = incognito,
                    onCheckedChange = { checked ->
                        incognito = checked
                        prefs(context).edit().putBoolean("incognito", checked).apply()
                    },
                )
            },
        )

        Spacer(Modifier.height(8.dp))

        val queued = DownloadQueue.items.size
        val failedDownloads = DownloadQueue.failed.size
        MoreCard(
            icon = Icons.Filled.Download,
            title = "Download queue",
            subtitle = when {
                failedDownloads > 0 && queued > 0 -> "$queued waiting · $failedDownloads failed"
                failedDownloads > 0 -> "$failedDownloads failed"
                queued == 0 -> "Nothing queued"
                DownloadQueue.paused -> "$queued waiting · paused"
                queued == 1 -> "1 chapter downloading"
                else -> "$queued chapters · downloading"
            },
            onClick = onOpenDownloads,
        )

        Spacer(Modifier.height(8.dp))

        MoreCard(
            icon = Icons.Filled.List,
            title = "Categories",
            subtitle = "Organize your library into collections",
            onClick = { showCategories = true },
        )

        Spacer(Modifier.height(8.dp))

        MoreCard(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "Appearance, reader, downloads, storage and privacy",
            onClick = onOpenSettings,
        )

        Spacer(Modifier.height(8.dp))

        MoreCard(
            icon = Icons.Filled.Star,
            title = "About Yomu",
            subtitle = "Native Kotlin manga reader · ${BuildConfig.VERSION_NAME}",
        )

        Spacer(Modifier.height(12.dp))
    }

    if (showCategories) {
        CategoryManagerDialog(onDismiss = { showCategories = false })
    }
}

@Composable
private fun MoreCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        ListItem(
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            },
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
