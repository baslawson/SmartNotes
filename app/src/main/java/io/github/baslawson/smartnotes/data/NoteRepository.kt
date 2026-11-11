package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.Search
import io.github.baslawson.smartnotes.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

class NoteRepository(
    private val dao: NoteDao,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    // Saves read the stored note and write it back; one at a time, so two saves can't undo each other.
    private val lock = Mutex()

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    /** Matching notes, or all notes when [text] has no words to search for. */
    fun search(text: String): Flow<List<Note>> = Search.matchQuery(text)?.let(dao::search) ?: dao.observeAll()

    suspend fun get(id: Long): Note? = dao.get(id)

    /**
     * Saves while the note is being edited: the text as typed, nothing else. Returns the note's id (new notes get one).
     * An empty new note is not stored (returns 0); an existing note emptied while typing is kept until [saveFinal].
     */
    suspend fun saveDraft(id: Long, title: String, body: String): Long =
        lock.withLock { write(id, title, body, deleteIfBlank = false) }

    /**
     * Saves a finished edit: `@tomorrow` and the like become dates, and the note's reminders are scheduled again.
     * An emptied note is deleted. Returns the stored note, or null when there is none.
     */
    suspend fun saveFinal(id: Long, title: String, body: String): Note? = lock.withLock {
        val normalized = NoteParser.normalizeDates(body, LocalDate.now(clock))
        val newId = write(id, title, normalized, deleteIfBlank = true)
        if (newId == 0L && id != 0L && dao.get(id) == null && !(title.isBlank() && body.isBlank())) return@withLock null
        if (newId == 0L) {
            if (id != 0L) {
                scheduler.cancel(id)
                AppLog.i("Notes", "Note $id emptied, so deleted")
            }
            return@withLock null
        }
        val note = dao.get(newId) ?: return@withLock null
        scheduler.reschedule(note)
        AppLog.i("Notes", "Saved note ${note.id} \"${note.logTitle()}\"" + if (normalized != body) " (relative dates filled in)" else "")
        note
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = lock.withLock {
        dao.get(id)?.let { dao.update(it.copy(pinned = pinned)) }
    }

    suspend fun delete(id: Long) = lock.withLock {
        val title = dao.get(id)?.logTitle()
        dao.delete(id)
        scheduler.cancel(id)
        AppLog.i("Notes", "Deleted note $id \"$title\"")
    }

    /** Ticks or unticks the task on [line]. False when that line is no longer a task. */
    suspend fun toggleTask(noteId: Long, line: Int): Boolean = lock.withLock {
        val note = dao.get(noteId) ?: return@withLock false.also { AppLog.w("Notes", "Task toggle: note $noteId is gone") }
        val body = NoteParser.toggleTask(note.body, line)
            ?: return@withLock false.also { AppLog.w("Notes", "Task toggle: line $line of note $noteId is no longer a task") }
        val updated = note.copy(body = body, updatedAt = clock.millis())
        dao.update(updated)
        scheduler.reschedule(updated)
        AppLog.i("Notes", "Toggled task on line $line of note $noteId \"${note.logTitle()}\"")
        true
    }

    /** Re-creates every note's reminder jobs (after an app update, in case jobs were lost). */
    suspend fun rescheduleAll() = dao.all().forEach { stale ->
        // Each note read again under the lock, so a save made meanwhile isn't undone by an older copy.
        lock.withLock { dao.get(stale.id)?.let { scheduler.reschedule(it) } }
    }

    private fun Note.logTitle() = NoteParser.displayTitle(title, body)

    private suspend fun write(id: Long, title: String, rawBody: String, deleteIfBlank: Boolean): Long {
        val now = clock.millis()
        val body = NoteParser.normalizeLineEnds(rawBody)
        val blank = title.isBlank() && body.isBlank()
        val existing = if (id != 0L) dao.get(id) else null
        return when {
            // Deleted meanwhile (elsewhere): not brought back as a new note.
            id != 0L && existing == null -> 0L.also { AppLog.w("Notes", "Save skipped: note $id no longer exists") }
            existing == null && blank -> 0L
            existing == null -> dao.insert(Note(title = title, body = body, createdAt = now, updatedAt = now))
            blank && deleteIfBlank -> { dao.delete(existing.id); 0L }
            existing.title == title && existing.body == body -> existing.id
            else -> { dao.update(existing.copy(title = title, body = body, updatedAt = now)); existing.id }
        }
    }
}
