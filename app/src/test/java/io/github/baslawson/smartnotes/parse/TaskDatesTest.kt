package io.github.baslawson.smartnotes.parse

import io.github.baslawson.smartnotes.parse.TaskDates.Found
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskDatesTest {
    // Thursday 12 November 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 12, 10, 0)
    private val thu = now.toLocalDate()
    private fun find(s: String) = TaskDates.find(s, now)
    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun relativeDaysAndTimes() {
        assertEquals(Found("Call the dentist", thu.plusDays(1), at(15)), find("Call the dentist tomorrow 3pm"))
        assertEquals(Found("Call the dentist", thu.plusDays(1), at(15)), find("Call the dentist 3pm tomorrow"))
        assertEquals(Found("Call the dentist", thu.plusDays(1), at(15, 30)), find("Call the dentist tomorrow at 15:30"))
        assertEquals(Found("Water plants", thu, null, passed = true), find("Water plants today"))
        assertEquals(Found("Bins out", thu, at(20)), find("Bins out tonight"))
        assertEquals(Found("Lunch", thu, at(12)), find("Lunch at noon"))
        assertEquals(Found("Pack", thu.plusDays(1), null), find("Pack TMRW"))
    }

    @Test fun aTimeAloneIsTheNextOne() {
        assertEquals(Found("Meeting", thu, at(15)), find("Meeting at 3"))
        assertEquals(Found("Meeting", thu, at(11, 30)), find("Meeting 11:30am"))
        // 9:00 has gone by at 10:00: tomorrow.
        assertEquals(Found("Gym", thu.plusDays(1), at(9)), find("Gym 9am"))
        assertEquals(Found("Midnight snack", thu.plusDays(1), at(0)), find("Midnight snack 12am"))
    }

    @Test fun weekdays() {
        assertEquals(Found("Pay rent", LocalDate.of(2026, 11, 13), null), find("Pay rent fri"))
        assertEquals(Found("Pay rent", thu, null, passed = true), find("Pay rent on Thursday"))
        assertEquals(Found("Pay rent", LocalDate.of(2026, 11, 19), null), find("Pay rent next thu"))
        assertEquals(Found("Pay rent", LocalDate.of(2026, 11, 16), at(9, 15)), find("Pay rent mon 9:15"))
    }

    @Test fun datesAndSpans() {
        assertEquals(Found("Book a table", LocalDate.of(2026, 12, 12), at(19, 30)), find("Book a table on 12 Dec at 19:30"))
        assertEquals(Found("Book a table", LocalDate.of(2026, 12, 3), null), find("Book a table december 3rd"))
        // Gone by this year: next year's.
        assertEquals(Found("Tax", LocalDate.of(2027, 1, 31), null), find("Tax 31 jan"))
        assertEquals(Found("Leap", LocalDate.of(2028, 2, 29), null), find("Leap 29 feb"))
        assertEquals(Found("Dentist", thu.plusDays(10), null), find("Dentist in 10 days"))
        assertEquals(Found("Dentist", thu.plusWeeks(2), null), find("Dentist in 2 weeks"))
        assertEquals(Found("Plan", LocalDate.of(2026, 11, 16), null), find("Plan next week"))
        assertEquals(Found("Plan", LocalDate.of(2026, 11, 15), null), TaskDates.find("Plan next week", now, DayOfWeek.SUNDAY))
    }

    @Test fun plainTextStaysPlain() {
        assertNull(find("Buy sun cream"))
        assertNull(find("Call Monday's client"))
        assertNull(find("Pay the plumber 2.50"))
        assertNull(find("Read chapter 12"))
        assertNull(find("tomorrow"))           // nothing left for a title
        assertNull(find("Feed cat 31 feb"))    // no such day
        assertNull(find("Meet at 25:00"))
    }

    @Test fun tonightTakesAGivenTime() {
        assertEquals(Found("Call mum", thu, at(21)), find("Call mum tonight 9pm"))
        assertEquals(Found("Call mum", thu, at(21)), find("Call mum 9pm tonight"))
        assertEquals(Found("Call mum", thu, at(20)), find("Call mum tonight"))
    }

    @Test fun aMomentGoneByIsMarked() {
        // At 10:00: today (9:00) and today 8am have gone by; the chip says so, as nothing will ring.
        assertEquals(Found("Pay rent", thu, null, passed = true), find("Pay rent today"))
        assertEquals(Found("Gym", thu, at(8), passed = true), find("Gym today 8am"))
        assertEquals(Found("Gym", thu, at(11)), find("Gym today 11am"))
    }
}
