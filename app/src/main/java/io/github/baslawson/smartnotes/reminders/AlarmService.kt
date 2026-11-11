package io.github.baslawson.smartnotes.reminders

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.R
import java.time.LocalDateTime
import java.util.UUID

/**
 * Rings a reminder like an alarm clock (after Planner's AlarmService): the alarm sound, looping through silent and
 * vibrate mode, with vibration, as an ongoing notification with Stop, Snooze and Done. A timed ring (10 s, 30 s, 1 min)
 * stops by itself and leaves the reminder's normal notification; "until I stop it" gives up after [MAX_RING_MINUTES]
 * and leaves a missed notification, so a forgotten phone goes quiet. A second reminder arriving while one rings takes
 * over; the first stays as its normal notification.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var tones: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing: ReminderContent? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ringOut = Runnable { endRing(timeUp = true) }
    /** The latest start: stopping with it can't cut off a ring whose start is already on its way. */
    private var lastStartId = 0
    private val beep = object : Runnable {
        override fun run() {
            tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_000)
            handler.postDelayed(this, 1_600L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                // A button from an alarm that already ended must not stop the one ringing now.
                if (intent.getStringExtra(EXTRA_STOP_TOKEN) == stopToken && stopToken != null) {
                    endRing(timeUp = false, keep = intent.getBooleanExtra(EXTRA_KEEP, false))
                }
                else if (ringing == null) stopSelf(startId)
            }
            ACTION_RING -> {
                val content = intent.toContent()
                if (content == null) {
                    // Started in the foreground's name: it must go into the foreground before stopping.
                    goForeground(placeholder())
                    stopEverything()
                } else startRinging(content)
            }
            else -> if (ringing == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(content: ReminderContent) {
        ringing?.let { previous -> Notifications.showReminder(this, previous, silent = true) }
        releaseSound()
        handler.removeCallbacks(ringOut)
        ringing = content
        stopToken = UUID.randomUUID().toString()
        current = Triple(content.kind, content.ownerId, content.at)
        if (!goForeground(notification(content))) {
            // Android refused the foreground (rare): the reminder is shown as its normal notification instead.
            Notifications.showReminder(this, content)
            stopEverything()
            return
        }
        startSound()
        startVibration()
        val seconds = content.sound.ringSeconds ?: 0
        handler.postDelayed(ringOut, if (seconds > 0) seconds * 1_000L else MAX_RING_MINUTES * 60_000L)
    }

    /**
     * [timeUp]: the ring ran its length (a timed one leaves its notification; "until stopped" leaves a missed one).
     * [keep]: stopped with the Stop button; a timed ring then leaves its normal notification, as in Planner.
     */
    private fun endRing(timeUp: Boolean, keep: Boolean = false) {
        val content = ringing
        if (content != null) {
            val untilStopped = (content.sound.ringSeconds ?: 0) == 0
            if (timeUp) {
                Notifications.showReminder(this, content, missed = untilStopped, silent = !untilStopped)
                AppLog.i("Reminders", "${content.kind} ${content.ownerId}: rang out" + if (untilStopped) " unanswered" else "")
            } else if (keep && !untilStopped) Notifications.showReminder(this, content, silent = true)
        }
        stopEverything()
    }

    private fun goForeground(shown: android.app.Notification): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, shown, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        true
    } catch (e: Exception) {
        AppLog.w("Reminders", "Couldn't ring in the foreground", e)
        false
    }

    private fun placeholder() = NotificationCompat.Builder(this, Notifications.ALARM_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Reminder").setSilent(true).build()

    private fun notification(c: ReminderContent): android.app.Notification =
        NotificationCompat.Builder(this, Notifications.ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.title)
            .setContentText(c.text)
            .setSubText(Notifications.whenText(c.at))
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            // Tapping opens it and stops the ringing.
            .setContentIntent(Notifications.open(this, c.route, NOTIFICATION_ID, stopToken))
            .setDeleteIntent(stopIntent(this, stopToken))
            .addAction(0, "Stop", stopIntent(this, stopToken))
            .addAction(0, "Snooze", SnoozeActivity.intent(this, c))
            .apply { if (c.canDone) addAction(0, "Done", ReminderActionReceiver.done(this@AlarmService, c)) }
            .build()

    private fun startSound() {
        // The alarm sound the phone has set, else its default alarm or ringtone, else beeps.
        val choices = listOfNotNull(
            runCatching { RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) }.getOrNull(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        ).distinct()
        play(choices, 0)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smartnotes:alarm")
            .apply { acquire(MAX_RING_MINUTES * 60_000L + 5_000L) }
    }

    private fun play(choices: List<Uri>, index: Int) {
        val uri = choices.getOrNull(index) ?: return startBeeps()
        val content = ringing
        val next = { why: String ->
            AppLog.w("Reminders", "Couldn't play $uri ($why); trying the next sound")
            handler.post { if (ringing === content && content != null) { releasePlayer(); play(choices, index + 1) } }
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setOnErrorListener { mp, what, extra -> if (player === mp) next("error $what/$extra"); true }
                setOnPreparedListener { mp -> if (player === mp) runCatching { mp.start() }.onFailure { next(it.toString()) } }
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepareAsync()
            }
        } catch (e: Exception) {
            releasePlayer()
            next(e.toString())
        }
    }

    private fun startBeeps() {
        tones = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }
            .onFailure { AppLog.w("Reminders", "No alarm sound could play", it) }.getOrNull() ?: return
        handler.post(beep)
    }

    private val vibrator: Vibrator?
        get() = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)

    private fun startVibration() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator?.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun releasePlayer() {
        player?.let { p -> player = null; runCatching { p.stop() }; p.release() }
    }

    private fun releaseSound() {
        releasePlayer()
        handler.removeCallbacks(beep)
        tones?.let { runCatching { it.stopTone() }; it.release() }
        tones = null
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopEverything() {
        handler.removeCallbacks(ringOut)
        releaseSound()
        ringing = null
        stopToken = null
        current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ringOut)
        releaseSound()
        stopToken = null
        current = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_RING = "io.github.baslawson.smartnotes.alarm.RING"
        private const val ACTION_STOP = "io.github.baslawson.smartnotes.alarm.STOP"
        const val EXTRA_STOP_TOKEN = "stopToken"
        private const val EXTRA_KEEP = "keep"
        const val MAX_RING_MINUTES = 10L
        private const val NOTIFICATION_ID = 1_000_000_001

        @Volatile private var stopToken: String? = null
        @Volatile private var current: Triple<String, Long, LocalDateTime>? = null

        fun ring(context: Context, c: ReminderContent) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_RING)
                .putExtra("kind", c.kind).putExtra("owner", c.ownerId).putExtra("at", c.at.toString())
                .putExtra("title", c.title).putExtra("text", c.text).putExtra("canDone", c.canDone)
                .putExtra("sound", c.sound.name).putExtra("route", c.route)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppLog.w("Reminders", "Couldn't start ringing; shown as a notification", e)
                Notifications.showReminder(context, c)
            }
        }

        /** Rings a test alarm with [sound] (Settings → Reminders). */
        fun test(context: Context, sound: ReminderSound) = ring(
            context,
            ReminderContent("test", 0, LocalDateTime.now().withSecond(0).withNano(0), "Test alarm",
                "This is how reminders sound", canDone = false, sound = sound, route = "settings"),
        )

        private fun stopIntent(context: Context, token: String?): PendingIntent = PendingIntent.getService(
            context, 1,
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
                .setData(Uri.parse("smartnotes://alarm-stop/${token.orEmpty()}")).putExtra(EXTRA_STOP_TOKEN, token)
                .putExtra(EXTRA_KEEP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Stops the ringing if it is for this reminder (it was done, snoozed or opened elsewhere). */
        fun stopIfRinging(context: Context, kind: String, ownerId: Long, at: LocalDateTime) {
            if (current == Triple(kind, ownerId, at)) stopToken?.let { requestStop(context, it) }
        }

        /** Stops the ringing if it is [kind] [ownerId]'s, whatever its time. */
        fun stopOwner(context: Context, kind: String, ownerId: Long) {
            val ringing = current
            if (ringing != null && ringing.first == kind && ringing.second == ownerId) stopToken?.let { requestStop(context, it) }
        }

        /** A tap on the ringing notification: stops it, if it is still the one ringing. */
        fun stopFromTap(context: Context, token: String?) {
            if (token != null && token == stopToken) requestStop(context, token)
        }

        private fun requestStop(context: Context, token: String) {
            runCatching {
                context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_STOP_TOKEN, token))
            }.onFailure { AppLog.w("Reminders", "Couldn't stop the ringing", it) }
        }

        private fun Intent.toContent(): ReminderContent? {
            val at = getStringExtra("at")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
            return ReminderContent(
                getStringExtra("kind") ?: return null, getLongExtra("owner", 0L), at,
                getStringExtra("title").orEmpty(), getStringExtra("text").orEmpty(), getBooleanExtra("canDone", false),
                ReminderSound.of(getStringExtra("sound")), getStringExtra("route") ?: "notes",
            )
        }
    }
}
