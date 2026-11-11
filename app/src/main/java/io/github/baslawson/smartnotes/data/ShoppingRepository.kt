package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.parse.Shopping
import io.github.baslawson.smartnotes.reminders.AlarmPlanner
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

/** Shopping lists (undated, or for a day with an optional reminder), their items, and the items ever added. */
class ShoppingRepository(
    private val dao: ShoppingDao,
    private val alarms: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val lock = Mutex()

    fun observeLists(): Flow<List<ShoppingList>> = dao.observeLists()
    fun observeItems(listId: Long): Flow<List<ShoppingItem>> = dao.observeItems(listId)
    fun observeHistory(): Flow<List<ShoppingHistory>> = dao.observeHistory()
    /** List id → items still to buy. */
    fun observeOpenCounts(): Flow<Map<Long, Int>> = dao.observeOpenCounts().map { rows -> rows.associate { it.listId to it.open } }
    /** Items in any list in use (past ones too) whose name contains [text], ignoring capitals. */
    fun search(text: String): Flow<List<ShoppingItem>> = dao.search(likeEscaped(Shopping.key(text)))

    /**
     * Adds "2x milk" to [listId] as milk, quantity 2x. An item already on that list isn't added twice: it is unticked
     * and, when a quantity was typed, gets the new quantity.
     */
    suspend fun add(listId: Long, input: String) = lock.withLock {
        val parsed = Shopping.parse(input) ?: return@withLock
        // Only onto a list that is there (the screen may not have picked one yet, or it was just removed).
        val added = dao.addItem(listId, parsed.name, parsed.quantity, clock.millis()) ?: return@withLock
        AppLog.i("Shopping", (if (added) "Added " else "Re-added ") + parsed.name +
            parsed.quantity.let { if (it.isEmpty()) "" else " ($it)" } + " to list $listId")
    }

    /**
     * Renames [item] to what [input] says ("2x oat milk": name and quantity; no quantity clears it). Returns false,
     * changing nothing, when another item on its list already has that name. Suggestions follow: the new name is
     * suggested, and the old one loses this use (gone when nothing else used it).
     */
    suspend fun rename(item: ShoppingItem, input: String): Boolean = lock.withLock {
        val parsed = Shopping.parse(input) ?: return@withLock true
        val done = dao.renameItem(item.id, item.key, parsed.name, parsed.quantity, clock.millis())
        if (done) AppLog.i("Shopping", "Renamed an item on list ${item.listId}")
        done
    }

    /**
     * One more or less of an item ("2x" → "3x"; see Shopping.step), from its quantity as stored now: taps on – or +
     * quicker than the screen updates each still count.
     */
    suspend fun step(id: Long, delta: Int) = lock.withLock {
        val item = dao.item(id) ?: return@withLock
        val q = Shopping.step(item.quantity, delta) ?: return@withLock
        dao.renameItem(item.id, item.key, item.name, q, clock.millis())
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) = lock.withLock {
        dao.setChecked(item.id, checked, if (checked) clock.millis() else 0)
    }

    /** A tick on the shopping widget's line for item [id] on list [listId] (see [ShoppingDao.tickFromWidget]). */
    suspend fun tickFromWidget(id: Long, listId: Long) = lock.withLock {
        dao.tickFromWidget(id, listId, LocalDate.now(clock), clock.zone, clock.millis())
    }

    suspend fun delete(id: Long) = lock.withLock { dao.delete(id) }

    /** Removes several items at once (selected). */
    suspend fun deleteItems(ids: List<Long>) = lock.withLock { ids.forEach { dao.delete(it) } }

    /** Puts back an item removed with ✕ (Undo), unless its list is gone or has it again. */
    suspend fun restoreItem(item: ShoppingItem) = lock.withLock { dao.restoreItem(item) }

    suspend fun clearChecked(listId: Long) = lock.withLock {
        dao.clearChecked(listId)
        AppLog.i("Shopping", "Cleared ticked items of list $listId")
    }

    suspend fun forget(key: String) = lock.withLock { dao.forget(key) }

    /**
     * Creates or updates a list (id 0: new) and sets its reminder to match. Returns its id, or null (nothing saved) when
     * another list already has its date: one list per day.
     */
    suspend fun saveList(list: ShoppingList): Long? = lock.withLock {
        if (list.date != null && dao.allLists().any { it.id != list.id && it.date == list.date }) {
            AppLog.i("Shopping", "List ${list.id} not saved: ${list.date} already has a list")
            return@withLock null
        }
        // A list moved to the bin meanwhile (another window) isn't brought back by saving it.
        if (list.id != 0L && dao.allLists().none { it.id == list.id }) return@withLock list.id
        val named = list.copy(name = list.name.trim().ifEmpty { "Shopping" }, deletedAt = null)
        val id = if (named.id == 0L) dao.insertList(named.copy(createdAt = clock.millis(), position = dao.maxListPosition() + 1)) else named.id.also {
            // Its place in "My order" stays as stored (the dialog's copy may be older than a drag).
            dao.updateList(named.copy(position = dao.list(named.id)?.position ?: named.position))
        }
        alarms.sync(AlarmScheduler.LIST, id, setOfNotNull(named.reminderTime()))
        AppLog.i("Shopping", "Saved list $id \"${named.name}\" date=${named.date} reminder=${named.remindAt} sound=${named.sound}")
        id
    }

    /** The list for [date], made if there isn't one yet. Returns its id. */
    suspend fun listForDate(date: LocalDate): Long = lock.withLock { listForDateUnlocked(date) }

    private suspend fun listForDateUnlocked(date: LocalDate): Long {
        val key = date.toString()
        return dao.allLists().firstOrNull { it.date == key }?.id ?: dao.insertList(
            ShoppingList(name = key, date = key, createdAt = clock.millis(), position = dao.maxListPosition() + 1)
        ).also { AppLog.i("Shopping", "New list $it for $key") }
    }

    /** Moves lists (with their items) to the recycle bin; reminders cancelled. List 1 (Anytime) always stays. */
    suspend fun trashLists(ids: List<Long>) = lock.withLock { trashUnlocked(ids) }

    /**
     * Tidies past lists (see [listsToTidy]) into the recycle bin, except [keep] (the list on screen). Run when Shopping
     * opens. "Today" is in the phone's current time zone, as the screen shows it.
     */
    suspend fun tidyPast(keep: Set<Long>, today: LocalDate = LocalDate.now()) = lock.withLock {
        val lists = dao.allLists().filter { it.id !in keep }
        val past = lists.filter { l -> l.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true }
        val withOpen = past.filter { dao.openItems(it.id).isNotEmpty() }.map { it.id }.toSet()
        val ids = listsToTidy(lists, today, withOpen, ZoneId.systemDefault())
        if (ids.isNotEmpty()) {
            trashUnlocked(ids)
            AppLog.i("Shopping", "Tidied ${ids.size} past list(s) with nothing left to buy")
        }
    }

    /**
     * Carries what is still to buy on past lists over to today's list (see [ShoppingDao.carryOver]). Run when Shopping
     * opens (before [tidyPast], which then bins the emptied lists) and when the day changes while it is open.
     */
    suspend fun carryOver(today: LocalDate = LocalDate.now()) = lock.withLock {
        val moved = dao.carryOver(today, ZoneId.systemDefault(), clock.millis())
        if (moved > 0) AppLog.i("Shopping", "Carried $moved item(s) over to today's list")
    }

    /**
     * Moves items [ids] to the list for [date] (null: Anytime), made if needed. An item that list already has isn't
     * added twice (see [planMove]). Returns what Undo needs, or null when nothing moved.
     */
    suspend fun moveItems(ids: List<Long>, date: LocalDate?): MoveUndo? = lock.withLock {
        val had = date == null || dao.allLists().any { it.date == date.toString() }
        val target = if (date == null) 1L else listForDateUnlocked(date)
        val undo = dao.moveItems(ids, target, clock.millis())?.takeIf { it.moved.isNotEmpty() }
        if (undo == null) {
            // Nothing moved: a list made just for it goes again.
            if (!had && dao.itemCount(target) == 0) dao.deleteList(target)
            return@withLock null
        }
        AppLog.i("Shopping", "Moved ${undo.moved.size} item(s) to list $target")
        if (had) undo else undo.copy(madeList = target)
    }

    /** Undo of [moveItems]. */
    suspend fun undoMove(undo: MoveUndo) = lock.withLock { dao.undoMove(undo) }

    /**
     * Adds [input] ("2x milk") to the list for [date] (null: Anytime), making that day's list if it has none yet; in one
     * go, so a delete or a date change meanwhile can't lose it or put it on another day.
     */
    suspend fun addOn(date: LocalDate?, input: String) = lock.withLock {
        val parsed = Shopping.parse(input) ?: return@withLock
        val id = if (date == null) 1L else listForDateUnlocked(date)
        val added = dao.addItem(id, parsed.name, parsed.quantity, clock.millis()) ?: return@withLock
        AppLog.i("Shopping", (if (added) "Added " else "Re-added ") + parsed.name +
            parsed.quantity.let { if (it.isEmpty()) "" else " ($it)" } + " to list $id")
    }

    /**
     * Lists without a date other than Anytime (left by older versions, which restored a list whose day had another
     * list without its date) have no place in the week strip: their items go onto Anytime.
     */
    suspend fun foldUndated() = lock.withLock {
        val stray = dao.allLists().filter { it.id != 1L && it.date == null }
        stray.forEach { dao.mergeList(it.id, 1L, clock.millis()) }
        if (stray.isNotEmpty()) AppLog.i("Shopping", "Put the items of ${stray.size} list(s) without a date on Anytime")
    }

    /** "My order": the items' ids (of one list) in their new order. */
    suspend fun arrangeItems(listId: Long, ids: List<Long>) = lock.withLock { dao.setItemPositions(listId, ids) }

    /** "My order" chosen the first time: [order] of the list's items still to buy, read and saved in one go. */
    suspend fun arrangeItemsFrom(listId: Long, order: (List<ShoppingItem>) -> List<ShoppingItem>) = lock.withLock {
        dao.setItemPositions(listId, order(dao.openItems(listId)).map { it.id })
    }

    private suspend fun trashUnlocked(ids: List<Long>) {
        val those = ids.filter { it != 1L }
        those.chunked(CHUNK).forEach { dao.setDeleted(it, clock.millis()) }
        those.forEach { alarms.sync(AlarmScheduler.LIST, it, emptySet()); alarms.dismiss(AlarmScheduler.LIST, it) }
        AppLog.i("Shopping", "Moved ${those.size} list(s) to the recycle bin: $those")
    }

    /**
     * Back from the bin, with their reminders when still to come. A list whose day has another list by now (or, from
     * an older version, without a date) puts its items on that list (or Anytime) instead, not twice.
     */
    suspend fun restoreLists(ids: List<Long>) = lock.withLock {
        for (id in ids) {
            val list = dao.list(id)?.takeIf { it.deletedAt != null } ?: continue
            val into = restoreTarget(list, dao.allLists())
            if (into != null) {
                dao.mergeList(id, into, clock.millis())
                alarms.sync(AlarmScheduler.LIST, id, emptySet())
                AppLog.i("Shopping", "Restored list $id onto list $into (its day has a list)")
                continue
            }
            // Brought back by hand, so it is kept: counted as made now, it isn't tidied away again (see listsToTidy).
            val back = list.copy(deletedAt = null, createdAt = clock.millis())
            dao.updateList(back)
            alarms.sync(AlarmScheduler.LIST, id, setOfNotNull(back.reminderTime()))
            AppLog.i("Shopping", "Restored list $id")
        }
    }

    /** Erases lists in the bin with their items: [ids], and every one deleted before [before]. */
    suspend fun eraseLists(ids: List<Long>, before: Long = 0) = lock.withLock {
        val gone = if (ids.isEmpty()) dao.toErase(ids, before) else ids.chunked(CHUNK).flatMap { dao.toErase(it, before) }.distinctBy { it.id }
        gone.forEach { dao.deleteItemsOf(it.id); dao.deleteList(it.id) }
        if (gone.isNotEmpty()) AppLog.i("Shopping", "Erased from the recycle bin: lists ${gone.map { it.id }}")
    }

    fun observeDeletedLists(): Flow<List<ShoppingList>> = dao.observeDeletedLists()
    suspend fun itemCount(listId: Long): Int = dao.itemCount(listId)
}

/**
 * Lists to tidy away: in use, their day before [today], nothing left to buy ([withOpenItems] are the lists with unticked
 * items), and made by their day. One made later (a list for a past day written up afterwards) or brought back from the
 * recycle bin after its day is kept.
 */
fun listsToTidy(lists: List<ShoppingList>, today: LocalDate, withOpenItems: Set<Long>, zone: ZoneId): List<Long> = lists.filter { l ->
    val day = l.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val made = Instant.ofEpochMilli(l.createdAt).atZone(zone).toLocalDate()
    l.id != 1L && l.deletedAt == null && day != null && day.isBefore(today) && l.id !in withOpenItems && !made.isAfter(day)
}.map { it.id }

/**
 * A list whose unticked items carry over to today: dated before [today], in use, and made on or before its day (a list
 * made for a past day afterwards, or brought back from the bin, is left as it is, as [listsToTidy] does).
 */
fun carriesOver(l: ShoppingList, today: LocalDate, zone: ZoneId): Boolean {
    val day = l.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val made = Instant.ofEpochMilli(l.createdAt).atZone(zone).toLocalDate()
    return l.id != 1L && l.deletedAt == null && day.isBefore(today) && !made.isAfter(day)
}

/**
 * What a widget tick on [item] ticks: the item, and when it's on a past list that carries over, the other open
 * copies of its name on lists that carry over too (the widget shows them as one line; carrying over keeps one).
 */
fun copiesToTick(lists: List<ShoppingList>, items: List<ShoppingItem>, item: ShoppingItem, today: LocalDate, zone: ZoneId): List<ShoppingItem> {
    val carried = lists.filter { carriesOver(it, today, zone) }.map { it.id }.toSet()
    if (item.listId !in carried) return listOf(item)
    return listOf(item) + items.filter { it.id != item.id && it.listId in carried && !it.checked && it.key == item.key }
}

/** What carrying over does: items to [move] to today's list (in order), today's items to [update], items to [drop]. */
data class CarryPlan(val move: List<ShoppingItem>, val update: List<ShoppingItem>, val drop: List<Long>)

/**
 * [open]: the unticked items of the past lists, oldest list first. An item today's list already has (by name, ticked
 * or not) isn't carried twice: the past one is dropped, and today's takes its quantity if it had none. The same item
 * on several past lists is carried once (the oldest, given a later one's quantity if it had none).
 */
fun planCarryOver(open: List<ShoppingItem>, todayItems: List<ShoppingItem>): CarryPlan {
    val todays = todayItems.associateBy { it.key }.toMutableMap()
    val moving = LinkedHashMap<String, ShoppingItem>()
    val updated = LinkedHashMap<Long, ShoppingItem>()
    val drop = mutableListOf<Long>()
    for (item in open) {
        val kept = moving[item.key] ?: todays[item.key]
        if (kept == null) {
            moving[item.key] = item
            continue
        }
        drop += item.id
        if (kept.quantity.isEmpty() && item.quantity.isNotEmpty()) {
            val filled = kept.copy(quantity = item.quantity)
            if (item.key in moving) moving[item.key] = filled else { todays[item.key] = filled; updated[filled.id] = filled }
        }
    }
    return CarryPlan(moving.values.toList(), updated.values.toList(), drop)
}

/**
 * The order of the list chips: Anytime (undated) first, then today and the days to come (soonest first), then past
 * days (newest first), so today's and tomorrow's lists are never pushed out of sight by old ones.
 */
fun orderLists(lists: List<ShoppingList>, today: LocalDate): List<ShoppingList> {
    val dated = lists.map { it to it.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
    val undated = dated.filter { it.second == null }.map { it.first }.sortedBy { it.id }
    val coming = dated.filter { it.second?.isBefore(today) == false }.sortedWith(compareBy({ it.second }, { it.first.id })).map { it.first }
    val past = dated.filter { it.second?.isBefore(today) == true }.sortedWith(compareByDescending<Pair<ShoppingList, LocalDate?>> { it.second }.thenBy { it.first.id }).map { it.first }
    return undated + coming + past
}

/**
 * Suggestions after renaming an item from [oldKey] (its history [old]) to [newKey] ([newName], its history [new]):
 * the old name with one use fewer (null: forget it) and the new name's entry (null: nothing to change). Only the
 * spelling changing ("milk" → "Milk") just updates that entry's name.
 */
fun renamedHistory(
    oldKey: String,
    old: ShoppingHistory?,
    new: ShoppingHistory?,
    newKey: String,
    newName: String,
    now: Long,
): Pair<ShoppingHistory?, ShoppingHistory?> {
    if (oldKey == newKey) return old to old?.copy(name = newName)
    val oldAfter = old?.takeIf { it.useCount > 1 }?.let { it.copy(useCount = it.useCount - 1) }
    return oldAfter to ShoppingHistory(newKey, newName, (new?.useCount ?: 0) + 1, now)
}

/** [text] for a LIKE pattern: its own % and _ (and the escape \) match only themselves. */
fun likeEscaped(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** A dated list whose day is before [today] (shown under the Past chip). */
fun ShoppingList.isPast(today: LocalDate): Boolean =
    date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true

/** A shopping list's reminder time: none without a date and time, or when it is in the recycle bin. */
fun ShoppingList.reminderTime(): LocalDateTime? = if (deletedAt != null) null else AlarmPlanner.listTime(date, remindAt)

/**
 * Where a list coming back from the bin goes: null, back as it was; or the id of the list in use ([inUse]) whose day it
 * had by now, or Anytime (1) for a list without a date: its items go there.
 */
fun restoreTarget(list: ShoppingList, inUse: List<ShoppingList>): Long? = when {
    list.id == 1L -> null
    list.date == null -> 1L
    else -> inUse.firstOrNull { it.id != list.id && it.date == list.date }?.id
}

/**
 * What [ShoppingDao.moveItems] changed: the moved items and the target list's merged items as they were before
 * ([moved], [merged]) and just after ([after]: id → row, absent when merged away), and the target list when the move
 * made it ([madeList]).
 */
data class MoveUndo(
    val moved: List<ShoppingItem>,
    val merged: List<ShoppingItem>,
    val after: Map<Long, ShoppingItem> = emptyMap(),
    val madeList: Long? = null,
)

/** Moving items: rows to [move] onto the target, target rows to [merge] into, and moved items to [drop] (merged). */
data class MovePlan(val move: List<ShoppingItem>, val merge: List<ShoppingItem>, val drop: List<Long>)

/**
 * Moving [moving] onto list [targetId] (its items [target]; [lastPosition] the highest place on it). They go at its end,
 * ticks kept. One the list already has (by name), or that comes twice, isn't added twice: the one there keeps its place,
 * takes the moved quantity (unless only it had one), and is still to buy if either was.
 */
fun planMove(moving: List<ShoppingItem>, target: List<ShoppingItem>, targetId: Long, lastPosition: Long, now: Long): MovePlan {
    val there = target.associateBy { it.key }.toMutableMap()
    val moved = LinkedHashMap<String, ShoppingItem>()
    val merged = LinkedHashMap<Long, ShoppingItem>()
    val drop = mutableListOf<Long>()
    var position = lastPosition
    for (item in moving) {
        if (item.listId == targetId) continue
        val kept = moved[item.key] ?: there[item.key]
        if (kept == null) {
            moved[item.key] = item.copy(listId = targetId, position = ++position, addedAt = now + moved.size)
            continue
        }
        drop += item.id
        val checked = kept.checked && item.checked
        val into = kept.copy(
            quantity = item.quantity.ifEmpty { kept.quantity },
            checked = checked,
            checkedAt = if (checked) kept.checkedAt else 0,
            addedAt = if (kept.checked && !checked) now else kept.addedAt,
        )
        if (item.key in moved) moved[item.key] = into else { there[item.key] = into; merged[into.id] = into }
    }
    return MovePlan(moved.values.toList(), merged.values.toList(), drop)
}
