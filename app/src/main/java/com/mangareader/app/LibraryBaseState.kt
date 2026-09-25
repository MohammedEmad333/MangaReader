package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The persisted pieces needed before the library can be arranged.
 *
 * A large imported library is one JSON string in SharedPreferences. Its first
 * read makes Android parse the preferences XML, then [Library.list] parses the
 * library JSON itself. Doing that while Compose is building the first frame
 * turns cold start into a frozen screen. Read the snapshot on IO and let the UI
 * show a lightweight loading state instead.
 */
internal data class LibraryBaseState(
    val entries: List<LibraryEntry>,
    val categories: List<Category>,
)

@Composable
internal fun rememberLibraryBaseState(
    context: Context,
    tick: Int,
): LibraryBaseState? {
    val appContext = context.applicationContext
    val state by produceState<LibraryBaseState?>(initialValue = null, tick) {
        value = withContext(Dispatchers.IO) {
            LibraryBaseState(
                entries = Library.list(appContext),
                categories = Categories.list(appContext),
            )
        }
    }
    return state
}
