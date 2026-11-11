package io.github.baslawson.smartnotes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Updating the app keeps what is in the database: a database made as an older version (its tables from the exported
 * schema in app/schemas) opened by this one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A database file as [version] made it (no data). */
    private fun createAs(version: Int, name: String): File {
        val schema = JSONObject(File("schemas/${NoteDatabase::class.java.name}/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { ix ->
                    for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                }
                e.optJSONArray("contentSyncTriggers")?.let { t -> for (j in 0 until t.length()) db.execSQL(t.getString(j)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
        }
        return file
    }

    @Test fun version4To5KeepsItemsAndAddsAislesAndStaples() = runBlocking {
        val file = createAs(4, "migration-test.db")
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO shopping_lists (id, name, sound, createdAt, position) VALUES (1, 'Shopping', 'DEFAULT', 0, 0)")
            db.execSQL("INSERT INTO shopping_items (id, listId, name, `key`, quantity, checked, addedAt, checkedAt, position) VALUES (7, 1, 'Milk', 'milk', '2x', 0, 1, 0, 0)")
        }
        val room = Room.databaseBuilder(context, NoteDatabase::class.java, file.name).allowMainThreadQueries().build()
        try {
            val dao = room.shopping()
            assertEquals("2x", dao.item(7)!!.quantity)
            dao.setAisles(listOf(ItemAisle("milk", "DAIRY")))
            dao.putStaples(listOf(Staple("milk", "Milk", "2x", 3)))
            assertEquals(listOf(ItemAisle("milk", "DAIRY")), dao.allAisles())
            assertEquals(listOf("milk"), dao.allStaples().map { it.key })
            assertEquals(5, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }
}
