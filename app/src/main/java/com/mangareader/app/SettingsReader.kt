package com.mangareader.app

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Reading mode",
                options = ReaderMode.entries.map { it.label },
                selected = ReaderMode.entries.indexOf(settings.mode),
                onSelect = { update(settings.copy(mode = ReaderMode.entries[it])) }
            )
            PrefChipRow(
                label = "Rotation",
                options = ReaderRotation.entries.map { it.label },
                selected = ReaderRotation.entries.indexOf(settings.rotation),
                onSelect = { update(settings.copy(rotation = ReaderRotation.entries[it])) }
            )
            PrefSliderRow(
                label = "Side padding",
                value = settings.sidePadding.toFloat(),
                valueLabel = "${settings.sidePadding}%",
                range = 0f..25f,
                steps = 4,
                onChange = { update(settings.copy(sidePadding = it.toInt())) }
            )
        }

        SectionHeader("Screen")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrefChipRow(
                label = "Background",
                options = ReaderBackground.entries.map { it.label },
                selected = ReaderBackground.entries.indexOf(settings.background),
                onSelect = { update(settings.copy(background = ReaderBackground.entries[it])) }
            )
        }
        Spacer(Modifier.height(4.dp))
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
            summary = "Off means the system brightness applies"
        ) {
            update(settings.copy(customBrightness = it))
        }
        if (settings.customBrightness) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrefSliderRow(
                    label = "Brightness",
                    value = settings.brightness,
                    valueLabel = "${(settings.brightness * 100).toInt()}%",
                    range = 0.01f..1f,
                    steps = 0,
                    onChange = { update(settings.copy(brightness = it)) }
                )
            }
        }
        PrefNote(
            "These are the values every chapter opens with. The reader's own " +
                "settings button writes to the same store, so a change made there " +
                "shows up here and the other way round."
        )
    }
}
