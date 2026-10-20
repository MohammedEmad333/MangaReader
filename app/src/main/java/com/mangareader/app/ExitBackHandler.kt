package com.mangareader.app

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val EXIT_CONFIRM_MS = 2000L

/** Decide root-level back behavior. Child screens and dialogs own their BackHandlers. */
internal enum class RootBackAction { HOME, CONFIRM_EXIT, EXIT }

internal fun rootBackAction(currentTab: Int, previousBackAt: Long, now: Long): RootBackAction =
    when {
        currentTab != 0 -> RootBackAction.HOME
        previousBackAt > 0L && now >= previousBackAt &&
            now - previousBackAt < EXIT_CONFIRM_MS -> RootBackAction.EXIT
        else -> RootBackAction.CONFIRM_EXIT
    }

@Composable
internal fun DoubleBackToExitHandler(
    activity: ComponentActivity?,
    currentTab: Int,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    var backArmedAt by remember { mutableLongStateOf(0L) }

    // A previous exit confirmation must not remain armed after switching tabs.
    LaunchedEffect(currentTab) { backArmedAt = 0L }

    BackHandler {
        val now = SystemClock.elapsedRealtime()
        when (rootBackAction(currentTab, backArmedAt, now)) {
            RootBackAction.HOME -> {
                backArmedAt = 0L
                onHome()
            }
            RootBackAction.EXIT -> {
                backArmedAt = 0L
                activity?.finish()
            }
            RootBackAction.CONFIRM_EXIT -> {
                backArmedAt = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
