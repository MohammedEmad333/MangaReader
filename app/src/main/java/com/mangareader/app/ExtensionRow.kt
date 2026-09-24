package com.mangareader.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * One extension in the index. Installed rows pull the real launcher icon from
 * the installed package; rows that aren't installed yet have no package to read
 * one from, so they fall back to initials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionRow(
    ext: Extension,
    onInstall: () -> Unit,
    onUninstall: (() -> Unit)? = null
) {
    ListItem(
        leadingContent = {
            SourceIcon(if (ext.isInstalled) ext.pkgName else null, ext.name)
        },
        headlineContent = { Text(ext.name) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    listOf(
                        ext.lang,
                        // Show what an update would move you from and to.
                        if (ext.hasUpdate) "${ext.installedVersion} \u2192 ${ext.versionName}"
                        else ext.versionName
                    ).filter { it.isNotBlank() }.joinToString(" ")
                )
                if (ext.isAnime) {
                    Text(
                        "Anime",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                if (ext.isNsfw) NsfwBadge()
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Updating is the same flow as installing: the system installer
                // treats a higher versionCode on the same package as an upgrade.
                if (!ext.isInstalled || ext.hasUpdate) {
                    TextButton(onClick = onInstall) {
                        Text(if (ext.hasUpdate) "Update" else "Install")
                    }
                }
                // The "Installed" label this replaces said nothing the section
                // header above the row didn't already say, and it was sitting in
                // the one place an action for an installed extension belongs.
                if (ext.isInstalled && onUninstall != null) {
                    IconButton(onClick = onUninstall) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Uninstall ${ext.name}"
                        )
                    }
                }
            }
        }
    )
}
