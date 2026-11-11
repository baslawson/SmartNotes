package io.github.baslawson.smartnotes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.data.Sorting
import io.github.baslawson.smartnotes.data.ItemSort
import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.NoteSort
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.AppState
import io.github.baslawson.smartnotes.data.BinKind
import io.github.baslawson.smartnotes.data.NoteRepository
import io.github.baslawson.smartnotes.data.RecycleBin
import io.github.baslawson.smartnotes.data.ShoppingHistory
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.ShoppingRepository
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.data.mergeTodo
import io.github.baslawson.smartnotes.data.TodoRepository
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.PastItem
import io.github.baslawson.smartnotes.parse.Related
import io.github.baslawson.smartnotes.parse.Search
import io.github.baslawson.smartnotes.parse.TaskDates
import io.github.baslawson.smartnotes.data.Week
import java.time.LocalDateTime
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val SHARING = SharingStarted.WhileSubscribed(5_000)

// ---------- Notes list ----------

/** Long-press selection on a list screen: the ids picked; empty when not selecting. */
class Selection {
    var ids by mutableStateOf(emptySet<Long>())
        private set
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun selectAll(all: Collection<Long>) { ids = all.toSet() }
    fun clear() { ids = emptySet() }
    /** Drops ids no longer shown (deleted elsewhere, say). */
    fun keepOnly(shown: Collection<Long>) { if (ids.any { it !in shown }) ids = ids.intersect(shown.toSet()) }
}

data class NoteRow(
    val note: Note,
    val title: String,
    val snippet: String,
    val tags: Set<String>,
    val openTasks: Int,
)

data class NotesState(
    val rows: List<NoteRow> = emptyList(),
    /** Every tag with the number of notes that have it, most used first. */
    val tags: List<Pair<String, Int>> = emptyList(),
    val terms: List<String> = emptyList(),
    val loaded: Boolean = false,
    /** The tag filter these rows were made with (it can be a moment behind the chip). */
    val tag: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repo: NoteRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    private val saved: AppState? = null,
    private val settings: AppSettings,
) : ViewModel() {
    val sort: StateFlow<NoteSort> = settings.noteSort

    /** Notes as a grid of cards (or a list). */
    val grid: StateFlow<Boolean> = settings.noteGrid
    fun setGrid(grid: Boolean) = settings.setNoteGrid(grid)

    /** The sort button. The first time "My order" is chosen, the order on screen then becomes its start. */
    fun setSort(new: NoteSort) {
        val old = sort.value
        if (new == NoteSort.MINE && !settings.arranged(ARRANGED)) {
            settings.setArranged(ARRANGED)
            appScope.launch { repo.arrangeFrom { all -> Sorting.notes(all, old) { NoteParser.displayTitle(it.title, it.body) } } }
        }
        settings.setNoteSort(new)
    }

    /** A drag ended (in any order): [ids] are all the notes in their new order, which becomes My order. */
    fun arrange(ids: List<Long>) {
        settings.setArranged(ARRANGED)
        // My order once the places are saved (so the list doesn't show the old places for a moment).
        appScope.launch { repo.arrange(ids); settings.setNoteSort(NoteSort.MINE) }
    }

    val selection = Selection()

    /** Moves the selected notes to the recycle bin. */
    fun deleteSelected() {
        val ids = selection.ids.toList()
        selection.clear()
        appScope.launch { bin.trash(BinKind.NOTE, ids) }
    }

    /** Kept for next time (AppState). */
    val query = MutableStateFlow(saved?.notesQuery.orEmpty())
    val tag = MutableStateFlow(saved?.notesTag)

    init {
        viewModelScope.launch { query.drop(1).collect { saved?.notesQuery = it } }
        viewModelScope.launch { tag.drop(1).collect { saved?.notesTag = it } }
    }

    val state: StateFlow<NotesState> = combine(
        query.debounce(150).flatMapLatest { q -> repo.search(q).map { q to it } },
        repo.observeAll(),
        tag,
        sort,
    ) { (q, results), all, selected, order ->
        val terms = Search.terms(q)
        val tagsOf = all.associate { it.id to NoteParser.tags(it.title + "\n" + it.body) }
        val counts = tagsOf.values.flatten().groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            // A selected tag that no note has any more stays as a chip (count 0), so it can still be switched off.
            .let { list -> if (selected != null && list.none { it.first == selected }) listOf(selected to 0) + list else list }
        val rows = Sorting.notes(results.filter { selected == null || selected in tagsOf[it.id].orEmpty() }, order) {
            NoteParser.displayTitle(it.title, it.body)
        }
            .map { note ->
                NoteRow(
                    note = note,
                    title = NoteParser.displayTitle(note.title, note.body),
                    snippet = Search.snippet(note.body, terms),
                    tags = tagsOf[note.id].orEmpty(),
                    openTasks = NoteParser.tasks(note.body).count { !it.done },
                )
            }
        NotesState(rows, counts, terms, loaded = true, tag = selected)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, NotesState())

    private companion object {
        const val ARRANGED = "notes"
    }
}

// ---------- Editor ----------

/** [self]: a link to this note's own title, which opens nothing. */
data class LinkInfo(val title: String, val noteId: Long?, val self: Boolean = false)

data class EditorInfo(
    val tags: Set<String> = emptySet(),
    val links: List<LinkInfo> = emptyList(),
    val backlinks: List<Note> = emptyList(),
    val related: List<Note> = emptyList(),
    /** The note's own `- [ ]` checkboxes, to tick here. */
    val checklist: List<io.github.baslawson.smartnotes.parse.NoteTask> = emptyList(),
)

/**
 * Edits one note. Typing is saved as a draft after a short pause; leaving the screen (or the app) saves it for real
 * (an emptied note is then removed). Saves run in the app's scope so they finish
 * even after this screen is gone.
 *
 * Only edits made here are saved ([dirty]): an editor that hasn't loaded its note, or that wasn't typed in, never
 * writes, so it can't put back older text over a change made elsewhere, or bring back a deleted note.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(
    private val repo: NoteRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    startId: Long,
    startTitle: String,
    /** Raised by every restore (Settings → Backup): this editor's copy is then out of date, so it closes unsaved. */
    restores: StateFlow<Long> = MutableStateFlow(0L),
) : ViewModel() {
    var id by mutableLongStateOf(startId)
        private set
    /** Opened as a new note (the + button, or a [[link]] to a note not written yet). */
    val isNew = startId == 0L
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
        EditorInfo(tags, links, backlinks, related, NoteParser.tasks(b))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, EditorInfo())

    init {
        val at = restores.value
        viewModelScope.launch {
            restores.first { it != at }
            deleted = true
            draftJob?.cancel()
            gone = true
        }
    }

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

    /** Ticks or unticks the checkbox line [line] in the text being edited (notes keep their own checkboxes). */
    fun toggleCheckbox(line: Int, expectedText: String) {
        if (!loaded) return
        // The list shown may be a moment behind the typing: only the same task on the same line is ticked.
        if (NoteParser.tasks(body.text).none { it.line == line && it.text == expectedText }) return
        val text = NoteParser.toggleTask(body.text, line) ?: return
        onBodyChange(body.copy(text = text))
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

    /** To the recycle bin, with what was typed last (an unsaved new note has nothing to bin). */
    fun delete() {
        val unsaved = loaded && dirty
        deleted = true
        draftJob?.cancel()
        gone = true
        appScope.launch {
            saveLock.withLock {
                val (noteId, t, b) = withContext(Dispatchers.Main) { Triple(id, title.text, body.text) }
                // A new note not saved yet is saved first, so what was typed goes to the bin (and can come back).
                val binId = when {
                    noteId == 0L && (t.isNotBlank() || b.isNotBlank()) -> repo.saveDraft(0, t, b)
                    unsaved && noteId != 0L -> noteId.also { repo.saveDraft(it, t, b) }
                    else -> noteId
                }
                if (binId != 0L) bin.trash(BinKind.NOTE, listOf(binId))
            }
        }
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
                // Line ends as stored ("\r\n" becomes "\n").
                if (saved != null && saved.body != b) body = body.replacedBy(saved.body)
            }
        }
    }

    override fun onCleared() = finish()

    private fun TextFieldValue.replacedBy(text: String) =
        TextFieldValue(text, TextRange(selection.start.coerceAtMost(text.length), selection.end.coerceAtMost(text.length)))
}

// ---------- To-do ----------

/**
 * The Shopping day to open on, from what was saved last time ([saved]: "anytime" or a date): Anytime (null), that day
 * while it is today or to come, else (gone by, or nothing saved) [today].
 */
fun dayToShow(saved: String?, today: java.time.LocalDate): java.time.LocalDate? {
    if (saved == ANYTIME) return null
    val day = saved?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return today
    return if (day.isBefore(today)) today else day
}

/** How the Anytime list is saved as the Shopping day. */
const val ANYTIME = "anytime"

data class TodoState(val todos: List<Todo> = emptyList(), val loaded: Boolean = false)

/** Tasks: one anytime checklist; any task can have its own reminder. */
class TodoViewModel(
    private val repo: TodoRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    private val settings: AppSettings,
) : ViewModel() {
    val selection = Selection()
    val sort: StateFlow<TaskSort> = settings.taskSort
    /** Done tasks shown below the open ones (hidden to start with). */
    val showDone: StateFlow<Boolean> = settings.showDoneTasks
    fun toggleShowDone() = settings.setShowDoneTasks(!showDone.value)

    /** The sort button. The first time "My order" is chosen, the open tasks' order then becomes its start. */
    fun setSort(new: TaskSort) {
        val old = sort.value
        if (new == TaskSort.MINE && !settings.arranged("tasks")) {
            settings.setArranged("tasks")
            appScope.launch { repo.arrangeFrom { open -> Sorting.tasks(open, old) } }
        }
        settings.setTaskSort(new)
    }

    /** A drag ended (in any order): [ids] are the open tasks in their new order, which becomes My order. */
    fun arrange(ids: List<Long>) {
        settings.setArranged("tasks")
        appScope.launch { repo.arrange(ids); settings.setTaskSort(TaskSort.MINE) }
    }

    fun deleteSelected() {
        val ids = selection.ids.toList()
        selection.clear()
        appScope.launch { bin.trash(BinKind.TASK, ids) }
    }

    var input by mutableStateOf("")
        private set

    /** ✕ on the reminder chip: the date words stay in the title (until the box is emptied). */
    var dateOff by mutableStateOf(false)

    fun type(text: String) {
        input = text
        if (text.isBlank()) dateOff = false
    }

    /** The reminder at the end of what is typed ("… tomorrow 3pm"), unless turned off with ✕. */
    fun typedDate(now: LocalDateTime = LocalDateTime.now()): TaskDates.Found? =
        if (dateOff) null else TaskDates.find(input, now, Week.firstDay())

    val state: StateFlow<TodoState> = repo.observeAll().map { TodoState(it, loaded = true) }
        .stateIn(viewModelScope, SHARING, TodoState())

    fun add() {
        val title = input.trim()
        if (title.isEmpty()) return
        val found = typedDate()
        type("")
        appScope.launch {
            if (found != null) repo.add(found.title, found.date, found.time) else repo.add(title)
        }
    }

    fun setDone(todo: Todo, done: Boolean) = appScope.launch { repo.setDone(todo.id, done) }

    /** One task to the recycle bin (swiped away), with Undo for a moment. */
    fun delete(id: Long) = appScope.launch { bin.trash(BinKind.TASK, listOf(id)) }

    /** The search box's text; null while search is closed. */
    val searchQuery = MutableStateFlow<String?>(null)

    /** Moves the ticked tasks to the recycle bin. */
    fun clearDone() = appScope.launch { bin.trash(BinKind.TASK, repo.doneIds()) }
}

/**
 * Tasks search: the tasks whose title and details hold every word of [query] (each as the start of a word, capitals
 * ignored): open ones first in [sort]'s order, then done ones, latest done first.
 */
fun searchTasks(todos: List<Todo>, query: String, sort: TaskSort): List<Todo> {
    val terms = Search.terms(query, limit = Int.MAX_VALUE)
    val found = todos.filter { Search.matchesAll(it.title + "\n" + it.details, terms) }
    return Sorting.tasks(found.filter { !it.done }, sort) + found.filter { it.done }.sortedByDescending { it.doneAt }
}

/** Edits one to-do task; every change is saved straight away (text after a short pause). */
@OptIn(FlowPreview::class)
class TodoEditorViewModel(
    private val repo: TodoRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    id: Long,
    /** Raised by every restore: this editor's copy is then out of date, so it closes unsaved. */
    restores: StateFlow<Long> = MutableStateFlow(0L),
) : ViewModel() {
    var todo by mutableStateOf<Todo?>(null)
        private set
    var gone by mutableStateOf(false)
        private set
    private var saveJob: Job? = null
    /** The row as this editor last saw it in the database: only fields changed from it are saved over the current row. */
    private var base: Todo? = null
    /** A change not yet saved. */
    private var pending = false
    /** Saves (and Done) run one at a time, in the order made. */
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            val loaded = repo.get(id)
            if (loaded == null) gone = true else { base = loaded; todo = loaded }
        }
        val at = restores.value
        viewModelScope.launch {
            restores.first { it != at }
            saveJob?.cancel()
            pending = false
            base = null
            todo = null
            gone = true
        }
    }

    /**
     * Ticks or unticks the step on [line] of the details (saved at once). [seen]: the details that line number was read
     * from; if they've changed since (the line could now be another step), the tap is ignored.
     */
    fun tickStep(line: Int, seen: String) = update {
        if (it.details != seen) it else it.copy(details = io.github.baslawson.smartnotes.parse.TaskSteps.toggle(it.details, line))
    }

    /** Adds a step at the end of the details (saved at once). */
    fun addStep(text: String) = update { it.copy(details = io.github.baslawson.smartnotes.parse.TaskSteps.add(it.details, text)) }

    /** Applies [change]; text edits are saved after a pause, everything else at once. */
    fun update(text: Boolean = false, change: (Todo) -> Todo) {
        val current = todo ?: return
        todo = change(current)
        pending = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (text) delay(600)
            save()
        }
    }

    /** Saves the editor's changes (read when the save's turn comes, so the latest win), then [after] if given. */
    private fun save(after: (suspend (Long) -> Unit)? = null) {
        pending = false
        appScope.launch {
            saveLock.withLock {
                val edited = withContext(Dispatchers.Main) { todo } ?: return@withLock
                val known = base ?: return@withLock
                var saved = repo.save(known, edited) ?: return@withLock
                if (after != null) {
                    after(edited.id)
                    saved = repo.get(edited.id) ?: return@withLock
                }
                withContext(Dispatchers.Main) {
                    base = saved
                    // Fields typed since this save started stay; the rest show the row as saved (and changed elsewhere).
                    // The title box keeps what is typed: the saved title is trimmed, and a blank one keeps the old.
                    todo = todo?.let { typed ->
                        val shown = mergeTodo(edited, typed, saved)
                        if (typed.title.isBlank() || typed.title.trim() == saved.title) shown.copy(title = typed.title) else shown
                    }
                }
            }
        }
    }

    fun setDone(done: Boolean) {
        if (todo == null) return
        saveJob?.cancel()
        // The box shows the outcome once saved: a repeating task moves on to its next date rather than being done.
        save { id -> repo.setDone(id, done) }
    }

    /** To the recycle bin, with the last changes saved first (also those a queued save hasn't written yet). */
    fun delete() {
        val t = todo ?: return
        // The editor's last copy with the row it was based on: only what was changed between them is saved.
        val known = base
        saveJob?.cancel()
        pending = false
        todo = null
        gone = true
        appScope.launch {
            saveLock.withLock {
                if (known != null && t != known) repo.save(known, t)
                bin.trash(BinKind.TASK, listOf(t.id))
            }
        }
    }

    /** Leaving the screen saves what is pending. */
    fun finish() {
        if (pending) {
            saveJob?.cancel()
            save()
        }
    }

    override fun onCleared() = finish()
}

// ---------- Shopping ----------

data class ShoppingState(
    val lists: List<ShoppingList> = emptyList(),
    /** The items of the day shown (none when that day has no list yet). */
    val items: List<ShoppingItem> = emptyList(),
    /** Anytime's items (shown under today's too). */
    val anytime: List<ShoppingItem> = emptyList(),
    val history: List<PastItem> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(
    private val repo: ShoppingRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    private val prefs: android.content.SharedPreferences?,
    startList: Long,
    private val settings: AppSettings,
) : ViewModel() {
    val itemSort: StateFlow<ItemSort> = settings.itemSort
    /** Ticked items shown ("In the basket"; hidden to start with). */
    val showTicked: StateFlow<Boolean> = settings.showTickedItems
    fun toggleShowTicked() = settings.setShowTickedItems(!showTicked.value)

    /**
     * Item order changes (choosing a sort, setting up My order, saving a drag) run one after another, in the order
     * they were made: a drag is never overwritten by setup, and a later choice is never undone by an earlier one.
     */
    private val orderJobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        appScope.launch {
            for (job in orderJobs) runCatching { job() }.onFailure { AppLog.w("Shopping", "Item order change failed", it) }
        }
    }

    override fun onCleared() {
        // The jobs already queued still run (the loop ends once they are done).
        orderJobs.close()
    }

    /**
     * The items' sort button. My order starts from the order each list showed: lists never arranged get it first
     * (see [toSeed]), then the sort switches.
     */
    fun setItemSort(new: ItemSort) {
        // Read when the job runs: choices made meanwhile (queued before it) count, so the last one wins.
        orderJobs.trySend {
            val old = itemSort.value
            if (new != old) {
                if (new == ItemSort.MINE) seed(toSeed(except = 0), old)
                settings.setItemSort(new)
            }
        }
    }

    /** The lists (but [except]) not arranged before; from now on they count as arranged. */
    private fun toSeed(except: Long): List<Long> {
        val ids = state.value.lists.map { it.id }.filter { it != except && !settings.arranged("items:$it") }
        ids.forEach { settings.setArranged("items:$it") }
        return ids
    }

    /** Each of [lists] gets the order its items showed in [old], so every list looks as it did a moment ago. */
    private suspend fun seed(lists: List<Long>, old: ItemSort) {
        if (old == ItemSort.MINE) return
        lists.forEach { id -> repo.arrangeItemsFrom(id) { open -> Sorting.items(open, old) } }
    }

    /** A drag ended (in any order): the open items of the list shown, in their new order, which becomes My order. */
    fun arrangeItems(ids: List<Long>) {
        // The item order is one setting for every list: the others keep the order they showed (set up once).
        val old = itemSort.value
        val list = listId.value
        if (list <= 0) return
        orderJobs.trySend {
            if (old != ItemSort.MINE) seed(toSeed(except = list), old)
            settings.setArranged("items:$list")
            repo.arrangeItems(list, ids)
            settings.setItemSort(ItemSort.MINE)
        }
    }

    /** The day shown: a date, or null for Anytime. Opens on the day looked at last (see [dayToShow]). */
    val day = MutableStateFlow(dayToShow(prefs?.getString(KEY_DAY, null), java.time.LocalDate.now()))
    var input by mutableStateOf("")

    private val lists: StateFlow<List<ShoppingList>> = repo.observeLists().stateIn(viewModelScope, SHARING, emptyList())

    /** The id of the day's list: Anytime is 1; 0 while the day has none (one is made with its first item). */
    val listId: StateFlow<Long> = combine(day, lists) { d, all -> listIdFor(d, all) }
        .stateIn(viewModelScope, SHARING, listIdFor(day.value, emptyList()))

    private fun listIdFor(d: java.time.LocalDate?, all: List<ShoppingList>): Long =
        if (d == null) 1L else all.firstOrNull { it.date == d.toString() }?.id ?: 0L

    /** The list a reminder opened Shopping on: never tidied away while it is being opened. */
    private val startedOn = startList

    init {
        // Opened from a list's reminder: that list's day.
        if (startList > 0) openListId(startList)
        // Once per visit to Shopping: what is still to buy on past lists moves to today's list, then past lists with
        // nothing left to buy go to the recycle bin (not the one shown).
        appScope.launch { newDay() }
    }

    /** Past lists' unticked items carried over to today, then the emptied past lists tidied away (see init). */
    private suspend fun newDay() {
        runCatching { repo.foldUndated() }.onFailure { AppLog.w("Shopping", "Folding undated lists failed", it) }
        runCatching { repo.carryOver() }.onFailure { AppLog.w("Shopping", "Carry-over failed", it) }
        runCatching {
            // The day shown, read now (not from the screen's flows, which may not have started yet).
            val shown = listIdFor(day.value, repo.observeLists().first())
            repo.tidyPast(keep = setOf(shown, startedOn))
        }.onFailure { AppLog.w("Shopping", "Tidy failed", it) }
    }

    /** The day changed while Shopping was open. */
    fun dayChanged() {
        appScope.launch { newDay() }
    }

    val state: StateFlow<ShoppingState> = combine(
        repo.observeLists(), listId.flatMapLatest { repo.observeItems(it) }, repo.observeItems(1L), repo.observeHistory(),
    ) { lists, items, anytime, history ->
        ShoppingState(lists, items, anytime, history.map(ShoppingHistory::toPast), loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, ShoppingState())

    /** List id → items still to buy (the strip's dots and Anytime's count). */
    val openCounts: StateFlow<Map<Long, Int>> = repo.observeOpenCounts().stateIn(viewModelScope, SHARING, emptyMap())

    /** The search box's text; null while search is closed. */
    val searchQuery = MutableStateFlow<String?>(null)

    /** Items in every list in use (past ones too) matching the search; empty until something is typed. */
    val searchResults: StateFlow<List<ShoppingItem>> = searchQuery
        .map { it?.trim().orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyList()) else repo.search(q) }
        .stateIn(viewModelScope, SHARING, emptyList())

    /** Counts every [show], so the week strip comes back to the day shown even when it is the same day again. */
    var shownTimes by mutableIntStateOf(0)
        private set

    /** Shows [date] (null: Anytime), remembered for next time. */
    fun show(date: java.time.LocalDate?) {
        if (day.value != date) selection.clear()
        day.value = date
        shownTimes++
        prefs?.edit()?.putString(KEY_DAY, date?.toString() ?: ANYTIME)?.apply()
    }

    /** Shows the day of list [id] (from its reminder), once the lists are read. */
    fun openListId(id: Long) {
        viewModelScope.launch { repo.observeLists().first().firstOrNull { it.id == id }?.let { openList(it) } }
    }

    /** Shows [list]'s day (a search result, a reminder). */
    fun openList(list: ShoppingList) =
        show(if (list.id == 1L) null else list.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() })

    /** Past items matching what is typed, but not what the day's list (or, on today, Anytime) still has to buy. */
    fun suggestions(state: ShoppingState, withAnytime: Boolean): List<PastItem> {
        val open = state.items.filter { !it.checked } + if (withAnytime) state.anytime.filter { !it.checked } else emptyList()
        return Shopping.suggest(input, state.history, open.map { it.key }.toSet())
    }

    /** Adds what is typed to the day shown (its list made with its first item). */
    fun add(text: String = input) {
        if (text.isBlank()) return
        input = ""
        val date = day.value
        appScope.launch { repo.addOn(date, text) }
    }

    /** Puts the suggestion on the list, keeping a quantity already typed ("2x mi" + "milk" → 2x milk). */
    fun pick(suggestion: PastItem) {
        val quantity = Shopping.parse(input)?.quantity.orEmpty()
        add(if (quantity.isEmpty()) suggestion.name else "$quantity ${suggestion.name}")
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) = appScope.launch { repo.setChecked(item, checked) }

    /** Saves an item's new name and quantity ("2x oat milk"). */
    fun rename(item: ShoppingItem, text: String) = appScope.launch {
        if (!repo.rename(item, text)) withContext(Dispatchers.Main) { nameTaken = true }
    }

    /** – / + on an item's quantity: one less or more ("2x" → "3x"); below 1 the quantity goes. */
    fun step(item: ShoppingItem, delta: Int) = appScope.launch { repo.step(item.id, delta) }

    /** A rename refused because the list already has an item with that name (added meanwhile, say). */
    var nameTaken by mutableStateOf(false)
    /** ✕: gone at once, with Undo for a moment. */
    fun delete(item: ShoppingItem) = appScope.launch {
        repo.delete(item.id)
        bin.itemRemoved(item)
    }
    fun clearChecked() = listId.value.let { list -> if (list > 0) appScope.launch { repo.clearChecked(list) } }
    fun forget(item: PastItem) = appScope.launch { repo.forget(Shopping.key(item.name)) }

    /** Saves a list's date and reminder (a day without a list yet gets one). */
    fun saveList(list: ShoppingList) = appScope.launch {
        val id = repo.saveList(list)
        withContext(Dispatchers.Main) {
            if (id == null) dateTaken = true
            // Moved to another day: shown there.
            else if (list.date != null && day.value?.toString() != list.date) openList(list.copy(id = id))
        }
    }

    /** A save refused because another list has that date (made meanwhile, say in another window). */
    var dateTaken by mutableStateOf(false)

    /** Items selected with a long press (to move to another day, or remove). */
    val selection = Selection()

    /**
     * Moves the selected items ([items]: those shown) to [date] (null: Anytime); [where] names it for the Undo bar
     * ("Sat 21 Nov").
     */
    fun moveSelected(items: List<ShoppingItem>, date: java.time.LocalDate?, where: String) {
        val ids = items.map { it.id }.filter { it in selection.ids }
        selection.clear()
        if (ids.isEmpty()) return
        appScope.launch { repo.moveItems(ids, date)?.let { bin.itemsMoved(it, where) } }
    }

    /** Removes the selected items ([items]: those shown), with Undo for a moment. */
    fun deleteSelected(items: List<ShoppingItem>) {
        val gone = items.filter { it.id in selection.ids }
        selection.clear()
        if (gone.isEmpty()) return
        appScope.launch {
            repo.deleteItems(gone.map { it.id })
            bin.itemsRemoved(gone)
        }
    }

    /** ✎ → Delete this list: it goes to the recycle bin; the day stays shown, now with nothing planned. */
    fun deleteList(id: Long) {
        appScope.launch { bin.trash(BinKind.LIST, listOf(id)) }
    }

    private companion object {
        const val KEY_DAY = "shopping_day"
    }
}

private fun ShoppingHistory.toPast() = PastItem(name, useCount, lastUsed)
