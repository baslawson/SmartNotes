package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStepsTest {
    private val details = "Bring the forms\n- [x] Book a room\n- [ ] Send invites\n* [X] Order cake\n+ [ ] Buy drinks\n[ ] not a step"

    @Test fun countsDoneSteps() {
        assertEquals("2 of 4", TaskSteps.progress(details))
        assertNull(TaskSteps.progress("Just words\n- a list item"))
        assertEquals("0 of 1", TaskSteps.progress("- [ ] one\r\n"))
    }

    @Test fun tickingChangesOnlyThatLine() {
        val ticked = TaskSteps.toggle(details, 2)
        assertEquals("- [x] Send invites", ticked.lines()[2])
        assertEquals(details.lines().filterIndexed { i, _ -> i != 2 }, ticked.lines().filterIndexed { i, _ -> i != 2 })
        assertEquals(details, TaskSteps.toggle(ticked, 2))
        // A line that's no longer a step: unchanged.
        assertEquals(details, TaskSteps.toggle(details, 0))
        assertEquals(details, TaskSteps.toggle(details, 99))
    }

    @Test fun addingGoesAtTheEnd() {
        assertEquals("- [ ] Call Sam", TaskSteps.add("", "  Call   Sam "))
        assertEquals("Notes\n- [ ] Call Sam", TaskSteps.add("Notes\n\n", "Call Sam"))
        assertEquals("Notes", TaskSteps.add("Notes", "   "))
    }

    @Test fun aboutSkipsSteps() {
        assertEquals("Bring the forms", TaskSteps.about(details))
        assertEquals("After", TaskSteps.about("- [ ] one\n\n  After  "))
        assertNull(TaskSteps.about("- [ ] one\n- [x] two"))
        assertNull(TaskSteps.about(""))
    }
}
