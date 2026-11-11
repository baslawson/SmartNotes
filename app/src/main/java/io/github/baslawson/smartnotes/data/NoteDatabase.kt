package io.github.baslawson.smartnotes.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Note::class, NoteFts::class, ShoppingItem::class, ShoppingHistory::class,
        ShoppingList::class, Todo::class, AlarmEntry::class, ItemAisle::class, Staple::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = NoteDatabase.To2::class),
        // Version 3: todos.repeatDay (monthly repeats keep their day); tasks are one list (no todo_lists, no listId).
        AutoMigration(from = 2, to = 3, spec = NoteDatabase.To3::class),
        // Version 4: a position on notes, tasks, shopping lists and items, for "My order".
        AutoMigration(from = 3, to = 4),
        // Version 5: the aisle chosen for an item, and staples (two new tables).
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun shopping(): ShoppingDao
    abstract fun todos(): TodoDao
    abstract fun alarms(): AlarmDao

    /** Version 2: shopping lists, to-do lists and the alarm ledger. Existing shopping items go into list 1. */
    class To2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) = insertDefaults(db)
    }

    /** Version 3: the to-do lists go; every task stays, in the one Tasks list. */
    @DeleteTable(tableName = "todo_lists")
    @DeleteColumn(tableName = "todos", columnName = "listId")
    class To3 : AutoMigrationSpec

    companion object {
        fun create(context: Context): NoteDatabase =
            Room.databaseBuilder(context, NoteDatabase::class.java, "smartnotes.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = insertDefaults(db)
                })
                .build()

        /** The list that always exists: shopping list 1 (shown as "Anytime"). */
        internal fun insertDefaults(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL("INSERT OR IGNORE INTO shopping_lists (id, name, date, remindAt, sound, createdAt) VALUES (1, 'Shopping', NULL, NULL, 'DEFAULT', $now)")
        }
    }
}
