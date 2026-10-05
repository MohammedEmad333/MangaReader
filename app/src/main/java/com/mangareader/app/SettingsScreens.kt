package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One page of settings. Order is the order of the index. */
private enum class SettingsSection(
    val title: String,
    val glyph: String,
    val summary: String,
) {
    APPEARANCE("Appearance", "🎨", "Theme and color preferences"),
    LIBRARY("Library", "📚", "Cover size, categories and library behavior"),
    READER("Reader", "📖", "Reading mode, display and color"),
    DOWNLOADS("Downloads", "⬇️", "Queue and downloaded chapters"),
    BROWSE("Browse", "🧭", "Extension repositories and global search"),
    DATA("Data and storage", "🗄️", "Backups, storage usage and caches"),
    PRIVACY("Security and privacy", "🔒", "Incognito mode and secure screen"),
    ADVANCED("Advanced", "🛠️", "Diagnostics, cookies and app information"),
}

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloadQueue: () -> Unit,
) {
    var openSection by rememberSaveable { mutableStateOf("") }
    val section = SettingsSection.entries.firstOrNull { it.name == openSection }
    val up: () -> Unit = { if (section != null) openSection = "" else onBack() }
    BackHandler { up() }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = section?.title ?: "Settings", onBack = up)
        when (section) {
            null -> SettingsIndex(onOpen = { openSection = it.name })
            SettingsSection.APPEARANCE -> AppearanceSettings()
            SettingsSection.LIBRARY -> LibrarySettings()
            SettingsSection.READER -> ReaderDefaultsSettings()
            SettingsSection.DOWNLOADS -> DownloadSettings(onOpenDownloadQueue)
            SettingsSection.BROWSE -> BrowseSettings()
            SettingsSection.DATA -> DataSettings()
            SettingsSection.PRIVACY -> PrivacySettings()
            SettingsSection.ADVANCED -> AdvancedSettings()
        }
    }
}

@Composable
private fun SettingsIndex(onOpen: (SettingsSection) -> Unit) {
    SettingsColumn {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "Customize Yomu",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Reading, downloads, sources, storage and privacy in one place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection.entries.forEach { section ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                ListItem(
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                section.glyph,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    supportingContent = {
                        Text(
                            section.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(section) },
                )
            }
        }
    }
}
