package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.Search
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

class NoteRepository(
    private val dao: NoteDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    // Saves read the stored note and write it back; one at a time, so two saves can't undo each other.
    private val lock = Mutex()

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    /** Matching notes, or all notes when [text] has no words to search for. */
    fun search(text: String): Flow<List<Note>> = Search.matchQuery(text)?.let(dao::search) ?: dao.observeAll()

    /** The note, unless it is gone or in the recycle bin. */
    suspend fun get(id: Long): Note? = live(id)

    private suspend fun live(id: Long): Note? = dao.get(id)?.takeIf { it.deletedAt == null }

    /**
     * Saves while the note is being edited: the text as typed, nothing else. Returns the note's id (new notes get one).
     * An empty new note is not stored (returns 0); an existing note emptied while typing is kept until [saveFinal].
     */
    suspend fun saveDraft(id: Long, title: String, body: String): Long =
        lock.withLock { write(id, title, body, deleteIfBlank = false) }

    /** Saves a finished edit. An emptied note is deleted. Returns the stored note, or null when there is none. */
    suspend fun saveFinal(id: Long, title: String, body: String): Note? = lock.withLock {
        val newId = write(id, title, body, deleteIfBlank = true)
        if (newId == 0L && id != 0L && live(id) == null && !(title.isBlank() && body.isBlank())) return@withLock null
        if (newId == 0L) {
            if (id != 0L) AppLog.i("Notes", "Note $id emptied, so deleted")
            return@withLock null
        }
        val note = dao.get(newId) ?: return@withLock null
        AppLog.i("Notes", "Saved note ${note.id} \"${note.logTitle()}\"")
        note
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = lock.withLock {
        live(id)?.let { dao.update(it.copy(pinned = pinned)) }
    }

    /** Moves notes to the recycle bin. */
    suspend fun trash(ids: List<Long>) = lock.withLock {
        ids.chunked(CHUNK).forEach { dao.setDeleted(it, clock.millis()) }
        AppLog.i("Notes", "Moved ${ids.size} note(s) to the recycle bin: $ids")
    }

    suspend fun restore(ids: List<Long>) = lock.withLock {
        ids.chunked(CHUNK).forEach { dao.setDeleted(it, null) }
        AppLog.i("Notes", "Restored ${ids.size} note(s): $ids")
    }

    /** Erases notes in the bin: [ids], and every one deleted before [before]. */
    suspend fun erase(ids: List<Long>, before: Long = 0) = lock.withLock {
        if (ids.isEmpty()) dao.erase(ids, before) else ids.chunked(CHUNK).forEach { dao.erase(it, before) }
        AppLog.i("Notes", "Erased from the recycle bin: $ids" + if (before > 0) " and those deleted before $before" else "")
    }

    fun observeDeleted(): Flow<List<Note>> = dao.observeDeleted()

    /** Ticks or unticks the task on [line]. False when that line is no longer a task. */
    suspend fun toggleTask(noteId: Long, line: Int): Boolean = lock.withLock {
        val note = live(noteId) ?: return@withLock false.also { AppLog.w("Notes", "Task toggle: note $noteId is gone") }
        val body = NoteParser.toggleTask(note.body, line)
            ?: return@withLock false.also { AppLog.w("Notes", "Task toggle: line $line of note $noteId is no longer a task") }
        val updated = note.copy(body = body, updatedAt = clock.millis())
        dao.update(updated)
        AppLog.i("Notes", "Toggled task on line $line of note $noteId \"${note.logTitle()}\"")
        true
    }

    private fun Note.logTitle() = NoteParser.displayTitle(title, body)

    private suspend fun write(id: Long, title: String, rawBody: String, deleteIfBlank: Boolean): Long {
        val now = clock.millis()
        val body = NoteParser.normalizeLineEnds(rawBody)
        val blank = title.isBlank() && body.isBlank()
        val existing = if (id != 0L) live(id) else null
        return when {
            // Deleted meanwhile (elsewhere, or to the bin): not brought back as a new note.
            id != 0L && existing == null -> 0L.also { AppLog.w("Notes", "Save skipped: note $id no longer exists") }
            existing == null && blank -> 0L
            existing == null -> dao.insert(Note(title = title, body = body, createdAt = now, updatedAt = now))
            blank && deleteIfBlank -> { dao.delete(existing.id); 0L }
            existing.title == title && existing.body == body -> existing.id
            else -> { dao.update(existing.copy(title = title, body = body, updatedAt = now)); existing.id }
        }
    }
}
