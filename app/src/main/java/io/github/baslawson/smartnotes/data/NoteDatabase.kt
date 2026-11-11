package io.github.baslawson.smartnotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Note::class, NoteFts::class, ShoppingItem::class, ShoppingHistory::class],
    version = 1,
    exportSchema = true,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun shopping(): ShoppingDao

    companion object {
        fun create(context: Context): NoteDatabase =
            Room.databaseBuilder(context, NoteDatabase::class.java, "smartnotes.db").build()
    }
}
