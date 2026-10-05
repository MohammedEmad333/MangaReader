package com.mangareader.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val YomuDarkBackground = Color(0xFF111318)
private val YomuDarkOnSurface = Color(0xFFE6E1E9)
private val YomuLightBackground = Color(0xFFFBF8FF)
private val YomuLightOnSurface = Color(0xFF1C1B20)

internal fun baselineDark(
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onAccent(primary),
    primaryContainer = mix(YomuDarkBackground, primary, 0.30f),
    onPrimaryContainer = YomuDarkOnSurface,
    secondary = secondary,
    onSecondary = onAccent(secondary),
    secondaryContainer = mix(YomuDarkBackground, secondary, 0.24f),
    onSecondaryContainer = YomuDarkOnSurface,
    tertiary = tertiary,
    onTertiary = onAccent(tertiary),
    tertiaryContainer = mix(YomuDarkBackground, tertiary, 0.24f),
    onTertiaryContainer = YomuDarkOnSurface,
    background = YomuDarkBackground,
    onBackground = YomuDarkOnSurface,
    surface = YomuDarkBackground,
    onSurface = YomuDarkOnSurface,
    surfaceVariant = Color(0xFF44464E),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191B20),
    surfaceContainer = Color(0xFF1D1F24),
    surfaceContainerHigh = Color(0xFF27292E),
    surfaceContainerHighest = Color(0xFF323439),
)

internal fun baselineLight(
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onAccent(primary),
    primaryContainer = mix(YomuLightBackground, primary, 0.16f),
    onPrimaryContainer = mix(Color.Black, primary, 0.28f),
    secondary = secondary,
    onSecondary = onAccent(secondary),
    secondaryContainer = mix(YomuLightBackground, secondary, 0.13f),
    onSecondaryContainer = mix(Color.Black, secondary, 0.28f),
    tertiary = tertiary,
    onTertiary = onAccent(tertiary),
    tertiaryContainer = mix(YomuLightBackground, tertiary, 0.13f),
    onTertiaryContainer = mix(Color.Black, tertiary, 0.28f),
    background = YomuLightBackground,
    onBackground = YomuLightOnSurface,
    surface = YomuLightBackground,
    onSurface = YomuLightOnSurface,
    surfaceVariant = Color(0xFFE6E1EA),
    onSurfaceVariant = Color(0xFF48464D),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFF0EDF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

internal fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)

internal fun tintedDark(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
): ColorScheme {
    val white = Color.White
    val onColor = mix(white, primary, 0.10f)
    val onVariant = mix(white, primary, 0.28f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        primaryContainer = mix(background, primary, 0.32f),
        onPrimaryContainer = onColor,
        secondary = secondary,
        onSecondary = onAccent(secondary),
        secondaryContainer = mix(background, secondary, 0.25f),
        onSecondaryContainer = onColor,
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
        tertiaryContainer = mix(background, tertiary, 0.25f),
        onTertiaryContainer = onColor,
        background = background,
        onBackground = onColor,
        surface = background,
        onSurface = onColor,
        surfaceVariant = mix(background, white, 0.20f),
        onSurfaceVariant = onVariant,
        outline = mix(background, white, 0.46f),
        outlineVariant = mix(background, white, 0.22f),
        surfaceContainerLowest = mix(background, Color.Black, 0.25f),
        surfaceContainerLow = mix(background, white, 0.06f),
        surfaceContainer = mix(background, white, 0.11f),
        surfaceContainerHigh = mix(background, white, 0.16f),
        surfaceContainerHighest = mix(background, white, 0.21f),
    )
}

internal fun tintedLight(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
): ColorScheme {
    val black = Color.Black
    val onColor = mix(black, primary, 0.16f)
    val onVariant = mix(black, primary, 0.32f)

    return lightColorScheme(
        primary = primary,
        onPrimary = onAccent(primary),
        primaryContainer = mix(background, primary, 0.16f),
        onPrimaryContainer = mix(black, primary, 0.34f),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        secondaryContainer = mix(background, secondary, 0.13f),
        onSecondaryContainer = mix(black, secondary, 0.32f),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
        tertiaryContainer = mix(background, tertiary, 0.13f),
        onTertiaryContainer = mix(black, tertiary, 0.32f),
        background = background,
        onBackground = onColor,
        surface = background,
        onSurface = onColor,
        surfaceVariant = mix(background, black, 0.08f),
        onSurfaceVariant = onVariant,
        outline = mix(background, black, 0.42f),
        outlineVariant = mix(background, black, 0.16f),
        surfaceContainerLowest = background,
        surfaceContainerLow = mix(background, black, 0.03f),
        surfaceContainer = mix(background, black, 0.05f),
        surfaceContainerHigh = mix(background, black, 0.08f),
        surfaceContainerHighest = mix(background, black, 0.11f),
    )
}
