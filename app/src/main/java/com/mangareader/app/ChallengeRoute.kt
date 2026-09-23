package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun ChallengeRoute(
    url: String,
    onSolved: () -> Unit,
    onBack: () -> Unit
) {
    ChallengeWebViewScreen(
        url = url,
        onSolved = onSolved,
        onBack = onBack
    )
}
