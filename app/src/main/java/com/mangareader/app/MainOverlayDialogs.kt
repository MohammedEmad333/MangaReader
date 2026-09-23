package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun MainOverlayDialogs(
    activeSource: Source?,
    probeOpen: Boolean,
    onDismissProbe: () -> Unit,
    filtersOpen: Boolean,
    onApplyFilters: (Source) -> Unit,
    onDismissFilters: () -> Unit,
    whatsNewOpen: Boolean,
    releaseNotes: List<ReleaseNote>,
    onDismissWhatsNew: () -> Unit,
    showSourceDialog: Boolean,
    editingConfig: SourceConfig?,
    onEditingConfigChange: (SourceConfig) -> Unit,
    onDismissSourceDialog: () -> Unit,
    onSaveSource: (SourceConfig) -> Unit
) {
    if (probeOpen && activeSource != null) {
        NetworkProbeDialog(
            source = activeSource,
            onDismiss = onDismissProbe
        )
    }

    if (filtersOpen && activeSource != null) {
        SourceFilterDialog(
            source = activeSource,
            onApply = { onApplyFilters(activeSource) },
            onDismiss = onDismissFilters
        )
    }

    if (whatsNewOpen) {
        WhatsNewDialog(
            notes = releaseNotes,
            onDismiss = onDismissWhatsNew
        )
    }

    if (showSourceDialog && editingConfig != null) {
        SourceDialog(
            value = editingConfig,
            onChange = onEditingConfigChange,
            onDismiss = onDismissSourceDialog,
            onSave = onSaveSource
        )
    }
}
