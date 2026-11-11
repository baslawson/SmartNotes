package io.github.baslawson.smartnotes.data

import androidx.room.ColumnInfo
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
    /** When it went to the recycle bin; null while it's in use. */
    val deletedAt: Long? = null,
    /** Place in "My order" (smaller first); see data/Sorting.kt. */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

/** Full-text index over notes; Room keeps it in step with the notes table through triggers. */
@Fts4(contentEntity = Note::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
data class NoteFts(val title: String, val body: String)

/**
 * A shopping list: undated ("Shopping"), or for a day, optionally with a reminder at [remindAt] on [date].
 * [date] is an ISO date (2026-11-14), [remindAt] an ISO time (09:00); [sound] a ReminderSound name.
 */
@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String? = null,
    val remindAt: String? = null,
    @ColumnInfo(defaultValue = "DEFAULT") val sound: String = "DEFAULT",
    val createdAt: Long = 0,
    /** When it went to the recycle bin (with its items); null while it's in use. */
    val deletedAt: Long? = null,
    /** Place in "My order" (smaller first); see data/Sorting.kt. */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

@Entity(tableName = "shopping_items", indices = [Index("key"), Index("listId")])
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Items from before lists existed belong to list 1, "Shopping". */
    @ColumnInfo(defaultValue = "1") val listId: Long = 1,
    val name: String,
    /** Shopping.key(name): the same item whatever its capitals. */
    val key: String,
    val quantity: String = "",
    val checked: Boolean = false,
    val addedAt: Long = 0,
    val checkedAt: Long = 0,
    /** Place in "My order" (smaller first); see data/Sorting.kt. */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

/** Every item ever added, for suggestions while typing. */
@Entity(tableName = "shopping_history")
data class ShoppingHistory(
    @PrimaryKey val key: String,
    val name: String,
    val useCount: Int,
    val lastUsed: Long,
)

/** The aisle chosen for an item ("Aisle…"), by its [key]; items without one go by the word list (parse/Aisles.kt). */
@Entity(tableName = "item_aisles")
data class ItemAisle(
    @PrimaryKey val key: String,
    /** An Aisle name. */
    val aisle: String,
)

/** Something bought again and again: "Add staples" puts every staple on the list shown. */
@Entity(tableName = "staples")
data class Staple(
    @PrimaryKey val key: String,
    val name: String,
    val quantity: String = "",
    val addedAt: Long = 0,
)

/**
 * A to-do task. [dueDate] ISO date, [dueTime] ISO time (both optional). With [remind], it reminds at the due date and
 * time (09:00 without a time). [repeat] is a Repeat name; [repeatEvery] the N of "every N days". [priority]: 0 low,
 * 1 normal, 2 high. [sound]: a ReminderSound name.
 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val remind: Boolean = false,
    val sound: String = "DEFAULT",
    val repeat: String = "NONE",
    val repeatEvery: Int = 1,
    /** A monthly repeat's own day of the month (0 = the due date's day), so Jan 31 → Feb 28 → Mar 31. */
    @ColumnInfo(defaultValue = "0") val repeatDay: Int = 0,
    val priority: Int = 1,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** When it went to the recycle bin; null while it's in use. */
    val deletedAt: Long? = null,
    /** Place in "My order" (smaller first); see data/Sorting.kt. */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

/**
 * Every alarm set and not yet gone off: [kind] "note", "list" or "todo"; [ownerId] its note, list or task; [at] the
 * reminder's own time (ISO local date-time) and [fireAt] when the alarm goes off (later after a snooze). The row's
 * [id] is the alarm's request code. A reboot clears Android's alarms; this table sets them again and finds missed ones.
 */
@Entity(tableName = "alarms", indices = [Index(value = ["kind", "ownerId", "at"], unique = true)])
data class AlarmEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val ownerId: Long,
    val at: String,
    val fireAt: String,
)
