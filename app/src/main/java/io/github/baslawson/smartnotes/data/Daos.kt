package io.github.baslawson.smartnotes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    /** [match] must come from Search.matchQuery, which only lets safe FTS syntax through. */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.rowid " +
            "WHERE notes_fts MATCH :match ORDER BY notes.pinned DESC, notes.updatedAt DESC"
    )
    fun search(match: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, CASE WHEN checked THEN -checkedAt ELSE addedAt END ASC")
    fun observeItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_history ORDER BY useCount DESC, lastUsed DESC")
    fun observeHistory(): Flow<List<ShoppingHistory>>

    @Query("SELECT * FROM shopping_items WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): ShoppingItem?

    @Query("SELECT * FROM shopping_history WHERE `key` = :key")
    suspend fun history(key: String): ShoppingHistory?

    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Update
    suspend fun update(item: ShoppingItem)

    @Upsert
    suspend fun upsertHistory(history: ShoppingHistory)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_items WHERE checked")
    suspend fun clearChecked()

    @Query("DELETE FROM shopping_history WHERE `key` = :key")
    suspend fun forget(key: String)
}
