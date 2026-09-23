package com.mangareader.app

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val EXIT_CONFIRM_MS = 2000L

@Composable
internal fun DoubleBackToExitHandler(activity: ComponentActivity?) {
    val context = LocalContext.current
    var backArmedAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - backArmedAt < EXIT_CONFIRM_MS) {
            activity?.finish()
        } else {
            backArmedAt = now
            Toast.makeText(
                context,
                "Press back again to exit",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
