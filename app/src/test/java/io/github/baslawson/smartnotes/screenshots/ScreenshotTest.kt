package io.github.baslawson.smartnotes.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.baslawson.smartnotes.MainActivity
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.ThemeMode
import io.github.baslawson.smartnotes.data.Todo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The real app drawn on the JVM with sample data, saved to app/build/screenshots/ to look at (not compared: these are
 * for seeing a design change, not for failing builds). Run: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val out = System.getProperty("screenshots.dir") ?: "build/screenshots"

    private fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SmartNotesApp>()
        app.notes.saveFinal(0, "Holiday ideas", "Lisbon in May?\n- [ ] book flights\n- [x] ask Sam\n#travel")
        app.notes.saveFinal(0, "Recipes", "Lemon pasta: lemon, parmesan, butter #food")
        app.notes.saveFinal(0, "Garden", "Plant tulip bulbs before December. See [[Holiday ideas]]")
        app.todos.add("Pay the car tax")
        app.todos.add("Call the dentist")
        app.todos.add("Water the plants").let { app.todos.setDone(it, true) }
        app.todos.add("Book a table for Friday")
        val today = app.shopping.listForDate(LocalDate.now())
        listOf("2x milk", "bread", "eggs x12", "apples 1kg", "coffee").forEach { app.shopping.add(today, it) }
        app.shopping.add(1, "batteries")
        app.shopping.add(today, "butter").also { }
        // Planned ahead: a dot in the week strip, and a past item to suggest.
        app.shopping.add(app.shopping.listForDate(LocalDate.now().plusDays(2)), "bananas")
        Unit
    }

    private fun app() = ApplicationProvider.getApplicationContext<SmartNotesApp>()

    /** Waits for [text] to be on screen (data loads off the main thread), then saves the picture. */
    private fun shot(name: String, text: String? = null) {
        if (text != null) compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun tour(prefix: String) {
        compose.waitForIdle()
        shot("$prefix-1-notes", "Garden")
        compose.onNodeWithContentDescription("Show as grid").performClick()
        shot("$prefix-1-notes-grid", "Garden")
        compose.onNodeWithContentDescription("Show as list").performClick()
        compose.onNodeWithText("Holiday ideas").performClick()
        shot("$prefix-2-note", "Lisbon")
        compose.activity.onBackPressedDispatcher.let { compose.runOnUiThread { it.onBackPressed() } }
        compose.onNodeWithText("Tasks").performClick()
        shot("$prefix-3-tasks", "Call the dentist")
        compose.onNodeWithText("Shopping").performClick()
        shot("$prefix-4-shopping", "coffee")
    }

    @Test fun light() {
        seed()
        tour("light")
        // Typing pops up past items under the box.
        compose.onNodeWithText("Add for today, e.g. 2x milk").performTextInput("ba")
        shot("light-5-suggestions", "bananas")
        // Long-press to select, then Move to…
        compose.onNodeWithText("milk").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Move to another day").performClick()
        shot("light-6-move", "Another date")
        compose.onNodeWithText("Tomorrow").performClick()
        compose.waitForIdle()
        // A day with nothing planned yet.
        // A day in this week's strip with nothing on it (today and the next two days have lists): later in the week if
        // there is one, else earlier. (Not next week: a pager that animates never lets Robolectric's main thread idle.)
        val now = LocalDate.now()
        val week = io.github.baslawson.smartnotes.data.Week
        val free = week.days(week.start(now, week.firstDay())).filter { it.isBefore(now) || it.isAfter(now.plusDays(2)) }
        val ahead = free.firstOrNull { it.isAfter(now) } ?: free.last()
        compose.onNodeWithContentDescription(io.github.baslawson.smartnotes.DateStyle.SHORT.format(ahead), substring = true).performClick()
        shot("light-7-empty-day", "Nothing planned yet")
        // Tap the month for a month calendar.
        compose.onNodeWithText(io.github.baslawson.smartnotes.data.Week.monthLabel(
            io.github.baslawson.smartnotes.data.Week.start(ahead, io.github.baslawson.smartnotes.data.Week.firstDay()))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Previous month").fetchSemanticsNodes().isNotEmpty() }
        shot("light-7b-month")
        // A tap on the card (its title) keeps it open; the strip's month label is the clickable one.
        val title = java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy").format(java.time.YearMonth.from(ahead))
        compose.onNode(hasText(title) and !hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous month").assertExists()
        // (Not swiped here: under Robolectric a pager that moves inside a dialog never lets the main thread idle,
        // even in a bare Dialog { HorizontalPager }, so the swipe is only checked on a device.)
        compose.onAllNodesWithText("Go to today").onLast().performClick()
        compose.waitForIdle()
        // Tasks: 🔍 in the add box searches all tasks, done ones too.
        compose.onNodeWithText("Tasks").performClick()
        compose.onNodeWithContentDescription("Search everything").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("the")
        shot("light-8-task-search", "Water the plants")
        // A date typed at the end: the chip shows the reminder it will set.
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Call Sam tomorrow 3pm")
        shot("light-9-task-date", "⏰ Tomorrow")
    }

    /** Tasks by day (Overdue, Today, …) and a task's steps. */
    @Test fun tasksByDayAndSteps() {
        val app = app()
        val today = LocalDate.now()
        runBlocking {
            app.todos.add("Renew the passport", today.minusDays(2))
            app.todos.add("Plan the party", today, java.time.LocalTime.of(18, 0), "For Sam's birthday\n- [x] Book a room\n- [ ] Send invites\n- [ ] Order the cake")
            app.todos.add("Call the bank", today.plusDays(1))
            app.todos.add("Dentist", today.plusDays(20))
            app.todos.add("Read a book")
            app.settings.setTaskSort(io.github.baslawson.smartnotes.data.TaskSort.DAY)
        }
        compose.onNodeWithText("Tasks").performClick()
        shot("light-10-tasks-by-day", "Overdue · 1")
        compose.onNodeWithText("☑ 1 of 3", substring = true).assertExists()
        compose.onNodeWithText("Plan the party").performClick()
        shot("light-11-task-steps", "Steps · 1 of 3")
        // Tick a step with its box: the line in the details changes, and the count with it.
        compose.onNodeWithText("Send invites").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.isToggleable())[2].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Steps · 2 of 3").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { runBlocking { app.todos.observeAll().first().first { it.title == "Plan the party" }.details.contains("- [x] Send invites") } }
        // A step added in the field goes at the end of the details.
        compose.onNodeWithText("Add a step").performTextInput("Buy balloons")
        compose.onNodeWithContentDescription("Add the step").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { runBlocking { app.todos.observeAll().first().first { it.title == "Plan the party" }.details.endsWith("\n- [ ] Buy balloons") } }
        compose.onNodeWithText("Steps · 2 of 4").assertExists()
    }

    /** Every section's 🔍 finds notes, tasks and shopping items, each under its own heading. */
    @Test fun searchFindsEverywhere() {
        val app = app()
        runBlocking {
            app.notes.saveFinal(0, "Recipes", "Lemon pasta: lemon, parmesan, butter #food")
            app.todos.add("Buy a lemon squeezer")
            app.shopping.add(app.shopping.listForDate(LocalDate.now()), "3 lemons")
        }
        // From Shopping: shopping first, then notes and tasks.
        compose.onNodeWithText("Shopping").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Search everything").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Search everything").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        shot("light-12-search-from-shopping", "In Tasks (1)")
        compose.onNodeWithText("In Shopping (1)").assertExists()
        compose.onNodeWithText("In Notes (1)").assertExists()
        // Where it is: the list and the aisle.
        compose.onNodeWithText("Shopping · Today · Fruit & veg").assertExists()
        // A note found here opens.
        compose.onNodeWithText("Recipes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Lemon pasta", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.activity.onBackPressedDispatcher.let { compose.runOnUiThread { it.onBackPressed() } }
        compose.waitForIdle()
        // From Notes: the notes, then tasks and shopping below them.
        compose.onNodeWithText("Notes").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        shot("light-13-search-from-notes", "In Shopping (1)")
        compose.onNodeWithText("In Notes (1)").assertExists()
        compose.onNodeWithText("Buy a lemon squeezer").assertExists()
    }

    /** "By aisle" puts the items under their aisles; staples are starred and "Add staples" puts back the missing ones. */
    @Test fun shoppingByAisleAndStaples() {
        val app = app()
        runBlocking {
            val today = app.shopping.listForDate(LocalDate.now())
            for (item in listOf("2x milk", "apples", "cheddar", "birthday card", "bananas")) app.shopping.add(today, item)
            app.shopping.setAisle(listOf("cheddar"), io.github.baslawson.smartnotes.parse.Aisle.SNACKS)
            val items = app.shopping.observeItems(today).first()
            app.shopping.setStaple(items.filter { it.key in setOf("milk", "bananas") }, staple = true)
            // A staple not on today's list.
            app.shopping.addOn(LocalDate.now().plusDays(1), "coffee")
            app.shopping.setStaple(app.shopping.observeItems(app.shopping.listForDate(LocalDate.now().plusDays(1))).first(), staple = true)
            app.settings.setItemSort(io.github.baslawson.smartnotes.data.ItemSort.AISLE)
        }
        compose.onNodeWithText("Shopping").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Got it").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Got it").performClick()
        // (No scrolling: Robolectric doesn't settle while this list scrolls; everything fits on screen.)
        shot("light-14-shopping-by-aisle", "Fruit & veg (2)")
        for (heading in listOf("Dairy & eggs (1)", "Snacks & sweets (1)", "Other (1)")) compose.onNodeWithText(heading).assertExists()
        compose.onAllNodesWithContentDescription("Staple").assertCountEquals(2) // milk and bananas
        compose.onNodeWithText("Add staples (1)").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Breakfast (1)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("coffee").assertExists()
        compose.onNodeWithText("Add staples", substring = true).assertDoesNotExist()
    }

    @Test fun dark() {
        app().settings.setTheme(ThemeMode.DARK)
        seed()
        tour("dark")
    }

    @Test fun playful() {
        // Empty: nothing written yet.
        shot("fun-1-empty-notes", "No notes yet")
        runBlocking {
            val app = app()
            val today = app.shopping.listForDate(LocalDate.now())
            listOf("milk", "bread", "eggs").forEach { app.shopping.add(today, it) }
            app.todos.add("Call the dentist")
        }
        compose.onNodeWithText("Shopping").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("eggs").fetchSemanticsNodes().isNotEmpty() }
        // Two ticked: the ring at 2 of 3.
        listOf("milk", "bread").forEach { name ->
            compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasText(name)))[0].performClick()
        }
        shot("fun-2-progress", "2 of 3")
        // The last one: confetti, caught part way.
        compose.mainClock.autoAdvance = false
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasText("eggs")))[0].performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("$out/fun-3-confetti.png")
        compose.mainClock.autoAdvance = true
    }
}
