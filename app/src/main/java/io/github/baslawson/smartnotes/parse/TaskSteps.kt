package io.github.baslawson.smartnotes.parse

/**
 * Steps in a task: `- [ ] step` / `- [x] step` lines in its details, written the same way as a note's checklist
 * (`*` and `+` work too). The details text stays the only place they're kept.
 */
object TaskSteps {
    /** The steps in [details]. */
    fun of(details: String): List<NoteTask> = NoteParser.tasks(NoteParser.normalizeLineEnds(details))

    /** "2 of 5" for [details] with steps (2 done of 5), else null. */
    fun progress(details: String): String? {
        val steps = of(details)
        return if (steps.isEmpty()) null else "${steps.count { it.done }} of ${steps.size}"
    }

    /** [details] with the step on [line] ticked or unticked (unchanged when that line is no longer a step). */
    fun toggle(details: String, line: Int): String =
        NoteParser.toggleTask(NoteParser.normalizeLineEnds(details), line) ?: details

    /** [details] with a new step [text] added at the end (blank text: unchanged). */
    fun add(details: String, text: String): String {
        val step = text.replace(Regex("""\s+"""), " ").trim()
        if (step.isEmpty()) return details
        val before = details.trimEnd()
        return (if (before.isEmpty()) "" else "$before\n") + "- [ ] $step"
    }

    /** The first line of [details] that isn't a step (for the task's line in the list), or null. */
    fun about(details: String): String? {
        val stepLines = of(details).map { it.line }.toSet()
        return NoteParser.normalizeLineEnds(details).split('\n')
            .withIndex().firstOrNull { (i, line) -> i !in stepLines && line.isNotBlank() }?.value?.trim()
    }
}
