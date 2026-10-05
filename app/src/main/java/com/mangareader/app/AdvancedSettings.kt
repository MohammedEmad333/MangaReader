package com.mangareader.app

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AdvancedSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var diagnosticsTitle by remember { mutableStateOf("Extension diagnostics") }
    var running by remember { mutableStateOf(false) }
    var crashPresent by remember { mutableStateOf(CrashLog.exists(context)) }
    var confirmCookies by remember { mutableStateOf(false) }

    SettingsColumn {
        SectionHeader("Diagnostics")
        SettingsActionCard(
            title = "Force a test crash",
            summary = "Throws on purpose. Reopen Yomu to verify the crash log captured the trace.",
            onClick = {
                throw IllegalStateException("Test crash from Diagnostics")
            },
        )
        SettingsActionCard(
            title = "Crash log",
            summary = if (crashPresent) {
                "The last uncaught exceptions, newest first"
            } else {
                "Nothing has crashed since this was last cleared"
            },
            onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.IO) {
                        CrashLog.read(context) ?: "No crashes recorded."
                    }
                    diagnosticsTitle = "Crash log"
                    diagnostics = report
                }
            },
            trailing = if (!crashPresent) null else {
                {
                    TextButton(onClick = {
                        CrashLog.clear(context)
                        crashPresent = false
                    }) { Text("Clear") }
                }
            },
        )
        SettingsActionCard(
            title = "Extension diagnostics",
            summary = if (running) {
                "Walking the loader…"
            } else {
                "Report every step of loading the installed extensions"
            },
            onClick = {
                if (!running) {
                    running = true
                    scope.launch {
                        val report = withContext(Dispatchers.IO) {
                            runCatching { diagnoseExtensions(context) }
                                .getOrElse {
                                    "Diagnostics failed: ${it.message ?: it::class.java.simpleName}"
                                }
                        }
                        diagnosticsTitle = "Extension diagnostics"
                        diagnostics = report
                        running = false
                    }
                }
            },
        )
        SettingsActionCard(
            title = "Startup timings",
            summary = "What the first frame waited on, and the size of the prefs file",
            onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.IO) {
                        runCatching { StartupTimings.report(context) }
                            .getOrElse {
                                "Timings failed: ${it.message ?: it::class.java.simpleName}"
                            }
                    }
                    diagnosticsTitle = "Startup timings"
                    diagnostics = report
                }
            },
        )

        SectionHeader("Network")
        SettingsActionCard(
            title = "Clear cookies",
            summary = "Signs out of every source and drops Cloudflare clearance",
            onClick = { confirmCookies = true },
        )

        SectionHeader("About")
        SettingsActionCard(
            title = "Yomu",
            summary = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                if (BuildConfig.DEBUG) " · debug" else "",
        )
        SettingsActionCard(
            title = "Sources",
            summary = "Tachiyomi / Mihon extension APKs, plus local folders",
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
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = { diagnostics = null }) { Text("Done") } },
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
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    runCatching { ClearanceUserAgents.clear(context) }
                    confirmCookies = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCookies = false }) { Text("Cancel") }
            },
        )
    }
}
