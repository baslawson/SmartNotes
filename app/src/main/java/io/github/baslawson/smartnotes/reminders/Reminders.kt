package io.github.baslawson.smartnotes.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import androidx.work.workDataOf
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.MainActivity
import io.github.baslawson.smartnotes.R
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.parse.NoteParser
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface ReminderScheduler {
    /** Makes the note's scheduled reminders match the ones now in its text. */
    suspend fun reschedule(note: Note)
    fun cancel(noteId: Long)
}

/**
 * One WorkManager job per note and reminder time. WorkManager keeps jobs across reboots, but Android may run them
 * a few minutes late when the phone is idle (no exact alarms in this version).
 */
class WorkReminderScheduler(context: Context) : ReminderScheduler {
    private val work = WorkManager.getInstance(context)

    /**
     * Jobs for reminders still in the note (and not ticked) are kept as they are, so a reminder that Android is running
     * a little late isn't lost when the note is saved; jobs for reminders gone from the note are cancelled; future
     * reminders without a job get one. Past reminders never get a new job (that would notify again).
     */
    override suspend fun reschedule(note: Note) {
        val now = LocalDateTime.now()
        val wanted = NoteParser.reminders(note.body).filter { !it.done }.map { it.at }.toSet()
        val pending = work.getWorkInfosByTagFlow(tag(note.id)).first().filter { !it.state.isFinished }
        val pendingTimes = mutableSetOf<LocalDateTime>()
        pending.forEach { info ->
            val at = info.tags.firstOrNull { it.startsWith(AT_TAG) }?.removePrefix(AT_TAG)
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (at != null && at in wanted) pendingTimes += at else work.cancelWorkById(info.id)
        }
        val times = wanted.filter { it.isAfter(now) && it !in pendingTimes }.sorted()
        if (times.isNotEmpty()) AppLog.i("Reminders", "Note ${note.id}: scheduled ${times.joinToString()}")
        times.forEach { at ->
                val delay = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .addTag(tag(note.id))
                    .addTag(AT_TAG + at)
                    .setInputData(workDataOf(ReminderWorker.NOTE_ID to note.id, ReminderWorker.AT to at.toString()))
                    .build()
                work.enqueueUniqueWork("reminder-${note.id}-$at", ExistingWorkPolicy.KEEP, request)
            }
    }

    override fun cancel(noteId: Long) {
        work.cancelAllWorkByTag(tag(noteId))
    }

    private fun tag(noteId: Long) = "note-$noteId"

    private companion object {
        const val AT_TAG = "at:"
    }
}

/** Shows the reminder, after checking the note still has it (it may have been edited, ticked off or deleted). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(NOTE_ID, 0)
        val at = inputData.getString(AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return Result.success()
        val app = applicationContext as SmartNotesApp
        val note = app.notes.get(noteId)
            ?: return Result.success().also { AppLog.i("Reminders", "Note $noteId is gone; reminder $at skipped") }
        val due = NoteParser.reminders(note.body).filter { it.at == at && !it.done }
        AppLog.i(
            "Reminders",
            "Note $noteId \"${NoteParser.displayTitle(note.title, note.body)}\" at $at: " +
                when {
                    due.isEmpty() -> "no longer in the note or ticked off, skipped"
                    !Notifications.canNotify(applicationContext) -> "due, but notifications are not allowed"
                    else -> "notified"
                },
        )
        if (due.isNotEmpty()) {
            Notifications.show(
                applicationContext, note,
                text = due.joinToString("\n") { it.text }.ifBlank { "Reminder" },
                id = (noteId * 31 + at.hashCode()).toInt(),
            )
        }
        return Result.success()
    }

    companion object {
        const val NOTE_ID = "noteId"
        const val AT = "at"
    }
}

object Notifications {
    private const val CHANNEL = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Dates written in notes, like @2026-11-20 14:00"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, note: Note, text: String, id: Int) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(NoteParser.displayTitle(note.title, note.body))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here; nothing to show then.
            AppLog.w("Reminders", "Notification not allowed", e)
        }
    }
}
