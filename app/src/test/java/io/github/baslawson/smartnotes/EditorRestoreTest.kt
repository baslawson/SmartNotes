package io.github.baslawson.smartnotes

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baslawson.smartnotes.ui.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A new note's screen brought back after Android closed the app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EditorRestoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<SmartNotesApp>()

    private fun editor(saved: SavedStateHandle, template: Long = 0L) =
        EditorViewModel(app.notes, app.bin, app.appScope, 0L, "", templateId = template, saved = saved)

    @Test fun aNewNoteSavedBeforeIsOpenedAsThatNote() {
        // Saved once (id 9) before the app was closed: that note, loaded from the database, not the template again.
        val vm = editor(SavedStateHandle(mapOf("newNoteId" to 9L)), template = 5L)
        assertEquals(9L, vm.id)
        assertEquals(false, vm.isNew)
        assertEquals(false, vm.loaded)
    }

    @Test fun aNewNoteNotSavedYetStartsNew() {
        val vm = editor(SavedStateHandle())
        assertEquals(0L, vm.id)
        assertEquals(true, vm.isNew)
        assertEquals(true, vm.loaded)
    }
}
