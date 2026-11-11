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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(repo: NoteRepository, private val bin: RecycleBin, private val appScope: CoroutineScope) : ViewModel() {
    val selection = Selection()

    /** Moves the selected notes to the recycle bin. */
    fun deleteSelected() {
        val ids = selection.ids.toList()
        selection.clear()
        appScope.launch { bin.trash(BinKind.NOTE, ids) }
    }

    val query = MutableStateFlow("")
    val tag = MutableStateFlow<String?>(null)

    val state: StateFlow<NotesState> = combine(
        query.debounce(150).flatMapLatest { q -> repo.search(q).map { q to it } },
        repo.observeAll(),
        tag,
    ) { (q, results), all, selected ->
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
        EditorInfo(tags, links, backlinks, related, NoteParser.tasks(b))
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

/** Remembers which list a screen showed last (per phone), so it opens there again. */
private fun rememberedList(prefs: android.content.SharedPreferences?, key: String): Long = prefs?.getLong(key, 1L) ?: 1L

data class TodoState(val todos: List<Todo> = emptyList(), val loaded: Boolean = false)

/** Tasks: one anytime checklist; any task can have its own reminder. */
class TodoViewModel(private val repo: TodoRepository, private val bin: RecycleBin, private val appScope: CoroutineScope) : ViewModel() {
    val selection = Selection()

    fun deleteSelected() {
        val ids = selection.ids.toList()
        selection.clear()
        appScope.launch { bin.trash(BinKind.TASK, ids) }
    }

    var input by mutableStateOf("")

    val state: StateFlow<TodoState> = repo.observeAll().map { TodoState(it, loaded = true) }
        .stateIn(viewModelScope, SHARING, TodoState())

    fun add() {
        val title = input.trim()
        if (title.isEmpty()) return
        input = ""
        appScope.launch { repo.add(title) }
    }

    fun setDone(todo: Todo, done: Boolean) = appScope.launch { repo.setDone(todo.id, done) }
    /** Moves the ticked tasks to the recycle bin. */
    fun clearDone() = appScope.launch { bin.trash(BinKind.TASK, repo.doneIds()) }
}

/** Edits one to-do task; every change is saved straight away (text after a short pause). */
@OptIn(FlowPreview::class)
class TodoEditorViewModel(
    private val repo: TodoRepository,
    private val bin: RecycleBin,
    private val appScope: CoroutineScope,
    id: Long,
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
    }

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
    val items: List<ShoppingItem> = emptyList(),
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
) : ViewModel() {
    val listId = MutableStateFlow(if (startList > 0) startList else rememberedList(prefs, KEY))
    var input by mutableStateOf("")

    val state: StateFlow<ShoppingState> = combine(
        repo.observeLists(), listId.flatMapLatest { repo.observeItems(it) }, repo.observeHistory(),
    ) { lists, items, history ->
        ShoppingState(lists, items, history.map(ShoppingHistory::toPast), loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SHARING, ShoppingState())

    fun select(id: Long) {
        listId.value = id
        prefs?.edit()?.putLong(KEY, id)?.apply()
    }

    fun suggestions(state: ShoppingState): List<PastItem> =
        Shopping.suggest(input, state.history, state.items.filter { !it.checked }.map { it.key }.toSet())

    fun add(text: String = input) {
        if (text.isBlank()) return
        input = ""
        val list = listId.value
        appScope.launch { repo.add(list, text) }
    }

    /** Puts the suggestion on the list, keeping a quantity already typed ("2x mi" + "milk" → 2x milk). */
    fun pick(suggestion: PastItem) {
        val quantity = Shopping.parse(input)?.quantity.orEmpty()
        add(if (quantity.isEmpty()) suggestion.name else "$quantity ${suggestion.name}")
    }

    fun setChecked(item: ShoppingItem, checked: Boolean) = appScope.launch { repo.setChecked(item, checked) }
    /** ✕: gone at once, with Undo for a moment. */
    fun delete(item: ShoppingItem) = appScope.launch {
        repo.delete(item.id)
        bin.itemRemoved(item)
    }
    fun clearChecked() = listId.value.let { list -> appScope.launch { repo.clearChecked(list) } }
    fun forget(item: PastItem) = appScope.launch { repo.forget(Shopping.key(item.name)) }

    /** Saves a list's date and reminder. */
    fun saveList(list: ShoppingList) = appScope.launch {
        if (repo.saveList(list) == null) withContext(Dispatchers.Main) { dateTaken = true }
    }

    /** A save refused because another list has that date (made meanwhile, say in another window). */
    var dateTaken by mutableStateOf(false)

    /** Shows the list for [date], making it first if needed. */
    fun openDate(date: java.time.LocalDate) = appScope.launch {
        val id = repo.listForDate(date)
        // Shown once the screen's lists include it: selecting it sooner could flip back to the first list.
        // (Not forever: the screen may be gone, or the list binned meanwhile.)
        withTimeoutOrNull(5_000) { state.first { s -> s.lists.any { it.id == id } } } ?: return@launch
        withContext(Dispatchers.Main) { select(id) }
    }

    /** Lists selected with a long press (Anytime can't be). */
    val selection = Selection()

    fun deleteList(id: Long) = deleteLists(listOf(id))

    fun deleteSelected() {
        val ids = selection.ids.toList()
        selection.clear()
        deleteLists(ids)
    }

    private fun deleteLists(ids: List<Long>) {
        if (listId.value in ids) select(1L)
        appScope.launch { bin.trash(BinKind.LIST, ids) }
    }

    private companion object {
        const val KEY = "shopping_list"
    }
}

private fun ShoppingHistory.toPast() = PastItem(name, useCount, lastUsed)
