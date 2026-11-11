package io.github.baslawson.smartnotes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Moving items between days and undoing it, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MoveUndoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NoteDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.shopping()

    @After fun close() = db.close()

    private suspend fun list(id: Long, date: String?) = dao.insertList(ShoppingList(id = id, name = date ?: "Shopping", date = date))
    private suspend fun add(list: Long, name: String, quantity: String = "") = dao.addItem(list, name, quantity, 10)
    private suspend fun on(list: Long) = dao.itemsOf(list).associate { it.name to it.quantity }

    @Test fun undoPutsMovedAndMergedItemsBack() = runBlocking {
        list(1, null); list(2, "2026-11-11"); list(3, "2026-11-14")
        add(2, "milk", "2x"); add(2, "bread"); add(3, "milk", "1L")
        val ids = dao.itemsOf(2).map { it.id }
        val undo = dao.moveItems(ids, 3, 100)!!
        assertEquals(mapOf<String, String>(), on(2))
        assertEquals(mapOf("milk" to "2x", "bread" to ""), on(3))
        dao.undoMove(undo)
        assertEquals(mapOf("milk" to "2x", "bread" to ""), on(2))
        assertEquals(mapOf("milk" to "1L"), on(3))
    }

    @Test fun undoKeepsWhatWasChangedSinceTheMove() = runBlocking {
        list(1, null); list(2, "2026-11-11"); list(3, "2026-11-14")
        add(2, "milk", "2x"); add(2, "bread"); add(3, "milk", "1L")
        val undo = dao.moveItems(dao.itemsOf(2).map { it.id }, 3, 100)!!
        // Since the move: the merged milk renamed, the bread ticked.
        val milk = dao.findByKey(3, "milk")!!
        dao.renameItem(milk.id, "milk", "oat milk", "2x", 200)
        val bread = dao.findByKey(3, "bread")!!
        dao.setChecked(bread.id, true, 300)
        dao.undoMove(undo)
        // The milk that went into it comes back to its day; the renamed one and the ticked bread stay as they are.
        assertEquals(mapOf("milk" to "2x"), on(2))
        assertEquals(mapOf("oat milk" to "2x", "bread" to ""), on(3))
        assertEquals(true, dao.findByKey(3, "bread")!!.checked)
    }

    @Test fun aListMadeForTheMoveGoesAgainOnUndoWhenEmpty() = runBlocking {
        list(1, null); list(2, "2026-11-11")
        add(2, "milk")
        val made = dao.insertList(ShoppingList(name = "2026-11-20", date = "2026-11-20"))
        val undo = dao.moveItems(dao.itemsOf(2).map { it.id }, made, 100)!!.copy(madeList = made)
        dao.undoMove(undo)
        assertNull(dao.list(made))
        assertEquals(mapOf("milk" to ""), on(2))
    }
}
