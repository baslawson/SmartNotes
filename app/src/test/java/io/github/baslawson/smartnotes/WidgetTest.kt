package io.github.baslawson.smartnotes

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baslawson.smartnotes.data.ItemSort
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.widget.ShoppingContent
import io.github.baslawson.smartnotes.widget.TasksContent
import io.github.baslawson.smartnotes.widget.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WidgetTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private val todos = listOf(
        Todo(1, "Pay rent", dueDate = "2026-11-10"),
        Todo(2, "Call Sam", dueDate = "2026-11-12", dueTime = "15:00"),
        Todo(3, "Gym", dueDate = "2026-11-12", dueTime = "08:00"),
        Todo(4, "Dentist", dueDate = "2026-11-13"),
        Todo(5, "Read"),
        Todo(6, "Water plants", dueDate = "2026-11-12", done = true),
        Todo(7, "Old", dueDate = "2026-11-01", deletedAt = 5),
    )

    @Test fun todayAndOverdueTasksSoonestFirst() {
        val lines = WidgetData.todayTasks(todos, today)
        assertEquals(listOf(1L, 3L, 2L), lines.map { it.id })
        assertEquals(listOf(true, false, false), lines.map { it.overdue })
    }

    @Test fun todaysListOnlyAndWhatsLeftToBuy() {
        val lists = listOf(ShoppingList(1, "Shopping"), ShoppingList(2, "", date = "2026-11-12"), ShoppingList(3, "", date = "2026-11-13"))
        assertEquals(2L, WidgetData.todayList(lists, today)?.id)
        assertNull(WidgetData.todayList(lists.filter { it.id != 2L }, today))
        val items = listOf(
            ShoppingItem(1, 2, "bread", "bread", addedAt = 2), ShoppingItem(2, 2, "milk", "milk", quantity = "2x", addedAt = 1),
            ShoppingItem(3, 2, "eggs", "eggs", checked = true),
        )
        assertEquals(listOf(2L, 1L), WidgetData.toBuy(items, ItemSort.ADDED).map { it.id })
        assertEquals(listOf(1L, 2L), WidgetData.toBuy(items, ItemSort.NAME).map { it.id })
    }

    @Test fun pastDaysStillToBuyCarryOverOntoToday() {
        val lists = listOf(
            ShoppingList(1, "Shopping"), ShoppingList(2, "", date = "2026-11-10"), ShoppingList(3, "", date = "2026-11-12"),
            ShoppingList(4, "", date = "2026-11-13"), ShoppingList(5, "", date = "2026-11-11", deletedAt = 9),
        )
        val items = listOf(
            ShoppingItem(1, 3, "milk", "milk", addedAt = 1),
            ShoppingItem(2, 2, "Milk", "milk"), // today has it already
            ShoppingItem(3, 2, "tea", "tea"),
            ShoppingItem(4, 2, "bread", "bread", checked = true),
            ShoppingItem(5, 4, "cake", "cake"), // tomorrow's
            ShoppingItem(6, 1, "batteries", "batteries"), // Anytime
            ShoppingItem(7, 5, "jam", "jam"), // in the bin
        )
        assertEquals(listOf(2L, 3L), WidgetData.listsForToday(lists, today).map { it.id })
        assertEquals(listOf(1L, 3L), WidgetData.toBuyToday(lists, items, today, ItemSort.ADDED).map { it.id })
        // No list for today yet: what past days still have.
        assertEquals(listOf(2L, 3L), WidgetData.toBuyToday(lists.filter { it.id != 3L }, items, today, ItemSort.ADDED).map { it.id })
    }

    @Test fun onlyListsThatCarryOverAndNoNameTodayHasTicked() {
        val zone = java.time.ZoneOffset.UTC
        val made = today.atStartOfDay(zone).toInstant().toEpochMilli() // made today
        val lists = listOf(
            ShoppingList(2, "", date = "2026-11-10"),
            ShoppingList(3, "", date = "2026-11-12"),
            ShoppingList(6, "", date = "2026-11-09", createdAt = made), // made after its day: stays there
        )
        val items = listOf(
            ShoppingItem(1, 3, "Milk", "milk", checked = true), // bought today already
            ShoppingItem(2, 2, "milk", "milk"),
            ShoppingItem(3, 2, "tea", "tea"),
            ShoppingItem(4, 6, "jam", "jam"),
        )
        assertEquals(listOf(2L, 3L), WidgetData.listsForToday(lists, today, zone).map { it.id })
        assertEquals(listOf(3L), WidgetData.toBuyToday(lists, items, today, ItemSort.ADDED, zone).map { it.id })
    }

    @Test fun aWidgetTickTicksEveryCarriedCopyOfTheName() {
        val zone = java.time.ZoneOffset.UTC
        val lists = listOf(
            ShoppingList(2, "", date = "2026-11-10"), ShoppingList(4, "", date = "2026-11-11"),
            ShoppingList(3, "", date = "2026-11-12"), ShoppingList(1, "Shopping"),
        )
        val milk = ShoppingItem(1, 2, "milk", "milk")
        val items = listOf(
            milk, ShoppingItem(2, 4, "Milk", "milk"), ShoppingItem(3, 4, "tea", "tea"),
            ShoppingItem(4, 3, "milk", "milk"), ShoppingItem(5, 1, "milk", "milk"), ShoppingItem(6, 4, "milk", "milk", checked = true),
        )
        // Past copies only: not today's own, not Anytime's, not one already ticked.
        assertEquals(listOf(1L, 2L), io.github.baslawson.smartnotes.data.copiesToTick(lists, items, milk, today, zone).map { it.id })
        // An item on today's list is ticked alone.
        assertEquals(listOf(4L), io.github.baslawson.smartnotes.data.copiesToTick(lists, items, items[3], today, zone).map { it.id })
    }

    @Test fun aWidgetTickOnShoppingTicksTheCarriedCopiesUnderTheLock() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, io.github.baslawson.smartnotes.data.NoteDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.shopping()
        val settings = AppSettings(context.getSharedPreferences("tick-test", android.content.Context.MODE_PRIVATE))
        val repo = io.github.baslawson.smartnotes.data.ShoppingRepository(dao, io.github.baslawson.smartnotes.reminders.AlarmScheduler(context, db, settings))
        val now = LocalDate.now()
        dao.insertList(ShoppingList(id = 1, name = "Shopping")) // Anytime (id 1, as the app makes it)
        val a = dao.insertList(ShoppingList(name = "", date = now.minusDays(2).toString()))
        val b = dao.insertList(ShoppingList(name = "", date = now.minusDays(1).toString()))
        val m1 = dao.insert(ShoppingItem(listId = a, name = "milk", key = "milk"))
        val m2 = dao.insert(ShoppingItem(listId = b, name = "Milk", key = "milk"))
        val tea = dao.insert(ShoppingItem(listId = b, name = "tea", key = "tea"))
        // The row's item moved to another list meanwhile: nothing is ticked.
        repo.tickFromWidget(m1, b)
        assertEquals(listOf(false, false), listOf(dao.item(m1)!!.checked, dao.item(m2)!!.checked))
        // Ticked: both past copies of milk (shown as one line), not the tea.
        repo.tickFromWidget(m1, a)
        assertEquals(listOf(true, true, false), listOf(dao.item(m1)!!.checked, dao.item(m2)!!.checked, dao.item(tea)!!.checked))
        // A row from a list in the recycle bin: not ticked.
        dao.setDeleted(listOf(b), 5)
        repo.tickFromWidget(tea, b)
        assertEquals(false, dao.item(tea)!!.checked)
        db.close()
    }

    @Test fun byAisleTakesInTheCarriedItemsToo() {
        val lists = listOf(ShoppingList(2, "", date = "2026-11-10"), ShoppingList(3, "", date = "2026-11-12"))
        val items = listOf(ShoppingItem(1, 3, "milk", "milk"), ShoppingItem(2, 2, "apples", "apples"), ShoppingItem(3, 3, "card", "card"))
        assertEquals(listOf(2L, 1L, 3L), WidgetData.toBuyToday(lists, items, today, ItemSort.AISLE, java.time.ZoneOffset.UTC).map { it.id })
    }

    @Test fun aWidgetTickOnlyCountsForTheDateItsRowShowed() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<SmartNotesApp>()
        val now = LocalDate.now()
        val id = app.todos.add("Daily $now", now)
        val base = app.todos.get(id)!!
        app.todos.save(base, base.copy(repeat = "DAILY"))
        // Ticked from a row showing today: a daily task moves on to tomorrow.
        app.todos.setDone(id, true, onlyIfOpenOn = now.toString())
        assertEquals(now.plusDays(1).toString(), app.todos.get(id)!!.dueDate)
        // The same old row tapped again: nothing more happens (no skipping to the day after).
        app.todos.setDone(id, true, onlyIfOpenOn = now.toString())
        assertEquals(now.plusDays(1).toString(), app.todos.get(id)!!.dueDate)
        // A one-off task ticked twice from the same row is just done.
        val once = app.todos.add("Once $now", now)
        app.todos.setDone(once, true, onlyIfOpenOn = now.toString())
        app.todos.setDone(once, true, onlyIfOpenOn = now.toString())
        assertEquals(true, app.todos.get(once)!!.done)
    }

    @Test fun tasksWidgetShowsItsTasks() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 200.dp))
        provideComposable { TasksContent(WidgetData.todayTasks(todos, today), today, DateStyle.SHORT, TimeStyle(true)) }
        onNode(hasText("Today's tasks")).assertExists()
        onNode(hasText("Pay rent")).assertExists()
        onNode(hasText("Overdue · Tue 10 Nov")).assertExists()
        onNode(hasText("15:00")).assertExists()
        onNode(hasText("Dentist")).assertDoesNotExist()
    }

    @Test fun emptyWidgetsSaySo() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 200.dp))
        provideComposable { ShoppingContent(null, emptyList()) }
        onNode(hasText("Nothing to buy today")).assertExists()
    }

    @Test fun shoppingWidgetShowsItemsAndQuantities() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 200.dp))
        provideComposable { ShoppingContent(2, listOf(ShoppingItem(2, 2, "milk", "milk", quantity = "2x"))) }
        onNode(hasText("Today's shopping")).assertExists()
        onNode(hasText("milk")).assertExists()
        onNode(hasText("2x")).assertExists()
    }
}
