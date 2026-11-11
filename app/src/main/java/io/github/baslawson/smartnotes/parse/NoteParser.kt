package io.github.baslawson.smartnotes.parse

/** A `- [ ] text` line in a note. [line] is its 0-based line number in the note body. */
data class NoteTask(val line: Int, val text: String, val done: Boolean, val tags: Set<String>)

/**
 * Everything "smart" about a note is read from its plain text, so the text stays the single source of truth:
 * - `#tag` anywhere is a tag (it needs a letter; `# Heading` and `page#anchor` are not tags),
 * - `[[Other note]]` (or `[[Other note|shown text]]`) links to the note with that title,
 * - `- [ ] item` / `- [x] item` lines are checkboxes (`*` and `+` work too).
 */
object NoteParser {
    private val TAG = Regex("""(?<![\p{L}\p{N}_#/&])#([\p{L}\p{N}_][\p{L}\p{N}_/-]*)""")
    private val LINK = Regex("""\[\[([^\[\]\n|]+)(?:\|[^\[\]\n]*)?]]""")
    private val TASK = Regex("""^(\s*[-*+]\s\[)([ xX])(].*)$""")

    private val LINK_SHOWN = Regex("""\[\[([^\[\]\n|]+)(?:\|([^\[\]\n]*))?]]""")

    /**
     * [text] as it reads in a list (display only: the note keeps its text): checkbox lines as ☐ / ☑, links as their
     * shown text (or title), and #tags left out (they're listed under the note). Only what this parser recognises
     * changes, so "Order #123" or "C#" stay as written.
     */
    fun readable(text: String): String = text.lines().joinToString("\n") { line ->
        val task = TASK.matchEntire(line)
        val body = if (task == null) line
        else (if (task.groupValues[2] == " ") "☐ " else "☑ ") + task.groupValues[3].drop(1).trimStart()
        // Tags first, on the text as written (as tags() reads it), then links.
        body.replace(TAG) { m -> if (m.groupValues[1].any { it.isLetter() }) "" else m.value }
            .replace(LINK_SHOWN) { m -> m.groupValues[2].trim().ifEmpty { m.groupValues[1].trim() } }
    }

    fun tags(text: String): Set<String> =
        TAG.findAll(text)
            .map { it.groupValues[1].trimEnd('-', '/').lowercase() }
            .filter { tag -> tag.any { it.isLetter() } }
            .toSortedSet()

    /** The tag that makes a note a template ("#template"). */
    const val TEMPLATE = "template"

    /** A note tagged #template: + on Notes offers it as the start of a new note. */
    fun isTemplate(title: String, body: String): Boolean = TEMPLATE in tags(title + "\n" + body)

    /**
     * A new note's title and body from a template's: the same text without its #template tags. A line that held only
     * the tag goes; elsewhere just the space the tag leaves is closed up. Everything else (other tags, spacing, blank
     * lines) stays as written.
     */
    fun fromTemplate(title: String, body: String): Pair<String, String> {
        fun isTemplateTag(m: MatchResult) = m.groupValues[1].trimEnd('-', '/').lowercase() == TEMPLATE
        fun stripLine(line: String): String {
            var text = line
            // Again until none is left: "#template#template" only shows its second tag once the first has gone.
            while (true) {
                val m = TAG.findAll(text).firstOrNull(::isTemplateTag) ?: return text
                val before = text.substring(0, m.range.first)
                val after = text.substring(m.range.last + 1)
                text = when {
                    // "a #template b" → "a b"; "#template b" → "b"; "a #template" → "a".
                    before.endsWith(' ') && (after.startsWith(' ') || after.isEmpty()) -> before.dropLast(1) + after
                    before.isEmpty() && after.startsWith(' ') -> after.drop(1)
                    else -> before + after
                }
            }
        }
        fun strip(text: String): String = text.lines().mapNotNull { line ->
            val cut = stripLine(line)
            if (cut != line && cut.isBlank()) null else cut
        }.joinToString("\n")
        return strip(title) to strip(body)
    }

    /** Linked note titles, in order of first appearance, without case-insensitive duplicates. */
    fun links(text: String): List<String> =
        LINK.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .toList()

    fun tasks(body: String): List<NoteTask> =
        body.split('\n').mapIndexedNotNull { index, line ->
            val m = TASK.matchEntire(line) ?: return@mapIndexedNotNull null
            val rest = m.groupValues[3].drop(1)
            NoteTask(
                line = index,
                text = rest.replace(Regex("""\s+"""), " ").trim(),
                done = m.groupValues[2] != " ",
                tags = tags(rest),
            )
        }

    /** Windows and old Mac line ends become "\n", so every line is read the same way. */
    fun normalizeLineEnds(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    /** The body with the task on [line] ticked or unticked, or null when that line is no longer a task. */
    fun toggleTask(body: String, line: Int): String? {
        val lines = body.split('\n').toMutableList()
        val m = lines.getOrNull(line)?.let { TASK.matchEntire(it) } ?: return null
        val mark = if (m.groupValues[2] == " ") "x" else " "
        lines[line] = m.groupValues[1] + mark + m.groupValues[3]
        return lines.joinToString("\n")
    }

    /** The note's title, or else its first line, or "Untitled". */
    fun displayTitle(title: String, body: String): String =
        title.trim().ifEmpty {
            body.lineSequence().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }?.take(60).orEmpty()
        }.ifEmpty { "Untitled" }
}
