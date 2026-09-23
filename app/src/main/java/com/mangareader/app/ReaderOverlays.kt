package com.mangareader.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.ReaderControlsOverlay(
    settings: ReaderSettings,
    showControls: Boolean,
    currentPage: Int,
    totalPages: Int,
    seekTarget: Float?,
    onSeekTargetChange: (Float) -> Unit,
    onSeekCommit: () -> Unit,
    verticalSliderLength: Dp,
    rtl: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    seriesTitle: String,
    chapterName: String,
    onClose: () -> Unit,
    pageTextColor: Color,
    pageOutlineColor: Color,
) {
    val lastPage = (totalPages - 1).coerceAtLeast(0)
    val seekPage = seekTarget?.roundToInt()?.coerceIn(0, lastPage) ?: currentPage

    if (settings.showPageNumber && !showControls && totalPages > 0) {
        OutlinedText(
            text = "${currentPage + 1} / $totalPages",
            color = pageTextColor,
            outline = pageOutlineColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }

    AnimatedVisibility(
        visible = showControls,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        ReaderTopBar(
            title = seriesTitle,
            subtitle = chapterName,
            onClose = onClose,
        )
    }

    AnimatedVisibility(
        visible = showControls &&
            settings.sliderPosition == ReaderSliderPosition.VERTICAL &&
            totalPages > 1,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.large,
        ) {
            VerticalSlider(
                value = seekTarget ?: currentPage.toFloat(),
                onValueChange = onSeekTargetChange,
                onValueChangeFinished = onSeekCommit,
                valueRange = 0f..lastPage.toFloat(),
                length = verticalSliderLength,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    AnimatedVisibility(
        visible = showControls,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReaderBottomBar(
            page = seekPage + 1,
            total = totalPages,
            showSlider =
                settings.sliderPosition == ReaderSliderPosition.HORIZONTAL &&
                    totalPages > 1,
            sliderValue = seekTarget ?: currentPage.toFloat(),
            onSliderChange = onSeekTargetChange,
            mirrorSlider = rtl,
            onSliderCommit = onSeekCommit,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onPrev = onPrev,
            onNext = onNext,
            onChapters = onOpenChapters,
            onSettings = onOpenSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSheets(
    showChapters: Boolean,
    onDismissChapters: () -> Unit,
    chapters: List<Chapter>,
    chapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    showSettings: Boolean,
    onDismissSettings: () -> Unit,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    if (showChapters) {
        ModalBottomSheet(onDismissRequest = onDismissChapters) {
            ChapterPickerSheet(
                chapters = chapters,
                current = chapterIndex,
                onSelect = onSelectChapter,
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = onDismissSettings) {
            ReaderSettingsSheet(
                settings = settings,
                onChange = onSettingsChange,
            )
        }
    }
}
