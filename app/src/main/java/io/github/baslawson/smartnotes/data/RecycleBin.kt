package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.parse.NoteParser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock

/** At most this many ids per query: SQLite up to 3.31 (Android 10) takes 999 values. */
const val CHUNK = 500

enum class BinKind(val label: String, val plural: String) { NOTE("note", "notes"), TASK("task", "tasks"), LIST("shopping list", "shopping lists") }

/** Something in the recycle bin. [title]: how it is shown; [detail]: a little more (a list's items, say). */
data class BinEntry(val kind: BinKind, val id: Long, val title: String, val detail: String, val deletedAt: Long)

/** Shown as a bar with Undo after deleting. */
sealed interface BinEvent {
    val text: String
    /** When it happened: an Undo bar left waiting (the app was closed) isn't shown much later. */
    val at: Long
    data class Trashed(val kind: BinKind, val ids: List<Long>, override val at: Long = System.currentTimeMillis()) : BinEvent {
        override val text = if (ids.size == 1) "${kind.label.replaceFirstChar(Char::uppercase)} moved to the recycle bin"
        else "${ids.size} ${kind.plural} moved to the recycle bin"
    }
    data class ItemRemoved(val item: ShoppingItem, override val at: Long = System.currentTimeMillis()) : BinEvent {
        override val text = "Removed ${item.name}"
    }
    /** Several shopping items removed at once (selected). */
    data class ItemsRemoved(val items: List<ShoppingItem>, override val at: Long = System.currentTimeMillis()) : BinEvent {
        override val text = if (items.size == 1) "Removed ${items[0].name}" else "Removed ${items.size} items"
    }
    /** Shopping items moved to another day ([where]: "Sat 21 Nov", "Anytime"). */
    data class ItemsMoved(val undo: MoveUndo, val where: String, override val at: Long = System.currentTimeMillis()) : BinEvent {
        override val text = (if (undo.moved.size == 1) "Moved ${undo.moved[0].name}" else "Moved ${undo.moved.size} items") + " to $where"
    }
}

/**
 * The recycle bin: deleting a note, task or shopping list moves it here (reminders cancelled), from where it can be
 * restored or erased. Things are erased by themselves after Settings → Recycle bin's time (see [purge]).
 */
class RecycleBin(
    private val notes: NoteRepository,
    private val todos: TodoRepository,
    private val shopping: ShoppingRepository,
    private val settings: AppSettings,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    // Kept until shown: a delete while the window is being rebuilt (turning the phone) still gets its Undo bar.
    private val _events = Channel<BinEvent>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<BinEvent> = _events.receiveAsFlow()

    suspend fun trash(kind: BinKind, ids: List<Long>) {
        if (ids.isEmpty()) return
        when (kind) {
            BinKind.NOTE -> notes.trash(ids)
            BinKind.TASK -> todos.trash(ids)
            BinKind.LIST -> shopping.trashLists(ids)
        }
        val shown = if (kind == BinKind.LIST) ids.filter { it != 1L } else ids
        if (shown.isNotEmpty()) _events.trySend(BinEvent.Trashed(kind, shown))
    }

    suspend fun restore(kind: BinKind, ids: List<Long>) = when (kind) {
        BinKind.NOTE -> notes.restore(ids)
        BinKind.TASK -> todos.restore(ids)
        BinKind.LIST -> shopping.restoreLists(ids)
    }

    suspend fun erase(kind: BinKind, ids: List<Long>) = when (kind) {
        BinKind.NOTE -> notes.erase(ids)
        BinKind.TASK -> todos.erase(ids)
        BinKind.LIST -> shopping.eraseLists(ids)
    }

    /** A shopping item removed with ✕: not binned, but offered back for a moment. */
    fun itemRemoved(item: ShoppingItem) {
        _events.trySend(BinEvent.ItemRemoved(item))
    }

    /** Selected shopping items removed: offered back for a moment, as one. */
    fun itemsRemoved(items: List<ShoppingItem>) {
        if (items.isNotEmpty()) _events.trySend(BinEvent.ItemsRemoved(items))
    }

    /** Shopping items moved to another day: "Moved 3 items to Sat 21 Nov · Undo". */
    fun itemsMoved(undo: MoveUndo, where: String) {
        if (undo.moved.isNotEmpty()) _events.trySend(BinEvent.ItemsMoved(undo, where))
    }

    suspend fun undo(event: BinEvent) = when (event) {
        is BinEvent.Trashed -> restore(event.kind, event.ids)
        is BinEvent.ItemRemoved -> shopping.restoreItem(event.item)
        is BinEvent.ItemsRemoved -> event.items.forEach { shopping.restoreItem(it) }
        is BinEvent.ItemsMoved -> shopping.undoMove(event.undo)
    }

    /** Everything in the bin, newest first. */
    fun observe(): Flow<List<BinEntry>> = combine(
        notes.observeDeleted(), todos.observeDeleted(), shopping.observeDeletedLists(),
    ) { n, t, l ->
        (n.map { BinEntry(BinKind.NOTE, it.id, NoteParser.displayTitle(it.title, it.body), it.body.lineSequence().firstOrNull { l -> l.isNotBlank() && l.trim() != it.title.trim() }.orEmpty().take(80), it.deletedAt ?: 0) } +
            t.map { BinEntry(BinKind.TASK, it.id, it.title.ifBlank { "Task" }, it.details.lineSequence().firstOrNull { l -> l.isNotBlank() }.orEmpty().take(80), it.deletedAt ?: 0) } +
            l.map { BinEntry(BinKind.LIST, it.id, it.date ?: it.name, "", it.deletedAt ?: 0) })
            .sortedByDescending { it.deletedAt }
    }

    /** Erases everything in the bin. */
    suspend fun empty() {
        val now = clock.millis() + 1
        notes.erase(emptyList(), now)
        todos.erase(emptyList(), now)
        shopping.eraseLists(emptyList(), now)
        AppLog.i("Bin", "Emptied the recycle bin")
    }

    /** Erases what has been in the bin longer than Settings → Recycle bin keeps it. Run at every start. */
    suspend fun purge() {
        val cutoff = settings.keepFor.value.cutoff(clock.millis()) ?: return
        notes.erase(emptyList(), cutoff)
        todos.erase(emptyList(), cutoff)
        shopping.eraseLists(emptyList(), cutoff)
    }
}
