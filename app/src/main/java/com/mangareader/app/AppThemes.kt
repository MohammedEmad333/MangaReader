package com.mangareader.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Full named colour themes, the SY model.
 *
 * The old [AccentColor] enum tinted ONE role — `primary` — and left every
 * surface, background and secondary at the Material 3 baseline. That is an
 * accent picker, not a theme picker: every "theme" was the same grey app with a
 * different button colour. SY's themes are complete schemes — a Midnight Dusk is
 * a desaturated pink sitting on a deep navy surface, not pink-on-grey — so each
 * entry here carries a WHOLE [ColorScheme] for light and for dark, and switching
 * theme repaints the surfaces, not just the accent.
 *
 * Why a scheme per variant rather than a seed colour run through a palette
 * generator: the generator (the same machinery `dynamicColorScheme` uses) needs
 * the Material colour-utilities dependency and produces tonally-correct but
 * generic results — every theme ends up the same lightness with a different hue,
 * which is exactly the flatness this card exists to fix. Hand-set surfaces are
 * what make Midnight Dusk read as navy and Tako as aubergine rather than as two
 * tints of the same grey.
 *
 * Each theme fills the roles the app actually paints with. What is NOT set here
 * inherits the M3 baseline for that variant, which is deliberate: `error` /
 * `onError` are left alone so a failure looks like a failure in every theme
 * rather than blending into the accent, the same reason [yomuColorScheme] has
 * always kept them.
 *
 * onPrimary / onSecondary / onTertiary are computed from their backing colour by
 * [onAccent] (black or white by luminance) rather than stored, for the reason
 * spelled out at length in [yomuColorScheme]: those roles have no meaning beyond
 * "legible on top of this", so deriving them is the definition, not a shortcut,
 * and it means a new theme cannot ship an illegible filled button by forgetting
 * a field.
 */
internal enum class AppColorTheme(
    val key: String,
    val label: String,
) {
    /**
     * The palette the app shipped before this card: Material baseline dark/light
     * with the lavender accent. Kept first and as the fallback so a user who
     * never opens the picker sees exactly what they saw before.
     */
    DEFAULT("default", "Default") {
        override fun dark() = baselineDark(
            primary = Color(0xFFB69DF8),
            secondary = Color(0xFFCBC2DB),
            tertiary = Color(0xFFF0A5B8),
        )
        override fun light() = baselineLight(
            primary = Color(0xFF4F3D8A),
            secondary = Color(0xFF5F5A70),
            tertiary = Color(0xFF7D2942),
        )
    },

    /** Desaturated rose on deep navy. SY's most recognisable theme. */
    MIDNIGHT_DUSK("midnight_dusk", "Midnight Dusk") {
        override fun dark() = tintedDark(
            primary = Color(0xFFFF6E86),
            secondary = Color(0xFFF3A3B0),
            tertiary = Color(0xFF87CEEB),
            background = Color(0xFF1A1830),
        )
        override fun light() = tintedLight(
            primary = Color(0xFFB4232F),
            secondary = Color(0xFF8C4A54),
            tertiary = Color(0xFF1B6E8C),
            background = Color(0xFFFBF2F4),
        )
    },

    /** Ocean blue on deep navy. SY's Tidal Wave. */
    TIDAL_WAVE("tidal_wave", "Tidal Wave") {
        override fun dark() = tintedDark(
            primary = Color(0xFF4FC3F7),
            secondary = Color(0xFF90CAF9),
            tertiary = Color(0xFF80DEEA),
            background = Color(0xFF0E1B2E),
        )
        override fun light() = tintedLight(
            primary = Color(0xFF00659B),
            secondary = Color(0xFF4A6472),
            tertiary = Color(0xFF006874),
            background = Color(0xFFEFF6FC),
        )
    },

    /** Warm green, leaf on charcoal. */
    GREEN_APPLE("green_apple", "Green Apple") {
        override fun dark() = tintedDark(
            primary = Color(0xFF5FD068),
            secondary = Color(0xFFA7D9A0),
            tertiary = Color(0xFFE6C176),
            background = Color(0xFF0F2417),
        )
        override fun light() = tintedLight(
            primary = Color(0xFF1F7A28),
            secondary = Color(0xFF4A6B44),
            tertiary = Color(0xFF7A5A12),
            background = Color(0xFFF3FAF2),
        )
    },

    /** Bright strawberry red, warm and high-contrast. */
    STRAWBERRY("strawberry", "Strawberry Daiquiri") {
        override fun dark() = tintedDark(
            primary = Color(0xFFFF5C6A),
            secondary = Color(0xFFF7A9A0),
            tertiary = Color(0xFFF3C969),
            background = Color(0xFF2E1519),
        )
        override fun light() = tintedLight(
            primary = Color(0xFFBB1A2A),
            secondary = Color(0xFF8C4A45),
            tertiary = Color(0xFF7A5A12),
            background = Color(0xFFFEF2F1),
        )
    },

    /** Aubergine purple — SY's "Tako". */
    TAKO("tako", "Tako") {
        override fun dark() = tintedDark(
            primary = Color(0xFFF3B94D),
            secondary = Color(0xFFE0C08A),
            tertiary = Color(0xFF9F86D6),
            background = Color(0xFF241A3D),
        )
        override fun light() = tintedLight(
            primary = Color(0xFF8B6A16),
            secondary = Color(0xFF5F5A70),
            tertiary = Color(0xFF4F3D8A),
            background = Color(0xFFF5F2FB),
        )
    },

    /** Soft lavender, gentler than the default violet. */
    LAVENDER("lavender", "Lavender") {
        override fun dark() = tintedDark(
            primary = Color(0xFFC9B4FF),
            secondary = Color(0xFFD6C9F0),
            tertiary = Color(0xFF9CC7F5),
            background = Color(0xFF1E1940),
        )
        override fun light() = tintedLight(
            primary = Color(0xFF6A4FC2),
            secondary = Color(0xFF625B70),
            tertiary = Color(0xFF2E4F82),
            background = Color(0xFFF6F3FE),
        )
    },

    /** Near-monochrome, a single blue spark. SY's "Yin & Yang". */
    YIN_YANG("yin_yang", "Yin & Yang") {
        override fun dark() = darkColorScheme(
            primary = Color(0xFFE3E3E3),
            onPrimary = onAccent(Color(0xFFE3E3E3)),
            secondary = Color(0xFFB8B8B8),
            onSecondary = onAccent(Color(0xFFB8B8B8)),
            tertiary = Color(0xFF9CC7F5),
            onTertiary = onAccent(Color(0xFF9CC7F5)),
            background = Color(0xFF121212),
            onBackground = Color(0xFFE3E3E3),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFE3E3E3),
            surfaceVariant = Color(0xFF2B2B2B),
            onSurfaceVariant = Color(0xFFC7C7C7),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFF2B2B2B),
            onPrimary = onAccent(Color(0xFF2B2B2B)),
            secondary = Color(0xFF5A5A5A),
            onSecondary = onAccent(Color(0xFF5A5A5A)),
            tertiary = Color(0xFF2E4F82),
            onTertiary = onAccent(Color(0xFF2E4F82)),
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFAFAFA),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFE4E4E4),
            onSurfaceVariant = Color(0xFF474747),
        )
    };

    abstract fun dark(): ColorScheme
    abstract fun light(): ColorScheme

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
