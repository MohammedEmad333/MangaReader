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
@Composable
private fun PrivacySettings() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(isIncognito(context)) }
    var secure by remember { mutableStateOf(AppTheme.secureScreen(context)) }

    SettingsColumn {
        SectionHeader("Privacy")
        PrefSwitchRow(
            title = "Incognito mode",
            checked = incognito,
            summary = "Pause reading-history logging"
        ) {
            incognito = it
            prefs(context).edit().putBoolean("incognito", it).apply()
        }
        PrefSwitchRow(
            title = "Secure screen",
            checked = secure,
            summary = "Blank the app in recents and block screenshots"
        ) {
            secure = it
            AppTheme.setSecureScreen(context, it)
        }
        PrefNote(
            "Secure screen takes effect immediately and is reapplied on every " +
                "launch. It can't hide anything already saved \u2014 downloads and " +
                "history are separate."
        )
    }
}

// ---------- advanced ----------

@Composable
private fun AdvancedSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var diagnosticsTitle by remember { mutableStateOf("Extension diagnostics") }
    var running by remember { mutableStateOf(false) }
    // Seeded once from disk rather than read per recomposition: this is a file
    // stat on the composition thread, and the answer only changes when the
    // process has died in between, which means a fresh composition anyway.
    var crashPresent by remember { mutableStateOf(CrashLog.exists(context)) }
    var confirmCookies by remember { mutableStateOf(false) }

    SettingsColumn {
        SectionHeader("Diagnostics")
        ListItem(
            headlineContent = { Text("Force a test crash") },
            supportingContent = {
                Text(
                    "Throws on purpose. The app will close; reopen it and the " +
                        "crash log should hold the trace"
                )
            },
            modifier = Modifier.clickable {
                // Deliberately outside runCatching, and deliberately not on a
                // background thread: this has to reach the default uncaught
                // handler by the shortest path there is, or a blank log after
                // tapping it would be ambiguous between "handler broken" and
                // "this particular route doesn't reach it".
                throw IllegalStateException("Test crash from Diagnostics")
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Crash log") },
            supportingContent = {
                Text(
                    if (crashPresent) "The last uncaught exceptions, newest first"
                    else "Nothing has crashed since this was last cleared"
                )
            },
            modifier = Modifier.clickable {
                scope.launch {
                    val report = withContext(Dispatchers.IO) {
                        CrashLog.read(context) ?: "No crashes recorded."
                    }
                    diagnosticsTitle = "Crash log"
                    diagnostics = report
                }
            },
            trailingContent = {
                if (crashPresent) {
                    TextButton(onClick = {
                        CrashLog.clear(context)
                        crashPresent = false
                    }) { Text("Clear") }
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Extension diagnostics") },
            supportingContent = {
                Text(
                    if (running) "Walking the loader\u2026"
                    else "Report every step of loading the installed extensions"
                )
            },
            modifier = Modifier.clickable {
                if (running) return@clickable
                running = true
                scope.launch {
                    // Classloading, on the loader's own path. Not a main-thread job.
                    val report = withContext(Dispatchers.IO) {
                        runCatching { diagnoseExtensions(context) }
                            .getOrElse { "Diagnostics failed: ${it.message ?: it::class.java.simpleName}" }
                    }
                    diagnosticsTitle = "Extension diagnostics"
                    diagnostics = report
                    running = false
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Startup timings") },
            supportingContent = {
                Text("What the first frame waited on, and the size of the prefs file")
            },
            modifier = Modifier.clickable {
                scope.launch {
                    // Stats a file and reads the whole prefs map. Warm by now,
                    // but still not a main-thread job.
                    val report = withContext(Dispatchers.IO) {
                        runCatching { StartupTimings.report(context) }
                            .getOrElse {
                                "Timings failed: ${it.message ?: it::class.java.simpleName}"
                            }
                    }
                    diagnosticsTitle = "Startup timings"
                    diagnostics = report
                }
            }
        )
        HorizontalDivider()

        SectionHeader("Network")
        ListItem(
            headlineContent = { Text("Clear cookies") },
            supportingContent = {
                Text("Signs out of every source and drops Cloudflare clearance")
            },
            modifier = Modifier.clickable { confirmCookies = true }
        )
        HorizontalDivider()

        SectionHeader("About")
        ListItem(
            headlineContent = { Text("Yomu") },
            supportingContent = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        if (BuildConfig.DEBUG) " \u00b7 debug" else ""
                )
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Sources") },
            supportingContent = { Text("Tachiyomi / Mihon extension APKs, plus local folders") }
        )
    }

    val report = diagnostics
    if (report != null) {
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text(diagnosticsTitle) },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = { diagnostics = null }) { Text("Done") } }
        )
    }

    if (confirmCookies) {
        AlertDialog(
            onDismissRequest = { confirmCookies = false },
            title = { Text("Clear cookies?") },
            text = {
                Text(
                    "Any source you're logged into will need logging in again, and a " +
                        "Cloudflare challenge you've already passed will come back. " +
                        "Useful when a source starts refusing requests that used to work."
                )
            },
            confirmButton = {
                Button(onClick = {
                    // The cookie jar is backed by the WebView's CookieManager, so
                    // this is the one call that clears both OkHttp's cookies and
                    // the WebView's - they are the same store.
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    // The UA a challenge was solved under is NOT in that store.
                    // It lives in its own prefs and is presented per host on
                    // every request, so leaving it behind meant this button
                    // promised a challenge would come back while the app went
                    // on identifying itself exactly as it had when it passed.
                    // It also left a state no fresh install can reach: no
                    // clearance, but a UA earned by a challenge that is gone.
                    runCatching { ClearanceUserAgents.clear(context) }
                    confirmCookies = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCookies = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- shared rows ----------
//
// Deliberately not the reader sheet's ChipRow / SwitchRow / SliderRow, which are
// private to ReaderScreen.kt and would only need widening to be shared. The
// reader is the least-tested file in the repo right now, and re-delivering all
// 634 lines of it to change three visibility keywords is a poor trade for fifty
// lines of trivial layout. If the reader settles, these are the obvious merge.

@Composable
internal fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackButton(onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
    HorizontalDivider()
}

@Composable
internal fun PrefChipRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    // Scrolls rather than wraps: FlowRow is still experimental on this Compose
    // version, same as the genre chips and the reader's own sheet.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
internal fun PrefSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    onChange: (Boolean) -> Unit
) {
    // Typed explicitly: without it the lambda is inferred as a plain () -> Unit
    // and won't fit ListItem's composable slot. Bound to a local first so the
    // null check and the lambda that uses it can't disagree.
    val s = summary
    val supporting: (@Composable () -> Unit)? =
        if (s == null) null else { { Text(s) } }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        // The whole row toggles, not just the switch: a 48dp target at the far
        // edge of the screen is the hardest thing on this page to hit one-handed.
        modifier = Modifier.clickable { onChange(!checked) }
    )
    HorizontalDivider()
}

@Composable
internal fun PrefSliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        steps = steps
    )
}

@Composable
internal fun PrefNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    )
}
