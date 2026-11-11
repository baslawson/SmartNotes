package io.github.baslawson.smartnotes

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Quicker input on the emulator: a date typed after a task, swiping a task right (tick) and left (bin), – / + on a
 * shopping item's quantity (0.5.0); a task's steps and the By day order (0.6.0). Names carry a run id, since the
 * emulator's data stays between runs.
 */
@RunWith(AndroidJUnit4::class)
class InputAndSwipeTest {
    @get:Rule(order = 0) val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val run = System.currentTimeMillis().toString().takeLast(6)
    private val app get() = compose.activity.application as SmartNotesApp

    /** A bottom-bar tab (the screen's title can have the same word). */
    private fun tab(name: String) {
        // The app reopens where it was left (a task, say): back out to the tabs first.
        repeat(3) {
            if (compose.onAllNodes(hasText(name) and hasClickAction()).fetchSemanticsNodes().isEmpty()) {
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun until(what: () -> Boolean) = compose.waitUntil(10_000) { what() }
    private fun task(title: String) = runBlocking { app.todos.observeAll().first().firstOrNull { it.title == title } }

    private fun addTask(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.waitForIdle()
    }

    @Test fun aDateTypedAfterATaskIsItsReminder() {
        // 24-hour here (the emulator's own clock is 12-hour; see twelveHourClockShowsAndPicksAmPm).
        compose.runOnUiThread { app.settings.setTimeFormat(TimeFormat.H24) }
        tab("Tasks")
        addTask("Call Sam $run tomorrow 3pm")
        // The chip shows what it will set.
        compose.onNodeWithText("⏰ Tomorrow 15:00", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).performImeAction()
        until { task("Call Sam $run") != null }
        val t = task("Call Sam $run")!!
        compose.runOnUiThread { app.settings.setTimeFormat(TimeFormat.PHONE) }
        assertEquals(LocalDate.now().plusDays(1).toString(), t.dueDate)
        assertEquals("15:00", t.dueTime)
        assertTrue(t.remind)
    }

    @Test fun crossingOutTheChipKeepsTheWords() {
        tab("Tasks")
        addTask("Water plants $run fri")
        compose.onNodeWithContentDescription("Keep as text", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        until { task("Water plants $run fri") != null }
        assertNull(task("Water plants $run fri")!!.dueDate)
    }

    @Test fun swipingATaskTicksOrBinsIt() {
        tab("Tasks")
        addTask("Swipe right $run")
        compose.onNode(hasSetTextAction()).performImeAction()
        addTask("Swipe left $run")
        compose.onNode(hasSetTextAction()).performImeAction()
        until { task("Swipe right $run") != null && task("Swipe left $run") != null }
        compose.onNodeWithText("Swipe right $run").performTouchInput { swipeRight() }
        until { task("Swipe right $run")?.done == true }
        compose.onNodeWithText("Swipe left $run").performTouchInput { swipeLeft() }
        until { task("Swipe left $run") == null }
        // In the bin, with Undo offered.
        assertTrue(runBlocking { app.todos.observeDeleted().first().any { it.title == "Swipe left $run" } })
        // Undo brings it back, and it stays back (the row's swipe state, restored with it, mustn't bin it again).
        until { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Undo")[0].performClick()
        until { task("Swipe left $run") != null }
        compose.onNodeWithText("Swipe left $run").assertExists()
        Thread.sleep(1500)
        compose.waitForIdle()
        assertTrue(task("Swipe left $run") != null)
        compose.onNodeWithText("Swipe left $run").assertExists()
        // And a second swipe left still bins it.
        compose.onNodeWithText("Swipe left $run").performTouchInput { swipeLeft() }
        until { task("Swipe left $run") == null }
    }

    @Test fun plusAndMinusChangeAQuantity() {
        tab("Shopping")
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Go to today").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Go to today")[0].performClick()
            compose.waitForIdle()
        }
        val name = "oats$run"
        compose.onNode(hasSetTextAction()).performTextInput("2x $name")
        compose.onNode(hasSetTextAction()).performImeAction()
        val list = runBlocking { app.shopping.listForDate(LocalDate.now()) }
        fun quantity() = runBlocking { app.shopping.observeItems(list).first().firstOrNull { it.name == name }?.quantity }
        until { quantity() == "2x" }
        compose.onNodeWithText("2x").performClick()
        compose.onNodeWithContentDescription("One more $name").performClick()
        until { quantity() == "3x" }
        compose.onNodeWithContentDescription("One less $name").performClick()
        compose.onNodeWithContentDescription("One less $name").performClick()
        until { quantity() == "1x" }
        compose.onNodeWithContentDescription("One less $name").performClick()
        until { quantity() == "" }
    }

    @Test fun twelveHourClockShowsAndPicksAmPm() {
        compose.runOnUiThread { app.settings.setTimeFormat(TimeFormat.H12) }
        try {
            tab("Tasks")
            addTask("Clock $run tomorrow 15:30")
            compose.onNodeWithText("⏰ Tomorrow 3:30 PM", substring = true).assertExists()
            compose.onNode(hasSetTextAction()).performImeAction()
            until { task("Clock $run") != null }
            // The row, then the editor's time button and its picker, in 12-hour.
            compose.onNodeWithText("Clock $run").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("3:30 PM").performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText("PM")[0].assertExists()
            compose.onAllNodesWithText("AM")[0].assertExists()
        } finally {
            compose.runOnUiThread { app.settings.setTimeFormat(TimeFormat.PHONE) }
        }
    }

    @Test fun tickingAStepCountsIt() {
        // Newest first, so this run's task is at the top (put back afterwards).
        app.settings.setTaskSort(io.github.baslawson.smartnotes.data.TaskSort.NEWEST)
        try { stepsCounted() } finally { app.settings.setTaskSort(io.github.baslawson.smartnotes.data.TaskSort.PRIORITY) }
    }

    private fun stepsCounted() {
        runBlocking { app.todos.add("Steps $run", details = "Pack\n- [ ] Passport $run\n- [ ] Tickets $run") }
        tab("Tasks")
        until { compose.onAllNodesWithText("☑ 0 of 2", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Steps $run").performClick()
        until { compose.onAllNodesWithText("Steps · 0 of 2").fetchSemanticsNodes().isNotEmpty() }
        // Each step's box is named after it (for TalkBack).
        compose.onNode(androidx.compose.ui.test.isToggleable() and androidx.compose.ui.test.hasContentDescription("Passport $run")).performClick()
        until { task("Steps $run")?.details == "Pack\n- [x] Passport $run\n- [ ] Tickets $run" }
        compose.onNodeWithText("Steps · 1 of 2").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        until { compose.onAllNodesWithText("☑ 1 of 2", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(false, task("Steps $run")!!.done)
    }

    @Test fun byDayGroupsTasks() {
        app.settings.setTaskSort(io.github.baslawson.smartnotes.data.TaskSort.DAY)
        try {
            val id = runBlocking { app.todos.add("Overdue $run", LocalDate.now().minusDays(3)) }
            tab("Tasks")
            until { compose.onAllNodesWithText("Overdue · ", substring = true).fetchSemanticsNodes().isNotEmpty() }
            // (Earlier runs leave tasks behind: scroll to this one.)
            compose.onNode(androidx.compose.ui.test.hasScrollToKeyAction()).performScrollToKey("t:$id")
            compose.onNodeWithText("Overdue $run").assertExists()
            // No drag handles in this order.
            assertTrue(compose.onAllNodesWithContentDescription("Drag", substring = true).fetchSemanticsNodes().isEmpty())
        } finally {
            app.settings.setTaskSort(io.github.baslawson.smartnotes.data.TaskSort.PRIORITY)
        }
    }

    @Test fun searchFindsEveryNoteTaskAndItem() {
        val word = "zq$run"
        runBlocking {
            app.notes.saveFinal(0, "Note $word", "body")
            app.todos.add("Task $word")
            app.shopping.add(app.shopping.listForDate(LocalDate.now()), "item $word")
        }
        tab("Tasks")
        compose.onNodeWithContentDescription("Search everything").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(word)
        until { compose.onAllNodesWithText("In Shopping (1)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("In Tasks (1)").assertExists()
        compose.onNodeWithText("In Notes (1)").assertExists()
        // The item says where it is, and a tap opens that list in Shopping.
        compose.onNodeWithText("Shopping · Today").assertExists()
        compose.onNodeWithText("item $word").performClick()
        until { compose.onAllNodesWithText("item $word").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("In Shopping (1)").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("Shopping") and hasClickAction()).assertExists()
    }

    @Test fun longPressSetsAnAisleAndMakesAStaple() {
        tab("Shopping")
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Go to today").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Go to today")[0].performClick()
            compose.waitForIdle()
        }
        val name = "zorbleberry$run"
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNode(hasSetTextAction()).performImeAction()
        val list = runBlocking { app.shopping.listForDate(LocalDate.now()) }
        val key = name.lowercase()
        until { runBlocking { app.shopping.observeItems(list).first().any { it.name == name } } }
        // Long-press → ⋮ → Aisle… → Frozen.
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Aisle…").performClick()
        compose.onNodeWithText("Frozen").performClick()
        until { runBlocking { app.shopping.observeAisles().first()[key] } == io.github.baslawson.smartnotes.parse.Aisle.FROZEN }
        // Long-press → ⋮ → Staple: starred, and a staple.
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Staple").performClick()
        until { runBlocking { app.shopping.observeStaples().first().any { it.key == key } } }
        // Tidy up: not a staple any more (the emulator's data stays between runs), aisle back to the guess.
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Not a staple").performClick()
        until { runBlocking { app.shopping.observeStaples().first().none { it.key == key } } }
        runBlocking { app.shopping.setAisle(listOf(key), null) }
    }
}
