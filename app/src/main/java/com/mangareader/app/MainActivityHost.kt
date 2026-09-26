package com.mangareader.app

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme is the one persisted value that has to be known before Android
        // creates the Activity window. Reading it after super.onCreate() leaves
        // light-theme users with a dark window/status bar until Compose paints.
        StartupTimings.once("Theme prefs load (onCreate)") {
            AppTheme.load(this)
        }
        val darkWindow = when (AppTheme.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (darkWindow) R.style.Theme_Yomu else R.style.Theme_Yomu_Light)
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()
        AppTheme.applySecureScreen(this, AppTheme.secureScreen(this))

        setContent {
            MaterialTheme(colorScheme = yomuColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YomuApp()
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        runCatching { requestPermissions(arrayOf(permission), 1) }
    }
}
