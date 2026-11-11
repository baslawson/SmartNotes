package io.github.baslawson.smartnotes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.parse.Aisle
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Aisles chosen for items and staples, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AislesStaplesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.shopping()
    private val repo = ShoppingRepository(
        dao, AlarmScheduler(context, db, AppSettings(context.getSharedPreferences("aisles-test", Context.MODE_PRIVATE))),
    )
    private val today = LocalDate.now()

    @After fun close() = db.close()

    private suspend fun names(date: LocalDate?): Map<String, Boolean> {
        val id = if (date == null) 1L else dao.allLists().firstOrNull { it.date == date.toString() }?.id ?: return emptyMap()
        return dao.itemsOf(id).associate { it.name to it.checked }
    }

    @Test fun anAisleIsChosenByNameAndCanGoBackToTheGuess() = runBlocking {
        repo.setAisle(listOf("milk", "tea"), Aisle.SNACKS)
        assertEquals(mapOf("milk" to Aisle.SNACKS, "tea" to Aisle.SNACKS), repo.observeAisles().first())
        repo.setAisle(listOf("milk"), Aisle.DRINKS)
        repo.setAisle(listOf("tea"), null)
        assertEquals(mapOf("milk" to Aisle.DRINKS), repo.observeAisles().first())
    }

    @Test fun addStaplesPutsWhatIsMissingOnTheDay() = runBlocking {
        dao.insertList(ShoppingList(id = 1, name = "Shopping"))
        repo.addOn(today, "2x milk")
        repo.addOn(today, "bread")
        repo.addOn(today, "eggs")
        val items = dao.itemsOf(dao.allLists().first { it.date == today.toString() }.id)
        repo.setStaple(items.filter { it.name != "eggs" }, staple = true)
        dao.insertStaple("tea", "Tea")
        dao.insertStaple("batteries", "batteries")
        assertEquals(listOf("milk", "bread", "tea", "batteries").sorted(), repo.observeStaples().first().map { it.key }.sorted())
        // Bread bought already (ticked): it goes back on, with the staple's quantity (none) not the list's; milk is
        // still to buy; batteries are on Anytime (shown on today too).
        repo.rename(items.first { it.name == "bread" }, "5x bread")
        repo.setChecked(dao.item(items.first { it.name == "bread" }.id)!!, true)
        repo.addOn(null, "batteries")
        assertEquals(2, repo.addStaples(today))
        assertEquals("", dao.itemsOf(dao.allLists().first { it.date == today.toString() }.id).first { it.name == "bread" }.quantity)
        assertEquals(mapOf("milk" to false, "bread" to false, "eggs" to false, "Tea" to false), names(today))
        assertEquals("2x", dao.itemsOf(dao.allLists().first { it.date == today.toString() }.id).first { it.name == "milk" }.quantity)
        // Nothing missing now.
        assertEquals(0, repo.addStaples(today))
        // A day without a list gets one with its staples.
        val later = today.plusDays(3)
        assertEquals(4, repo.addStaples(later))
        assertEquals(setOf("milk", "bread", "Tea", "batteries"), names(later).keys)
        // Not staples any more.
        repo.setStaple(dao.itemsOf(dao.allLists().first { it.date == later.toString() }.id).filter { it.key in setOf("tea", "batteries") }, staple = false)
        assertEquals(listOf("bread", "milk"), repo.observeStaples().first().map { it.key }.sorted())
    }

    private suspend fun ShoppingDao.insertStaple(key: String, name: String) = putStaples(listOf(Staple(key, name)))
}
