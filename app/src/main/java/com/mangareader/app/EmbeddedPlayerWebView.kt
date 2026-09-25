package com.mangareader.app

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun ColumnScope.EmbeddedPlayerWebView(
    url: String,
    referer: String,
    reveal: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onProgress: (Int) -> Unit,
    onTitle: (String?) -> Unit,
    onShowFullscreen: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideFullscreen: () -> Unit,
) {
    AndroidView(
        modifier = if (reveal) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.size(1.dp)
        },
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mediaPlaybackRequiresUserGesture = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                val allowedHost = runCatching { Uri.parse(url).host }.getOrNull()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val target = request?.url?.host ?: return false
                        return allowedHost != null && target != allowedHost
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?,
                    ): Boolean {
                        runCatching {
                            (view?.parent as? ViewGroup)?.removeView(view)
                            view?.destroy()
                        }
                        return true
                    }
                }

                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        onTitle(title)
                    }

                    override fun onShowCustomView(
                        view: View?,
                        callback: CustomViewCallback?,
                    ) {
                        onShowFullscreen(view, callback)
                    }

                    override fun onHideCustomView() {
                        onHideFullscreen()
                    }
                }

                loadUrl(url, mapOf("Referer" to referer))
            }.also(onWebViewReady)
        },
    )
}

@Composable
internal fun ColumnScope.EmbeddedPlayerStatus(
    reveal: Boolean,
    searched: Boolean,
    onReveal: () -> Unit,
) {
    if (reveal) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!searched) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(
                "Finding the video…",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The player is loading in the background. When the video " +
                    "address turns up it opens in your video player.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "No video address found",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This player may build its stream entirely in the page, " +
                    "which leaves nothing an outside app can open.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onReveal) {
                Text("Show the page anyway")
            }
        }
    }
}
