package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {
    @Test fun matchQueryOnlyLetsWordsThrough() {
        assertEquals("gro* milk*", Search.matchQuery("  Gro \"milk\" -"))
        assertEquals("and* or* not*", Search.matchQuery("AND OR NOT"))
        assertEquals("café*", Search.matchQuery("Café!"))
        assertNull(Search.matchQuery(" *\"()- "))
    }

    @Test fun matchesAreWordPrefixes() {
        assertEquals(listOf(0..2, 14..17), Search.matches("Groceries and milky", listOf("gro", "milk")))
        assertEquals(emptyList<IntRange>(), Search.matches("program", listOf("gram")))
    }

    @Test fun snippetCentresOnTheFirstMatch() {
        val body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore target word here"
        val s = Search.snippet(body, listOf("target"), length = 40)
        assertEquals("…tempor incididunt ut labore target word…", s)
        assertEquals("short", Search.snippet("short", listOf("x")))
    }

    @Test fun relatedRanksSharedTagsAndWords() {
        val c = listOf(
            Related.Candidate("a", setOf("trip"), Related.words("Packing list for Paris")),
            Related.Candidate("b", emptySet(), Related.words("Recipe for pancakes")),
            Related.Candidate("c", emptySet(), Related.words("Paris museums and packing")),
        )
        assertEquals(listOf("a", "c"), Related.rank(setOf("trip"), Related.words("Paris packing"), c))
    }
}
