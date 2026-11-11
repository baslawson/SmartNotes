package io.github.baslawson.smartnotes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NoteGridSettingTest {
    private fun settings(name: String) =
        AppSettings(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE))

    @Test fun gridIsRememberedAndGoesIntoBackups() {
        val a = settings("a")
        assertFalse(a.noteGrid.value)
        a.setNoteGrid(true)
        // Kept for next time the app opens.
        assertTrue(settings("a").noteGrid.value)
        assertEquals(true, a.export()["noteGrid"])
        // Restored from a backup; an older backup without it leaves it as it is.
        val b = settings("b")
        b.import(a.export())
        assertTrue(b.noteGrid.value)
        b.import(a.export() - "noteGrid" + ("theme" to "LIGHT"))
        assertTrue(b.noteGrid.value)
        b.import(mapOf("noteGrid" to false))
        assertFalse(b.noteGrid.value)
    }
}
