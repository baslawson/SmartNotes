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

    private val BULLET = Regex("""^\s*(?:[-*•+]\s*)?(?:\[[ xX]?]\s*)?(?:\d{1,3}[.)]\s+)?""")

    /** Text shared in as a shopping list: one item per line, without bullets, boxes or "1." numbering. */
    fun sharedLines(text: String): List<String> =
        text.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }

    /** "2x milk", "2 milk", "milk 2L", "milk x2" → name "milk" and quantity "2x"/"2"/"2L"/"x2". */
    fun parse(input: String): ParsedItem? {
        val text = input.replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null
        LEADING.matchEntire(text)?.let { return ParsedItem(it.groupValues[2].trim(), it.groupValues[1].trim()) }
        TRAILING.matchEntire(text)?.let { return ParsedItem(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        return ParsedItem(text, "")
    }

    private val QTY_NUMBER = Regex("""\d+(?:([.,])\d+)?""")
    /** Weights and volumes ("500g", "2L") aren't counted one by one: no − / + for them. */
    private val MEASURE = Regex("""\d\s*(?:kg|g|gr|mg|l|ml|cl|dl)$""", RegexOption.IGNORE_CASE)

    /**
     * [quantity] one more or less ([delta]): "2x" → "3x", "x12" → "x13", "2 packs" → "3 packs". Down from 1 (or below
     * 1) there is no quantity left (""). Null when it has no number to change, or is a weight or volume ("500g").
     */
    fun step(quantity: String, delta: Int): String? {
        if (MEASURE.containsMatchIn(quantity.trim())) return null
        val m = QTY_NUMBER.find(quantity) ?: return null
        val sep = m.groupValues[1]
        val n = m.value.replace(',', '.').toBigDecimal() + delta.toBigDecimal()
        if (n.signum() <= 0) return ""
        val text = n.stripTrailingZeros().toPlainString().let { if (sep == ",") it.replace('.', ',') else it }
        return quantity.replaceRange(m.range, text)
    }

    /** An item as it would be typed ("2x milk", "milk 2L"), so editing it and saving keeps the same name and quantity. */
    fun editText(name: String, quantity: String): String {
        if (quantity.isEmpty()) return name
        val wanted = ParsedItem(name, quantity)
        return listOf("$quantity $name", "$name $quantity").firstOrNull { parse(it) == wanted } ?: "$quantity $name"
    }

    /**
     * Where [typed] appears in [name], ignoring case, as positions in [name] itself (null when it doesn't, or nothing is
     * typed). Compared letter by letter, so letters whose lowercase is longer (İ) can't shift the positions.
     */
    fun matchIn(name: String, typed: String): IntRange? {
        if (typed.isEmpty()) return null
        val at = (0..name.length - typed.length).firstOrNull { name.regionMatches(it, typed, 0, typed.length, ignoreCase = true) }
        return at?.let { it until it + typed.length }
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
