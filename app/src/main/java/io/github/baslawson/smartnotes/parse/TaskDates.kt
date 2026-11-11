package io.github.baslawson.smartnotes.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * A date and time typed at the end of a new task ("Call the dentist tomorrow 3pm", "Pay rent fri", "Book a table on
 * 12 Dec at 19:30"): the task gets that reminder and the words come off its title. Only words at the end count, so
 * "Buy sun cream" isn't Sunday and "Call Monday's client" stays as typed. English words only.
 */
object TaskDates {
    /**
     * The task [title] without the date words, its [date] and [time] (null: the reminder rings at 9:00). [passed]: that
     * moment has already gone by (it won't ring).
     */
    data class Found(val title: String, val date: LocalDate, val time: LocalTime?, val passed: Boolean = false)

    private val OPT = setOf(RegexOption.IGNORE_CASE)
    private const val LEAD = """\s+(?:(?:on|by|due|at|@)\s+)?"""

    // Times, at the end.
    // Only ":" between hours and minutes: "Pay the plumber 2.50" is a price, not 2:50.
    private val CLOCK = Regex("""$LEAD(\d{1,2}):(\d{2})\s*([ap]\.?m\.?)?$""", OPT)
    private val AMPM = Regex("""$LEAD(\d{1,2})\s*([ap]\.?m\.?)$""", OPT)
    private val BARE_AT = Regex("""\s+(?:at|@)\s+(\d{1,2})$""", OPT)
    private val NOON = Regex("""$LEAD(noon|midday)$""", OPT)
    private val TONIGHT = Regex("""\s+(tonight)$""", OPT)

    // Dates, at the end.
    private val RELATIVE = Regex("""$LEAD(today|tod|tomorrow|tmrw|tmr|tomorow)$""", OPT)
    private val WEEKDAY = Regex(
        """$LEAD(next\s+|this\s+)?(mon|monday|tue|tues|tuesday|wed|weds|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday)$""", OPT,
    )
    private val NEXT_WEEK = Regex("""$LEAD(next\s+week)$""", OPT)
    private val IN_N = Regex("""\s+in\s+(\d{1,3})\s+(days?|weeks?)$""", OPT)
    private const val MONTHS = "jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december"
    private val DAY_MONTH = Regex("""$LEAD(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)$""", OPT)
    private val MONTH_DAY = Regex("""$LEAD($MONTHS)\s+(\d{1,2})(?:st|nd|rd|th)?$""", OPT)

    /** What [text] ends with, read at [now]; null when it ends with no date or time (or would leave no title). */
    fun find(text: String, now: LocalDateTime, firstDay: DayOfWeek = DayOfWeek.MONDAY): Found? {
        var rest = text.trimEnd()
        var date: LocalDate? = null
        var time: LocalTime? = null
        val today = now.toLocalDate()
        // Up to one date and one time, in either order ("tomorrow 3pm", "3pm tomorrow").
        // "tonight" is today's date (20:00 unless a time is given too: "tonight 9pm", "9pm tonight").
        var tonight = false
        repeat(2) {
            if (time == null) {
                matchTime(rest)?.let { (t, cut) -> time = t; rest = rest.substring(0, cut) }
            }
            if (date == null) {
                TONIGHT.find(rest)?.let { date = today; tonight = true; rest = rest.substring(0, it.range.first) }
                if (date == null) matchDate(rest, today, firstDay)?.let { (d, cut) -> date = d; rest = rest.substring(0, cut) }
            }
        }
        if (tonight && time == null) time = LocalTime.of(20, 0)
        if (date == null && time == null) return null
        val title = rest.trim()
        if (title.isEmpty()) return null
        // A time alone: today if it's still to come, else tomorrow.
        val day = date ?: if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
        return Found(title, day, time, passed = !day.atTime(time ?: LocalTime.of(9, 0)).isAfter(now))
    }

    private fun matchTime(s: String): Pair<LocalTime, Int>? {
        CLOCK.find(s)?.let { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            val hour = if (m.groupValues[3].isNotEmpty()) ampm(h, m.groupValues[3]) else h.takeIf { it in 0..23 }
            if (hour != null && min in 0..59) return LocalTime.of(hour, min) to m.range.first
        }
        AMPM.find(s)?.let { m -> ampm(m.groupValues[1].toInt(), m.groupValues[2])?.let { return LocalTime.of(it, 0) to m.range.first } }
        BARE_AT.find(s)?.let { m ->
            // "at 3" is 15:00 and "at 9" 9:00: an hour from 1 to 7 without am/pm is read as the afternoon.
            val h = m.groupValues[1].toInt()
            val hour = when (h) { in 1..7 -> h + 12; in 8..23 -> h; else -> null }
            if (hour != null) return LocalTime.of(hour, 0) to m.range.first
        }
        NOON.find(s)?.let { return LocalTime.NOON to it.range.first }
        return null
    }

    private fun ampm(h: Int, suffix: String): Int? {
        if (h !in 1..12) return null
        val pm = suffix.lowercase().startsWith("p")
        return when {
            pm && h < 12 -> h + 12
            !pm && h == 12 -> 0
            else -> h
        }
    }

    private fun matchDate(s: String, today: LocalDate, firstDay: DayOfWeek): Pair<LocalDate, Int>? {
        RELATIVE.find(s)?.let { m ->
            val d = if (m.groupValues[1].lowercase().startsWith("tod")) today else today.plusDays(1)
            return d to m.range.first
        }
        NEXT_WEEK.find(s)?.let { m ->
            // The first day of next week (the phone's first day of the week).
            return today.with(TemporalAdjusters.next(firstDay)) to m.range.first
        }
        WEEKDAY.find(s)?.let { m ->
            val day = weekday(m.groupValues[2])
            // "fri": the coming Friday (today if it is Friday); "next fri": a week after that.
            val coming = today.with(TemporalAdjusters.nextOrSame(day))
            val d = if (m.groupValues[1].lowercase().startsWith("next")) coming.plusWeeks(1) else coming
            return d to m.range.first
        }
        IN_N.find(s)?.let { m ->
            val n = m.groupValues[1].toLong()
            val d = if (m.groupValues[2].lowercase().startsWith("week")) today.plusWeeks(n) else today.plusDays(n)
            return d to m.range.first
        }
        DAY_MONTH.find(s)?.let { m -> onOrAfter(today, m.groupValues[1].toInt(), month(m.groupValues[2]))?.let { return it to m.range.first } }
        MONTH_DAY.find(s)?.let { m -> onOrAfter(today, m.groupValues[2].toInt(), month(m.groupValues[1]))?.let { return it to m.range.first } }
        return null
    }

    /** [day] [month] this year, or next year once it has gone by; null for a day the month doesn't have. */
    private fun onOrAfter(today: LocalDate, day: Int, month: Month): LocalDate? =
        (today.year..today.year + 8).firstNotNullOfOrNull { y ->
            runCatching { LocalDate.of(y, month, day) }.getOrNull()?.takeIf { !it.isBefore(today) }
        }

    private fun weekday(word: String): DayOfWeek = when (word.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun month(word: String): Month = Month.entries.first { it.name.lowercase().startsWith(word.lowercase().take(3)) }
}
