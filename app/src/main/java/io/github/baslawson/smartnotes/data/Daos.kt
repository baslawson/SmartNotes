package io.github.baslawson.smartnotes.data

import androidx.room.Dao
import java.time.ZoneId
import java.time.LocalDate
import io.github.baslawson.smartnotes.parse.Shopping
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    /** [match] must come from Search.matchQuery, which only lets safe FTS syntax through. */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.rowid " +
            "WHERE notes_fts MATCH :match AND notes.deletedAt IS NULL ORDER BY notes.pinned DESC, notes.updatedAt DESC"
    )
    fun search(match: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    suspend fun all(): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Note>>

    /** To the recycle bin ([at] a time) or back out of it (null). */
    @Query("UPDATE notes SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, at: Long?)

    /** Erases notes in the bin: those of [ids], or (ids null) all deleted before [before]. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND (id IN (:ids) OR deletedAt < :before)")
    suspend fun erase(ids: List<Long>, before: Long): Int

    @Insert
    suspend fun insert(note: Note): Long

    // For backups: every note (in the recycle bin too), and replacing them all.
    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun everyNote(): List<Note>

    @Insert
    suspend fun insertAll(notes: List<Note>)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    // ---------- My order ----------

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long)

    @Query("SELECT COALESCE(MIN(position), 0) FROM notes")
    suspend fun minPosition(): Long

    /** [ids] in their new order, numbered 0, 1, 2… in one go. */
    @Transaction
    suspend fun setPositions(ids: List<Long>) { ids.forEachIndexed { i, id -> setPosition(id, i.toLong()) } }
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NULL ORDER BY date IS NULL DESC, date ASC, id ASC")
    fun observeLists(): Flow<List<ShoppingList>>

    /** The lists in use (not in the recycle bin). */
    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NULL ORDER BY id")
    suspend fun allLists(): List<ShoppingList>

    /** Every list, in the bin too (to cancel or set their reminders). */
    @Query("SELECT * FROM shopping_lists")
    suspend fun everyList(): List<ShoppingList>

    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeletedLists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NOT NULL AND (id IN (:ids) OR deletedAt < :before)")
    suspend fun toErase(ids: List<Long>, before: Long): List<ShoppingList>

    @Query("UPDATE shopping_lists SET deletedAt = :at WHERE id IN (:ids) AND id != 1")
    suspend fun setDeleted(ids: List<Long>, at: Long?)

    @Query("SELECT COUNT(*) FROM shopping_items WHERE listId = :listId")
    suspend fun itemCount(listId: Long): Int

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun list(id: Long): ShoppingList?

    @Insert
    suspend fun insertList(list: ShoppingList): Long

    @Update
    suspend fun updateList(list: ShoppingList)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun deleteItemsOf(listId: Long)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY checked ASC, CASE WHEN checked THEN -checkedAt ELSE addedAt END ASC")
    fun observeItems(listId: Long): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND NOT checked ORDER BY addedAt")
    suspend fun openItems(listId: Long): List<ShoppingItem>

    @Query("SELECT * FROM shopping_history ORDER BY useCount DESC, lastUsed DESC")
    fun observeHistory(): Flow<List<ShoppingHistory>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND `key` = :key LIMIT 1")
    suspend fun findByKey(listId: Long, key: String): ShoppingItem?

    @Query("SELECT * FROM shopping_history WHERE `key` = :key")
    suspend fun history(key: String): ShoppingHistory?

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun item(id: Long): ShoppingItem?

    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    suspend fun itemsOf(listId: Long): List<ShoppingItem>

    /**
     * Moves what is still to buy on past lists (see [carriesOver]) to [today]'s list, made if needed, in one go (see
     * [planCarryOver]). Returns how many items were carried over (0: nothing to do, no list made).
     */
    @Transaction
    suspend fun carryOver(today: LocalDate, zone: ZoneId, now: Long): Int {
        val sources = allLists().filter { carriesOver(it, today, zone) }.sortedWith(compareBy({ it.date }, { it.id }))
        val open = sources.flatMap { l -> openItems(l.id).sortedWith(compareBy({ it.position }, { it.addedAt }, { it.id })) }
        if (open.isEmpty()) return 0
        val day = today.toString()
        val todayId = allLists().firstOrNull { it.date == day }?.id
            ?: insertList(ShoppingList(name = day, date = day, createdAt = now, position = maxListPosition() + 1))
        val plan = planCarryOver(open, itemsOf(todayId))
        var position = maxItemPosition(todayId)
        // At the end of today's list in every order: "Added" goes by addedAt, My order by position.
        plan.move.forEachIndexed { i, item -> update(item.copy(listId = todayId, position = ++position, addedAt = now + i)) }
        plan.update.forEach { update(it) }
        plan.drop.forEach { delete(it) }
        return plan.move.size
    }

    /**
     * Adds [name]/[quantity] to [listId] in one go (so carrying over meanwhile can't make a second one): returns true
     * when added, false when the list had it already (unticked, and given the new quantity if one was typed), null when
     * the list isn't there.
     */
    @Transaction
    suspend fun addItem(listId: Long, name: String, quantity: String, now: Long, exactQuantity: Boolean = false): Boolean? {
        if (list(listId).let { it == null || it.deletedAt != null }) return null
        val key = Shopping.key(name)
        val existing = findByKey(listId, key)
        if (existing == null) {
            // At the end of "My order", as in the order added.
            insert(ShoppingItem(listId = listId, name = name, key = key, quantity = quantity, addedAt = now,
                position = maxItemPosition(listId) + 1))
        } else {
            update(
                existing.copy(
                    // A staple comes back with its own quantity (none, if it had none).
                    quantity = if (exactQuantity) quantity else quantity.ifEmpty { existing.quantity },
                    checked = false,
                    addedAt = if (existing.checked) now else existing.addedAt,
                )
            )
        }
        val past = history(key)
        upsertHistory(ShoppingHistory(key, name, (past?.useCount ?: 0) + 1, now))
        return existing == null
    }

    /** Puts back an item removed with ✕ (Undo), unless its list is gone or has it again; in one go. */
    @Transaction
    suspend fun restoreItem(item: ShoppingItem) {
        if (allLists().none { it.id == item.listId } || findByKey(item.listId, item.key) != null) return
        insert(item)
    }

    /**
     * Renames item [id] (only while its key is still [expectedKey]: a draft made before a restore can't rename another
     * item) to [name]/[quantity], with its suggestions, in one go. False when another item on its list has that name.
     */
    @Transaction
    suspend fun renameItem(id: Long, expectedKey: String, name: String, quantity: String, now: Long): Boolean {
        val item = item(id)?.takeIf { it.key == expectedKey } ?: return true
        val key = Shopping.key(name)
        if (key != item.key && findByKey(item.listId, key) != null) return false
        update(item.copy(name = name, key = key, quantity = quantity))
        if (name == item.name) return true // only the quantity changed: suggestions stay as they are
        val (old, new) = renamedHistory(item.key, history(item.key), history(key), key, name, now)
        if (key != item.key) {
            if (old == null) forget(item.key) else upsertHistory(old)
        }
        new?.let { upsertHistory(it) }
        return true
    }

    /**
     * Moves items [ids] to list [targetId] in one go (see [planMove]: an item the list already has isn't added twice).
     * Returns what Undo needs, or null when the target list isn't in use (nothing moved).
     */
    @Transaction
    suspend fun moveItems(ids: List<Long>, targetId: Long, now: Long): MoveUndo? {
        if (list(targetId).let { it == null || it.deletedAt != null }) return null
        val moving = ids.mapNotNull { item(it) }.filter { it.listId != targetId }
        if (moving.isEmpty()) return MoveUndo(emptyList(), emptyList())
        val target = itemsOf(targetId)
        val plan = planMove(moving, target, targetId, maxItemPosition(targetId), now)
        plan.move.forEach { update(it) }
        plan.merge.forEach { update(it) }
        plan.drop.forEach { delete(it) }
        val changed = plan.merge.map { it.id }.toSet()
        val after = (plan.move + plan.merge).associateBy { it.id }
        return MoveUndo(moving, target.filter { it.id in changed }, after)
    }

    /**
     * Undoes [moveItems]: the moved items back as they were (where their list is still in use), the merged ones too;
     * only rows still as the move left them (a tick, rename or move since is kept). A list the move made goes again
     * when it is left empty.
     */
    @Transaction
    suspend fun undoMove(undo: MoveUndo) {
        undo.merged.forEach { old -> if (item(old.id) == undo.after[old.id]) update(old) }
        for (old in undo.moved) {
            if (list(old.listId).let { it == null || it.deletedAt != null }) continue
            val now = item(old.id)
            if (now != null) {
                if (now == undo.after[old.id] && findByKey(old.listId, old.key) == null) update(old)
            } else if (old.id !in undo.after && findByKey(old.listId, old.key) == null) insert(old)
        }
        undo.madeList?.let { id -> if (itemCount(id) == 0 && list(id)?.remindAt == null) deleteList(id) }
    }

    /** Puts [sourceId]'s items on [targetId] (not twice), then erases [sourceId]. */
    @Transaction
    suspend fun mergeList(sourceId: Long, targetId: Long, now: Long) {
        moveItems(itemsOf(sourceId).map { it.id }, targetId, now) ?: return
        deleteItemsOf(sourceId)
        deleteList(sourceId)
    }

    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Update
    suspend fun update(item: ShoppingItem)

    @Upsert
    suspend fun upsertHistory(history: ShoppingHistory)

    // For backups.
    @Query("SELECT * FROM shopping_items ORDER BY id")
    suspend fun allItems(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_history")
    suspend fun allHistory(): List<ShoppingHistory>

    @Insert
    suspend fun insertLists(lists: List<ShoppingList>)

    @Insert
    suspend fun insertItems(items: List<ShoppingItem>)

    @Insert
    suspend fun insertHistory(history: List<ShoppingHistory>)

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAllLists()

    @Query("DELETE FROM shopping_history")
    suspend fun deleteAllHistory()

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_items WHERE listId = :listId AND checked")
    suspend fun clearChecked(listId: Long)

    @Query("DELETE FROM shopping_history WHERE `key` = :key")
    suspend fun forget(key: String)

    // ---------- My order ----------

    /** How many items each list still has to buy (lists with none aren't in it). */
    @Query("SELECT listId, COUNT(*) AS open FROM shopping_items WHERE NOT checked GROUP BY listId")
    fun observeOpenCounts(): Flow<List<OpenCount>>

    /** Items in lists in use whose key (the lowercased name) contains [pattern] (% and _ escaped with \). */
    @Query(
        "SELECT i.* FROM shopping_items i JOIN shopping_lists l ON l.id = i.listId " +
            "WHERE l.deletedAt IS NULL AND i.key LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY i.checked, i.name COLLATE NOCASE"
    )
    fun search(pattern: String): Flow<List<ShoppingItem>>

    // ---------- Aisles and staples ----------

    @Query("SELECT * FROM item_aisles")
    fun observeAisles(): Flow<List<ItemAisle>>

    @Query("SELECT * FROM item_aisles")
    suspend fun allAisles(): List<ItemAisle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAisles(aisles: List<ItemAisle>)

    @Query("DELETE FROM item_aisles WHERE `key` IN (:keys)")
    suspend fun clearAisles(keys: List<String>)

    @Query("DELETE FROM item_aisles")
    suspend fun deleteAllAisles()

    @Query("SELECT * FROM staples ORDER BY addedAt, `key`")
    fun observeStaples(): Flow<List<Staple>>

    @Query("SELECT * FROM staples ORDER BY addedAt, `key`")
    suspend fun allStaples(): List<Staple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStaples(staples: List<Staple>)

    @Query("DELETE FROM staples WHERE `key` IN (:keys)")
    suspend fun dropStaples(keys: List<String>)

    @Query("DELETE FROM staples")
    suspend fun deleteAllStaples()

    /**
     * A tick on the shopping widget's line for item [id] on list [listId], in one go: the item and the copies of its
     * name the widget showed as that line ([copiesToTick]). Nothing happens if the item has gone, moved or is ticked
     * already, or if its list is in the recycle bin.
     */
    @Transaction
    suspend fun tickFromWidget(id: Long, listId: Long, today: java.time.LocalDate, zone: java.time.ZoneId, now: Long) {
        val item = item(id)?.takeIf { it.listId == listId && !it.checked } ?: return
        val lists = allLists()
        if (lists.none { it.id == listId }) return
        val items = lists.filter { it.id != listId && carriesOver(it, today, zone) }.flatMap { openItems(it.id) }
        copiesToTick(lists, items + item, item, today, zone).forEach { setChecked(it.id, true, now) }
    }

    /** Ticks or unticks just that (not the rest of the row, which may have changed meanwhile: its place, say). */
    @Query("UPDATE shopping_items SET checked = :checked, checkedAt = :checkedAt WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, checkedAt: Long)

    /** Only while the item is still on [listId]: an order saved late can't move an item carried to another list. */
    @Query("UPDATE shopping_items SET position = :position WHERE id = :id AND listId = :listId")
    suspend fun setItemPosition(listId: Long, id: Long, position: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM shopping_items WHERE listId = :listId")
    suspend fun maxItemPosition(listId: Long): Long

    @Transaction
    suspend fun setItemPositions(listId: Long, ids: List<Long>) { ids.forEachIndexed { i, id -> setItemPosition(listId, id, i.toLong()) } }

    @Query("UPDATE shopping_lists SET position = :position WHERE id = :id")
    suspend fun setListPosition(id: Long, position: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM shopping_lists")
    suspend fun maxListPosition(): Long

    @Transaction
    suspend fun setListPositions(ids: List<Long>) { ids.forEachIndexed { i, id -> setListPosition(id, i.toLong()) } }
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos")
    suspend fun all(): List<Todo>

    // For backups.
    @Insert
    suspend fun insertAll(todos: List<Todo>)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()

    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Todo>>

    @Query("SELECT id FROM todos WHERE done AND deletedAt IS NULL")
    suspend fun doneIds(): List<Long>

    @Query("UPDATE todos SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, at: Long?)

    @Query("SELECT id FROM todos WHERE deletedAt IS NOT NULL AND (id IN (:ids) OR deletedAt < :before)")
    suspend fun toErase(ids: List<Long>, before: Long): List<Long>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)


    // ---------- My order ----------

    @Query("UPDATE todos SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long)

    @Query("SELECT COALESCE(MIN(position), 0) FROM todos")
    suspend fun minPosition(): Long

    @Transaction
    suspend fun setPositions(ids: List<Long>) { ids.forEachIndexed { i, id -> setPosition(id, i.toLong()) } }
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE kind = :kind AND ownerId = :ownerId")
    suspend fun of(kind: String, ownerId: Long): List<AlarmEntry>

    @Query("SELECT * FROM alarms WHERE kind = :kind AND ownerId = :ownerId AND at = :at")
    suspend fun find(kind: String, ownerId: Long, at: String): AlarmEntry?

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntry?

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntry>

    @Insert
    suspend fun insert(entry: AlarmEntry): Long

    @Update
    suspend fun update(entry: AlarmEntry)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}

/** A list's number of items still to buy. */
data class OpenCount(val listId: Long, val open: Int)
