package io.github.baslawson.smartnotes.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
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
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("the")
        shot("light-8-task-search", "Water the plants")
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
