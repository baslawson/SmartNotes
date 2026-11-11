package io.github.baslawson.smartnotes

import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.parse.Search
import io.github.baslawson.smartnotes.ui.searchTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSearchTest {
    private val todos = listOf(
        Todo(id = 1, title = "Call the dentist", createdAt = 1),
        Todo(id = 2, title = "Pay the car tax", details = "Online, before the 30th", createdAt = 2),
        Todo(id = 3, title = "Dentist bill", done = true, doneAt = 10, createdAt = 3),
        Todo(id = 4, title = "Old dentist note", done = true, doneAt = 20, createdAt = 4),
        Todo(id = 5, title = "Water the plants", createdAt = 5),
    )

    private fun ids(q: String, sort: TaskSort = TaskSort.NEWEST) = searchTasks(todos, q, sort).map { it.id }

    @Test fun everyWordMustStartAWord() {
        assertTrue(Search.matchesAll("Call the dentist", Search.terms("den CALL")))
        assertFalse(Search.matchesAll("Call the dentist", Search.terms("tist")))
        assertFalse(Search.matchesAll("Call the dentist", Search.terms("den x")))
        assertFalse(Search.matchesAll("Call the dentist", emptyList()))
    }

    @Test fun openFirstThenDoneLatestFirst() {
        assertEquals(listOf(1L, 4L, 3L), ids("dent"))
        assertEquals(listOf(5L, 2L, 1L), ids("the"))
    }

    @Test fun detailsCount() {
        assertEquals(listOf(2L), ids("online"))
        assertEquals(listOf(2L), ids("tax 30"))
    }

    @Test fun blankFindsNothing() {
        assertEquals(emptyList<Long>(), ids("  "))
        assertEquals(emptyList<Long>(), ids("zebra"))
    }

    @Test fun splitsWordsBeforeLowercasing() {
        // "İ" lowercases to i + a combining dot; that mustn't split "AİB" into two words.
        assertFalse(Search.matchesAll("AİB", Search.terms("b")))
        assertTrue(Search.matchesAll("AİB", Search.terms("ai")))
        // The typed words too: a title is found by itself.
        assertEquals(listOf("i̇stanbul"), Search.terms("İstanbul"))
        assertTrue(Search.matchesAll("İstanbul", Search.terms("İstanbul")))
        assertTrue(Search.matchesAll("AİB", Search.terms("AİB")))
        // Highlights stay on the original letters: "İs" is 2 characters of "İstanbul", the whole word 8.
        assertEquals(listOf(0..1), Search.matches("İstanbul", Search.terms("İs")))
        assertEquals(listOf(0..7), Search.matches("İstanbul", Search.terms("İstanbul")))
        assertEquals(listOf(4..6), Search.matches("Pay İstanbul", Search.terms("İst")))
    }

    @Test fun everyWordCountsBeyondTen() {
        val ten = "one two three four five six seven eight nine ten"
        val t = listOf(Todo(id = 9, title = ten))
        assertEquals(listOf(9L), searchTasks(t, ten, TaskSort.NEWEST).map { it.id })
        assertEquals(emptyList<Long>(), searchTasks(t, "$ten missing", TaskSort.NEWEST).map { it.id })
    }

    @Test fun detailsSnippetKeepsTagsAsTyped() {
        assertEquals("Check #project", Search.snippet("Check #project", listOf("project"), 60, readable = false))
    }
}
