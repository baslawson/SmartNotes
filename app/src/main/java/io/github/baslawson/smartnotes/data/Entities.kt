package io.github.baslawson.smartnotes.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    // An INTEGER PRIMARY KEY is SQLite's rowid, which notes_fts uses to point back at the note.
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Full-text index over notes; Room keeps it in step with the notes table through triggers. */
@Fts4(contentEntity = Note::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
data class NoteFts(val title: String, val body: String)

@Entity(tableName = "shopping_items", indices = [Index("key")])
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Shopping.key(name): the same item whatever its capitals. */
    val key: String,
    val quantity: String = "",
    val checked: Boolean = false,
    val addedAt: Long = 0,
    val checkedAt: Long = 0,
)

/** Every item ever added, for suggestions while typing. */
@Entity(tableName = "shopping_history")
data class ShoppingHistory(
    @PrimaryKey val key: String,
    val name: String,
    val useCount: Int,
    val lastUsed: Long,
)
