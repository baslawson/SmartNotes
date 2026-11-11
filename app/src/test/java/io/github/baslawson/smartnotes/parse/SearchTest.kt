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

    @Test fun snippetsReadWithoutMarkup() {
        assertEquals("Lisbon in May? ☐ book flights ☑ ask Sam", Search.snippet("Lisbon in May?\n- [ ] book flights\n- [x] ask Sam\n#travel", emptyList()))
        assertEquals("See Holiday ideas and C#", Search.snippet("See [[Holiday ideas]] and C# #todo", emptyList()))
        // Only what the parser recognises: shown link text, nested tags, + checkboxes; "#123" isn't a tag.
        assertEquals("the trip, (). Order #123 ☐ pack", Search.snippet("[[Holiday ideas|the trip]], (#idea). Order #123 #home/kitchen\n+ [ ] pack", emptyList()))
        assertEquals("-[ ] not a box", Search.snippet("-[ ] not a box", emptyList()))
        assertEquals("See Topic", Search.snippet("See [[Topic]]#work", emptyList()))
        assertEquals("See #123abc", Search.snippet("See [[Topic|#123]]abc", emptyList()))
    }
}
