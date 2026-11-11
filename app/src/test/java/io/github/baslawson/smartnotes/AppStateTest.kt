package io.github.baslawson.smartnotes

import io.github.baslawson.smartnotes.ui.ANYTIME
import io.github.baslawson.smartnotes.ui.dayToShow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppStateTest {
    @Test fun placeOfRemembersTabsAndSavedNotesAndTasks() {
        assertEquals("notes", AppState.placeOf("notes", 0))
        assertEquals("shopping", AppState.placeOf("shopping?list={list}", 0))
        assertEquals("note/5", AppState.placeOf("note/{id}?title={title}", 5))
        assertEquals("todo/7", AppState.placeOf("todo/{id}", 7))
        assertNull(AppState.placeOf("note/{id}?title={title}", 0))
        assertNull(AppState.placeOf("settings", 0))
        assertNull(AppState.placeOf("bin", 0))
        assertNull(AppState.placeOf(null, 0))
    }

    @Test fun routesForOpensTheTaskOverTasks() {
        assertEquals(emptyList<String>(), AppState.routesFor("notes"))
        assertEquals(listOf("tasks"), AppState.routesFor("tasks"))
        assertEquals(listOf("shopping"), AppState.routesFor("shopping"))
        assertEquals(listOf("note/5?title="), AppState.routesFor("note/5"))
        assertEquals(listOf("tasks", "todo/7"), AppState.routesFor("todo/7"))
        assertEquals(emptyList<String>(), AppState.routesFor("todo/x"))
        assertEquals(emptyList<String>(), AppState.routesFor("garbage"))
    }

    @Test fun withoutPrefsItStartsOnNotes() {
        val state = AppState(null)
        assertEquals("notes", state.place)
        assertEquals("", state.notesQuery)
        assertEquals(0 to 0, state.scroll("notes"))
    }

    @Test fun shoppingOpensOnTheRememberedDayUnlessItHasGoneBy() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(LocalDate.of(2026, 11, 21), dayToShow("2026-11-21", today))
        assertEquals(today, dayToShow("2026-11-11", today))
        // Gone by, never saved, or unreadable: today.
        assertEquals(today, dayToShow("2026-11-10", today))
        assertEquals(today, dayToShow(null, today))
        assertEquals(today, dayToShow("17", today))
        // Anytime.
        assertNull(dayToShow(ANYTIME, today))
    }
}
