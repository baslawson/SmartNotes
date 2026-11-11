package io.github.baslawson.smartnotes.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.SmartNotesApp
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The Snooze button: a small dialog over whatever is on screen, with Planner's choices. */
class SnoozeActivity : ComponentActivity() {
    /** Set once a snooze time is picked; otherwise closing the dialog in any way keeps the reminder. */
    private var chosen = false
    private var keepReminder: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND)
        val ownerId = intent.getLongExtra(EXTRA_OWNER, 0L)
        val missed = intent.getBooleanExtra(EXTRA_MISSED, false)
        val at = intent.getStringExtra(EXTRA_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (kind == null || at == null) { finish(); return }
        // Snoozing stops the ringing straight away; the dialog then asks for how long.
        AlarmService.stopIfRinging(this, kind, ownerId, at)
        val app = application as SmartNotesApp
        val now = LocalDateTime.now()
        // Closed without a choice (Cancel, outside the dialog, Home): the reminder stays as its normal (quiet)
        // notification, not lost. Done in onDestroy, which every way of closing reaches.
        keepReminder = {
            if (kind != "test") app.appScope.launch {
                app.alarms.content(kind, ownerId, at)?.let { Notifications.showReminder(app, it, missed = missed, silent = true) }
            }
        }
        val cancel = { finish() }
        setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = cancel,
                    title = { Text("Snooze reminder") },
                    text = {
                        Column {
                            SnoozeChoice.entries.forEach { choice ->
                                TextButton(onClick = {
                                    chosen = true
                                    val until = choice.until(LocalDateTime.now())
                                    Notifications.cancel(this@SnoozeActivity, kind, ownerId, at)
                                    if (kind != "test") app.appScope.launch { app.alarms.snooze(kind, ownerId, at, until) }
                                    else AppLog.i("Reminders", "Test alarm snoozed (nothing to remind)")
                                    Toast.makeText(this@SnoozeActivity, "Snoozed: ${choice.label(now, io.github.baslawson.smartnotes.TimeStyle.of(this@SnoozeActivity))}", Toast.LENGTH_SHORT).show()
                                    finish()
                                }) { Text(choice.label(now, io.github.baslawson.smartnotes.TimeStyle.of(this@SnoozeActivity))) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!chosen && !isChangingConfigurations) keepReminder?.invoke()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_AT = "at"
        private const val EXTRA_MISSED = "missed"

        /** [missed]: the button is on a "Missed:" notification, which stays one if the dialog is cancelled. */
        fun intent(context: Context, c: ReminderContent, missed: Boolean = false): PendingIntent = PendingIntent.getActivity(
            context, Notifications.id(c.kind, c.ownerId, c.at) + 1,
            Intent(context, SnoozeActivity::class.java)
                .setData(Uri.parse("smartnotes://snooze/${c.kind}/${c.ownerId}/${c.at}"))
                .putExtra(EXTRA_KIND, c.kind).putExtra(EXTRA_OWNER, c.ownerId).putExtra(EXTRA_AT, c.at.toString())
                .putExtra(EXTRA_MISSED, missed)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
