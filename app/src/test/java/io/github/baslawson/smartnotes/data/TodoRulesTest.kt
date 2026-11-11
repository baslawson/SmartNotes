package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoRulesTest {
    private val base = Todo(id = 5, title = "Pay rent", details = "", dueDate = "2026-11-11", repeat = "DAILY", priority = 1)

    @Test fun mergeKeepsChangesMadeElsewhereToFieldsTheEditorDidNotTouch() {
        // Done from a notification moved the repeating task on while the editor was open.
        val current = base.copy(dueDate = "2026-11-12", updatedAt = 99)
        val edited = base.copy(details = "by bank transfer")
        val merged = mergeTodo(base, edited, current)
        assertEquals("by bank transfer", merged.details)
        assertEquals("2026-11-12", merged.dueDate)
        assertEquals(false, merged.done)
    }

    @Test fun mergeTakesWhatTheEditorChangedEvenOverOtherChanges() {
        val current = base.copy(dueDate = "2026-11-12", priority = 0)
        val edited = base.copy(dueDate = "2026-12-01")
        val merged = mergeTodo(base, edited, current)
        assertEquals("2026-12-01", merged.dueDate)
        assertEquals(0, merged.priority)
    }

    @Test fun monthlyRepeatKeepsItsDayAfterAShortMonth() {
        var t = Todo(title = "Report", dueDate = "2027-01-31", repeat = "MONTHLY")
        val dates = (1..3).map { t = nextDue(t)!!; t.dueDate }
        assertEquals(listOf("2027-02-28", "2027-03-31", "2027-04-30"), dates)
        assertEquals(31, t.repeatDay)
    }

    @Test fun otherRepeatsMoveOnAndNoRepeatOrNoDateGivesNull() {
        assertEquals("2026-11-12", nextDue(base)!!.dueDate)
        assertEquals("2026-11-21", nextDue(base.copy(repeat = "EVERY_N_DAYS", repeatEvery = 10))!!.dueDate)
        assertNull(nextDue(base.copy(repeat = "NONE")))
        assertNull(nextDue(base.copy(dueDate = null)))
    }
}
