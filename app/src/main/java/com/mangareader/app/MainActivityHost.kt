package com.mangareader.app

import android.content.pm.PackageManager
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
        setTheme(R.style.Theme_Yomu)
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()

        // Theme is the one persisted value that must be ready before the first
        // composition or a light/custom-theme user gets a visible colour flash.
        // Everything else is hydrated by StartupUiSnapshot off the UI thread.
        StartupTimings.once("Theme prefs load (onCreate)") {
            AppTheme.load(this)
        }
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
