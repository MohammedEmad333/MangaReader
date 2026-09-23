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

@Composable
internal fun PrivacySettings() {
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
internal fun AdvancedSettings() {
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
