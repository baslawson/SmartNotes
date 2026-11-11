package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test fun tasksWithStateDueDateAndTags() {
        val body = "Intro\n- [ ] buy milk @2026-11-20 #shopping\n  * [x] call Ann\n+ [ ]\nnot - [ ] a task\n-[ ] neither"
        val tasks = NoteParser.tasks(body)
        assertEquals(3, tasks.size)
        assertEquals(NoteTask(1, "buy milk #shopping", false, LocalDateTime.of(2026, 11, 20, 9, 0), setOf("shopping")), tasks[0])
        assertEquals(NoteTask(2, "call Ann", true, null, emptySet()), tasks[1])
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

    @Test fun remindersWithAndWithoutTime() {
        val r = NoteParser.reminders("Dentist @2026-11-20 14:30\n- [x] pay @2026-11-21\nboth @2026-12-01 8:05 and @2026-12-02")
        assertEquals(
            listOf(
                Reminder(0, LocalDateTime.of(2026, 11, 20, 14, 30), "Dentist", false),
                Reminder(1, LocalDateTime.of(2026, 11, 21, 9, 0), "pay", true),
                Reminder(2, LocalDateTime.of(2026, 12, 1, 8, 5), "both and", false),
                Reminder(2, LocalDateTime.of(2026, 12, 2, 9, 0), "both and", false),
            ),
            r,
        )
    }

    @Test fun notReminders() {
        val text = "mail bas@2026-11-20.nl, @2026-02-30, @2026-11-20x, @2026-11-20-01"
        assertEquals(emptyList<Reminder>(), NoteParser.reminders(text))
    }

    @Test fun invalidTimeFallsBackToDateOnlyWhenSeparated() {
        // "25:00" isn't a time, so the date counts on its own (at 09:00) and the rest stays text.
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), NoteParser.reminders("x @2026-11-20 25:00").single().at)
    }

    @Test fun timeFollowedByARangeOrSuffixKeepsItsTime() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 0), NoteParser.reminders("@2026-11-20 14:00-15:00").single().at)
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 30), NoteParser.reminders("@2026-11-20 14:30h").single().at)
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 0), NoteParser.reminders("@2026-11-20 14:00–15:00").single().at)
        // More digits after the minutes: not a time, so the date alone (09:00).
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), NoteParser.reminders("@2026-11-20 14:005").single().at)
    }

    @Test fun amPmTimes() {
        fun at(text: String) = NoteParser.reminders(text).single().at.toLocalTime().toString()
        assertEquals("14:00", at("@2026-11-20 2:00pm"))
        assertEquals("14:00", at("@2026-11-20 2:00 PM"))
        assertEquals("00:30", at("@2026-11-20 12:30am"))
        assertEquals("12:15", at("@2026-11-20 12:15p.m."))
        assertEquals("09:05", at("@2026-11-20 9:05 a.m. dentist"))
        assertEquals("14:00", at("@2026-11-20 14:00pm"))
        assertEquals("09:30", at("call @2026-11-20 9:30 about the plan"))
        // Words starting with am/pm are not am/pm, and stay whole in the reminder text.
        assertEquals("11:00", at("@2026-11-20 11:00 pmd bag out"))
        val r = NoteParser.reminders("- [ ] flight @2026-11-20 9:00 Amsterdam").single()
        assertEquals("09:00", r.at.toLocalTime().toString())
        assertEquals("flight Amsterdam", r.text)
        assertEquals("party Amy's house", NoteParser.reminders("party @2026-11-20 8:00 Amy's house").single().text)
    }

    @Test fun windowsLineEndsAreNormalised() {
        val body = NoteParser.normalizeLineEnds("- [ ] milk\r\n- [ ] eggs\rend")
        assertEquals("- [ ] milk\n- [ ] eggs\nend", body)
        assertEquals(2, NoteParser.tasks(body).size)
    }

    @Test fun relativeDatesBecomeRealDates() {
        val wednesday = LocalDate.of(2026, 11, 11)
        assertEquals(
            "call @2026-11-11, @2026-11-12 9:00, (@2026-11-13) @2026-11-16 @2026-11-11 @2026-11-15",
            NoteParser.normalizeDates("call @today, @Tomorrow 9:00, (@fri) @monday @wed @sun", wednesday),
        )
    }

    @Test fun relativeWordsInsideOtherTextAreLeftAlone() {
        val d = LocalDate.of(2026, 11, 11)
        val text = "@todays a@tomorrow @friyay @mon-fri"
        assertEquals(text, NoteParser.normalizeDates(text, d))
    }

    @Test fun displayTitle() {
        assertEquals("Title", NoteParser.displayTitle("  Title ", "body"))
        assertEquals("First line", NoteParser.displayTitle("", "\n\n## First line\nmore"))
        assertEquals("Untitled", NoteParser.displayTitle(" ", " \n "))
        assertTrue(NoteParser.displayTitle("", "x".repeat(100)).length == 60)
    }
}
