package io.github.baslawson.smartnotes.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.MainActivity
import io.github.baslawson.smartnotes.R
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.data.AlarmEntry
import io.github.baslawson.smartnotes.data.NoteDatabase
import io.github.baslawson.smartnotes.data.reminderTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What a reminder shows, worked out when it goes off (so it is always current). [canDone]: it has a Done button. */
data class ReminderContent(
    val kind: String,
    val ownerId: Long,
    val at: LocalDateTime,
    val title: String,
    val text: String,
    val canDone: Boolean,
    val sound: ReminderSound,
    /** The screen to open: a navigation route. */
    val route: String,
)

/**
 * Reminders for shopping lists and tasks, as in Planner: exact alarms (on time when the phone
 * sleeps), set again after a restart or a clock or time-zone change, and missed ones shown after a restart.
 * Every alarm set is a row in the alarms table (see AlarmEntry); its id is the alarm's request code.
 */
class AlarmScheduler(private val context: Context, private val db: NoteDatabase, private val settings: AppSettings) {
    private val alarms = db.alarms()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    // Notes had @date reminders until 0.2.0; any of theirs still set are cancelled when alarms are next restored.

    /** Makes one owner's alarms match [wanted] (see AlarmPlanner.plan). */
    suspend fun sync(kind: String, ownerId: Long, wanted: Set<LocalDateTime>) = lock.withLock { syncLocked(kind, ownerId, wanted) }

    private suspend fun syncLocked(kind: String, ownerId: Long, wanted: Set<LocalDateTime>) {
        val rows = alarms.of(kind, ownerId)
        val plan = AlarmPlanner.plan(wanted, rows.mapNotNull { it.toLedger() }, LocalDateTime.now())
        // A row that can't be read is of no use either.
        val unreadable = rows.filter { it.toLedger() == null }.map { it.id }
        (plan.cancel + unreadable).forEach { id -> cancelAlarm(id); alarms.delete(id) }
        plan.add.forEach { at ->
            val entry = AlarmEntry(kind = kind, ownerId = ownerId, at = at.toString(), fireAt = at.toString())
            set(entry.copy(id = alarms.insert(entry)))
        }
        if (plan.add.isNotEmpty() || plan.cancel.isNotEmpty()) {
            AppLog.i("Reminders", "$kind $ownerId: set ${plan.add.joinToString().ifEmpty { "-" }}, removed ${plan.cancel.size}")
        }
    }

    /** [sync] with the owner's reminders as stored now, read under the lock. */
    private suspend fun resync(kind: String, ownerId: Long) = lock.withLock {
        val wanted: Set<LocalDateTime> = when (kind) {
            LIST -> db.shopping().list(ownerId)?.let { setOfNotNull(it.reminderTime()) }
            TODO -> db.todos().get(ownerId)?.let { setOfNotNull(it.reminderTime()) }
            else -> null
        } ?: emptySet()
        syncLocked(kind, ownerId, wanted)
    }

    /** Snoozes the reminder [kind]/[ownerId]/[at] until [until]: its alarm goes off again then. */
    suspend fun snooze(kind: String, ownerId: Long, at: LocalDateTime, until: LocalDateTime) = lock.withLock {
        val existing = alarms.find(kind, ownerId, at.toString())
        val entry = existing?.copy(fireAt = until.toString())
            ?: AlarmEntry(kind = kind, ownerId = ownerId, at = at.toString(), fireAt = until.toString())
        val saved = if (existing == null) entry.copy(id = alarms.insert(entry)) else entry.also { alarms.update(it) }
        set(saved)
        AppLog.i("Reminders", "$kind $ownerId at $at snoozed until $until")
    }

    /** The alarm [id] went off: its row goes, and what it should show now (null: nothing, it is no longer wanted). */
    suspend fun fired(id: Long): ReminderContent? {
        val entry = lock.withLock { alarms.get(id)?.also { alarms.delete(id) } } ?: return null
        val at = runCatching { LocalDateTime.parse(entry.at) }.getOrNull() ?: return null
        return content(entry.kind, entry.ownerId, at)
    }

    /**
     * Sets every alarm again (Android forgets them at a restart, a force-stop clears them, and a clock or time-zone
     * change moves them). An alarm due before the phone last started was missed while it was off: shown as missed, not
     * rung late, whichever comes first after a restart, the app starting or [boot] (BOOT_COMPLETED).
     */
    suspend fun restoreAll(boot: Boolean) {
        val missed = mutableListOf<ReminderContent>()
        lock.withLock {
            val now = LocalDateTime.now()
            val started = now.minus(java.time.Duration.ofMillis(android.os.SystemClock.elapsedRealtime()))
            for (row in alarms.all()) {
                val ledger = row.toLedger()
                if (ledger == null) { alarms.delete(row.id); continue }
                // Due while the phone was off (or, at a restart, late by over a minute): missed. Otherwise set again
                // (a past time goes off at once).
                if (ledger.fireAt.isBefore(started) || boot && ledger.fireAt.isBefore(now.minusMinutes(1))) {
                    alarms.delete(row.id)
                    content(row.kind, row.ownerId, ledger.at)?.let(missed::add)
                } else set(row)
            }
        }
        missed.forEach { Notifications.showReminder(context, it, missed = true, silent = true) }
        if (missed.isNotEmpty()) AppLog.i("Reminders", "After restart: ${missed.size} missed reminder(s) shown")
        // Note reminders are gone (0.2.0): any still in the ledger are cancelled.
        alarms.all().filter { it.kind == NOTE }.map { it.ownerId }.distinct().forEach { resync(NOTE, it) }
        // Owners whose reminders aren't all in the ledger get them now. Each one is read again under the lock, so a
        // save made meanwhile can't be undone by an older copy.
        db.shopping().everyList().forEach { resync(LIST, it.id) }
        db.todos().all().forEach { resync(TODO, it.id) }
        AppLog.i("Reminders", "All alarms set again (${if (boot) "restart" else "update or clock change"})")
    }

    /** What the reminder shows now, or null when its owner no longer has it (edited, ticked off, deleted). */
    suspend fun content(kind: String, ownerId: Long, at: LocalDateTime): ReminderContent? {
        val setting = settings.reminderSound.value
        return when (kind) {
            LIST -> {
                val list = db.shopping().list(ownerId) ?: return null
                if (list.reminderTime() != at) return null
                val open = db.shopping().openItems(ownerId)
                val text = if (open.isEmpty()) "Nothing left to buy"
                else "${open.size} to buy: " + open.joinToString(", ") { if (it.quantity.isEmpty()) it.name else "${it.quantity} ${it.name}" }
                ReminderContent(
                    kind, ownerId, at, "Shopping · " + settings.dateStyle.value.dayLabel(at.toLocalDate()), text, canDone = true,
                    sound = ReminderSound.of(list.sound).resolve(setting), route = "shopping?list=$ownerId",
                )
            }
            TODO -> {
                val todo = db.todos().get(ownerId) ?: return null
                if (todo.reminderTime() != at) return null
                ReminderContent(
                    kind, ownerId, at, todo.title, todo.details.lineSequence().firstOrNull { it.isNotBlank() } ?: "Task",
                    canDone = true, sound = ReminderSound.of(todo.sound).resolve(setting), route = "todo/$ownerId",
                )
            }
            else -> null
        }
    }

    private fun set(entry: AlarmEntry) {
        val fireAt = runCatching { LocalDateTime.parse(entry.fireAt) }.getOrNull() ?: return
        val trigger = AlarmPlanner.millis(fireAt)
        val pending = pending(entry.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (e: SecurityException) {
            AppLog.w("Reminders", "Exact alarm not allowed; set inexact", e)
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
        } catch (e: IllegalStateException) {
            AppLog.e("Reminders", "Android refused the alarm (too many alarms)", e)
        }
    }

    /** Before a backup is restored: every alarm, notification and ringing goes (they are set again from the new data). */
    suspend fun clearAll() {
        lock.withLock {
            alarms.all().forEach { cancelAlarm(it.id) }
            alarms.deleteAll()
        }
        AlarmService.stopRinging(context)
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
    }

    /** Its reminder gone (to the recycle bin): stops it ringing and removes its notifications. */
    fun dismiss(kind: String, ownerId: Long) {
        AlarmService.stopOwner(context, kind, ownerId)
        Notifications.cancelOwner(context, kind, ownerId)
    }

    private fun cancelAlarm(id: Long) {
        pending(id, PendingIntent.FLAG_NO_CREATE)?.let { manager.cancel(it); it.cancel() }
    }

    private fun pending(id: Long, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, id.toInt(),
        // The data makes each alarm its own; extras don't take part in matching.
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("smartnotes://alarm/$id")).putExtra(EXTRA_ALARM_ID, id),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun AlarmEntry.toLedger(): LedgerRow? {
        val a = runCatching { LocalDateTime.parse(at) }.getOrNull() ?: return null
        val f = runCatching { LocalDateTime.parse(fireAt) }.getOrNull() ?: return null
        return LedgerRow(id, a, f)
    }

    companion object {
        const val NOTE = "note"
        const val LIST = "list"
        const val TODO = "todo"
        const val EXTRA_ALARM_ID = "alarmId"
    }
}

/** An alarm went off: shows its reminder, ringing it through AlarmService when its sound rings. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
        val app = context.applicationContext as SmartNotesApp
        val result = goAsync()
        app.appScope.launch {
            try {
                val content = app.alarms.fired(id)
                if (content == null) {
                    AppLog.i("Reminders", "Alarm $id: no longer wanted, skipped")
                } else {
                    AppLog.i("Reminders", "${content.kind} ${content.ownerId} at ${content.at}: ${content.sound.label}")
                    if (content.sound.rings && Notifications.canNotify(context)) AlarmService.ring(context, content)
                    else Notifications.showReminder(context, content)
                }
            } finally {
                result.finish()
            }
        }
    }
}

/** The Done button: a note's task line is ticked, a to-do task is done (a repeating one moves on), a list's is dismissed. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val ownerId = intent.getLongExtra(EXTRA_OWNER, 0L)
        val at = intent.getStringExtra(EXTRA_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return
        val app = context.applicationContext as SmartNotesApp
        Notifications.cancel(context, kind, ownerId, at)
        AlarmService.stopIfRinging(context, kind, ownerId, at)
        val result = goAsync()
        app.appScope.launch {
            try {
                when (kind) {
                    AlarmScheduler.TODO -> app.todos.setDone(ownerId, true, onlyIfDueAt = at)
                    else -> Unit
                }
                AppLog.i("Reminders", "Done: $kind $ownerId at $at")
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_OWNER = "owner"
        const val EXTRA_AT = "at"

        fun done(context: Context, c: ReminderContent): PendingIntent = PendingIntent.getBroadcast(
            context, Notifications.id(c.kind, c.ownerId, c.at),
            Intent(context, ReminderActionReceiver::class.java)
                .setData(Uri.parse("smartnotes://done/${c.kind}/${c.ownerId}/${c.at}"))
                .putExtra(EXTRA_KIND, c.kind).putExtra(EXTRA_OWNER, c.ownerId).putExtra(EXTRA_AT, c.at.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Sets every alarm again after a restart, an app update, or a clock or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        val app = context.applicationContext as SmartNotesApp
        val result = goAsync()
        app.appScope.launch {
            try {
                AppLog.i("Reminders", "Received $action")
                app.alarms.restoreAll(boot = action == Intent.ACTION_BOOT_COMPLETED)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

object Notifications {
    private const val CHANNEL = "reminders"
    /** The ringing alarm's channel: silent, since AlarmService plays the alarm sound itself. */
    const val ALARM_CHANNEL = "alarm"
    private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders from notes, shopping lists and to-do tasks"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL, "Ringing reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders that ring with the alarm sound (the sound itself is set per reminder)"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun id(kind: String, ownerId: Long, at: LocalDateTime): Int = "$kind:$ownerId:$at".hashCode()

    fun whenText(at: LocalDateTime): String = WHEN.format(at)

    fun open(context: Context, route: String, requestCode: Int, stopToken: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("smartnotes://open/$requestCode/${stopToken.orEmpty()}"))
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .putExtra(AlarmService.EXTRA_STOP_TOKEN, stopToken)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The reminder's own notification, with Open, Snooze and (where it means something) Done. */
    fun showReminder(context: Context, c: ReminderContent, missed: Boolean = false, silent: Boolean = false) {
        if (!canNotify(context)) {
            AppLog.w("Reminders", "${c.kind} ${c.ownerId}: due, but notifications are not allowed")
            return
        }
        val id = id(c.kind, c.ownerId, c.at)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle((if (missed) "Missed: " else "") + c.title)
            .setContentText(c.text)
            .setSubText(whenText(c.at))
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(open(context, c.route, id))
            .apply { if (c.canDone) addAction(0, "Done", ReminderActionReceiver.done(context, c)) }
            .addAction(0, "Snooze", SnoozeActivity.intent(context, c, missed))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag(c.kind, c.ownerId), id, notification)
        } catch (e: SecurityException) {
            AppLog.w("Reminders", "Notification not allowed", e)
        }
    }

    fun cancel(context: Context, kind: String, ownerId: Long, at: LocalDateTime) {
        NotificationManagerCompat.from(context).cancel(tag(kind, ownerId), id(kind, ownerId, at))
    }

    /** Every reminder notification of one owner (a task or list moved to the recycle bin, say). */
    fun cancelOwner(context: Context, kind: String, ownerId: Long) {
        val tag = tag(kind, ownerId)
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        runCatching { manager.activeNotifications.filter { it.tag == tag }.forEach { manager.cancel(it.tag, it.id) } }
    }

    /** Groups an owner's notifications, so they can all be removed together. */
    private fun tag(kind: String, ownerId: Long) = "$kind:$ownerId"
}
