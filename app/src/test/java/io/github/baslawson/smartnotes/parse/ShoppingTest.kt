package io.github.baslawson.smartnotes.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingTest {
    @Test fun quantities() {
        assertEquals(ParsedItem("milk", "2x"), Shopping.parse("2x milk"))
        assertEquals(ParsedItem("apples", "3"), Shopping.parse(" 3   apples "))
        assertEquals(ParsedItem("Coke Zero", "1.5 l"), Shopping.parse("Coke Zero 1.5 l"))
        assertEquals(ParsedItem("milk", "2L"), Shopping.parse("milk 2L"))
        assertEquals(ParsedItem("eggs", "x12"), Shopping.parse("eggs x12"))
        assertEquals(ParsedItem("grapes", "500g"), Shopping.parse("500g grapes"))
        assertEquals(ParsedItem("grapes", "2"), Shopping.parse("2 grapes"))
        assertEquals(ParsedItem("7up", ""), Shopping.parse("7up"))
        assertEquals(ParsedItem("bread", ""), Shopping.parse("bread"))
        assertEquals(ParsedItem("12", ""), Shopping.parse("12"))
        assertNull(Shopping.parse("   "))
    }

    @Test fun suggestionsPreferPrefixThenUse() {
        val history = listOf(
            PastItem("Oat milk", 9, 1), PastItem("Milk", 3, 2), PastItem("Milkshake", 5, 1),
            PastItem("Bread", 20, 3), PastItem("Mild cheese", 1, 4),
        )
        assertEquals(listOf("Milkshake", "Milk", "Oat milk"), Shopping.suggest("2x mil", history, emptySet()).map { it.name }.filter { "milk" in it.lowercase() })
        assertEquals(listOf("Milkshake", "Oat milk"), Shopping.suggest("milk", history, emptySet()).map { it.name })
        assertEquals(listOf("Milkshake", "Mild cheese", "Oat milk"), Shopping.suggest("mil", history, setOf("milk")).map { it.name })
        assertEquals(emptyList<PastItem>(), Shopping.suggest(" ", history, emptySet()))
    }

    @Test fun editTextGivesBackTheSameNameAndQuantity() {
        val cases = listOf("milk" to "", "milk" to "2x", "Coke Zero" to "1.5 l", "milk" to "2L", "eggs" to "x12", "7up" to "", "apples" to "3")
        for ((name, quantity) in cases) {
            assertEquals(ParsedItem(name, quantity), Shopping.parse(Shopping.editText(name, quantity)))
        }
        assertEquals("2x milk", Shopping.editText("milk", "2x"))
        assertEquals("milk", Shopping.editText("milk", ""))
    }

    @Test fun matchInFindsTypedLettersInTheNameItself() {
        assertEquals(4..5, Shopping.matchIn("Oat milk", "MI"))
        assertEquals(0..2, Shopping.matchIn("Milk", "mil"))
        assertEquals(null, Shopping.matchIn("Milk", "x"))
        assertEquals(null, Shopping.matchIn("Milk", ""))
        assertEquals(null, Shopping.matchIn("Mi", "milk"))
        // "İ" lowercases to two characters: positions must still fit the name (this used to crash).
        assertEquals(1..4, Shopping.matchIn("İncir", "ncir"))
        assertEquals(null, Shopping.matchIn("İncir", "i̇ncir"))
    }
}
