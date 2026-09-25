package com.mangareader.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal fun baselineDark(
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onAccent(primary),
    secondary = secondary,
    onSecondary = onAccent(secondary),
    tertiary = tertiary,
    onTertiary = onAccent(tertiary),
)

internal fun baselineLight(
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onAccent(primary),
    secondary = secondary,
    onSecondary = onAccent(secondary),
    tertiary = tertiary,
    onTertiary = onAccent(tertiary),
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
        secondary = secondary,
        onSecondary = onAccent(secondary),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
        background = background,
        onBackground = onColor,
        surface = background,
        onSurface = onColor,
        surfaceVariant = mix(background, white, 0.20f),
        onSurfaceVariant = onVariant,
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
        secondary = secondary,
        onSecondary = onAccent(secondary),
        tertiary = tertiary,
        onTertiary = onAccent(tertiary),
        background = background,
        onBackground = onColor,
        surface = background,
        onSurface = onColor,
        surfaceVariant = mix(background, black, 0.08f),
        onSurfaceVariant = onVariant,
        surfaceContainerLowest = background,
        surfaceContainerLow = mix(background, black, 0.03f),
        surfaceContainer = mix(background, black, 0.05f),
        surfaceContainerHigh = mix(background, black, 0.08f),
        surfaceContainerHighest = mix(background, black, 0.11f),
    )
}
