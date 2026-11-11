package io.github.baslawson.smartnotes

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test fun textScaleIsClampedAndStepped() {
        assertEquals(0.8f, AppSettings.clampScale(0.1f))
        assertEquals(1.6f, AppSettings.clampScale(3f))
        assertEquals(1.2f, AppSettings.clampScale(1.23f))
        assertEquals(1.0f, AppSettings.clampScale(1f))
    }

    @Test fun dateStylesFormatTheChosenWay() {
        val today = java.time.LocalDate.parse("2026-11-11")
        val d = java.time.LocalDate.parse("2026-11-20")
        val en = java.util.Locale.ENGLISH
        assertEquals("20/11/2026", DateStyle.DAY_MONTH_YEAR.format(d, today, en))
        assertEquals("11/20/2026", DateStyle.MONTH_DAY_YEAR.format(d, today, en))
        assertEquals("2026-11-20", DateStyle.ISO.format(d, today, en))
        assertEquals("Fri 20 Nov", DateStyle.SHORT.format(d, today, en))
        assertEquals("Wed 6 Jan 2027", DateStyle.SHORT.format(java.time.LocalDate.parse("2027-01-06"), today, en))
    }

    @Test fun nearbyDaysAreWrittenOutAndUnknownStyleIsTheDefault() {
        val today = java.time.LocalDate.parse("2026-11-11")
        assertEquals("Today", DateStyle.ISO.dayLabel(today, today))
        assertEquals("Tomorrow", DateStyle.ISO.dayLabel(today.plusDays(1), today))
        assertEquals("Yesterday", DateStyle.ISO.dayLabel(today.minusDays(1), today))
        assertEquals(DateStyle.DAY_MONTH_YEAR, DateStyle.of(null))
        assertEquals(DateStyle.DAY_MONTH_YEAR, DateStyle.of("NOPE"))
        assertEquals(DateStyle.ISO, DateStyle.of("ISO"))
    }
}
