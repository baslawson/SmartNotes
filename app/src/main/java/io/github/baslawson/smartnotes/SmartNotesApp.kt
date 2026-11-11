package io.github.baslawson.smartnotes

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import io.github.baslawson.smartnotes.data.Backups
import io.github.baslawson.smartnotes.data.NoteDatabase
import io.github.baslawson.smartnotes.data.NoteRepository
import io.github.baslawson.smartnotes.data.RecycleBin
import io.github.baslawson.smartnotes.data.ShoppingRepository
import io.github.baslawson.smartnotes.data.TodoRepository
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import io.github.baslawson.smartnotes.reminders.Notifications
import io.github.baslawson.smartnotes.data.ReleaseApi
import io.github.baslawson.smartnotes.data.Updates
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class SmartNotesApp : Application() {
    /** For saves that must finish even when the screen that started them is gone. */
    // A failed background save is logged rather than closing the app.
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> AppLog.e("App", "Background task failed", e) }
    )

    private val database by lazy { NoteDatabase.create(this) }
    /** Every reminder (shopping lists, tasks) as an exact alarm. */
    val alarms by lazy { AlarmScheduler(this, database, settings) }
    val notes by lazy { NoteRepository(database.notes()) }
    val shopping by lazy { ShoppingRepository(database.shopping(), alarms) }
    val todos by lazy { TodoRepository(database.todos(), alarms) }
    /** Deleted notes, tasks and shopping lists, kept for Settings → Recycle bin's time. */
    val bin by lazy { RecycleBin(notes, todos, shopping, settings) }

    val settings by lazy { AppSettings(getSharedPreferences("settings", MODE_PRIVATE)) }
    /** Backups to a file or a folder, and restoring them (Settings → Backup). */
    val backups by lazy {
        Backups(this, database, settings, alarms, { versionName }, onRestored = {
            state.forgetPlaces()
            getSharedPreferences("lists", MODE_PRIVATE).edit().remove("shopping_list").apply()
        })
    }
    /** Where the app was last (screen, search, scrolling), to open there again. */
    val state by lazy { AppState(getSharedPreferences("state", MODE_PRIVATE)) }

    val versionName: String by lazy { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" }
    val versionCode: Long by lazy { PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)) }

    /** New versions on GitHub (Settings → Updates). Only the release app updates itself; the debug build is another app. */
    val updates: Updates by lazy {
        Updates(
            Updates.prefs(this), ReleaseApi(OkHttpClient()), installed = versionName,
            folder = File(cacheDir, "updates"), supported = packageName == Updates.RELEASE_ID,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // In noBackupFilesDir: the log never goes into Android's cloud backup or a phone-to-phone transfer.
        AppLog.init(File(noBackupFilesDir, "logs"))
        File(filesDir, "logs").deleteRecursively()
        val crashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            AppLog.e("Crash", "Uncaught on ${thread.name}", e)
            crashHandler?.uncaughtException(thread, e)
        }
        AppLog.i(
            "App", "Started $packageName $versionName ($versionCode), Android ${android.os.Build.VERSION.RELEASE} " +
                "(API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
        Notifications.createChannels(this)
        // Every start sets the alarms again: a force-stop clears them without telling the app. Only alarms still to go
        // off are in the ledger, so this is cheap, and setting one again just replaces it.
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val version = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        appScope.launch {
            if (prefs.getLong("scheduledFor", -1) != version) {
                // Version 0.1.0 scheduled note reminders with WorkManager: those jobs go (by their worker's class name,
                // which WorkManager adds as a tag), exact alarms replace them. Other jobs (automatic backups) stay.
                runCatching {
                    androidx.work.WorkManager.getInstance(this@SmartNotesApp)
                        .cancelAllWorkByTag("io.github.baslawson.smartnotes.reminders.ReminderWorker")
                }
                prefs.edit().putLong("scheduledFor", version).apply()
            }
            // The first start since the phone restarted applies the restart rules (missed, not rung late), whether it
            // comes before or after BOOT_COMPLETED (which can come long after, at the first unlock).
            val bootCount = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
            val saved = prefs.getInt("restoredBoot", -1)
            val soonAfterBoot = android.os.SystemClock.elapsedRealtime() < 10 * 60_000L
            // Nothing saved yet (the first start of this version): only counts as a restart soon after one.
            val firstSinceBoot = if (bootCount >= 0) saved != bootCount && (saved >= 0 || soonAfterBoot) else soonAfterBoot
            alarms.restoreAll(boot = firstSinceBoot)
            if (bootCount >= 0) prefs.edit().putInt("restoredBoot", bootCount).apply()
            bin.purge()
            backups.schedule()
        }
    }
}
