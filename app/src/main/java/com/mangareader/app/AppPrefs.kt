package com.mangareader.app

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide settings that aren't the reader's — see [ReaderPrefs] for those.
 *
 * The theme lives in process-wide Compose snapshot state rather than in a
 * `remember` somewhere, for the same reason [DownloadQueue] does: the thing that
 * *reads* it is `MainActivity.setContent`, at the very root of the tree, and the
 * thing that *writes* it is a settings screen eight levels down. Hoisting it all
 * the way up through `YomuApp` would mean threading a parameter through every
 * screen in between to change one colour scheme. An object holding
 * `mutableStateOf` is read by the root and written by the leaf, and Compose
 * recomposes the root on its own.
 */
internal enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        /**
         * Defaults to [DARK], not [SYSTEM].
         *
         * Every screen in this app has only ever been rendered against
         * `darkColorScheme()`, so "follow system" would flip an existing user on
         * a light phone into a palette nothing has been checked in. Dark keeps
         * the current behaviour exactly, and light is there for whoever asks.
         */
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: DARK
    }
}

internal object AppTheme {

    private const val KEY_THEME = "app_theme"
    private const val KEY_SECURE_SCREEN = "secure_screen"

    var mode by mutableStateOf(ThemeMode.DARK)
        private set

    /** Called once from `MainActivity.onCreate`, before the first composition. */
    fun load(context: Context) {
        mode = ThemeMode.from(prefs(context).getString(KEY_THEME, null))
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY_THEME, value.key).apply()
    }

    // ---- secure screen ----
    //
    // FLAG_SECURE is a window flag, not a compose value: it keeps the app out of
    // the recents thumbnail and blocks screenshots. It's read at Activity start
    // and applied again the moment it's toggled, so it doesn't wait for a restart.

    fun secureScreen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SECURE_SCREEN, false)

    fun setSecureScreen(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SECURE_SCREEN, value).apply()
        applySecureScreen(context, value)
    }

    /** No-op when [context] isn't an Activity — there's no window to flag. */
    fun applySecureScreen(context: Context, value: Boolean) {
        val window = (context as? ComponentActivity)?.window ?: return
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** The scheme `MainActivity` wraps the app in. Recomposes when [AppTheme.mode] changes. */
@Composable
internal fun yomuColorScheme(): ColorScheme {
    val dark = when (AppTheme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Primary is darkened a step from the Material 3 baseline. It is the accent
    // on the Resume button, the filter icon while filtering, and the saved-page
    // line on a chapter row, so it sits against the surface far more often than
    // it sits behind text — and the baseline lavender is bright enough on a
    // dark surface to pull the eye off the covers.
    //
    // onPrimary is deliberately left alone: both replacements stay well clear of
    // it on contrast, and the pair is what keeps a filled button legible.
    return if (dark) {
        darkColorScheme(primary = Color(0xFFB69DF8))
    } else {
        lightColorScheme(primary = Color(0xFF4F3D8A))
    }
}
