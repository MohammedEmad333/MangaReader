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

/**
 * The accent the whole app is tinted with.
 *
 * Each entry carries **two** colours, and that is the point of the enum rather
 * than a stored hex. A single colour cannot serve both themes: an accent light
 * enough to read on a dark surface is invisible on a white one, and vice versa.
 * The dark values sit around Material 3's tone 80 and the light ones around
 * tone 30, which is the pairing `darkColorScheme` / `lightColorScheme` already
 * assume for `onPrimary` — leaving `onPrimary` alone is what keeps a filled
 * button legible, and is why this offers a palette rather than a free picker.
 *
 * [VIOLET] is the pair the app has always shipped, so the default changes
 * nothing for anyone who never opens the setting.
 */
internal enum class AccentColor(
    val key: String,
    val label: String,
    val dark: Long,
    val light: Long
) {
    VIOLET("violet", "Violet", 0xFFB69DF8, 0xFF4F3D8A),
    BLUE("blue", "Blue", 0xFF9CC7F5, 0xFF2E4F82),
    TEAL("teal", "Teal", 0xFF87D2CC, 0xFF1F5551),
    GREEN("green", "Green", 0xFF8FD79A, 0xFF2C5C36),
    AMBER("amber", "Amber", 0xFFE6C176, 0xFF6B4E14),
    ROSE("rose", "Rose", 0xFFF0A5B8, 0xFF7D2942);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: VIOLET
    }
}

internal object AppTheme {

    private const val KEY_THEME = "app_theme"
    private const val KEY_ACCENT = "app_accent"
    private const val KEY_SECURE_SCREEN = "secure_screen"

    var mode by mutableStateOf(ThemeMode.DARK)
        private set

    /**
     * Snapshot state like [mode], and for the same reason: written from a
     * settings row eight levels down, read by `MainActivity.setContent` at the
     * root, so Compose recomposes the whole tree on its own.
     */
    var accent by mutableStateOf(AccentColor.VIOLET)
        private set

    /** Called once from `MainActivity.onCreate`, before the first composition. */
    fun load(context: Context) {
        mode = ThemeMode.from(prefs(context).getString(KEY_THEME, null))
        accent = AccentColor.from(prefs(context).getString(KEY_ACCENT, null))
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY_THEME, value.key).apply()
    }

    fun setAccent(context: Context, value: AccentColor) {
        accent = value
        prefs(context).edit().putString(KEY_ACCENT, value.key).apply()
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
    // onPrimary is deliberately left alone: every pair in [AccentColor] stays
    // well clear of it on contrast, and that pair is what keeps a filled button
    // legible. Only `primary` moves, so nothing else in the scheme has to be
    // re-checked per accent.
    val accent = AppTheme.accent
    return if (dark) {
        darkColorScheme(primary = Color(accent.dark))
    } else {
        lightColorScheme(primary = Color(accent.light))
    }
}
