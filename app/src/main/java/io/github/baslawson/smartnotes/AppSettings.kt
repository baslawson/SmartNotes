package io.github.baslawson.smartnotes

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Settings → Appearance: the theme and the text size, kept on the phone. */
class AppSettings(private val prefs: SharedPreferences) {
    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Multiplies the phone's own font size, so the system setting still counts. */
    private val _textScale = MutableStateFlow(clampScale(prefs.getFloat(KEY_TEXT_SCALE, 1f)))
    val textScale: StateFlow<Float> = _textScale.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
        AppLog.i("Settings", "Theme ${mode.label}")
    }

    fun setTextScale(scale: Float) {
        val s = clampScale(scale)
        prefs.edit().putFloat(KEY_TEXT_SCALE, s).apply()
        _textScale.value = s
    }

    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.6f
        private const val KEY_THEME = "theme"
        private const val KEY_TEXT_SCALE = "text_scale"

        /** Within 80–160 %, rounded to 10 % steps. */
        fun clampScale(scale: Float): Float =
            (Math.round(scale.coerceIn(MIN_SCALE, MAX_SCALE) * 10) / 10f)
    }
}
