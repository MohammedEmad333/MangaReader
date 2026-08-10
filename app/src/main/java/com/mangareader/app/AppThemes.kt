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
            background = Color(0xFF1B1A2E),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFFB4232F),
            onPrimary = onAccent(Color(0xFFB4232F)),
            secondary = Color(0xFF8C4A54),
            onSecondary = onAccent(Color(0xFF8C4A54)),
            tertiary = Color(0xFF1B6E8C),
            onTertiary = onAccent(Color(0xFF1B6E8C)),
            background = Color(0xFFFBF8FD),
            onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFFBF8FD),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFEDE0E9),
            onSurfaceVariant = Color(0xFF4C444D),
        )
    },

    /** Warm green, leaf on charcoal. */
    GREEN_APPLE("green_apple", "Green Apple") {
        override fun dark() = tintedDark(
            primary = Color(0xFF5FD068),
            secondary = Color(0xFFA7D9A0),
            tertiary = Color(0xFFE6C176),
            background = Color(0xFF14211A),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFF1F7A28),
            onPrimary = onAccent(Color(0xFF1F7A28)),
            secondary = Color(0xFF4A6B44),
            onSecondary = onAccent(Color(0xFF4A6B44)),
            tertiary = Color(0xFF7A5A12),
            onTertiary = onAccent(Color(0xFF7A5A12)),
            background = Color(0xFFF8FBF3),
            onBackground = Color(0xFF191D17),
            surface = Color(0xFFF8FBF3),
            onSurface = Color(0xFF191D17),
            surfaceVariant = Color(0xFFDEE5D8),
            onSurfaceVariant = Color(0xFF424940),
        )
    },

    /** Bright strawberry red, warm and high-contrast. */
    STRAWBERRY("strawberry", "Strawberry Daiquiri") {
        override fun dark() = tintedDark(
            primary = Color(0xFFFF5C6A),
            secondary = Color(0xFFF7A9A0),
            tertiary = Color(0xFFF3C969),
            background = Color(0xFF261A1D),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFFBB1A2A),
            onPrimary = onAccent(Color(0xFFBB1A2A)),
            secondary = Color(0xFF8C4A45),
            onSecondary = onAccent(Color(0xFF8C4A45)),
            tertiary = Color(0xFF7A5A12),
            onTertiary = onAccent(Color(0xFF7A5A12)),
            background = Color(0xFFFFF8F7),
            onBackground = Color(0xFF201A1A),
            surface = Color(0xFFFFF8F7),
            onSurface = Color(0xFF201A1A),
            surfaceVariant = Color(0xFFF3DDDD),
            onSurfaceVariant = Color(0xFF524343),
        )
    },

    /** Aubergine purple — SY's "Tako". */
    TAKO("tako", "Tako") {
        override fun dark() = tintedDark(
            primary = Color(0xFFF3B94D),
            secondary = Color(0xFFE0C08A),
            tertiary = Color(0xFF9F86D6),
            background = Color(0xFF2A2440),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFF8B6A16),
            onPrimary = onAccent(Color(0xFF8B6A16)),
            secondary = Color(0xFF5F5A70),
            onSecondary = onAccent(Color(0xFF5F5A70)),
            tertiary = Color(0xFF4F3D8A),
            onTertiary = onAccent(Color(0xFF4F3D8A)),
            background = Color(0xFFF6F4FB),
            onBackground = Color(0xFF1B1B23),
            surface = Color(0xFFF6F4FB),
            onSurface = Color(0xFF1B1B23),
            surfaceVariant = Color(0xFFE4E1F0),
            onSurfaceVariant = Color(0xFF47464F),
        )
    },

    /** Soft lavender, gentler than the default violet. */
    LAVENDER("lavender", "Lavender") {
        override fun dark() = tintedDark(
            primary = Color(0xFFC9B4FF),
            secondary = Color(0xFFD6C9F0),
            tertiary = Color(0xFF9CC7F5),
            background = Color(0xFF201C33),
        )
        override fun light() = lightColorScheme(
            primary = Color(0xFF6A4FC2),
            onPrimary = onAccent(Color(0xFF6A4FC2)),
            secondary = Color(0xFF625B70),
            onSecondary = onAccent(Color(0xFF625B70)),
            tertiary = Color(0xFF2E4F82),
            onTertiary = onAccent(Color(0xFF2E4F82)),
            background = Color(0xFFFBF8FF),
            onBackground = Color(0xFF1B1A22),
            surface = Color(0xFFFBF8FF),
            onSurface = Color(0xFF1B1A22),
            surfaceVariant = Color(0xFFE6E1F0),
            onSurfaceVariant = Color(0xFF48454F),
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

/**
 * The Material baseline dark scheme with three roles tinted — what [AppColorTheme.DEFAULT]
 * uses so it stays byte-for-byte the app's historic look. Pulled out so DEFAULT
 * reads as "baseline plus an accent" rather than repeating the full 30-field
 * scheme the baseline already provides.
 */
private fun baselineDark(primary: Color, secondary: Color, tertiary: Color) =
    darkColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
    )

private fun baselineLight(primary: Color, secondary: Color, tertiary: Color) =
    lightColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
    )

/** Channel-wise blend, [t] from 0 (all [a]) to 1 (all [b]). */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)

/**
 * A dark scheme whose SURFACES carry the theme's hue, not just its accent.
 *
 * This is what makes a theme read as itself across the whole app the way SY's
 * do — the background, the nav bar, the cards are all a tint of one coloured
 * dark, not a shared neutral grey with a coloured button on top. Give it the
 * three accents and ONE [background] colour (a dark, saturated version of the
 * theme's hue) and it derives the rest:
 *
 * - the surface roles step from [background] a little way toward white, so the
 *   nav bar and raised cards sit just above the page while keeping the hue;
 * - `surfaceVariant` is that same step, used by the swatch card and chips;
 * - the `on-` text roles are near-white with a trace of the hue mixed back in,
 *   which reads as part of the theme rather than pure grey text dropped on top.
 *
 * The accents' `on-` roles are still luminance-derived by [onAccent], the same
 * rule the rest of the file uses, so a filled button stays legible whatever the
 * accent is.
 */
private fun tintedDark(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
): ColorScheme {
    val white = Color.White
    val onColor = mix(white, primary, 0.10f)          // near-white, faint hue
    val onVariant = mix(white, primary, 0.28f)        // dimmer, for supporting text
    return darkColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
        background = background,
        onBackground = onColor,
        surface = background,
        onSurface = onColor,
        surfaceVariant = mix(background, white, 0.14f),
        onSurfaceVariant = onVariant,
        // The container ladder M3 1.3 uses for the nav bar, sheets and cards.
        // Each step is a little further toward white, so elevation still reads
        // while every level keeps the theme's colour.
        surfaceContainerLowest = mix(background, Color.Black, 0.30f),
        surfaceContainerLow = mix(background, white, 0.04f),
        surfaceContainer = mix(background, white, 0.08f),
        surfaceContainerHigh = mix(background, white, 0.12f),
        surfaceContainerHighest = mix(background, white, 0.16f),
    )
}
