package com.mangareader.app

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One page of settings. Order is the order of the index. */
private enum class SettingsSection(
    val title: String,
    val glyph: String,
    val summary: String
) {
    APPEARANCE("Appearance", "\uD83C\uDFA8", "Theme"),
    LIBRARY("Library", "\uD83D\uDCDA", "Cover size, categories"),
    READER("Reader", "\uD83D\uDCD6", "Reading mode, display, colour"),
    DOWNLOADS("Downloads", "\u2B07\uFE0F", "Queue, downloaded chapters"),
    BROWSE("Browse", "\uD83E\uDDED", "Extension repositories, global search"),
    DATA("Data and storage", "\uD83D\uDDC4\uFE0F", "Backups, storage use, caches"),
    PRIVACY("Security and privacy", "\uD83D\uDD12", "Incognito mode, secure screen"),
    ADVANCED("Advanced", "\uD83D\uDEE0\uFE0F", "Diagnostics, cookies, app info"),
}

/**
 * Settings.
 *
 * Mihon's shape — an index of sections, each opening onto its own page — with
 * two deliberate differences.
 *
 * **The sub-screen is not a branch of `YomuApp`'s routing chain.** That chain is
 * already an eight-arm `if / else if` encoding real navigation rules, and adding
 * eight more arms to it for the inside of one screen would be the worst place to
 * put them. Which section is open is local state here, so the chain gains a
 * single boolean, and back pops the section before it pops the screen.
 *
 * **The rows are led by emoji, not icons.** `material-icons-core` is about forty
 * glyphs and has none of palette / storage / shield / sliders, so every row here
 * would be an approximation of the wrong thing — the fourth time in this repo
 * that's come up. The bottom nav bar already uses emoji for exactly this reason.
 * Adding `material-icons-extended` would let all of it be done properly at once;
 * until someone decides that deliberately, this is at least consistent.
 *
 * Sections only exist where there's something real behind them, which is why
 * there is no Tracking page: nothing in this app tracks anything yet, and a row
 * that opens onto an apology is worse than no row.
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloadQueue: () -> Unit
) {
    // Stored as the enum's name rather than the enum: rememberSaveable's default
    // saver only takes what a Bundle takes, and a String is unambiguously that.
    // "" is the index.
    var openSection by rememberSaveable { mutableStateOf("") }
    val section = SettingsSection.entries.firstOrNull { it.name == openSection }

    // Explicitly typed: an `if` whose branches are assignments is a statement,
    // not an expression, and without the type annotation the compiler tries to
    // read it as this lambda's return value.
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
        SettingsSection.entries.forEach { section ->
            ListItem(
                leadingContent = {
                    Text(section.glyph, style = MaterialTheme.typography.headlineSmall)
                },
                headlineContent = { Text(section.title) },
                supportingContent = { Text(section.summary) },
                modifier = Modifier.clickable { onOpen(section) }
            )
            HorizontalDivider()
        }
    }
}

// ---------- appearance ----------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
