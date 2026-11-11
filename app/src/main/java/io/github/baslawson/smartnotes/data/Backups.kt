package io.github.baslawson.smartnotes.data

import android.content.Context
import androidx.core.net.toUri
import androidx.core.content.edit
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.reminders.AlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Settings → Backup → Automatic backup. */
enum class AutoBackup(val days: Long, val label: String) {
    OFF(0, "Off"), DAILY(1, "Daily"), WEEKLY(7, "Weekly");

    companion object {
        fun of(name: String?): AutoBackup = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** What Settings → Backup shows about automatic backups. */
data class BackupStatus(
    val folder: String? = null,
    val folderName: String? = null,
    val auto: AutoBackup = AutoBackup.OFF,
    val lastAt: Long = 0,
    val lastError: String? = null,
)

/**
 * Backups of all the app's data (see BackupFormat): to a file the user picks, automatically to a folder they chose,
 * and restoring one, which replaces everything after saving a safety copy that "Undo last restore" puts back.
 */
class Backups(
    private val context: Context,
    private val db: NoteDatabase,
    private val settings: AppSettings,
    private val alarms: AlarmScheduler,
    private val appVersion: () -> String,
    /** After a restore: forget where the app was left (those ids may now be other notes, tasks or lists). */
    private val onRestored: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()
    private val _busy = MutableStateFlow(false)
    /** A backup or restore is running (Settings → Backup's buttons wait). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _restores = MutableStateFlow(0L)
    /** Raised by every restore and undo: open editors hold old copies then, and close without saving. */
    val restores: StateFlow<Long> = _restores.asStateFlow()

    /** One at a time, shown as busy. */
    private suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock {
        _busy.value = true
        try { block() } finally { _busy.value = false }
    }

    /** Everything, read in one go so it is consistent. */
    suspend fun snapshot(): BackupData = db.withTransaction {
        val lists = db.shopping().everyList()
        val listIds = lists.map { it.id }.toSet() + 1L
        BackupData(
            createdAt = clock.millis(), appVersion = appVersion(),
            notes = db.notes().everyNote(), todos = db.todos().all(), lists = lists,
            // An item whose list is gone can't be shown anyway; left out, so the file is one this app reads back.
            items = db.shopping().allItems().filter { it.listId in listIds }, history = db.shopping().allHistory(),
            settings = settings.export(),
            aisles = db.shopping().allAisles(), staples = db.shopping().allStaples(),
        )
    }

    /** "SmartNotes-backup-2026-11-11-150312.json" */
    fun fileName(): String = BackupFormat.FILE_PREFIX + now() + ".json"

    /** "SmartNotes-auto-2026-11-11-150312.json": only these are pruned from the folder, never backups made by hand. */
    private fun autoFileName(): String = BackupFormat.AUTO_PREFIX + now() + ".json"

    private fun now() = NAME_TIME.format(Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.systemDefault()))

    /** Back up now: to [uri], a file the user picked. */
    suspend fun exportTo(uri: Uri): BackupData = exclusive {
        val data = snapshot()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BackupFormat.write(data).toByteArray()) }
                ?: throw BackupException("Couldn't write the backup file.")
        }
        AppLog.i("Backup", "Backed up to a file: ${data.summary()}")
        data
    }

    /** Reads and checks a backup file the user picked (nothing is changed yet). */
    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw BackupException("This file is too big to be a SmartNotes backup.")
            String(bytes)
        } ?: throw BackupException("Couldn't read the file.")
        BackupFormat.read(text)
    }

    /** Replaces everything with [data], after saving the current data as a safety copy (see [undoRestore]). */
    suspend fun restore(data: BackupData) = exclusive {
        saveSafetyCopy()
        replaceAll(data)
        AppLog.i("Backup", "Restored a backup from ${data.createdAt}: ${data.summary()}")
    }

    /** Whether a restore can be undone (a safety copy from before it is kept). */
    fun canUndoRestore(): Boolean = safetyCopies().isNotEmpty()

    /** Puts back the data from before the last restore. */
    suspend fun undoRestore() = exclusive {
        val file = safetyCopies().firstOrNull() ?: return@exclusive
        val data = withContext(Dispatchers.IO) { BackupFormat.read(file.readText()) }
        replaceAll(data)
        file.delete()
        AppLog.i("Backup", "Undid the last restore: ${data.summary()}")
    }

    // ---------- Automatic backups ----------

    /** The folder for automatic backups (a tree the user picked; null to stop). */
    fun setFolder(uri: Uri?, name: String?) {
        val old = prefs.getString(KEY_FOLDER, null)
        // The new folder's permission first: if it can't be had, the old folder stays as it was.
        if (uri != null) context.contentResolver.takePersistableUriPermission(uri, RW)
        if (old != null && old != uri?.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(old.toUri(), RW) }
        }
        prefs.edit { putString(KEY_FOLDER, uri?.toString()); putString(KEY_FOLDER_NAME, name); remove(KEY_ERROR) }
        _status.value = readStatus()
        schedule(replace = true)
    }

    fun setAuto(auto: AutoBackup) {
        prefs.edit { putString(KEY_AUTO, auto.name) }
        _status.value = readStatus()
        schedule(replace = true)
        AppLog.i("Backup", "Automatic backup ${auto.label}")
    }

    /** Sets the background job to match the settings ([replace]: when they just changed). Run at every start. */
    fun schedule(replace: Boolean = false) {
        val s = _status.value
        val work = WorkManager.getInstance(context)
        if (s.auto == AutoBackup.OFF || s.folder == null) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(s.auto.days, TimeUnit.DAYS).build()
        work.enqueueUniquePeriodicWork(WORK, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One automatic backup into the chosen folder, keeping the newest [KEEP_AUTO]. */
    suspend fun autoBackup() = exclusive {
        val folder = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse) ?: return@exclusive
        try {
            val data = snapshot()
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val treeId = DocumentsContract.getTreeDocumentId(folder)
                val dir = DocumentsContract.buildDocumentUriUsingTree(folder, treeId)
                val file = DocumentsContract.createDocument(resolver, dir, "application/json", autoFileName())
                    ?: throw BackupException("Couldn't make a file in the backup folder.")
                try {
                    resolver.openOutputStream(file, "wt")?.use { it.write(BackupFormat.write(data).toByteArray()) }
                        ?: throw BackupException("Couldn't write the backup file.")
                } catch (e: Exception) {
                    // No half-written file left behind to count as a backup.
                    runCatching { DocumentsContract.deleteDocument(resolver, file) }
                    throw e
                }
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, treeId)
                val found = mutableMapOf<String, String>()
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { c -> while (c.moveToNext()) found[c.getString(1)] = c.getString(0) }
                BackupFormat.toPrune(found.keys.toList(), KEEP_AUTO).forEach { name ->
                    runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, found.getValue(name))) }
                }
            }
            prefs.edit { putLong(KEY_LAST, clock.millis()); remove(KEY_ERROR) }
            AppLog.i("Backup", "Automatic backup: ${data.summary()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = (e as? BackupException)?.message ?: "The backup folder can't be used (moved or removed?). Choose it again."
            prefs.edit { putString(KEY_ERROR, message) }
            AppLog.w("Backup", "Automatic backup failed", e)
        }
        _status.value = readStatus()
    }

    // ---------- Inside ----------

    private suspend fun replaceAll(data: BackupData) {
        _restores.value++
        alarms.clearAll()
        // Whatever happens below (a full disk rolls the data back), the alarms are set again from what is there.
        try { replaceData(data) } finally { withContext(NonCancellable) { alarms.restoreAll(boot = false) } }
        onRestored()
    }

    private suspend fun replaceData(data: BackupData) {
        db.withTransaction {
            db.notes().deleteAll()
            db.todos().deleteAll()
            db.shopping().deleteAllItems()
            db.shopping().deleteAllLists()
            db.shopping().deleteAllHistory()
            db.notes().insertAll(data.notes)
            db.todos().insertAll(data.todos)
            // List 1 ("Anytime") always exists.
            val lists = if (data.lists.any { it.id == 1L }) data.lists
            else data.lists + ShoppingList(id = 1, name = "Shopping", createdAt = clock.millis())
            db.shopping().insertLists(lists.map { if (it.id == 1L) it.copy(deletedAt = null) else it })
            db.shopping().insertItems(data.items)
            db.shopping().insertHistory(data.history)
            // A file from before aisles and staples (0.8.0) leaves the ones there are.
            data.aisles?.let { db.shopping().deleteAllAisles(); db.shopping().setAisles(it) }
            data.staples?.let { db.shopping().deleteAllStaples(); db.shopping().putStaples(it) }
        }
        settings.import(data.settings)
    }

    /** The current data, kept inside the app before a restore; the newest [KEEP_SAFETY]. */
    private suspend fun saveSafetyCopy() {
        val data = snapshot()
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            File(dir, "before-restore-" + fileName()).writeText(BackupFormat.write(data))
            safetyCopies().drop(KEEP_SAFETY).forEach { it.delete() }
        }
    }

    /** Newest first. */
    private fun safetyCopies(): List<File> =
        File(context.filesDir, "backups").listFiles { f -> f.name.startsWith("before-restore-") }?.sortedByDescending { it.name }.orEmpty()

    private fun readStatus() = BackupStatus(
        folder = prefs.getString(KEY_FOLDER, null), folderName = prefs.getString(KEY_FOLDER_NAME, null),
        auto = AutoBackup.of(prefs.getString(KEY_AUTO, null)), lastAt = prefs.getLong(KEY_LAST, 0),
        lastError = prefs.getString(KEY_ERROR, null),
    )

    private companion object {
        val NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val WORK = "auto-backup"
        const val KEEP_AUTO = 5
        const val KEEP_SAFETY = 3
        const val MAX_BYTES = 50 * 1024 * 1024
        const val KEY_FOLDER = "folder"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_AUTO = "auto"
        const val KEY_LAST = "last"
        const val KEY_ERROR = "error"
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes is Android 13+). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Settings → Backup → Automatic backup, run by WorkManager. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SmartNotesApp).backups.autoBackup()
        return Result.success()
    }
}
