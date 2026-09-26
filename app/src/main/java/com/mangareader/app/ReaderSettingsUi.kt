package com.mangareader.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    useGlobalDefaults: Boolean,
    canOverrideSeries: Boolean,
    onUseGlobalDefaultsChange: (Boolean) -> Unit,
    onChange: (ReaderSettings) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Layout") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Screen") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Colour") })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (canOverrideSeries) {
                SwitchRow("Use global defaults", useGlobalDefaults) {
                    onUseGlobalDefaultsChange(it)
                }
                Text(
                    if (useGlobalDefaults) {
                        "Changes here update the defaults used by every series."
                    } else {
                        "Changes here apply only to this series."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when (tab) {
                0 -> {
                    ChipRow(
                        label = "Reading mode",
                        options = ReaderMode.entries.map { it.label },
                        selected = ReaderMode.entries.indexOf(settings.mode),
                        onSelect = { onChange(settings.copy(mode = ReaderMode.entries[it])) }
                    )
                    ChipRow(
                        label = "Rotation",
                        options = ReaderRotation.entries.map { it.label },
                        selected = ReaderRotation.entries.indexOf(settings.rotation),
                        onSelect = { onChange(settings.copy(rotation = ReaderRotation.entries[it])) }
                    )
                    ChipRow(
                        label = "Page slider",
                        options = ReaderSliderPosition.entries.map { it.label },
                        selected = ReaderSliderPosition.entries.indexOf(settings.sliderPosition),
                        onSelect = {
                            onChange(
                                settings.copy(sliderPosition = ReaderSliderPosition.entries[it])
                            )
                        }
                    )
                    SliderRow(
                        label = "Side padding",
                        value = settings.sidePadding.toFloat(),
                        valueLabel = "${settings.sidePadding}%",
                        range = 0f..25f,
                        steps = 4,
                        onChange = { onChange(settings.copy(sidePadding = it.toInt())) }
                    )
                    if (settings.mode != ReaderMode.LONG_STRIP) {
                        SwitchRow("Tap zones", settings.tapZones) {
                            onChange(settings.copy(tapZones = it))
                        }
                        if (settings.tapZones) {
                            Text(
                                "Outer thirds turn pages; the centre third toggles controls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (settings.mode == ReaderMode.LONG_STRIP) {
                        SliderRow(
                            label = "Page gap",
                            value = settings.pageGap.toFloat(),
                            valueLabel = "${settings.pageGap} dp",
                            range = 0f..24f,
                            steps = 5,
                            onChange = { onChange(settings.copy(pageGap = it.toInt())) }
                        )
                    }
                }
                1 -> {
                    ChipRow(
                        label = "Background",
                        options = ReaderBackground.entries.map { it.label },
                        selected = ReaderBackground.entries.indexOf(settings.background),
                        onSelect = {
                            onChange(settings.copy(background = ReaderBackground.entries[it]))
                        }
                    )
                    SwitchRow("Show page number", settings.showPageNumber) {
                        onChange(settings.copy(showPageNumber = it))
                    }
                    SwitchRow("Fullscreen", settings.fullscreen) {
                        onChange(settings.copy(fullscreen = it))
                    }
                    SwitchRow("Keep screen on", settings.keepScreenOn) {
                        onChange(settings.copy(keepScreenOn = it))
                    }
                }
                else -> {
                    SwitchRow("Grayscale", settings.grayscale) {
                        onChange(settings.copy(grayscale = it))
                    }
                    SwitchRow("Invert colours", settings.inverted) {
                        onChange(settings.copy(inverted = it))
                    }
                    SwitchRow("Custom brightness", settings.customBrightness) {
                        onChange(settings.copy(customBrightness = it))
                    }
                    if (settings.customBrightness) {
                        SliderRow(
                            label = "Brightness",
                            value = settings.brightness,
                            valueLabel = "${(settings.brightness * 100).toInt()}%",
                            range = 0.01f..1f,
                            steps = 0,
                            onChange = { onChange(settings.copy(brightness = it)) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    // Horizontal scroll rather than a wrap: FlowRow is still experimental on
    // this Compose version, which is the same reason the genre chips on the
    // series screen scroll.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        steps = steps
    )
}

@Composable
internal fun ReaderBackground.toColor(): Color = when (this) {
    ReaderBackground.BLACK -> Color.Black
    ReaderBackground.GRAY -> Color(0xFF2B2B2B)
    ReaderBackground.WHITE -> Color.White
    ReaderBackground.THEME -> MaterialTheme.colorScheme.background
}

/**
 * Grayscale and inversion as a single colour matrix.
 *
 * The two-filter case is a precomputed matrix rather than one applied after the
 * other: composing matrices needs an operator whose argument order is easy to
 * get backwards, and inverted luminance is short enough to write out.
 */
internal fun readerColorFilter(grayscale: Boolean, inverted: Boolean): ColorFilter? = when {
    grayscale && inverted -> ColorFilter.colorMatrix(ColorMatrix(GRAY_INVERT_MATRIX))
    grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    inverted -> ColorFilter.colorMatrix(ColorMatrix(INVERT_MATRIX))
    else -> null
}

private val INVERT_MATRIX = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Rec. 709 luminance weights, negated, with the offset that inverts them. */
private val GRAY_INVERT_MATRIX = floatArrayOf(
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)
