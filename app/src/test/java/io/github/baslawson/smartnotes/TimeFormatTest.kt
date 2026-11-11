package io.github.baslawson.smartnotes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class TimeFormatTest {
    @Test fun twelveAndTwentyFourHours() {
        val h12 = TimeFormat.H12.style(null)
        val h24 = TimeFormat.H24.style(null)
        assertEquals("3:30 PM", h12.format(LocalTime.of(15, 30), Locale.US))
        assertEquals("12:05 AM", h12.format(LocalTime.of(0, 5), Locale.US))
        assertEquals("12:00 PM", h12.format(LocalTime.NOON, Locale.US))
        assertEquals("15:30", h24.format(LocalTime.of(15, 30), Locale.US))
        assertEquals("09:00", h24.format(LocalTime.of(9, 0), Locale.US))
        // Without a phone to ask, "Phone's setting" falls back to 24-hour.
        assertEquals(true, TimeFormat.PHONE.style(null).h24)
        assertEquals(TimeFormat.PHONE, TimeFormat.of("nonsense"))
    }
}
