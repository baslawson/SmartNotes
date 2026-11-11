package io.github.baslawson.smartnotes.data

import androidx.room.Dao
import androidx.room.Insert
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

    /** Ticks or unticks just that (not the rest of the row, which may have changed meanwhile: its place, say). */
    @Query("UPDATE shopping_items SET checked = :checked, checkedAt = :checkedAt WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, checkedAt: Long)

    @Query("UPDATE shopping_items SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM shopping_items WHERE listId = :listId")
    suspend fun maxItemPosition(listId: Long): Long

    @Transaction
    suspend fun setItemPositions(ids: List<Long>) { ids.forEachIndexed { i, id -> setItemPosition(id, i.toLong()) } }

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
