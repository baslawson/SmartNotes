package io.github.baslawson.smartnotes

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import io.github.baslawson.smartnotes.data.NoteDatabase
import io.github.baslawson.smartnotes.data.NoteRepository
import io.github.baslawson.smartnotes.data.ShoppingRepository
import io.github.baslawson.smartnotes.reminders.Notifications
import io.github.baslawson.smartnotes.reminders.WorkReminderScheduler
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
    val notes by lazy { NoteRepository(database.notes(), WorkReminderScheduler(this)) }
    val shopping by lazy { ShoppingRepository(database.shopping()) }

    val settings by lazy { AppSettings(getSharedPreferences("settings", MODE_PRIVATE)) }

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
        Notifications.createChannel(this)
        // Once per installed version, schedule every reminder again, in case jobs were lost.
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val version = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        if (prefs.getLong("scheduledFor", -1) != version) {
            appScope.launch {
                notes.rescheduleAll()
                AppLog.i("Reminders", "Rescheduled all reminders for version $version")
                prefs.edit().putLong("scheduledFor", version).apply()
            }
        }
    }
}
