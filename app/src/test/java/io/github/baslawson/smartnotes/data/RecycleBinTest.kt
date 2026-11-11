package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.KeepFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecycleBinTest {
    private val day = KeepFor.DAY_MILLIS
    private val now = 100 * day

    @Test fun keepForGivesTheCutoffOrNoneForForever() {
        assertEquals(now - 30 * day, KeepFor.MONTH.cutoff(now))
        assertEquals(now - 7 * day, KeepFor.WEEK.cutoff(now))
        assertNull(KeepFor.FOREVER.cutoff(now))
        assertEquals(KeepFor.MONTH, KeepFor.of(null))
        assertEquals(KeepFor.MONTH, KeepFor.of("NOPE"))
    }

    @Test fun daysLeftCountsDownToZero() {
        assertEquals(30, KeepFor.MONTH.daysLeft(now, now))
        assertEquals(30, KeepFor.MONTH.daysLeft(now - day / 2, now))
        // 23 hours left is still "1 day left", not "erased at the next start".
        assertEquals(1, KeepFor.MONTH.daysLeft(now - 30 * day + 23 * day / 24, now))
        assertEquals(0, KeepFor.MONTH.daysLeft(now - 40 * day, now))
        assertNull(KeepFor.FOREVER.daysLeft(now, now))
    }

    @Test fun aRestoredListComesBackUnlessItsDayHasAnotherListThenItsItemsGoThere() {
        val list = ShoppingList(id = 4, name = "2026-11-20", date = "2026-11-20", remindAt = "09:00", deletedAt = 5)
        val other = ShoppingList(id = 7, name = "2026-11-21", date = "2026-11-21")
        assertNull(restoreTarget(list, listOf(ShoppingList(1, "Shopping"), other)))
        assertEquals(9L, restoreTarget(list, listOf(other, ShoppingList(id = 9, name = "x", date = "2026-11-20"))))
        // Without a date (an older version's "Restored (…)"): onto Anytime. Anytime itself just comes back.
        assertEquals(1L, restoreTarget(list.copy(date = null), listOf(other)))
        assertNull(restoreTarget(ShoppingList(1, "Shopping", deletedAt = 5), listOf(other)))
    }

    @Test fun binnedThingsHaveNoReminder() {
        val todo = Todo(id = 1, title = "x", dueDate = "2026-11-20", dueTime = "09:00", remind = true)
        assertEquals(java.time.LocalDateTime.parse("2026-11-20T09:00"), todo.reminderTime())
        assertNull(todo.copy(deletedAt = 1).reminderTime())
        val list = ShoppingList(id = 2, name = "d", date = "2026-11-20", remindAt = "10:00")
        assertEquals(java.time.LocalDateTime.parse("2026-11-20T10:00"), list.reminderTime())
        assertNull(list.copy(deletedAt = 1).reminderTime())
    }

    @Test fun undoBarTexts() {
        assertEquals("Note moved to the recycle bin", BinEvent.Trashed(BinKind.NOTE, listOf(1)).text)
        assertEquals("3 tasks moved to the recycle bin", BinEvent.Trashed(BinKind.TASK, listOf(1, 2, 3)).text)
        assertEquals("Removed milk", BinEvent.ItemRemoved(ShoppingItem(name = "milk", key = "milk")).text)
    }
}
