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
internal fun AppearanceSettings() {
    val context = LocalContext.current

    SettingsColumn {
        SectionHeader("Theme")

        // Segmented control, not three chips: System/Light/Dark are one mutually
        // exclusive choice, and the connected pill is how SY (and Material) show
        // that. Chips read as independent toggles.
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            ThemeMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = AppTheme.mode == m,
                    onClick = { AppTheme.setMode(context, m) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    label = { Text(m.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // The preview cards. Each renders in its OWN scheme — a little mock of a
        // title bar, a two-swatch card and a FAB against that theme's surface —
        // so the choice shows what it does before it is made, the way SY's does.
        // Which variant a card previews follows the mode: on Dark it shows the
        // dark scheme, on Light the light, on System whatever the phone is right
        // now. The whole-app repaint on tap is still the real preview; this is
        // what lets you choose without a repaint per candidate first.
        val previewDark = when (AppTheme.mode) {
            ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppColorTheme.entries.forEach { theme ->
                ThemePreviewCard(
                    theme = theme,
                    dark = previewDark,
                    selected = AppTheme.colorTheme == theme,
                    onClick = { AppTheme.setColorTheme(context, theme) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PrefSwitchRow(
            title = "Pure black dark mode",
            checked = AppTheme.amoled,
            summary = "True black backgrounds in dark mode. Saves battery on " +
                "OLED screens; no effect on a light theme.",
            onChange = { AppTheme.setAmoled(context, it) }
        )
    }
}

/**
 * A single theme swatch in the picker — a phone-shaped mock painted in [theme]'s
 * own colours so the row previews each theme rather than naming it.
 *
 * Deliberately hand-drawn rather than a real component miniature: it needs to
 * read at ~110dp wide, where a genuine scaled-down screen would be mud. The three
 * elements are the ones that carry a theme's identity — the surface it sits on,
 * the accent (primary) and its partner (secondary), and the FAB — which is
 * exactly what SY's own swatch shows.
 *
 * It paints from an explicit [ColorScheme] rather than reading MaterialTheme,
 * because every card must show a DIFFERENT scheme than the one the app is
 * currently in; MaterialTheme.colorScheme is the same for all of them.
 */
@Composable
private fun ThemePreviewCard(
    theme: AppColorTheme,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = if (dark) theme.dark() else theme.light()
    val ring = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected)
                        Modifier.border(2.dp, ring, RoundedCornerShape(16.dp))
                    else Modifier
                )
                .background(scheme.background)
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.onSurface.copy(alpha = 0.15f))
                )
                Spacer(modifier = Modifier.height(10.dp))
                // A card carrying the two accents.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 24.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                .background(scheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 24.dp)
                                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                .background(scheme.secondary)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // A FAB dot beside a neutral bar.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(scheme.onSurface.copy(alpha = 0.25f))
                    )
                }
            }

            // Selected badge, top-right, over the mock.
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ring),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = onAccent(ring),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ring else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- library ----------
