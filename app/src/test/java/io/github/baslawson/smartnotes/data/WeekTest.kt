package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class WeekTest {
    private val wed = LocalDate.of(2026, 11, 11)

    @Test fun weeksStartOnThePhonesFirstDay() {
        assertEquals(DayOfWeek.MONDAY, Week.firstDay(Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, Week.firstDay(Locale.US))
        assertEquals(LocalDate.of(2026, 11, 9), Week.start(wed, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 8), Week.start(wed, DayOfWeek.SUNDAY))
        // The first day itself starts its own week.
        assertEquals(LocalDate.of(2026, 11, 9), Week.start(LocalDate.of(2026, 11, 9), DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 15), Week.days(LocalDate.of(2026, 11, 9)).last())
        assertEquals(7, Week.days(LocalDate.of(2026, 11, 9)).size)
    }

    @Test fun offsetsCountWholeWeeksAcrossMonthsAndYears() {
        val mon = DayOfWeek.MONDAY
        assertEquals(0, Week.offset(wed, LocalDate.of(2026, 11, 15), mon))
        assertEquals(1, Week.offset(wed, LocalDate.of(2026, 11, 16), mon))
        assertEquals(-1, Week.offset(wed, LocalDate.of(2026, 11, 8), mon))
        assertEquals(8, Week.offset(wed, LocalDate.of(2027, 1, 4), mon))
        // Sunday 15 is next week when weeks start on Sunday.
        assertEquals(1, Week.offset(wed, LocalDate.of(2026, 11, 15), DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2027, 1, 4), Week.startAt(wed, 8, mon))
        assertEquals(LocalDate.of(2026, 11, 2), Week.startAt(wed, -1, mon))
    }

    @Test fun monthLabelSpansMonthsAndYears() {
        assertEquals("November 2026", Week.monthLabel(LocalDate.of(2026, 11, 9), Locale.UK))
        assertEquals("Nov – Dec 2026", Week.monthLabel(LocalDate.of(2026, 11, 30), Locale.UK))
        assertEquals("Dec 2026 – Jan 2027", Week.monthLabel(LocalDate.of(2026, 12, 28), Locale.UK))
    }

    @Test fun monthGridFillsWholeWeeks() {
        val nov = java.time.YearMonth.of(2026, 11) // 1 November 2026 is a Sunday
        val mon = Week.monthGrid(nov, DayOfWeek.MONDAY)
        assertEquals(List(6) { null }, mon.take(6))
        assertEquals(LocalDate.of(2026, 11, 1), mon[6])
        assertEquals(42, mon.size) // 6 + 30 days + 6
        // Starting on Sunday, the 1st is the first cell.
        val sun = Week.monthGrid(nov, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sun[0])
        assertEquals(35, sun.size)
        // February 2027 starts on a Monday and has exactly four weeks; 2028 is a leap year.
        assertEquals(28, Week.monthGrid(java.time.YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)
        val feb28 = Week.monthGrid(java.time.YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2028, 2, 29), feb28.filterNotNull().last())
        assertEquals(0, feb28.size % 7)
    }

    @Test fun monthsBetweenCountsCalendarMonths() {
        val nov = java.time.YearMonth.of(2026, 11)
        assertEquals(0, Week.monthsBetween(nov, nov))
        assertEquals(2, Week.monthsBetween(nov, java.time.YearMonth.of(2027, 1)))
        assertEquals(-11, Week.monthsBetween(nov, java.time.YearMonth.of(2025, 12)))
    }
}
