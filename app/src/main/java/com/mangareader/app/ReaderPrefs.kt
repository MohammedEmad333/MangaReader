package com.mangareader.app

import android.content.Context

/**
 * Reader settings, and where they're kept.
 *
 * One store for everything, global rather than per-series. Mihon scopes reading
 * mode and rotation per manga on top of a global default, which is genuinely
 * better for a library mixing manga and webtoons — but it needs a second store
 * keyed by series id and a "use default" state distinct from every real value,
 * and none of that is worth writing before the settings themselves have been
 * used. The enums below carry a `key`, so moving to a per-series overlay later
 * is additive.
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
