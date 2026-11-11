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

    @Test fun tasksGroupByDay() {
        // Thursday 12 Nov 2026; the week starts on Monday (ends Sun 15) or Sunday (ends Sat 14).
        val today = LocalDate.of(2026, 11, 12)
        val todos = listOf(
            Todo(1, "late", dueDate = "2026-11-10"),
            Todo(2, "today later", dueDate = "2026-11-12", dueTime = "18:00"),
            Todo(3, "today", dueDate = "2026-11-12"),
            Todo(4, "tomorrow", dueDate = "2026-11-13"),
            Todo(5, "sunday", dueDate = "2026-11-15"),
            Todo(6, "saturday", dueDate = "2026-11-14"),
            Todo(7, "next week", dueDate = "2026-11-17"),
            Todo(8, "no date"),
            Todo(9, "broken date", dueDate = "soon"),
        )
        fun groups(first: java.time.DayOfWeek) =
            Sorting.taskDays(todos, today, first).map { (day, ts) -> day to ts.map { it.id } }
        assertEquals(
            listOf(
                TaskDay.OVERDUE to listOf(1L), TaskDay.TODAY to listOf(3L, 2L), TaskDay.TOMORROW to listOf(4L),
                TaskDay.WEEK to listOf(6L, 5L), TaskDay.LATER to listOf(7L), TaskDay.NONE to listOf(9L, 8L),
            ),
            groups(java.time.DayOfWeek.MONDAY),
        )
        // A week starting on Sunday ends on Saturday: Sunday 15 is next week.
        assertEquals(listOf(6L), groups(java.time.DayOfWeek.SUNDAY).first { it.first == TaskDay.WEEK }.second)
        assertEquals(listOf(5L, 7L), groups(java.time.DayOfWeek.SUNDAY).first { it.first == TaskDay.LATER }.second)
        // Empty groups are left out; tomorrow being the week's last day leaves "This week" empty.
        val sat = LocalDate.of(2026, 11, 14)
        assertEquals(listOf(TaskDay.TODAY, TaskDay.TOMORROW), Sorting.taskDays(listOf(todos[5], todos[4]), sat, java.time.DayOfWeek.MONDAY).map { it.first })
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

    @Test fun moveTakesARowToWhereAnotherWas() {
        assertEquals(listOf(2L, 3L, 1L, 4L), Sorting.move(listOf(1, 2, 3, 4), 1, 3))
        assertEquals(listOf(4L, 1L, 2L, 3L), Sorting.move(listOf(1, 2, 3, 4), 4, 1))
        assertEquals(listOf(1L, 2L), Sorting.move(listOf(1, 2), 1, 9))
    }

    @Test fun itemsByAisleInTheShopsOrderThenAToZ() {
        val items = listOf(
            ShoppingItem(1, 2, "Milk", "milk", addedAt = 1), ShoppingItem(2, 2, "tea", "tea", addedAt = 2),
            ShoppingItem(3, 2, "apples", "apples", addedAt = 3), ShoppingItem(4, 2, "birthday card", "birthday card", addedAt = 4),
            ShoppingItem(5, 2, "bananas", "bananas", addedAt = 5), ShoppingItem(6, 2, "cheese", "cheese", addedAt = 6),
        )
        val chosen = mapOf("cheese" to io.github.baslawson.smartnotes.parse.Aisle.SNACKS)
        assertEquals(listOf(3L, 5L, 1L, 2L, 6L, 4L), Sorting.items(items, ItemSort.AISLE, chosen).map { it.id })
        val groups = Sorting.itemAisles(items, chosen)
        assertEquals(listOf("Fruit & veg", "Dairy & eggs", "Breakfast", "Snacks & sweets", "Other"), groups.map { it.first.label })
        assertEquals(listOf(3L, 5L), groups.first().second.map { it.id })
    }
}
