package io.github.baslawson.smartnotes

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Shopping's month calendar on a real (emulated) device: swiping changes the month. */
@RunWith(AndroidJUnit4::class)
class CalendarSwipeTest {
    // The notification permission is asked on first start (Android 13+); granted here so no system dialog covers the app.
    @get:Rule(order = 0) val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun label(m: YearMonth) = DateTimeFormatter.ofPattern("LLLL yyyy").format(m)

    @Test fun swipingChangesTheMonth() {
        compose.onNode(hasText("Shopping") and hasClickAction()).performClick()
        compose.waitForIdle()
        // Show today (the app reopens on the day last shown), then the month over the strip opens the calendar on it.
        if (compose.onAllNodesWithText("Go to today").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Go to today")[0].performClick()
            compose.waitForIdle()
        }
        val now = YearMonth.now()
        compose.onNode(hasClickAction() and hasText(now.year.toString(), substring = true)).performClick()
        compose.onNodeWithContentDescription("Previous month").assertExists()
        compose.onNodeWithTag("month-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(label(now.plusMonths(1))).assertExists()
        compose.onNodeWithTag("month-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("month-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(label(now.minusMonths(1))).assertExists()
        // › twice quickly: two months on.
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(now.plusMonths(1))).assertExists()
    }
}
