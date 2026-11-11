package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SortingTest {
    private fun note(id: Long, title: String, created: Long, edited: Long, position: Long = 0, pinned: Boolean = false) =
        Note(id, title, "", pinned, created, edited, position = position)

    private val notes = listOf(
        note(1, "banana", created = 1, edited = 30, position = 2),
        note(2, "Apple", created = 3, edited = 10, position = 0),
        note(3, "cherry", created = 2, edited = 20, position = 1, pinned = true),
        note(4, "date", created = 4, edited = 40, position = 0, pinned = true),
    )

    @Test fun notesKeepPinnedOnTopInEveryOrder() {
        fun ids(sort: NoteSort) = Sorting.notes(notes, sort) { it.title }.map { it.id }
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(NoteSort.EDITED))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(NoteSort.CREATED))
        assertEquals(listOf(3L, 4L, 2L, 1L), ids(NoteSort.TITLE)) // A–Z ignores capitals
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(NoteSort.MINE))
    }

    @Test fun myOrderTiesFallBackToTheFirstOrder() {
        val tied = notes.map { it.copy(position = 0, pinned = false) }
        assertEquals(Sorting.notes(tied, NoteSort.EDITED) { it.title }, Sorting.notes(tied, NoteSort.MINE) { it.title })
    }

    @Test fun tasksSortByPriorityDueNewestTitleOrMine() {
        val todos = listOf(
            Todo(1, "walk dog", priority = 1, createdAt = 1, dueDate = "2026-11-20", position = 3),
            Todo(2, "Buy milk", priority = 2, createdAt = 2, position = 1),
            Todo(3, "call mum", priority = 1, createdAt = 3, dueDate = "2026-11-12", dueTime = "18:00", position = 2),
            Todo(4, "pay rent", priority = 0, createdAt = 4, dueDate = "2026-11-12", position = 0),
        )
        fun ids(sort: TaskSort) = Sorting.tasks(todos, sort).map { it.id }
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(TaskSort.PRIORITY))
        // Same day: no time counts as 09:00, so before 18:00. Undated last.
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(TaskSort.DUE))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(TaskSort.NEWEST))
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(TaskSort.TITLE))
        assertEquals(listOf(4L, 2L, 3L, 1L), ids(TaskSort.MINE))
    }

    @Test fun itemsSortByAddedNameOrMine() {
        val items = listOf(
            ShoppingItem(1, 1, "milk", "milk", addedAt = 1, position = 2),
            ShoppingItem(2, 1, "Bread", "bread", addedAt = 2, position = 0),
            ShoppingItem(3, 1, "apples", "apples", addedAt = 3, position = 1),
        )
        assertEquals(listOf(1L, 2L, 3L), Sorting.items(items, ItemSort.ADDED).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), Sorting.items(items, ItemSort.NAME).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), Sorting.items(items, ItemSort.MINE).map { it.id })
    }

    @Test fun listsByDateOrInMyOrder() {
        val today = LocalDate.of(2026, 11, 11)
        val lists = listOf(
            ShoppingList(1, "Shopping", position = 3),
            ShoppingList(2, "", date = "2026-11-10", position = 0),
            ShoppingList(3, "", date = "2026-11-12", position = 1),
            ShoppingList(4, "", date = "2026-11-11", position = 1),
        )
        assertEquals(listOf(1L, 4L, 3L, 2L), Sorting.lists(lists, ListSort.DATE, today).map { it.id })
        // Same position (3 and 4): date order decides.
        assertEquals(listOf(2L, 4L, 3L, 1L), Sorting.lists(lists, ListSort.MINE, today).map { it.id })
    }

    @Test fun moveTakesARowToWhereAnotherWas() {
        assertEquals(listOf(2L, 3L, 1L, 4L), Sorting.move(listOf(1, 2, 3, 4), 1, 3))
        assertEquals(listOf(4L, 1L, 2L, 3L), Sorting.move(listOf(1, 2, 3, 4), 4, 1))
        assertEquals(listOf(1L, 2L), Sorting.move(listOf(1, 2), 1, 9))
    }
}
