package io.github.baslawson.smartnotes

import android.content.SharedPreferences
import io.github.baslawson.smartnotes.reminders.ReminderSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Settings → Recycle bin: how long deleted things are kept before they are erased. */
enum class KeepFor(val days: Int, val label: String) {
    WEEK(7, "7 days"), MONTH(30, "30 days"), QUARTER(90, "90 days"), FOREVER(0, "Forever");

    /** Things deleted before this time (millis) are erased; null when they are kept forever. */
    fun cutoff(now: Long): Long? = if (days == 0) null else now - days * DAY_MILLIS

    /** Days (started ones count) before something deleted at [deletedAt] is erased (0 = at the next start); null when forever. */
    fun daysLeft(deletedAt: Long, now: Long): Int? =
        if (days == 0) null else (((deletedAt + days * DAY_MILLIS - now).coerceAtLeast(0) + DAY_MILLIS - 1) / DAY_MILLIS).toInt()

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        val DEFAULT = MONTH
        fun of(name: String?): KeepFor = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Settings → Date format: how dates show in shopping lists and tasks. */
enum class DateStyle(private val pattern: String) {
    DAY_MONTH_YEAR("dd/MM/yyyy"), SHORT("EEE d MMM"), MONTH_DAY_YEAR("MM/dd/yyyy"), ISO("yyyy-MM-dd");

    /** [date] in this style; the short style adds the year when it isn't this year's. */
    fun format(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
        val p = if (this == SHORT && date.year != today.year) "$pattern yyyy" else pattern
        return DateTimeFormatter.ofPattern(p, locale).format(date)
    }

    /** Today, Tomorrow or Yesterday; other dates in this style. */
    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> format(date, today, locale)
    }

    companion object {
        val DEFAULT = DAY_MONTH_YEAR
        fun of(name: String?): DateStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Settings → Appearance: the theme and the text size, kept on the phone. */
class AppSettings(private val prefs: SharedPreferences) {
    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Multiplies the phone's own font size, so the system setting still counts. */
    private val _textScale = MutableStateFlow(clampScale(prefs.getFloat(KEY_TEXT_SCALE, 1f)))
    val textScale: StateFlow<Float> = _textScale.asStateFlow()

    /** How reminders sound unless a list or task chooses its own (never DEFAULT itself). */
    private val _reminderSound = MutableStateFlow(
        ReminderSound.of(prefs.getString(KEY_SOUND, null)).takeUnless { it == ReminderSound.DEFAULT } ?: ReminderSound.SETTING_DEFAULT
    )
    val reminderSound: StateFlow<ReminderSound> = _reminderSound.asStateFlow()

    private val _keepFor = MutableStateFlow(KeepFor.of(prefs.getString(KEY_KEEP, null)))
    val keepFor: StateFlow<KeepFor> = _keepFor.asStateFlow()

    fun setKeepFor(keep: KeepFor) {
        prefs.edit().putString(KEY_KEEP, keep.name).apply()
        _keepFor.value = keep
        AppLog.i("Settings", "Recycle bin keeps ${keep.label}")
    }

    private val _dateStyle = MutableStateFlow(DateStyle.of(prefs.getString(KEY_DATE, null)))
    val dateStyle: StateFlow<DateStyle> = _dateStyle.asStateFlow()

    fun setDateStyle(style: DateStyle) {
        prefs.edit().putString(KEY_DATE, style.name).apply()
        _dateStyle.value = style
        AppLog.i("Settings", "Date format ${style.name}")
    }

    fun setReminderSound(sound: ReminderSound) {
        if (sound == ReminderSound.DEFAULT) return
        prefs.edit().putString(KEY_SOUND, sound.name).apply()
        _reminderSound.value = sound
        AppLog.i("Settings", "Reminder sound ${sound.label}")
    }

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

    /** The settings a backup carries (see data/Backup.kt). */
    fun export(): Map<String, Any> = mapOf(
        "theme" to theme.value.name, "textScale" to textScale.value.toDouble(), "reminderSound" to reminderSound.value.name,
        "dateStyle" to dateStyle.value.name, "keepFor" to keepFor.value.name,
    )

    /** Takes the settings of a backup being restored; unknown or missing ones are left as they are. */
    fun import(values: Map<String, Any?>) {
        (values["theme"] as? String)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let(::setTheme)
        (values["textScale"] as? Number)?.let { setTextScale(it.toFloat()) }
        (values["reminderSound"] as? String)?.let { name -> ReminderSound.entries.firstOrNull { it.name == name } }?.let(::setReminderSound)
        (values["dateStyle"] as? String)?.let { name -> DateStyle.entries.firstOrNull { it.name == name } }?.let(::setDateStyle)
        (values["keepFor"] as? String)?.let { name -> KeepFor.entries.firstOrNull { it.name == name } }?.let(::setKeepFor)
    }

    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.6f
        private const val KEY_THEME = "theme"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_SOUND = "reminder_sound"
        private const val KEY_DATE = "date_style"
        private const val KEY_KEEP = "bin_keep"

        /** Within 80–160 %, rounded to 10 % steps. */
        fun clampScale(scale: Float): Float =
            (Math.round(scale.coerceIn(MIN_SCALE, MAX_SCALE) * 10) / 10f)
    }
}
