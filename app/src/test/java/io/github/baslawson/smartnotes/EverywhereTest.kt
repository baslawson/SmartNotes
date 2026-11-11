package io.github.baslawson.smartnotes

import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.ui.Everywhere
import io.github.baslawson.smartnotes.ui.Found
import io.github.baslawson.smartnotes.ui.Hit
import io.github.baslawson.smartnotes.ui.Hits
import io.github.baslawson.smartnotes.ui.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EverywhereTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val lists = listOf(
        ShoppingList(1, "Shopping"),
        ShoppingList(2, "", date = "2026-11-10"),
        ShoppingList(3, "", date = "2026-11-12"),
        ShoppingList(4, "", date = "2026-11-14"),
    )

    private fun item(id: Long, list: Long, name: String, checked: Boolean = false) =
        ShoppingItem(id, list, name, name.lowercase(), checked = checked)

    private fun collect(query: String, items: List<ShoppingItem> = emptyList(), todos: List<Todo> = emptyList(), notes: List<Note> = emptyList()) =
        Everywhere.collect(query, notes, todos, items, lists, TaskSort.PRIORITY, today)

    @Test fun itemsMatchWordStartsAndSayWhereTheyAre() {
        val items = listOf(
            item(1, 2, "Oat milk"), item(2, 3, "Milk", checked = true), item(3, 4, "milkshake"),
            item(4, 1, "Buttermilk"), item(5, 3, "Bread"), item(6, 99, "Milk powder"),
        )
        val found = collect("milk", items)
        // "Buttermilk" doesn't start with milk; an item on a list not in use (99) isn't shown. Still to buy first, then
        // by the lists' order (Anytime, today and ahead, then past), then ticked.
        assertEquals(listOf(3L, 1L, 2L), found.items.map { it.first.id })
        assertEquals(listOf(4L, 2L, 3L), found.items.map { it.second.id })
        // Every word must match.
        assertEquals(listOf(1L), collect("oat mil", items).items.map { it.first.id })
    }

    @Test fun tasksOpenFirstThenDone() {
        val todos = listOf(
            Todo(1, "Call the bank", done = true, doneAt = 5),
            Todo(2, "Bank transfer", details = "rent"),
            Todo(3, "Walk", details = "via the bank"),
            Todo(4, "Banquet"),
        )
        // Open ones in the sort's order (Priority: same priority, newest first), done ones after.
        assertEquals(listOf(4L, 3L, 2L, 1L), collect("ban", todos = todos).tasks.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), collect("bank", todos = todos).tasks.map { it.id })
    }

    @Test fun notesComeAsTheListShowsThem() {
        val notes = listOf(Note(7, "", "Trip\nPack the #travel bag\n- [ ] passport", false, 0, 0))
        val row = collect("pack", notes = notes).notes.single()
        assertEquals("Trip", row.title)
        assertEquals(setOf("travel"), row.tags)
        assertEquals(1, row.openTasks)
    }

    @Test fun nothingTypedFindsNothing() {
        val found = collect("  !! ", listOf(item(1, 3, "milk")))
        assertEquals(0, found.total)
        assertTrue(found.terms.isEmpty())
    }

    @Test fun ownSectionFirstOthersShortenedUntilShown() {
        val found = Found(
            "x", listOf("x"),
            tasks = List(7) { Todo(it.toLong(), "x$it") },
            items = List(2) { item(it.toLong(), 3, "x$it") to lists[2] },
            loaded = true,
        )
        val fromShopping = Hits.of(found, Section.SHOPPING, emptySet())
        // Shopping (its own) first, all of it; then Tasks, its first 5 and "Show all"; Notes found nothing: left out.
        assertEquals(
            listOf("s:SHOPPING", "i:0", "i:1", "s:TASKS", "t:0", "t:1", "t:2", "t:3", "t:4", "m:TASKS"),
            fromShopping.map { it.key },
        )
        assertEquals(Hit.More(Section.TASKS, 2), fromShopping.last())
        assertEquals(9, Hits.of(found, Section.SHOPPING, setOf(Section.TASKS)).count { it is Hit.TaskHit } + 2)
        // From Notes, which found nothing: "Nothing in Notes" first.
        assertEquals(listOf("s:NOTES", "z:NOTES", "s:TASKS"), Hits.of(found, Section.NOTES, emptySet()).take(3).map { it.key })
        // Notes lists its own notes itself.
        assertEquals("s:TASKS", Hits.of(found, Section.NOTES, emptySet(), withOwn = false).first().key)
    }
}
