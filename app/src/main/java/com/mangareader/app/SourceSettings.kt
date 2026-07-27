package com.mangareader.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.source.ConfigurableSource

/**
 * Reads the preferences an extension exposes through `ConfigurableSource` and
 * turns them into something Compose can render.
 *
 * Extensions declare their settings by populating an *androidx* PreferenceScreen
 * in `setupPreferenceScreen()` — the view-based preference framework, which this
 * app has no Fragment to host. So instead of displaying that screen, we build it
 * headlessly, walk the Preference objects it collected, and read their metadata
 * (title, entries, current value) into the model below.
 *
 * The values themselves live in SharedPreferences named `source_<id>`, which is
 * exactly where the extension reads them back from — see
 * `ConfigurableSource.getSourcePreferences()`. Pointing the PreferenceManager at
 * that same file before building the screen is what makes each Preference come
 * back already holding the persisted value.
 */
sealed interface SourcePrefItem {
    /** Kept so edits can run the extension's own change listener — see [apply]. */
    val pref: Preference
    val key: String
    val title: String
    val summary: String?

    data class Toggle(
        override val pref: Preference,
        override val key: String,
        override val title: String,
        override val summary: String?,
        val checked: Boolean
    ) : SourcePrefItem

    data class Choice(
        override val pref: Preference,
        override val key: String,
        override val title: String,
        override val summary: String?,
        val entries: List<String>,
        val values: List<String>,
        val current: String?
    ) : SourcePrefItem

    data class MultiChoice(
        override val pref: Preference,
        override val key: String,
        override val title: String,
        override val summary: String?,
        val entries: List<String>,
        val values: List<String>,
        val current: Set<String>
    ) : SourcePrefItem

    data class TextEntry(
        override val pref: Preference,
        override val key: String,
        override val title: String,
        override val summary: String?,
        val current: String
    ) : SourcePrefItem
}

object SourceSettings {

    /** Whether this source exposes any settings UI at all. */
    fun isConfigurable(source: Source): Boolean = configurableOf(source) != null

    private fun configurableOf(source: Source): ConfigurableSource? =
        (source as? TachiyomiSourceAdapter)?.catalogueSource as? ConfigurableSource

    /**
     * Builds the extension's preference screen and flattens it into a list.
     *
     * Returns empty rather than throwing when an extension's setup code misbehaves:
     * a broken settings screen shouldn't be able to take down the Browse tab.
     */
    @SuppressLint("RestrictedApi")
    fun load(context: Context, source: Source): List<SourcePrefItem> {
        val configurable = configurableOf(source) ?: return emptyList()
        val appCtx = context.applicationContext

        return runCatching {
            val manager = PreferenceManager(appCtx)
            // Must be set before the screen is built: attaching a Preference to
            // the hierarchy is what loads its persisted value, and it reads from
            // whichever file the manager points at.
            manager.sharedPreferencesName = preferenceKeyOf(configurable)
            manager.sharedPreferencesMode = Context.MODE_PRIVATE

            val screen = manager.createPreferenceScreen(appCtx)
            configurable.setupPreferenceScreen(screen)
            flatten(screen).mapNotNull { toItem(it) }
        }.getOrElse {
            // The app theme is a plain platform theme, not an AppCompat one, so
            // if androidx.preference ever fails to resolve its style attributes
            // this is where it shows up. Swallowing it keeps Browse alive; the
            // log is the only way to tell "no settings" from "settings broke".
            Log.e("SourceSettings", "Failed to build settings for ${source.name}", it)
            emptyList()
        }
    }

    /**
     * Writes a new value back.
     *
     * The extension's own `OnPreferenceChangeListener` runs first, because some
     * extensions do their real work there — persisting under a second key,
     * clearing a cached client. Then the value is written under the Preference's
     * own key, which covers the majority that rely on normal persistence.
     * Doing both is harmless when an extension does both.
     */
    fun apply(context: Context, source: Source, item: SourcePrefItem, newValue: Any) {
        val configurable = configurableOf(source) ?: return
        runCatching { item.pref.callChangeListener(newValue) }

        val editor = prefsFor(context, configurable).edit()
        when (newValue) {
            is Boolean -> editor.putBoolean(item.key, newValue)
            is String -> editor.putString(item.key, newValue)
            is Set<*> -> editor.putStringSet(
                item.key,
                newValue.filterIsInstance<String>().toSet()
            )
            else -> return
        }
        editor.apply()
    }

    private fun prefsFor(context: Context, configurable: ConfigurableSource): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(preferenceKeyOf(configurable), Context.MODE_PRIVATE)

    /** Mirrors ConfigurableSource.preferenceKey() without depending on it. */
    private fun preferenceKeyOf(configurable: ConfigurableSource): String =
        "source_${configurable.id}"

    /** Preference groups can nest; the UI shows one flat list. */
    private fun flatten(group: PreferenceGroup): List<Preference> = buildList {
        for (i in 0 until group.preferenceCount) {
            val child = group.getPreference(i)
            if (child is PreferenceGroup) addAll(flatten(child)) else add(child)
        }
    }

    private fun toItem(pref: Preference): SourcePrefItem? {
        // No key means nothing to persist under, so there's nothing to edit.
        val key = pref.key ?: return null
        val title = pref.title?.toString().orEmpty().ifBlank { key }
        val summary = pref.summary?.toString()?.takeIf { it.isNotBlank() }

        return when (pref) {
            // Covers SwitchPreferenceCompat and CheckBoxPreference both.
            is TwoStatePreference ->
                SourcePrefItem.Toggle(pref, key, title, summary, pref.isChecked)

            is MultiSelectListPreference ->
                SourcePrefItem.MultiChoice(
                    pref, key, title, summary,
                    entries = pref.entries?.map { it.toString() } ?: emptyList(),
                    values = pref.entryValues?.map { it.toString() } ?: emptyList(),
                    current = pref.values
                )

            is ListPreference ->
                SourcePrefItem.Choice(
                    pref, key, title, summary,
                    entries = pref.entries?.map { it.toString() } ?: emptyList(),
                    values = pref.entryValues?.map { it.toString() } ?: emptyList(),
                    current = pref.value
                )

            is EditTextPreference ->
                SourcePrefItem.TextEntry(pref, key, title, summary, pref.text.orEmpty())

            // Plain Preference, or a type with no inline editor. Skipped rather
            // than shown as a dead row.
            else -> null
        }
    }

    /** Label for a Choice's current value, falling back to the raw value. */
    fun labelFor(item: SourcePrefItem.Choice): String {
        val idx = item.values.indexOf(item.current)
        return if (idx >= 0 && idx < item.entries.size) item.entries[idx]
        else item.current.orEmpty()
    }
}
