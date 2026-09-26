package com.mangareader.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExtensionResumeObserver(
    activity: ComponentActivity?,
    onSourcesChanged: (List<Source>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(activity) {
        val appContext = context.applicationContext

        fun refreshSources() {
            scope.launch {
                val sources = withContext(Dispatchers.IO) {
                    runCatching {
                        SourceManager.listAllSources(appContext)
                            .filter { it.id.isExtensionSourceId() }
                    }.getOrDefault(emptyList())
                }
                onSourcesChanged(sources)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSources()
            }
        }

        activity?.lifecycle?.addObserver(observer)

        // The observer can be attached after the Activity has already reached
        // RESUMED. In that case Lifecycle will not emit another ON_RESUME until
        // the app backgrounds and returns, leaving Browse > Sources empty on a
        // fresh launch even though Extensions can see installed packages.
        refreshSources()

        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }
}
