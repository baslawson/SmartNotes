package io.github.baslawson.smartnotes.parse

object Search {
    private val WORD = Regex("""[\p{L}\p{N}]+""")

    /** The words typed in the search box, lowercased; at most 10. */
    fun terms(input: String): List<String> =
        WORD.findAll(input.lowercase()).map { it.value }.distinct().take(10).toList()

    /**
     * An FTS4 MATCH expression: every word must appear, each as a word prefix (`gro` finds "groceries").
     * Only letters and digits get through, so the user can't type FTS syntax that breaks the query.
     * Words are lowercased, so "and"/"or"/"not" are never read as operators (those are upper case in FTS).
     */
    fun matchQuery(input: String): String? =
        terms(input).takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

    /** Character ranges in [text] of the words that start with one of [terms] (for highlighting). */
    fun matches(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty()) return emptyList()
        return WORD.findAll(text).mapNotNull { w ->
            val lower = w.value.lowercase()
            terms.filter { lower.startsWith(it) }.maxOfOrNull { it.length }
                ?.let { w.range.first until w.range.first + it }
        }.toList()
    }

    /** A one-line piece of [body] around the first match, or its start when nothing matches. */
    fun snippet(body: String, terms: List<String>, length: Int = 120): String {
        val flat = NoteParser.readable(body).replace(Regex("""\s+"""), " ").trim()
        val first = matches(flat, terms).firstOrNull()?.first ?: 0
        val start = if (first < 30) 0 else (first - 30).let { s -> flat.indexOf(' ', s).takeIf { it in s until first }?.plus(1) ?: s }
        val end = minOf(flat.length, start + length)
        return (if (start > 0) "…" else "") + flat.substring(start, end).trimEnd() + (if (end < flat.length) "…" else "")
    }
}

object Related {
    private val WORD = Regex("""[\p{L}][\p{L}\p{N}]{3,}""")
    private val STOP = setOf(
        "this", "that", "with", "from", "have", "will", "your", "about", "there", "their", "what", "when", "which",
        "would", "could", "should", "been", "were", "they", "them", "then", "than", "also", "just", "into", "some",
        "more", "only", "very", "here", "over", "like", "make", "done", "todo", "today", "tomorrow",
    )

    fun words(text: String): Set<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filterNot { it in STOP }.toSet()

    data class Candidate<T>(val item: T, val tags: Set<String>, val words: Set<String>)

    /** Up to [limit] candidates most like ([tags], [words]): each shared tag counts 2, word overlap (Jaccard) up to 10. */
    fun <T> rank(tags: Set<String>, words: Set<String>, candidates: List<Candidate<T>>, limit: Int = 5): List<T> =
        candidates.map { c ->
            val shared = (c.words intersect words).size
            val union = (c.words union words).size
            val score = 2.0 * (c.tags intersect tags).size + if (union == 0) 0.0 else 10.0 * shared / union
            c to score
        }.filter { it.second >= 1.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.item }
}
