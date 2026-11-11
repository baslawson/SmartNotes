package io.github.baslawson.smartnotes.parse

/** What the user typed in the shopping list's add box, split into item name and quantity ("" when none). */
data class ParsedItem(val name: String, val quantity: String)

/** A past shopping item, for suggestions while typing. */
data class PastItem(val name: String, val useCount: Int, val lastUsed: Long)

object Shopping {
    private const val NUMBER = """\d+(?:[.,]\d+)?"""
    private const val UNIT = """(?:x|×|kg|g|gr|mg|l|ml|cl|dl|pcs|pc|st|pack|packs|bags?|bottles?|cans?|jars?|boxes|box)"""
    private val QTY = """(?:$NUMBER\s*$UNIT?|[x×]\s*$NUMBER)"""

    private val LEADING = Regex("""^($QTY)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val TRAILING = Regex("""^(.+?)\s+($QTY)$""", RegexOption.IGNORE_CASE)

    /** "2x milk", "2 milk", "milk 2L", "milk x2" → name "milk" and quantity "2x"/"2"/"2L"/"x2". */
    fun parse(input: String): ParsedItem? {
        val text = input.replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null
        LEADING.matchEntire(text)?.let { return ParsedItem(it.groupValues[2].trim(), it.groupValues[1].trim()) }
        TRAILING.matchEntire(text)?.let { return ParsedItem(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        return ParsedItem(text, "")
    }

    /** The key that makes "Milk" and "milk " the same item. */
    fun key(name: String): String = name.trim().lowercase()

    /**
     * Past items matching what is being typed (the quantity part is ignored), most-used first; names that start with
     * the text come before names that only contain it. Items already open on the list are left out.
     */
    fun suggest(input: String, history: List<PastItem>, onList: Set<String>, limit: Int = 5): List<PastItem> {
        val typed = key(parse(input)?.name ?: return emptyList())
        if (typed.isEmpty()) return emptyList()
        return history.asSequence()
            .filter { key(it.name) !in onList }
            .filter { key(it.name).contains(typed) && key(it.name) != typed }
            .sortedWith(
                compareByDescending<PastItem> { key(it.name).startsWith(typed) }
                    .thenByDescending { it.useCount }
                    .thenByDescending { it.lastUsed }
            )
            .take(limit)
            .toList()
    }
}
