package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.parse.Shopping
import io.github.baslawson.smartnotes.reminders.AlarmPlanner
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

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

    /**
     * Adds "2x milk" to [listId] as milk, quantity 2x. An item already on that list isn't added twice: it is unticked
     * and, when a quantity was typed, gets the new quantity.
     */
    suspend fun add(listId: Long, input: String) = lock.withLock {
        val parsed = Shopping.parse(input) ?: return@withLock
        val key = Shopping.key(parsed.name)
        val now = clock.millis()
        val existing = dao.findByKey(listId, key)
        if (existing == null) {
            dao.insert(ShoppingItem(listId = listId, name = parsed.name, key = key, quantity = parsed.quantity, addedAt = now))
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

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) = lock.withLock {
        dao.update(item.copy(checked = checked, checkedAt = if (checked) clock.millis() else 0))
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
        val id = if (named.id == 0L) dao.insertList(named.copy(createdAt = clock.millis())) else named.id.also { dao.updateList(named) }
        alarms.sync(AlarmScheduler.LIST, id, setOfNotNull(named.reminderTime()))
        AppLog.i("Shopping", "Saved list $id \"${named.name}\" date=${named.date} reminder=${named.remindAt} sound=${named.sound}")
        id
    }

    /** The list for [date], made if there isn't one yet. Returns its id. */
    suspend fun listForDate(date: LocalDate): Long = lock.withLock {
        val key = date.toString()
        dao.allLists().firstOrNull { it.date == key }?.id ?: dao.insertList(
            ShoppingList(name = key, date = key, createdAt = clock.millis())
        ).also { AppLog.i("Shopping", "New list $it for $key") }
    }

    /** Moves lists (with their items) to the recycle bin; reminders cancelled. List 1 (Anytime) always stays. */
    suspend fun trashLists(ids: List<Long>) = lock.withLock {
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
            val back = restoredList(list, taken)
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

/** A shopping list's reminder time: none without a date and time, or when it is in the recycle bin. */
fun ShoppingList.reminderTime(): LocalDateTime? = if (deletedAt != null) null else AlarmPlanner.listTime(date, remindAt)

/**
 * [list] as it comes back from the bin: as it was, or, when its date has another list by now ([taken]), without a date
 * or reminder, named after the day it had.
 */
fun restoredList(list: ShoppingList, taken: Set<String>): ShoppingList =
    if (list.date != null && list.date in taken) list.copy(name = "Restored (${list.date})", date = null, remindAt = null, deletedAt = null)
    else list.copy(deletedAt = null)
