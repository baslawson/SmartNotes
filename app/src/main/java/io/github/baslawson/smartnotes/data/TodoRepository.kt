package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.reminders.AlarmPlanner
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import io.github.baslawson.smartnotes.reminders.Repeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** The to-do lists and their tasks, kept apart from notes. Every change sets the task's reminder to match. */
class TodoRepository(private val dao: TodoDao, private val alarms: AlarmScheduler, private val clock: Clock = Clock.systemDefaultZone()) {
    private val lock = Mutex()

    fun observeAll(): Flow<List<Todo>> = dao.observeAll()
    /** The task, unless it is gone or in the recycle bin (an old notification then opens nothing). */
    suspend fun get(id: Long): Todo? = dao.get(id)?.takeIf { it.deletedAt == null }

    suspend fun add(title: String): Long = lock.withLock {
        val now = clock.millis()
        val id = dao.insert(Todo(title = title.trim(), createdAt = now, updatedAt = now))
        AppLog.i("Todo", "Added task $id \"${title.trim()}\"")
        id
    }

    /**
     * Saves what the editor changed: the fields where [edited] differs from [base] (the row as the editor last knew it)
     * go over the current row, so a change made elsewhere meanwhile (Done from a notification, say) isn't undone. A
     * blank title keeps the old one. Returns the row as saved, or null when the task is gone.
     */
    suspend fun save(base: Todo, edited: Todo): Todo? = lock.withLock {
        val old = dao.get(edited.id) ?: return@withLock null
        val merged = mergeTodo(base, edited, old)
        val saved = merged.copy(
            title = merged.title.trim().ifEmpty { old.title },
            // A date or repeat changed in the editor starts a monthly repeat afresh from the new date's day (even
            // when it happens to equal the stored date, which may have moved on meanwhile).
            repeatDay = if (edited.dueDate != base.dueDate || edited.repeat != base.repeat) 0 else merged.repeatDay,
            updatedAt = clock.millis(),
        )
        dao.update(saved)
        syncAlarm(saved)
        AppLog.i("Todo", "Saved task ${saved.id} \"${saved.title}\"")
        saved
    }

    /**
     * Ticks or unticks a task. A repeating task ticked off isn't done: it moves on to its next date (and its reminder
     * with it), as in Planner. With [onlyIfDueAt] (a notification's Done) nothing happens unless that is still the
     * task's reminder time, so an old notification can't tick off a later date.
     */
    suspend fun setDone(id: Long, done: Boolean, onlyIfDueAt: LocalDateTime? = null) = lock.withLock {
        val todo = dao.get(id) ?: return@withLock
        if (onlyIfDueAt != null && todo.reminderTime() != onlyIfDueAt) {
            AppLog.i("Todo", "Done from an old reminder ($onlyIfDueAt) ignored for task $id: it has changed since")
            return@withLock
        }
        val next = if (done) nextDue(todo) else null
        val updated = if (next != null) {
            AppLog.i("Todo", "Repeating task $id \"${todo.title}\" moved from ${todo.dueDate} to ${next.dueDate}")
            next.copy(updatedAt = clock.millis())
        } else {
            AppLog.i("Todo", (if (done) "Done: " else "Not done: ") + "task $id \"${todo.title}\"")
            todo.copy(done = done, doneAt = if (done) clock.millis() else 0, updatedAt = clock.millis())
        }
        dao.update(updated)
        syncAlarm(updated)
    }

    /** Moves tasks to the recycle bin; their reminders are cancelled. */
    suspend fun trash(ids: List<Long>) = lock.withLock {
        ids.chunked(CHUNK).forEach { dao.setDeleted(it, clock.millis()) }
        ids.forEach { alarms.sync(AlarmScheduler.TODO, it, emptySet()); alarms.dismiss(AlarmScheduler.TODO, it) }
        AppLog.i("Todo", "Moved ${ids.size} task(s) to the recycle bin: $ids")
    }

    /** Back from the bin, with their reminders when still to come. */
    suspend fun restore(ids: List<Long>) = lock.withLock {
        ids.chunked(CHUNK).forEach { dao.setDeleted(it, null) }
        ids.forEach { id -> dao.get(id)?.let { syncAlarm(it) } }
        AppLog.i("Todo", "Restored ${ids.size} task(s): $ids")
    }

    /** Erases tasks in the bin: [ids], and every one deleted before [before]. */
    suspend fun erase(ids: List<Long>, before: Long = 0) = lock.withLock {
        val gone = if (ids.isEmpty()) dao.toErase(ids, before) else ids.chunked(CHUNK).flatMap { dao.toErase(it, before) }
        gone.forEach { dao.delete(it) }
        if (gone.isNotEmpty()) AppLog.i("Todo", "Erased from the recycle bin: $gone")
    }

    fun observeDeleted(): Flow<List<Todo>> = dao.observeDeleted()

    /** The ticked tasks (for "Clear done", which moves them to the bin). */
    suspend fun doneIds(): List<Long> = dao.doneIds()

    private suspend fun syncAlarm(todo: Todo) = alarms.sync(AlarmScheduler.TODO, todo.id, setOfNotNull(todo.reminderTime()))
}

/** A task's reminder time: none when it doesn't remind, is done or is in the recycle bin. */
fun Todo.reminderTime(): LocalDateTime? = if (deletedAt != null) null else AlarmPlanner.todoTime(remind, done, dueDate, dueTime)

/** Each field the editor changed ([edited] differs from [base]) is taken from [edited]; every other from [current]. */
fun mergeTodo(base: Todo, edited: Todo, current: Todo): Todo {
    fun <T> pick(field: (Todo) -> T): T = if (field(edited) != field(base)) field(edited) else field(current)
    return current.copy(
        title = pick { it.title }, details = pick { it.details },
        done = pick { it.done }, doneAt = pick { it.doneAt }, dueDate = pick { it.dueDate }, dueTime = pick { it.dueTime },
        remind = pick { it.remind }, sound = pick { it.sound }, repeat = pick { it.repeat },
        repeatEvery = pick { it.repeatEvery }, priority = pick { it.priority },
    )
}

/**
 * A repeating task moved on to its next date, or null when it doesn't repeat (or has no date). A monthly repeat keeps
 * its first day: Jan 31 → Feb 28 → Mar 31.
 */
fun nextDue(todo: Todo): Todo? {
    val repeat = Repeat.of(todo.repeat)
    val due = todo.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val day = if (todo.repeatDay in 1..31) todo.repeatDay else due.dayOfMonth
    val next = repeat.next(due, todo.repeatEvery, day) ?: return null
    return todo.copy(dueDate = next.toString(), repeatDay = if (repeat == Repeat.MONTHLY) day else 0)
}
