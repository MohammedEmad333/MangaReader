package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ReaderDefaultsSettings() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }

    fun update(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    SettingsColumn {
        SectionHeader("Layout")
        SettingsPanel {
            PrefChipRow(
                label = "Reading mode",
                options = ReaderMode.entries.map { it.label },
                selected = ReaderMode.entries.indexOf(settings.mode),
                onSelect = { update(settings.copy(mode = ReaderMode.entries[it])) },
            )
            PrefChipRow(
                label = "Rotation",
                options = ReaderRotation.entries.map { it.label },
                selected = ReaderRotation.entries.indexOf(settings.rotation),
                onSelect = { update(settings.copy(rotation = ReaderRotation.entries[it])) },
            )
            PrefSliderRow(
                label = "Side padding",
                value = settings.sidePadding.toFloat(),
                valueLabel = "${settings.sidePadding}%",
                range = 0f..25f,
                steps = 4,
                onChange = { update(settings.copy(sidePadding = it.toInt())) },
            )
        }

        SectionHeader("Screen")
        SettingsPanel {
            PrefChipRow(
                label = "Background",
                options = ReaderBackground.entries.map { it.label },
                selected = ReaderBackground.entries.indexOf(settings.background),
                onSelect = { update(settings.copy(background = ReaderBackground.entries[it])) },
            )
        }
        PrefSwitchRow("Show page number", settings.showPageNumber) {
            update(settings.copy(showPageNumber = it))
        }
        PrefSwitchRow("Fullscreen", settings.fullscreen) {
            update(settings.copy(fullscreen = it))
        }
        PrefSwitchRow("Keep screen on", settings.keepScreenOn) {
            update(settings.copy(keepScreenOn = it))
        }

        SectionHeader("Colour")
        PrefSwitchRow("Grayscale", settings.grayscale) {
            update(settings.copy(grayscale = it))
        }
        PrefSwitchRow("Invert colours", settings.inverted) {
            update(settings.copy(inverted = it))
        }
        PrefSwitchRow(
            title = "Custom brightness",
            checked = settings.customBrightness,
            summary = "Off means the system brightness applies",
        ) {
            update(settings.copy(customBrightness = it))
        }
        if (settings.customBrightness) {
            SettingsPanel {
                PrefSliderRow(
                    label = "Brightness",
                    value = settings.brightness,
                    valueLabel = "${(settings.brightness * 100).toInt()}%",
                    range = 0.01f..1f,
                    steps = 0,
                    onChange = { update(settings.copy(brightness = it)) },
                )
            }
        }

        PrefNote(
            "These are the values every chapter opens with. The reader's own " +
                "settings button writes to the same store, so a change made there " +
                "shows up here and the other way round.",
        )
    }
}
