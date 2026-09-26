package com.mangareader.app

import android.content.Context
import org.json.JSONObject

/**
 * Reader settings, and where they're kept.
 *
 * Global defaults live in [ReaderPrefs]. Optional per-series overrides live in
 * [ReaderSeriesPrefs] and are keyed by source + series identity so unrelated
 * extensions cannot collide even when they reuse the same internal series id.
 */
internal enum class ReaderMode(val key: String, val label: String) {
    PAGED_LTR("paged_ltr", "Paged \u2192"),
    PAGED_RTL("paged_rtl", "Paged \u2190"),
    LONG_STRIP("long_strip", "Long strip");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: PAGED_LTR
    }
}

internal enum class ReaderRotation(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

internal enum class ReaderWidePageMode(val key: String, val label: String) {
    FIT("fit", "Fit"),
    ROTATE_RIGHT("rotate_right", "Rotate 90°");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: FIT
    }
}

internal enum class ReaderBackground(val key: String, val label: String) {
    BLACK("black", "Black"),
    GRAY("gray", "Gray"),
    WHITE("white", "White"),
    THEME("theme", "Theme");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: BLACK
    }
}

/**
 * Where the chapter slider sits.
 *
 * Horizontal is the default because it lives inside the control bar and costs
 * no screen. Vertical is what a long strip wants — the thumb then travels the
 * same direction the pages do, and it sits under the thumb of the hand already
 * holding the phone.
 */
internal enum class ReaderSliderPosition(val key: String, val label: String) {
    HORIZONTAL("horizontal", "Horizontal"),
    VERTICAL("vertical", "Vertical");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: HORIZONTAL
    }
}

internal data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.PAGED_LTR,
    val rotation: ReaderRotation = ReaderRotation.SYSTEM,
    val background: ReaderBackground = ReaderBackground.BLACK,
    /** Horizontal breathing room, as a percentage of screen width. */
    val sidePadding: Int = 0,
    /** Vertical spacing between items in long-strip mode, in dp. */
    val pageGap: Int = 0,
    /** How landscape/wide pages are presented in paged mode. */
    val widePageMode: ReaderWidePageMode = ReaderWidePageMode.FIT,
    /** Outer thirds turn pages in paged modes; centre third toggles controls. */
    val tapZones: Boolean = true,
    /** Seconds before reader chrome hides itself. 0 keeps controls visible. */
    val controlsAutoHideSeconds: Int = 4,
    val showPageNumber: Boolean = true,
    val sliderPosition: ReaderSliderPosition = ReaderSliderPosition.HORIZONTAL,
    val fullscreen: Boolean = true,
    val keepScreenOn: Boolean = true,
    val grayscale: Boolean = false,
    val inverted: Boolean = false,
    /** When off, the system brightness applies and [brightness] is ignored. */
    val customBrightness: Boolean = false,
    val brightness: Float = 0.5f,
)

internal object ReaderPrefs {

    private const val MODE = "reader_mode"
    private const val ROTATION = "reader_rotation"
    private const val BACKGROUND = "reader_background"
    private const val SIDE_PADDING = "reader_side_padding"
    private const val PAGE_GAP = "reader_page_gap"
    private const val WIDE_PAGE_MODE = "reader_wide_page_mode"
    private const val TAP_ZONES = "reader_tap_zones"
    private const val CONTROLS_AUTO_HIDE_SECONDS = "reader_controls_auto_hide_seconds"
    private const val PAGE_NUMBER = "reader_page_number"
    private const val SLIDER_POSITION = "reader_slider_position"
    private const val FULLSCREEN = "reader_fullscreen"
    private const val KEEP_SCREEN_ON = "reader_keep_screen_on"
    private const val GRAYSCALE = "reader_grayscale"
    private const val INVERTED = "reader_inverted"
    private const val CUSTOM_BRIGHTNESS = "reader_custom_brightness"
    private const val BRIGHTNESS = "reader_brightness"

    fun load(context: Context): ReaderSettings {
        val p = prefs(context)
        val defaults = ReaderSettings()
        return ReaderSettings(
            mode = ReaderMode.from(p.getString(MODE, null)),
            rotation = ReaderRotation.from(p.getString(ROTATION, null)),
            background = ReaderBackground.from(p.getString(BACKGROUND, null)),
            sidePadding = p.getInt(SIDE_PADDING, defaults.sidePadding),
            pageGap = p.getInt(PAGE_GAP, defaults.pageGap),
            widePageMode = ReaderWidePageMode.from(p.getString(WIDE_PAGE_MODE, null)),
            tapZones = p.getBoolean(TAP_ZONES, defaults.tapZones),
            controlsAutoHideSeconds = p.getInt(
                CONTROLS_AUTO_HIDE_SECONDS,
                defaults.controlsAutoHideSeconds,
            ).coerceIn(0, 10),
            showPageNumber = p.getBoolean(PAGE_NUMBER, defaults.showPageNumber),
            sliderPosition = ReaderSliderPosition.from(p.getString(SLIDER_POSITION, null)),
            fullscreen = p.getBoolean(FULLSCREEN, defaults.fullscreen),
            keepScreenOn = p.getBoolean(KEEP_SCREEN_ON, defaults.keepScreenOn),
            grayscale = p.getBoolean(GRAYSCALE, defaults.grayscale),
            inverted = p.getBoolean(INVERTED, defaults.inverted),
            customBrightness = p.getBoolean(CUSTOM_BRIGHTNESS, defaults.customBrightness),
            brightness = p.getFloat(BRIGHTNESS, defaults.brightness),
        )
    }

    /**
     * Written whole rather than field by field. The settings sheet edits a
     * single [ReaderSettings] in composable state and saves the result, so
     * per-key setters would only be a way to forget one.
     */
    fun save(context: Context, settings: ReaderSettings) {
        prefs(context).edit()
            .putString(MODE, settings.mode.key)
            .putString(ROTATION, settings.rotation.key)
            .putString(BACKGROUND, settings.background.key)
            .putInt(SIDE_PADDING, settings.sidePadding)
            .putInt(PAGE_GAP, settings.pageGap)
            .putString(WIDE_PAGE_MODE, settings.widePageMode.key)
            .putBoolean(TAP_ZONES, settings.tapZones)
            .putInt(CONTROLS_AUTO_HIDE_SECONDS, settings.controlsAutoHideSeconds)
            .putBoolean(PAGE_NUMBER, settings.showPageNumber)
            .putString(SLIDER_POSITION, settings.sliderPosition.key)
            .putBoolean(FULLSCREEN, settings.fullscreen)
            .putBoolean(KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(GRAYSCALE, settings.grayscale)
            .putBoolean(INVERTED, settings.inverted)
            .putBoolean(CUSTOM_BRIGHTNESS, settings.customBrightness)
            .putFloat(BRIGHTNESS, settings.brightness)
            .apply()
    }
}


/**
 * Optional per-series reader settings layered over [ReaderPrefs].
 *
 * Absence means "use global defaults". A stored value is a full snapshot, so
 * turning the override on copies the current effective settings and subsequent
 * edits stay isolated to that series until the override is cleared.
 */
internal object ReaderSeriesPrefs {
    private fun p(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    private fun key(sourceId: String, seriesId: String) =
        "reader_series:" + sourceId.length + ":" + sourceId + ":" + seriesId

    fun load(context: Context, sourceId: String, seriesId: String): ReaderSettings? {
        if (sourceId.isBlank() || seriesId.isBlank()) return null
        val raw = p(context).getString(key(sourceId, seriesId), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            ReaderSettings(
                mode = ReaderMode.from(o.optString("mode").takeIf { it.isNotBlank() }),
                rotation = ReaderRotation.from(
                    o.optString("rotation").takeIf { it.isNotBlank() }
                ),
                background = ReaderBackground.from(
                    o.optString("background").takeIf { it.isNotBlank() }
                ),
                sidePadding = o.optInt("sidePadding", 0),
                pageGap = o.optInt("pageGap", 0),
                widePageMode = ReaderWidePageMode.from(
                    o.optString("widePageMode").takeIf { it.isNotBlank() }
                ),
                tapZones = o.optBoolean("tapZones", true),
                controlsAutoHideSeconds = o.optInt("controlsAutoHideSeconds", 4)
                    .coerceIn(0, 10),
                showPageNumber = o.optBoolean("showPageNumber", true),
                sliderPosition = ReaderSliderPosition.from(
                    o.optString("sliderPosition").takeIf { it.isNotBlank() }
                ),
                fullscreen = o.optBoolean("fullscreen", true),
                keepScreenOn = o.optBoolean("keepScreenOn", true),
                grayscale = o.optBoolean("grayscale", false),
                inverted = o.optBoolean("inverted", false),
                customBrightness = o.optBoolean("customBrightness", false),
                brightness = o.optDouble("brightness", 0.5).toFloat(),
            )
        }.getOrNull()
    }

    fun save(
        context: Context,
        sourceId: String,
        seriesId: String,
        settings: ReaderSettings,
    ) {
        if (sourceId.isBlank() || seriesId.isBlank()) return
        val o = JSONObject()
            .put("mode", settings.mode.key)
            .put("rotation", settings.rotation.key)
            .put("background", settings.background.key)
            .put("sidePadding", settings.sidePadding)
            .put("pageGap", settings.pageGap)
            .put("widePageMode", settings.widePageMode.key)
            .put("tapZones", settings.tapZones)
            .put("controlsAutoHideSeconds", settings.controlsAutoHideSeconds)
            .put("showPageNumber", settings.showPageNumber)
            .put("sliderPosition", settings.sliderPosition.key)
            .put("fullscreen", settings.fullscreen)
            .put("keepScreenOn", settings.keepScreenOn)
            .put("grayscale", settings.grayscale)
            .put("inverted", settings.inverted)
            .put("customBrightness", settings.customBrightness)
            .put("brightness", settings.brightness.toDouble())
        p(context).edit().putString(key(sourceId, seriesId), o.toString()).apply()
    }

    fun clear(context: Context, sourceId: String, seriesId: String) {
        if (sourceId.isBlank() || seriesId.isBlank()) return
        p(context).edit().remove(key(sourceId, seriesId)).apply()
    }
}
