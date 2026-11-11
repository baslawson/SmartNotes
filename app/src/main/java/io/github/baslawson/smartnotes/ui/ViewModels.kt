package io.github.baslawson.smartnotes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.data.NoteRepository
import io.github.baslawson.smartnotes.data.ShoppingHistory
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingRepository
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.NoteTask
import io.github.baslawson.smartnotes.parse.PastItem
import io.github.baslawson.smartnotes.parse.Related
import io.github.baslawson.smartnotes.parse.Reminder
import io.github.baslawson.smartnotes.parse.Search
import io.github.baslawson.smartnotes.parse.Shopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private val SHARING = SharingStarted.WhileSubscribed(5_000)

// ---------- Notes list ----------

data class NoteRow(
    val note: Note,
    val title: String,
    val snippet: String,
    val tags: Set<String>,
    val openTasks: Int,
    val nextReminder: LocalDateTime?,
)

data class NotesState(
    val rows: List<NoteRow> = emptyList(),
    /** Every tag with the number of notes that have it, most used first. */
    val tags: List<Pair<String, Int>> = emptyList(),
    val terms: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(repo: NoteRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val tag = MutableStateFlow<String?>(null)

    val state: StateFlow<NotesState> = combine(
        query.debounce(150).flatMapLatest { q -> repo.search(q).map { q to it } },
        repo.observeAll(),
        tag,
    ) { (q, results), all, selected ->
        val now = LocalDateTime.now()
        val terms = Search.terms(q)
        val tagsOf = all.associate { it.id to NoteParser.tags(it.title + "\n" + it.body) }
        val counts = tagsOf.values.flatten().groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            // A selected tag that no note has any more stays as a chip (count 0), so it can still be switched off.
            .let { list -> if (selected != null && list.none { it.first == selected }) listOf(selected to 0) + list else list }
        val rows = results
            .filter { selected == null || selected in tagsOf[it.id].orEmpty() }
            .map { note ->
                NoteRow(
                    note = note,
                    title = NoteParser.displayTitle(note.title, note.body),
                    snippet = Search.snippet(note.body, terms),
                    tags = tagsOf[note.id].orEmpty(),
                    openTasks = NoteParser.tasks(note.body).count { !it.done },
                    nextReminder = NoteParser.reminders(note.body).filter { !it.done && it.at.isAfter(now) }.minOfOrNull { it.at },
                )
            }
        NotesState(rows, counts, terms, loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, NotesState())
}

// ---------- Editor ----------

/** [self]: a link to this note's own title, which opens nothing. */
data class LinkInfo(val title: String, val noteId: Long?, val self: Boolean = false)

data class EditorInfo(
    val tags: Set<String> = emptySet(),
    val links: List<LinkInfo> = emptyList(),
    val backlinks: List<Note> = emptyList(),
    val related: List<Note> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

/**
 * Edits one note. Typing is saved as a draft after a short pause; leaving the screen (or the app) saves it for real,
 * which turns `@tomorrow` into a date and schedules the reminders. Saves run in the app's scope so they finish
 * even after this screen is gone.
 *
 * Only edits made here are saved ([dirty]): an editor that hasn't loaded its note, or that wasn't typed in, never
 * writes, so it can't put back older text over a change made elsewhere, or bring back a deleted note.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(
    private val repo: NoteRepository,
    private val appScope: CoroutineScope,
    startId: Long,
    startTitle: String,
) : ViewModel() {
    var id by mutableLongStateOf(startId)
        private set
    var title by mutableStateOf(TextFieldValue(startTitle))
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var pinned by mutableStateOf(false)
        private set
    /** The note was deleted (here or elsewhere); the screen should close. */
    var gone by mutableStateOf(false)
        private set

    /** The note has been read; until then the fields are read-only and nothing is saved. */
    var loaded by mutableStateOf(startId == 0L)
        private set

    // Main thread only. Edited since the last real save; drafts don't clear it, only the real save does.
    private var dirty = false
    /** Raised on every edit, so a save or reload can tell whether the user typed on meanwhile. */
    private var revision = 0

    private val saveLock = Mutex()
    private var draftJob: Job? = null
    @Volatile private var deleted = false

    val info: StateFlow<EditorInfo> = combine(
        snapshotFlow { title.text to body.text }.debounce(300),
        repo.observeAll(),
    ) { (t, b), all ->
        val ownId = id
        val others = all.filter { it.id != ownId }
        // The note's own title counts too, so [[its own title]] isn't offered as a new note.
        val byTitle = all.associateBy { NoteParser.displayTitle(it.title, it.body).lowercase() }
        val ownTitle = NoteParser.displayTitle(t, b).lowercase()
        val tags = NoteParser.tags(t + "\n" + b)
        val links = NoteParser.links(b).map {
            if (it.lowercase() == ownTitle) LinkInfo(it, ownId.takeIf { id -> id != 0L }, self = true)
            else LinkInfo(it, byTitle[it.lowercase()]?.id)
        }
        val linked = links.mapNotNull { it.noteId }.toSet()
        val backlinks = others.filter { n -> NoteParser.links(n.body).any { it.lowercase() == ownTitle } }
        val related = Related.rank(
            tags, Related.words(t + " " + b),
            others.filter { it.id !in linked && it !in backlinks }.map { n ->
                Related.Candidate(n, NoteParser.tags(n.title + "\n" + n.body), Related.words(n.title + " " + n.body))
            },
        )
        EditorInfo(tags, links, backlinks, related, NoteParser.reminders(b).sortedBy { it.at })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, EditorInfo())

    fun onTitleChange(value: TextFieldValue) {
        val changed = value.text != title.text
        title = value
        if (changed) edited()
    }

    fun onBodyChange(value: TextFieldValue) {
        val changed = value.text != body.text
        body = value
        if (changed) edited()
    }

    private fun edited() {
        revision++
        dirty = true
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(800)
            appScope.launch { persist(final = false) }
        }
    }

    /**
     * Called when the screen becomes visible: loads the note, and picks up changes made elsewhere (a task ticked on
     * the Tasks screen, say). Unsaved typing here is never replaced.
     */
    fun reload() {
        if (id == 0L || deleted) return
        val startRevision = revision
        val loadId = id
        appScope.launch {
            saveLock.withLock {
                // A save meanwhile may have emptied (and so removed) the note: then there is nothing to load.
                if (withContext(Dispatchers.Main) { id } != loadId) return@withLock
                val note = repo.get(loadId)
                withContext(Dispatchers.Main) {
                    if (note == null) {
                        // Deleted elsewhere: close without saving anything back.
                        deleted = true
                        draftJob?.cancel()
                        gone = true
                        return@withContext
                    }
                    pinned = note.pinned
                    if (dirty || revision != startRevision) return@withContext
                    if (note.title != title.text) title = title.replacedBy(note.title)
                    if (note.body != body.text) body = body.replacedBy(note.body)
                    loaded = true
                }
            }
        }
    }

    /** The real save: on leaving the screen, the app going to the background, and when this screen is destroyed. */
    fun finish() {
        draftJob?.cancel()
        if (!loaded || !dirty || deleted) return
        appScope.launch { persist(final = true) }
    }

    fun togglePin() {
        draftJob?.cancel()
        val target = !pinned
        pinned = target
        appScope.launch {
            saveLock.withLock {
                if (deleted) return@withLock
                if (id == 0L) {
                    val (t, b) = withContext(Dispatchers.Main) { title.text to body.text }
                    val newId = repo.saveDraft(0, t, b)
                    withContext(Dispatchers.Main) { id = newId }
                }
                if (id != 0L) repo.setPinned(id, target)
                // An empty new note isn't stored, so there is nothing to pin yet.
                else withContext(Dispatchers.Main) { pinned = false }
            }
        }
    }

    fun delete() {
        deleted = true
        draftJob?.cancel()
        gone = true
        appScope.launch { saveLock.withLock { if (id != 0L) repo.delete(id) } }
    }

    private suspend fun persist(final: Boolean) = saveLock.withLock {
        if (deleted) return@withLock
        val (t, b, rev) = withContext(Dispatchers.Main) { Triple(title.text, body.text, revision) }
        // Checked again here: an earlier queued save may already have done the work (or found the note deleted).
        if (!withContext(Dispatchers.Main) { loaded && (dirty || !final) && !deleted }) return@withLock
        if (!final) {
            val newId = repo.saveDraft(id, t, b)
            withContext(Dispatchers.Main) { if (newId != 0L) id = newId }
            return@withLock
        }
        val savedId = id
        val saved = repo.saveFinal(savedId, t, b)
        withContext(Dispatchers.Main) {
            if (saved == null && savedId != 0L && (t.isNotBlank() || b.isNotBlank())) {
                // The note was deleted elsewhere: close, and never save this text as a new note.
                deleted = true
                gone = true
                return@withContext
            }
            id = saved?.id ?: 0L
            if (revision == rev) {
                dirty = false
                // Show "@tomorrow" as the date it became.
                if (saved != null && saved.body != b) body = body.replacedBy(saved.body)
            }
        }
    }

    override fun onCleared() = finish()

    private fun TextFieldValue.replacedBy(text: String) =
        TextFieldValue(text, TextRange(selection.start.coerceAtMost(text.length), selection.end.coerceAtMost(text.length)))
}

// ---------- Tasks ----------

data class TaskRow(val noteId: Long, val noteTitle: String, val task: NoteTask)

class TasksViewModel(private val repo: NoteRepository, private val appScope: CoroutineScope) : ViewModel() {
    val tasks: StateFlow<List<TaskRow>?> = repo.observeAll().map { notes ->
        notes.flatMap { n ->
            val title = NoteParser.displayTitle(n.title, n.body)
            NoteParser.tasks(n.body).map { TaskRow(n.id, title, it) }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, null)

    fun toggle(row: TaskRow) {
        appScope.launch { repo.toggleTask(row.noteId, row.task.line) }
    }
}

// ---------- Shopping ----------

data class ShoppingState(
    val items: List<ShoppingItem> = emptyList(),
    /** Open tasks from notes tagged #shopping (or with #shopping on the task line). */
    val fromNotes: List<TaskRow> = emptyList(),
    val history: List<PastItem> = emptyList(),
    val loaded: Boolean = false,
)

class ShoppingViewModel(
    private val repo: ShoppingRepository,
    private val notes: NoteRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    var input by mutableStateOf("")

    val state: StateFlow<ShoppingState> = combine(
        repo.observeItems(), repo.observeHistory(), notes.observeAll(),
    ) { items, history, all ->
        val fromNotes = all.flatMap { n ->
            val noteTagged = SHOPPING_TAG in NoteParser.tags(n.title + "\n" + n.body)
            val title = NoteParser.displayTitle(n.title, n.body)
            NoteParser.tasks(n.body)
                .filter { !it.done && (noteTagged || SHOPPING_TAG in it.tags) }
                .map { TaskRow(n.id, title, it) }
        }
        ShoppingState(items, fromNotes, history.map(ShoppingHistory::toPast), loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, ShoppingState())

    fun suggestions(state: ShoppingState): List<PastItem> =
        Shopping.suggest(input, state.history, state.items.filter { !it.checked }.map { it.key }.toSet())

    fun add(text: String = input) {
        if (text.isBlank()) return
        input = ""
        appScope.launch { repo.add(text) }
    }

    /** Puts the suggestion on the list, keeping a quantity already typed ("2x mi" + "milk" → 2x milk). */
    fun pick(suggestion: PastItem) {
        val quantity = Shopping.parse(input)?.quantity.orEmpty()
        add(if (quantity.isEmpty()) suggestion.name else "$quantity ${suggestion.name}")
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) = appScope.launch { repo.setChecked(item, checked) }
    fun delete(item: ShoppingItem) = appScope.launch { repo.delete(item.id) }
    fun clearChecked() = appScope.launch { repo.clearChecked() }
    fun forget(item: PastItem) = appScope.launch { repo.forget(Shopping.key(item.name)) }
    fun tickNoteTask(row: TaskRow) = appScope.launch { notes.toggleTask(row.noteId, row.task.line) }

    companion object {
        const val SHOPPING_TAG = "shopping"
    }
}

private fun ShoppingHistory.toPast() = PastItem(name, useCount, lastUsed)
