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
        if (dao.list(listId).let { it == null || it.deletedAt != null }) return@withLock
        val key = Shopping.key(parsed.name)
        val now = clock.millis()
        val existing = dao.findByKey(listId, key)
        if (existing == null) {
            // At the end of "My order", as in the order added.
            dao.insert(ShoppingItem(listId = listId, name = parsed.name, key = key, quantity = parsed.quantity, addedAt = now,
                position = dao.maxItemPosition(listId) + 1))
        } else {
            dao.update(
                existing.copy(
                    quantity = parsed.quantity.ifEmpty { existing.quantity },
                    checked = false,
                    addedAt = if (existing.checked) now else existing.addedAt,
                )
            )
        }
        AppLog.i("Shopping", (if (existing == null) "Added " else "Re-added ") + parsed.name +
            parsed.quantity.let { if (it.isEmpty()) "" else " ($it)" } + " to list $listId")
        val past = dao.history(key)
        dao.upsertHistory(ShoppingHistory(key, parsed.name, (past?.useCount ?: 0) + 1, now))
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

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) = lock.withLock {
        dao.setChecked(item.id, checked, if (checked) clock.millis() else 0)
    }

    suspend fun delete(id: Long) = lock.withLock { dao.delete(id) }

    /** Puts back an item removed with ✕ (Undo), unless its list is gone or has it again. */
    suspend fun restoreItem(item: ShoppingItem) = lock.withLock {
        if (dao.allLists().none { it.id == item.listId } || dao.findByKey(item.listId, item.key) != null) return@withLock
        dao.insert(item)
    }

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
    suspend fun listForDate(date: LocalDate): Long = lock.withLock {
        val key = date.toString()
        dao.allLists().firstOrNull { it.date == key }?.id ?: dao.insertList(
            ShoppingList(name = key, date = key, createdAt = clock.millis(), position = dao.maxListPosition() + 1)
        ).also { AppLog.i("Shopping", "New list $it for $key") }
    }

    /** Moves lists (with their items) to the recycle bin; reminders cancelled. List 1 (Anytime) always stays. */
    suspend fun trashLists(ids: List<Long>) = lock.withLock { trashUnlocked(ids) }

    /**
     * Tidies past lists (see [listsToTidy]) into the recycle bin, except [keep] (the list on screen). Run when Shopping
     * opens. "Today" is in the phone's current time zone, as the screen shows it.
     */
    suspend fun tidyPast(keep: Long, today: LocalDate = LocalDate.now()) = lock.withLock {
        val lists = dao.allLists().filter { it.id != keep }
        val past = lists.filter { l -> l.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true }
        val withOpen = past.filter { dao.openItems(it.id).isNotEmpty() }.map { it.id }.toSet()
        val ids = listsToTidy(lists, today, withOpen, ZoneId.systemDefault())
        if (ids.isNotEmpty()) {
            trashUnlocked(ids)
            AppLog.i("Shopping", "Tidied ${ids.size} past list(s) with nothing left to buy")
        }
    }

    /** "My order": the lists' ids in their new order. */
    suspend fun arrangeLists(ids: List<Long>) = lock.withLock { dao.setListPositions(ids) }

    /** "My order": the items' ids (of one list) in their new order. */
    suspend fun arrangeItems(ids: List<Long>) = lock.withLock { dao.setItemPositions(ids) }

    /** "My order" chosen the first time: [order] of the list's items still to buy, read and saved in one go. */
    suspend fun arrangeItemsFrom(listId: Long, order: (List<ShoppingItem>) -> List<ShoppingItem>) = lock.withLock {
        dao.setItemPositions(order(dao.openItems(listId)).map { it.id })
    }

    /** "My order" chosen the first time for the lists: [order] of the lists in use, read and saved in one go. */
    suspend fun arrangeListsFrom(order: (List<ShoppingList>) -> List<ShoppingList>) = lock.withLock {
        dao.setListPositions(order(dao.allLists()).map { it.id })
    }

    private suspend fun trashUnlocked(ids: List<Long>) {
        val those = ids.filter { it != 1L }
        those.chunked(CHUNK).forEach { dao.setDeleted(it, clock.millis()) }
        those.forEach { alarms.sync(AlarmScheduler.LIST, it, emptySet()); alarms.dismiss(AlarmScheduler.LIST, it) }
        AppLog.i("Shopping", "Moved ${those.size} list(s) to the recycle bin: $those")
    }

    /**
     * Back from the bin, with their reminders when still to come. A list whose day has another list by now comes
     * back without a date (see [restoredDate]).
     */
    suspend fun restoreLists(ids: List<Long>) = lock.withLock {
        for (id in ids) {
            val list = dao.list(id)?.takeIf { it.deletedAt != null } ?: continue
            val taken = dao.allLists().mapNotNull { it.date }.toSet()
            // Brought back by hand, so it is kept: counted as made now, it isn't tidied away again (see listsToTidy).
            val back = restoredList(list, taken).copy(createdAt = clock.millis())
            dao.updateList(back)
            alarms.sync(AlarmScheduler.LIST, id, setOfNotNull(back.reminderTime()))
            AppLog.i("Shopping", "Restored list $id" + if (back.date != list.date) " without its date (${list.date} has a list)" else "")
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
 * [list] as it comes back from the bin: as it was, or, when its date has another list by now ([taken]), without a date
 * or reminder, named after the day it had.
 */
fun restoredList(list: ShoppingList, taken: Set<String>): ShoppingList =
    if (list.date != null && list.date in taken) list.copy(name = "Restored (${list.date})", date = null, remindAt = null, deletedAt = null)
    else list.copy(deletedAt = null)
