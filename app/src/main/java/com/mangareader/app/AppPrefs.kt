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
import androidx.compose.ui.graphics.luminance
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
 * SUPERSEDED by [AppColorTheme] in AppThemes.kt as of the theme-settings card.
 * That enum carries a whole [ColorScheme] per variant instead of a single accent
 * pair, so surfaces move with the theme rather than every theme being the same
 * grey with a different button. This is kept only so the one-time preference
 * migration in [AppTheme.load] can read a user's old accent key and map it to the
 * nearest new theme; nothing else references it and it can be deleted once that
 * migration has shipped for long enough.
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

        /**
         * The theme a pre-migration accent maps to. Only the accents that have a
         * clear new home are mapped; the rest fall to [AppColorTheme.DEFAULT],
         * which is the old baseline-plus-lavender look, so no one is moved
         * somewhere jarring. Green → Green Apple and Rose → Strawberry are the
         * two obvious ones; Violet was the default and stays Default.
         */
        fun toTheme(key: String?): AppColorTheme = when (from(key)) {
            GREEN -> AppColorTheme.GREEN_APPLE
            ROSE -> AppColorTheme.STRAWBERRY
            else -> AppColorTheme.DEFAULT
        }
    }
}

internal object AppTheme {

    private const val KEY_THEME = "app_theme"
    private const val KEY_ACCENT = "app_accent"
    private const val KEY_COLOR_THEME = "app_color_theme"
    private const val KEY_AMOLED = "app_amoled"
    private const val KEY_SECURE_SCREEN = "secure_screen"

    var mode by mutableStateOf(ThemeMode.DARK)
        private set

    /**
     * The full colour scheme, snapshot state like [mode] and for the same
     * reason: written from a settings row deep in the tree, read by
     * `MainActivity.setContent` at the root, so Compose repaints the whole app
     * on its own when it changes.
     */
    var colorTheme by mutableStateOf(AppColorTheme.DEFAULT)
        private set

    /**
     * Pure-black surfaces in dark mode, for OLED screens. Only bites when the
     * resolved variant is dark — see [yomuColorScheme]. Snapshot state so the
     * toggle repaints instantly.
     */
    var amoled by mutableStateOf(false)
        private set

    /** Called once from `MainActivity.onCreate`, before the first composition. */
    fun load(context: Context) {
        val p = prefs(context)
        mode = ThemeMode.from(p.getString(KEY_THEME, null))

        // One-time migration off the old accent-only setting. If the new key is
        // absent but an old accent was stored, carry the user to the nearest new
        // theme and write it forward, so this runs once. A fresh install has
        // neither key and lands on DEFAULT, which is the historic look anyway.
        colorTheme = when {
            p.contains(KEY_COLOR_THEME) ->
                AppColorTheme.from(p.getString(KEY_COLOR_THEME, null))
            p.contains(KEY_ACCENT) -> {
                val migrated = AccentColor.toTheme(p.getString(KEY_ACCENT, null))
                p.edit().putString(KEY_COLOR_THEME, migrated.key).apply()
                migrated
            }
            else -> AppColorTheme.DEFAULT
        }

        amoled = p.getBoolean(KEY_AMOLED, false)
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY_THEME, value.key).apply()
    }

    fun setColorTheme(context: Context, value: AppColorTheme) {
        colorTheme = value
        prefs(context).edit().putString(KEY_COLOR_THEME, value.key).apply()
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        prefs(context).edit().putBoolean(KEY_AMOLED, value).apply()
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

/**
 * The scheme `MainActivity` wraps the app in. Recomposes when [AppTheme.mode],
 * [AppTheme.colorTheme] or [AppTheme.amoled] change.
 *
 * Three inputs, resolved in order: the theme supplies a full light and dark
 * scheme, [mode] chooses which, and AMOLED — only when the chosen one is dark —
 * drops the backgrounds and surfaces to true black. The accent survives the
 * AMOLED override untouched, which is the whole point of AMOLED: black canvas,
 * theme colour still on the buttons.
 */
@Composable
internal fun yomuColorScheme(): ColorScheme {
    val dark = when (AppTheme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val theme = AppTheme.colorTheme
    val scheme = if (dark) theme.dark() else theme.light()

    // AMOLED only touches dark. On a light theme "pure black" is meaningless,
    // and forcing it would just break the light scheme.
    return if (dark && AppTheme.amoled) scheme.toAmoled() else scheme
}

/**
 * The same scheme with every canvas role forced to true black.
 *
 * Only the surfaces move — `background`, `surface`, and the surface-tint roles a
 * dark theme actually paints large areas with. The accent roles (`primary`,
 * `secondary`, `tertiary` and their `on-` pairs) and `onSurface` are left as the
 * theme set them, so text stays legible and the buttons keep the theme's colour
 * against the black. `surfaceVariant` becomes a near-black rather than #000000 so
 * a chip or a divider drawn on it is still faintly distinguishable from the page
 * behind it — pure-black-on-pure-black would erase those edges entirely.
 */
private fun ColorScheme.toAmoled(): ColorScheme {
    val black = Color(0xFF000000)
    val nearBlack = Color(0xFF0A0A0A)
    return copy(
        background = black,
        onBackground = onBackground,
        surface = black,
        onSurface = onSurface,
        surfaceVariant = nearBlack,
        surfaceContainerLowest = black,
        surfaceContainerLow = black,
        surfaceContainer = nearBlack,
        surfaceContainerHigh = nearBlack,
        surfaceContainerHighest = nearBlack,
    )
}

/** Black or white, whichever stays legible on [background]. */
internal fun onAccent(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
