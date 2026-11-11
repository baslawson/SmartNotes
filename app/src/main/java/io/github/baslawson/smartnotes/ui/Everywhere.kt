package io.github.baslawson.smartnotes.ui

import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.data.NoteRepository
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.ShoppingRepository
import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.data.TodoRepository
import io.github.baslawson.smartnotes.data.orderLists
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.Search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The app's three sections, as search results are grouped. */
enum class Section(val label: String) { NOTES("Notes"), TASKS("Tasks"), SHOPPING("Shopping") }

/**
 * What a search found in every section: [notes] (as the Notes list shows them), [tasks] (open ones first, then done)
 * and shopping [items] with the list each is on (still to buy first, then ticked). [terms]: the words searched for.
 */
data class Found(
    val query: String = "",
    val terms: List<String> = emptyList(),
    val notes: List<NoteRow> = emptyList(),
    val tasks: List<Todo> = emptyList(),
    val items: List<Pair<ShoppingItem, ShoppingList>> = emptyList(),
    /** False until the first results for [query] are in (so "Nothing found" doesn't flash up while searching). */
    val loaded: Boolean = false,
) {
    fun count(section: Section): Int = when (section) {
        Section.NOTES -> notes.size
        Section.TASKS -> tasks.size
        Section.SHOPPING -> items.size
    }

    val total: Int get() = notes.size + tasks.size + items.size
}

/**
 * One search over notes, tasks and shopping, used by the 🔍 of every section. The same rule everywhere: every word
 * typed must start a word in the note, the task (title or details) or the item's name, capitals ignored. Nothing in
 * the recycle bin is searched.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class Everywhere(
    private val notes: NoteRepository,
    private val todos: TodoRepository,
    private val shopping: ShoppingRepository,
    private val taskSort: Flow<TaskSort>,
    private val appScope: CoroutineScope,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** Results for [query] as it changes (null or blank: nothing searched). */
    fun find(query: Flow<String?>): Flow<Found> = query
        .map { it?.trim().orEmpty() }
        .distinctUntilChanged()
        .debounce(150)
        .flatMapLatest { q ->
            val terms = Search.terms(q, limit = Int.MAX_VALUE)
            if (terms.isEmpty()) flowOf(Found(q, loaded = true))
            // Shopping: the items whose name holds the longest word (a quick narrowing in the database), then the rule.
            else combine(
                notes.search(q), todos.observeAll(), shopping.search(terms.maxBy { it.length }), shopping.observeLists(), taskSort,
            ) { n, t, i, lists, sort -> collect(q, n, t, i, lists, sort, today()) }
        }
        .flowOn(Dispatchers.Default)

    /** Ticks or unticks a task found (from Notes' or Shopping's search). */
    fun setDone(todo: Todo, done: Boolean) = appScope.launch { todos.setDone(todo.id, done) }

    companion object {
        /** The results for [query] from what the sections hold now (notes as their full-text search found them). */
        fun collect(
            query: String,
            notes: List<Note>,
            todos: List<Todo>,
            items: List<ShoppingItem>,
            lists: List<ShoppingList>,
            sort: TaskSort,
            today: LocalDate,
        ): Found {
            val terms = Search.terms(query, limit = Int.MAX_VALUE)
            if (terms.isEmpty()) return Found(query, loaded = true)
            val noteRows = notes.map { note ->
                NoteRow(
                    note = note,
                    title = NoteParser.displayTitle(note.title, note.body),
                    snippet = Search.snippet(note.body, terms),
                    tags = NoteParser.tags(note.title + "\n" + note.body),
                    openTasks = NoteParser.tasks(note.body).count { !it.done },
                )
            }
            // Lists as the Shopping strip orders them (Anytime, today and the days ahead, then past days).
            val order = orderLists(lists, today).withIndex().associate { (i, l) -> l.id to i }
            val byId = lists.associateBy { it.id }
            val found = items
                .filter { Search.matchesAll(it.name, terms) }
                .mapNotNull { item -> byId[item.listId]?.let { item to it } }
                .sortedWith(compareBy<Pair<ShoppingItem, ShoppingList>> { it.first.checked }.thenBy { order[it.second.id] ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name })
            return Found(query, terms, noteRows, searchTasks(todos, query, sort), found, loaded = true)
        }
    }
}
