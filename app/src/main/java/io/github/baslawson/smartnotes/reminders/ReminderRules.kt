package io.github.baslawson.smartnotes.reminders

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * How a reminder sounds, as in Planner: the plain notification sound (muted by silent and vibrate mode), or the alarm
 * sound ringing through silent and vibrate mode for 10 s, 30 s, 1 min, or until stopped. [DEFAULT] is Settings' choice.
 */
enum class ReminderSound(val label: String, val ringSeconds: Int?) {
    DEFAULT("Default", null),
    NOTIFICATION("Notification sound only", null),
    SECONDS_10("Ring 10 seconds", 10),
    SECONDS_30("Ring 30 seconds", 30),
    MINUTE("Ring 1 minute", 60),
    UNTIL_STOPPED("Ring until I stop it", 0);

    /** [DEFAULT] becomes [setting] (itself never DEFAULT). */
    fun resolve(setting: ReminderSound): ReminderSound = if (this == DEFAULT) setting.takeUnless { it == DEFAULT } ?: SETTING_DEFAULT else this

    /** Rings through AlarmService: for [ringSeconds] seconds, 0 = until stopped. Call on a resolved sound. */
    val rings: Boolean get() = ringSeconds != null

    companion object {
        /** Settings' choice on a new install, as Planner's. */
        val SETTING_DEFAULT = SECONDS_10
        fun of(name: String?): ReminderSound = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How a to-do task repeats; ticking a repeating task moves it to its next date instead of finishing it. */
enum class Repeat(val label: String) {
    NONE("Doesn't repeat"), DAILY("Every day"), WEEKLY("Every week"), MONTHLY("Every month"), EVERY_N_DAYS("Every … days");

    /** The next due date after [from]; null when it doesn't repeat. Monthly keeps the day, or the month's last day. */
    fun next(from: LocalDate, every: Int, originalDay: Int = from.dayOfMonth): LocalDate? = when (this) {
        NONE -> null
        DAILY -> from.plusDays(1)
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1).let { it.withDayOfMonth(minOf(originalDay, it.lengthOfMonth())) }
        EVERY_N_DAYS -> from.plusDays(every.coerceAtLeast(1).toLong())
    }

    companion object {
        fun of(name: String?): Repeat = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Snooze choices, as Planner's. */
enum class SnoozeChoice(private val text: String) {
    TEN_MINUTES("10 minutes"), ONE_HOUR("1 hour"), TOMORROW("Tomorrow");

    /** Before 4 am "tomorrow" is the coming morning, and is said so; 9:00 as Settings → Time format writes it. */
    fun label(now: LocalDateTime, clock: io.github.baslawson.smartnotes.TimeStyle = io.github.baslawson.smartnotes.TimeStyle(false)): String {
        if (this != TOMORROW) return text
        val nine = clock.format(java.time.LocalTime.of(9, 0))
        return if (now.hour < EARLY_MORNING_HOUR) "This morning at $nine" else "Tomorrow at $nine"
    }

    fun until(now: LocalDateTime): LocalDateTime = when (this) {
        TEN_MINUTES -> now.plusMinutes(10)
        ONE_HOUR -> now.plusHours(1)
        TOMORROW -> now.toLocalDate().let { if (now.hour < EARLY_MORNING_HOUR) it else it.plusDays(1) }.atTime(9, 0)
    }

    companion object {
        const val EARLY_MORNING_HOUR = 4
    }
}

/** One reminder an owner (note, list, task) wants: its own time and what the notification says. */
data class WantedReminder(val at: LocalDateTime)

/** An alarm in the ledger, as the planner sees it. */
data class LedgerRow(val id: Long, val at: LocalDateTime, val fireAt: LocalDateTime)

/** What to do to make one owner's alarms match its reminders. */
data class AlarmPlan(val cancel: List<Long>, val add: List<LocalDateTime>, val keep: List<Long>)

object AlarmPlanner {
    /**
     * Rows whose reminder is gone are cancelled. Rows still wanted are kept as they are (a snoozed one keeps its snooze;
     * one Android is delivering a little late isn't lost). Wanted times in the future without a row get one; past ones
     * never do (that would notify again for something already shown).
     */
    fun plan(wanted: Set<LocalDateTime>, rows: List<LedgerRow>, now: LocalDateTime): AlarmPlan {
        val keep = rows.filter { it.at in wanted }
        val cancel = rows.filter { it.at !in wanted }.map { it.id }
        val have = keep.map { it.at }.toSet()
        return AlarmPlan(cancel, wanted.filter { it.isAfter(now) && it !in have }.sorted(), keep.map { it.id })
    }

    /** After a restart: rows whose alarm time passed while the phone was off are missed; the rest are set again. */
    fun missed(rows: List<LedgerRow>, now: LocalDateTime): List<LedgerRow> = rows.filter { !it.fireAt.isAfter(now) }

    /** A to-do task's reminder time: its due date at its time, or at 09:00 without one; null when it doesn't remind. */
    fun todoTime(remind: Boolean, done: Boolean, dueDate: String?, dueTime: String?): LocalDateTime? {
        if (!remind || done) return null
        val date = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val time = dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)
        return date.atTime(time)
    }

    /** A shopping list's reminder time, or null. */
    fun listTime(date: String?, remindAt: String?): LocalDateTime? {
        val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val t = remindAt?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        return d.atTime(t)
    }

    fun millis(at: LocalDateTime): Long = ZonedDateTime.of(at, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
