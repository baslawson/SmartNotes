package io.github.baslawson.smartnotes.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** A `- [ ] text` line in a note. [line] is its 0-based line number in the note body. */
data class NoteTask(val line: Int, val text: String, val done: Boolean, val due: LocalDateTime?, val tags: Set<String>)

/** An `@2026-11-20 14:00` date in a note. [done] is true when it sits on a ticked task line. */
data class Reminder(val line: Int, val at: LocalDateTime, val text: String, val done: Boolean)

/**
 * Everything "smart" about a note is read from its plain text, so the text stays the single source of truth:
 * - `#tag` anywhere is a tag (it needs a letter; `# Heading` and `page#anchor` are not tags),
 * - `[[Other note]]` (or `[[Other note|shown text]]`) links to the note with that title,
 * - `- [ ] item` / `- [x] item` lines are tasks (`*` and `+` work too),
 * - `@2026-11-20` or `@2026-11-20 14:00` is a reminder (09:00 when no time is given).
 *   `@today`, `@tomorrow` and weekdays (`@fri`, `@friday`) are turned into such dates when the note is saved,
 *   see [normalizeDates].
 */
object NoteParser {
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private val TAG = Regex("""(?<![\p{L}\p{N}_#/&])#([\p{L}\p{N}_][\p{L}\p{N}_/-]*)""")
    private val LINK = Regex("""\[\[([^\[\]\n|]+)(?:\|[^\[\]\n]*)?]]""")
    private val TASK = Regex("""^(\s*[-*+]\s\[)([ xX])(].*)$""")

    // A date starts the text or follows a space or an opening bracket, so e-mail addresses are left alone.
    // A time may be followed by anything but more digits ("14:00-15:00" and "14:30h" are at 14:00 and 14:30), and
    // may carry am/pm ("2:00pm" is 14:00); a date without a time must stand alone ("@2026-11-20x" is no date).
    private val DATE = Regex(
        """(?<![^\s(\[])@(\d{4})-(\d{2})-(\d{2})(?:[ T]([01]?\d|2[0-3]):([0-5]\d)(?:\s?([aApP])\.?[mM]\.?(?!\p{L}))?(?![\p{N}:])|(?![\p{L}\p{N}_:-]))"""
    )
    private val RELATIVE = Regex(
        """(?<![^\s(\[])@(today|tomorrow|monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)(?![\p{L}\p{N}_-])""",
        RegexOption.IGNORE_CASE,
    )

    fun tags(text: String): Set<String> =
        TAG.findAll(text)
            .map { it.groupValues[1].trimEnd('-', '/').lowercase() }
            .filter { tag -> tag.any { it.isLetter() } }
            .toSortedSet()

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
                text = cleanText(rest),
                done = m.groupValues[2] != " ",
                due = dates(rest).firstOrNull(),
                tags = tags(rest),
            )
        }

    fun reminders(body: String): List<Reminder> =
        body.split('\n').flatMapIndexed { index, line ->
            val task = TASK.matchEntire(line)
            val done = task != null && task.groupValues[2] != " "
            val text = cleanText(task?.groupValues?.get(3)?.drop(1) ?: line)
            dates(line).map { Reminder(index, it, text, done) }
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

    /** Replaces `@today`, `@tomorrow` and weekdays with real dates, so a reminder doesn't move when the note is edited later. */
    fun normalizeDates(text: String, today: LocalDate): String =
        RELATIVE.replace(text) { m -> "@" + resolveRelative(m.groupValues[1].lowercase(), today) }

    /** Weekdays mean the coming one; on that weekday itself they mean today. */
    internal fun resolveRelative(word: String, today: LocalDate): LocalDate = when (word) {
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        else -> today.with(TemporalAdjusters.nextOrSame(weekday(word)))
    }

    private fun weekday(word: String): DayOfWeek = when (word.take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun dates(text: String): List<LocalDateTime> =
        DATE.findAll(text).mapNotNull { m ->
            val (y, mo, d, h, mi, ampm) = m.destructured
            val date = runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return@mapNotNull null
            val time = if (h.isEmpty()) DEFAULT_TIME else LocalTime.of(hour24(h.toInt(), ampm), mi.toInt())
            date.atTime(time)
        }.toList()

    /** 12-hour times: 12:30am is 00:30, 2pm is 14:00, 12pm is noon; a 24-hour time ("14:00pm") is kept as it is. */
    private fun hour24(hour: Int, ampm: String): Int = when {
        ampm.isEmpty() || hour !in 1..12 -> hour
        ampm.equals("a", ignoreCase = true) -> hour % 12
        else -> hour % 12 + 12
    }

    /** The line as people read it: without dates and with spaces collapsed (tags stay, they are part of the text). */
    private fun cleanText(text: String): String = DATE.replace(text, "").replace(Regex("""\s+"""), " ").trim()

    /** The note's title, or else its first line, or "Untitled". */
    fun displayTitle(title: String, body: String): String =
        title.trim().ifEmpty {
            body.lineSequence().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }?.take(60).orEmpty()
        }.ifEmpty { "Untitled" }
}
