package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParserTest {
    @Test fun tagsAreFoundLowercasedAndSorted() {
        assertEquals(setOf("groceries", "home/kitchen", "work"), NoteParser.tags("Buy #Groceries for #work\n#home/kitchen #work"))
    }

    @Test fun headingsAnchorsNumbersAndEntitiesAreNotTags() {
        assertEquals(emptySet<String>(), NoteParser.tags("# Heading\nsee page#anchor and https://x.org/#frag, #123, &#39;"))
    }

    @Test fun tagEndingInPunctuation() {
        assertEquals(setOf("idea"), NoteParser.tags("Great #idea-. Another (#idea)"))
    }

    @Test fun linksWithAliasesAndNoDuplicates() {
        assertEquals(listOf("Trip plan", "budget"), NoteParser.links("See [[Trip plan]] and [[budget|money]], [[trip plan]] [[ ]]"))
    }

    @Test fun tasksWithStateAndTags() {
        val body = "Intro\n- [ ] buy  milk #shopping\n  * [x] call Ann\n+ [ ]\nnot - [ ] a task\n-[ ] neither"
        val tasks = NoteParser.tasks(body)
        assertEquals(3, tasks.size)
        assertEquals(NoteTask(1, "buy milk #shopping", false, setOf("shopping")), tasks[0])
        assertEquals(NoteTask(2, "call Ann", true, emptySet()), tasks[1])
        assertEquals("", tasks[2].text)
    }

    @Test fun toggleTaskKeepsTheRestOfTheNote() {
        val body = "a\n  - [ ] milk\nb"
        val ticked = NoteParser.toggleTask(body, 1)
        assertEquals("a\n  - [x] milk\nb", ticked)
        assertEquals(body, NoteParser.toggleTask(ticked!!, 1))
        assertNull(NoteParser.toggleTask(body, 0))
        assertNull(NoteParser.toggleTask(body, 7))
    }

    @Test fun windowsLineEndsAreNormalised() {
        val body = NoteParser.normalizeLineEnds("- [ ] milk\r\n- [ ] eggs\rend")
        assertEquals("- [ ] milk\n- [ ] eggs\nend", body)
        assertEquals(2, NoteParser.tasks(body).size)
    }

    @Test fun displayTitle() {
        assertEquals("Title", NoteParser.displayTitle("  Title ", "body"))
        assertEquals("First line", NoteParser.displayTitle("", "\n\n## First line\nmore"))
        assertEquals("Untitled", NoteParser.displayTitle(" ", " \n "))
        assertTrue(NoteParser.displayTitle("", "x".repeat(100)).length == 60)
    }

    @Test fun aTemplateIsANoteTaggedTemplate() {
        assertEquals(true, NoteParser.isTemplate("Weekly plan", "Goals:\n#template"))
        assertEquals(true, NoteParser.isTemplate("Weekly plan #Template", ""))
        assertEquals(false, NoteParser.isTemplate("Weekly plan", "#templates and #temp"))
        assertEquals(false, NoteParser.isTemplate("Weekly plan", "a#template"))
    }

    @Test fun aNoteFromATemplateLeavesOutTheTag() {
        // A line with only the tag goes; elsewhere the gap closes; other tags stay.
        assertEquals(
            "Weekly plan" to "Goals:\n- [ ] one\nNotes #work here\n",
            NoteParser.fromTemplate("Weekly plan #template", "Goals:\n- [ ] one\n#Template\nNotes #template #work here\n"),
        )
        // Blank lines and spacing written on purpose stay.
        assertEquals("" to "a\n\nName:    value", NoteParser.fromTemplate("", "a\n\nName:    value #template"))
        // At the start of a line: no stray space.
        assertEquals("" to "Weekly plan", NoteParser.fromTemplate("", "#template Weekly plan"))
        // Twice in a row: both go (the copy isn't a template).
        val (t, b) = NoteParser.fromTemplate("", "Plan #template#template")
        assertEquals("Plan", b)
        assertEquals(false, NoteParser.isTemplate(t, b))
        // The title as written, but for the tag and its space.
        assertEquals("  Plan  " to "", NoteParser.fromTemplate("  Plan  ", "#template"))
        // Not a tag: left alone.
        assertEquals("" to "word#template", NoteParser.fromTemplate("", "word#template"))
    }
}
