package io.github.baslawson.smartnotes.data

import java.time.LocalDate

/** Notes' sort button. Pinned notes stay on top whatever the order. */
enum class NoteSort(val label: String) { EDITED("Last edited"), CREATED("Created"), TITLE("Title A–Z"), MINE("My order") }

/** Tasks' sort button (open tasks; done ones stay below, latest done first). */
enum class TaskSort(val label: String) { PRIORITY("Priority"), DUE("Due date"), NEWEST("Newest"), TITLE("A–Z"), MINE("My order") }

/** Shopping items' sort button (items to buy; ticked ones stay below, latest ticked first). */
enum class ItemSort(val label: String) { ADDED("Added"), NAME("A–Z"), MINE("My order") }

/** Shopping lists' (the date chips') sort button. */
enum class ListSort(val label: String) { DATE("By date"), MINE("My order") }

/**
 * The orders the sort buttons offer, as plain functions. "My order" is the rows' [position] (smaller first); rows with
 * the same position (never arranged) fall back to the section's first order.
 */
object Sorting {
    private val text = Comparator<String> { a, b -> a.compareTo(b, ignoreCase = true) }

    /** [titleOf]: the title shown (a note without one shows its first line). */
    fun notes(notes: List<Note>, sort: NoteSort, titleOf: (Note) -> String): List<Note> {
        val edited = compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id }
        val order = when (sort) {
            NoteSort.EDITED -> edited
            NoteSort.CREATED -> compareByDescending<Note> { it.createdAt }.thenByDescending { it.id }
            NoteSort.TITLE -> compareBy<Note, String>(text) { titleOf(it) }.then(edited)
            NoteSort.MINE -> compareBy<Note> { it.position }.then(edited)
        }
        return notes.sortedWith(compareByDescending<Note> { it.pinned }.then(order))
    }

    /** Open tasks. */
    fun tasks(todos: List<Todo>, sort: TaskSort): List<Todo> {
        val priority = compareByDescending<Todo> { it.priority }.thenByDescending { it.createdAt }.thenByDescending { it.id }
        val order = when (sort) {
            TaskSort.PRIORITY -> priority
            // Dated first (soonest first; a date without a time counts as 09:00, as its reminder), undated after.
            TaskSort.DUE -> compareBy<Todo> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.dueTime ?: "09:00" }.then(priority)
            TaskSort.NEWEST -> compareByDescending<Todo> { it.createdAt }.thenByDescending { it.id }
            TaskSort.TITLE -> compareBy<Todo, String>(text) { it.title }.then(priority)
            TaskSort.MINE -> compareBy<Todo> { it.position }.then(priority)
        }
        return todos.sortedWith(order)
    }

    /** Items still to buy. */
    fun items(items: List<ShoppingItem>, sort: ItemSort): List<ShoppingItem> {
        val added = compareBy<ShoppingItem> { it.addedAt }.thenBy { it.id }
        val order = when (sort) {
            ItemSort.ADDED -> added
            ItemSort.NAME -> compareBy<ShoppingItem, String>(text) { it.name }.then(added)
            ItemSort.MINE -> compareBy<ShoppingItem> { it.position }.then(added)
        }
        return items.sortedWith(order)
    }

    /** [ids] with [moved] put where [target] is (a drag ended there); unchanged if either isn't in [ids]. */
    fun move(ids: List<Long>, moved: Long, target: Long): List<Long> {
        val from = ids.indexOf(moved)
        val to = ids.indexOf(target)
        if (from < 0 || to < 0 || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
