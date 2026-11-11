package io.github.baslawson.smartnotes.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The Shopping week strip's calendar maths: weeks start on the phone's first day of the week (Monday or Sunday, say). */
object Week {
    fun firstDay(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** The first day of the week [date] is in. */
    fun start(date: LocalDate, first: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(first))

    /** The seven days of the week starting [start]. */
    fun days(start: LocalDate): List<LocalDate> = List(7) { start.plusDays(it.toLong()) }

    /** How many weeks [date]'s week is after [today]'s (negative: before). */
    fun offset(today: LocalDate, date: LocalDate, first: DayOfWeek): Int =
        ChronoUnit.WEEKS.between(start(today, first), start(date, first)).toInt()

    /** The week [offset] weeks after [today]'s: its first day. */
    fun startAt(today: LocalDate, offset: Int, first: DayOfWeek): LocalDate = start(today, first).plusWeeks(offset.toLong())

    /** "November 2026"; a week across two months "Nov – Dec 2026", across two years "Dec 2026 – Jan 2027". */
    fun monthLabel(start: LocalDate, locale: Locale = Locale.getDefault()): String {
        val end = start.plusDays(6)
        fun fmt(d: LocalDate, pattern: String) = java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(d)
        return when {
            start.month == end.month -> fmt(start, "LLLL yyyy")
            start.year == end.year -> "${fmt(start, "LLL")} – ${fmt(end, "LLL yyyy")}"
            else -> "${fmt(start, "LLL yyyy")} – ${fmt(end, "LLL yyyy")}"
        }
    }
}
