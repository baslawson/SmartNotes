package io.github.baslawson.smartnotes.widget

import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.Sorting
import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.data.ItemSort
import io.github.baslawson.smartnotes.data.carriesOver
import io.github.baslawson.smartnotes.parse.Aisle
import java.time.LocalDate
import java.time.ZoneId

/** What the home-screen widgets show, worked out from the app's data (plain functions, so they can be tested). */
object WidgetData {
    /** A task on the "Today's tasks" widget: [overdue] when its date has gone by. */
    data class TaskLine(val id: Long, val title: String, val date: LocalDate, val time: String?, val overdue: Boolean, val priority: Int)

    /** Open tasks due today or earlier (overdue first), soonest first, as the "Due date" order. */
    fun todayTasks(todos: List<Todo>, today: LocalDate): List<TaskLine> =
        Sorting.tasks(todos.filter { !it.done && it.deletedAt == null }, TaskSort.DUE).mapNotNull { t ->
            val date = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (date.isAfter(today)) null else TaskLine(t.id, t.title, date, t.dueTime, date.isBefore(today), t.priority)
        }

    /** Today's shopping list, if there is one yet (the widget doesn't make one). */
    fun todayList(lists: List<ShoppingList>, today: LocalDate): ShoppingList? =
        lists.firstOrNull { it.deletedAt == null && it.date == today.toString() }

    /** Items still to buy on a list, in the order the Shopping screen shows them. */
    fun toBuy(items: List<ShoppingItem>, sort: ItemSort, aisles: Map<String, Aisle> = emptyMap()): List<ShoppingItem> =
        Sorting.items(items.filter { !it.checked }, sort, aisles)

    /**
     * The lists whose open items belong on today's widget: today's, and the past days' that carry over to today (the
     * same rule as the Shopping screen: not a list made after its own day).
     */
    fun listsForToday(lists: List<ShoppingList>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<ShoppingList> =
        lists.filter { l -> (l.deletedAt == null && l.date == today.toString()) || carriesOver(l, today, zone) }

    /**
     * What's still to buy today: today's list in the screen's order, then what past days still have (carried over to
     * today when Shopping next opens; a name today's list has, ticked or not, isn't carried), A–Z, leaving out names today's list already has.
     */
    fun toBuyToday(
        lists: List<ShoppingList>, items: List<ShoppingItem>, today: LocalDate, sort: ItemSort,
        zone: ZoneId = ZoneId.systemDefault(), aisles: Map<String, Aisle> = emptyMap(),
    ): List<ShoppingItem> {
        val todayId = todayList(lists, today)?.id
        val pastIds = listsForToday(lists, today, zone).map { it.id }.toSet() - setOfNotNull(todayId)
        val onToday = toBuy(items.filter { it.listId == todayId }, sort, aisles)
        // Every name on today's list, ticked ones too: carrying over drops past items today already has.
        val keys = items.filter { it.listId == todayId }.map { it.key }.toMutableSet()
        val carried = items.filter { it.listId in pastIds && !it.checked }.sortedBy { it.name.lowercase() }.filter { keys.add(it.key) }
        // By aisle: today's and the carried ones together, under the same aisles as Shopping will show them.
        return if (sort == ItemSort.AISLE) Sorting.items(onToday + carried, sort, aisles) else onToday + carried
    }
}
