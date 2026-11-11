package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.parse.Aisle
import io.github.baslawson.smartnotes.parse.Aisles
import java.time.DayOfWeek
import java.time.LocalDate

/** Notes' sort button. Pinned notes stay on top whatever the order. */
enum class NoteSort(val label: String) { EDITED("Last edited"), CREATED("Created"), TITLE("Title A–Z"), MINE("My order") }

/** Tasks' sort button (open tasks; done ones stay below, latest done first). */
enum class TaskSort(val label: String) { PRIORITY("Priority"), DUE("Due date"), DAY("By day"), NEWEST("Newest"), TITLE("A–Z"), MINE("My order") }

/** The groups of the "By day" task sort, in the order shown. */
enum class TaskDay(val label: String) {
    OVERDUE("Overdue"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("This week"), LATER("Later"), NONE("No date"),
}

/** Shopping items' sort button (items to buy; ticked ones stay below, latest ticked first). */
enum class ItemSort(val label: String) { ADDED("Added"), NAME("A–Z"), AISLE("By aisle"), MINE("My order") }

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
            TaskSort.DUE, TaskSort.DAY -> compareBy<Todo> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.dueTime ?: "09:00" }.then(priority)
            TaskSort.NEWEST -> compareByDescending<Todo> { it.createdAt }.thenByDescending { it.id }
            TaskSort.TITLE -> compareBy<Todo, String>(text) { it.title }.then(priority)
            TaskSort.MINE -> compareBy<Todo> { it.position }.then(priority)
        }
        return todos.sortedWith(order)
    }

    /**
     * Open tasks by day: overdue (a date before [today]), today, tomorrow, the rest of this week (the week starting on
     * [firstDay]), later, and no date; soonest first within each, as "Due date". Empty groups are left out.
     */
    fun taskDays(todos: List<Todo>, today: LocalDate, firstDay: DayOfWeek): List<Pair<TaskDay, List<Todo>>> {
        val weekEnd = Week.start(today, firstDay).plusDays(6)
        fun group(t: Todo): TaskDay {
            val d = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return TaskDay.NONE
            return when {
                d.isBefore(today) -> TaskDay.OVERDUE
                d == today -> TaskDay.TODAY
                d == today.plusDays(1) -> TaskDay.TOMORROW
                !d.isAfter(weekEnd) -> TaskDay.WEEK
                else -> TaskDay.LATER
            }
        }
        val groups = tasks(todos, TaskSort.DAY).groupBy(::group)
        return TaskDay.entries.mapNotNull { day -> groups[day]?.let { day to it } }
    }

    /** Items still to buy; [aisles]: the aisles chosen for items (by key), for "By aisle". */
    fun items(items: List<ShoppingItem>, sort: ItemSort, aisles: Map<String, Aisle> = emptyMap()): List<ShoppingItem> {
        val added = compareBy<ShoppingItem> { it.addedAt }.thenBy { it.id }
        val name = compareBy<ShoppingItem, String>(text) { it.name }.then(added)
        val order = when (sort) {
            ItemSort.ADDED -> added
            ItemSort.NAME -> name
            // Aisle by aisle in the shop's order, A–Z within each.
            ItemSort.AISLE -> compareBy<ShoppingItem> { Aisles.of(it.key, it.name, aisles).ordinal }.then(name)
            ItemSort.MINE -> compareBy<ShoppingItem> { it.position }.then(added)
        }
        return items.sortedWith(order)
    }

    /** Items still to buy aisle by aisle ("By aisle"), A–Z within each; empty aisles left out. */
    fun itemAisles(items: List<ShoppingItem>, aisles: Map<String, Aisle>): List<Pair<Aisle, List<ShoppingItem>>> =
        items(items, ItemSort.AISLE, aisles).groupBy { Aisles.of(it.key, it.name, aisles) }.toList()

    /** [ids] with [moved] put where [target] is (a drag ended there); unchanged if either isn't in [ids]. */
    fun move(ids: List<Long>, moved: Long, target: Long): List<Long> {
        val from = ids.indexOf(moved)
        val to = ids.indexOf(target)
        if (from < 0 || to < 0 || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
