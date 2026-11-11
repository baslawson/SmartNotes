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
}
