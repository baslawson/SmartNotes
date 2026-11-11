package io.github.baslawson.smartnotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.baslawson.smartnotes.data.BackupException
import io.github.baslawson.smartnotes.data.BackupData
import io.github.baslawson.smartnotes.data.AutoBackup
import androidx.compose.runtime.LaunchedEffect
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.ThemeMode
import io.github.baslawson.smartnotes.data.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt

private const val COPY_LIMIT = 200_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onBin: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartNotesApp
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Heading("About")
            Text("SmartNotes ${app.versionName} (build ${app.versionCode})")
            if (!app.updates.supported) Text("Debug build (${context.packageName})", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            AppearanceSection(app.settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            RemindersSection(app.settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            BinSection(app.settings, onBin)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            BackupSection(app)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            UpdatesSection(app)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LogSection(app)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Appearance ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(settings: AppSettings) {
    val mode by settings.theme.collectAsStateWithLifecycle()
    val scale by settings.textScale.collectAsStateWithLifecycle()
    Heading("Appearance")
    Text("Theme")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { settings.setTheme(m) },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
            ) { Text(m.label) }
        }
    }
    Text("Text size: ${(scale * 100).roundToInt()} %")
    Slider(
        value = scale,
        onValueChange = settings::setTextScale,
        valueRange = AppSettings.MIN_SCALE..AppSettings.MAX_SCALE,
        // 80, 90, … 160 %: 7 stops between the ends.
        steps = 7,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sample text: buy milk @tomorrow 9:00 #shopping", Modifier.weight(1f))
        TextButton(onClick = { settings.setTextScale(1f) }, enabled = scale != 1f) { Text("Reset") }
    }
    Hint("On top of the phone's own font size (Settings → Display on the phone).")
    val dateStyle by settings.dateStyle.collectAsStateWithLifecycle()
    val today = remember { java.time.LocalDate.now() }
    ChoiceRow(
        "Date format", dateStyle.format(today), io.github.baslawson.smartnotes.DateStyle.entries,
        { it.format(today) }, settings::setDateStyle,
    )
    Hint("For shopping lists and task dates. Today, Tomorrow and Yesterday are still written out.")
}

// ---------- Recycle bin ----------

@Composable
private fun BinSection(settings: AppSettings, onBin: () -> Unit) {
    val keep by settings.keepFor.collectAsStateWithLifecycle()
    Heading("Recycle bin")
    ChoiceRow("Keep deleted items for", keep.label, io.github.baslawson.smartnotes.KeepFor.entries, { it.label }, settings::setKeepFor)
    Hint("Deleted notes, tasks and shopping lists stay in the bin this long, then are erased when the app starts.")
    OutlinedButton(onClick = onBin) { Text("Open recycle bin") }
}

// ---------- Backup ----------

@Composable
private fun BackupSection(app: SmartNotesApp) {
    val context = LocalContext.current
    val backups = app.backups
    val status by backups.status.collectAsStateWithLifecycle()
    val style = LocalDateStyle.current
    val busy by backups.busy.collectAsStateWithLifecycle()
    // A picked backup file waiting for "Restore?" (its uri survives turning the phone; it is read again then).
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    val canUndo = remember(busy) { backups.canUndoRestore() }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    // In the app's scope: leaving the screen doesn't cut a backup or restore short.
    fun work(action: suspend () -> String) {
        val appContext = context.applicationContext
        app.appScope.launch {
            val message = try { action() } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Backup", "Failed", e)
                (e as? BackupException)?.message ?: "That didn't work: ${e.message ?: e.javaClass.simpleName}"
            }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        }
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) work { "Backed up: " + backups.exportTo(uri).summary() }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pending = null; pendingUri = uri.toString() }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val name = runCatching {
                val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()
            runCatching { backups.setFolder(uri, name) }.onFailure { toast("That folder can't be used for backups.") }
        }
    }
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        if (pending != null) return@LaunchedEffect
        try {
            pending = backups.read(Uri.parse(uri))
        } catch (e: Exception) {
            pendingUri = null
            toast((e as? BackupException)?.message ?: "Couldn't read that file.")
        }
    }

    Heading("Backup")
    Hint("A backup is one file with all your notes, tasks and shopping lists (the recycle bin too) and these settings.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exportFile.launch(backups.fileName()) }, enabled = !busy) { Text("Back up now") }
        OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !busy) {
            Text("Restore from file")
        }
    }
    if (canUndo) TextButton(onClick = { confirmUndo = true }, enabled = !busy) { Text("Undo last restore") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

    Text("Automatic backup", Modifier.padding(top = 8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AutoBackup.entries.forEachIndexed { i, a ->
            SegmentedButton(
                selected = status.auto == a, onClick = { backups.setAuto(a) },
                shape = SegmentedButtonDefaults.itemShape(i, AutoBackup.entries.size),
            ) { Text(a.label) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Folder: " + (status.folderName ?: if (status.folder != null) "chosen" else "none yet"),
            Modifier.weight(1f),
        )
        TextButton(onClick = { pickFolder.launch(null) }) { Text(if (status.folder == null) "Choose" else "Change") }
    }
    when {
        status.auto != AutoBackup.OFF && status.folder == null -> Hint("Choose a folder for the automatic backups.")
        status.lastError != null -> Text(status.lastError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        status.lastAt > 0 -> Hint("Last backup: " + java.time.Instant.ofEpochMilli(status.lastAt).atZone(java.time.ZoneId.systemDefault()).let {
            style.dayLabel(it.toLocalDate()) + " " + it.toLocalTime().withSecond(0).withNano(0)
        })
    }
    if (status.auto != AutoBackup.OFF) Hint("The newest 5 automatic backups are kept in that folder; older ones are removed.")

    pending?.let { data ->
        val made = java.time.Instant.ofEpochMilli(data.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        AlertDialog(
            onDismissRequest = { pending = null; pendingUri = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "From ${style.format(made)}: ${data.summary()}.\n\nThis replaces everything in the app now. " +
                        "A copy of what's there now is kept, so you can undo it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    pendingUri = null
                    work { backups.restore(data); "Restored: " + data.summary() }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null; pendingUri = null }) { Text("Cancel") } },
        )
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Undo the last restore?") },
            text = { Text("The app goes back to what it had before that restore.") },
            confirmButton = {
                TextButton(onClick = { confirmUndo = false; work { backups.undoRestore(); "Back to before the restore" } }) { Text("Undo restore") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Reminders ----------

@Composable
private fun RemindersSection(settings: AppSettings) {
    val context = LocalContext.current
    val sound by settings.reminderSound.collectAsStateWithLifecycle()
    Heading("Reminders")
    ChoiceRow(
        "Default sound", sound.label,
        io.github.baslawson.smartnotes.reminders.ReminderSound.entries.filter { it != io.github.baslawson.smartnotes.reminders.ReminderSound.DEFAULT },
        { it.label }, settings::setReminderSound,
    )
    Hint(
        "Used by shopping lists and tasks set to Default. Ringing uses the alarm sound and rings in " +
            "silent and vibrate mode too; \"until I stop it\" gives up after 10 minutes."
    )
    OutlinedButton(onClick = { io.github.baslawson.smartnotes.reminders.AlarmService.test(context, sound) }) { Text("Test alarm") }
    if (!io.github.baslawson.smartnotes.reminders.Notifications.canNotify(context)) {
        Text("Notifications are off for SmartNotes, so reminders can't show. Allow them in the phone's app settings.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Updates ----------

/** Hands the checked APK to Android's installer, which asks before updating. The first time Android wants SmartNotes allowed to install apps. */
private fun installUpdate(context: Context, file: File) {
    try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Allow SmartNotes to install updates, then tap Install now again.", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        // Only an APK of this very app is handed to the installer (not, say, a debug build uploaded by mistake).
        val apkPackage = context.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName
        if (apkPackage != Updates.RELEASE_ID) {
            AppLog.w("Updates", "Downloaded APK is for $apkPackage, not ${Updates.RELEASE_ID}; not installed")
            file.delete()
            Toast.makeText(context, "The downloaded file isn't a SmartNotes update, so it was deleted.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        AppLog.i("Updates", "Opened the installer for ${file.name}")
    } catch (e: android.content.ActivityNotFoundException) {
        AppLog.w("Updates", "No installer available", e)
        Toast.makeText(context, "This phone doesn't let apps install updates. Download SmartNotes from GitHub instead.", Toast.LENGTH_LONG).show()
    }
}

/** Install now, after checking the file is still the one downloaded and checked. */
private fun installReady(context: Context) {
    val app = context.applicationContext as SmartNotesApp
    app.appScope.launch {
        if (!app.updates.stillReady()) return@launch
        val file = (app.updates.state.value as? Updates.State.Ready)?.file ?: return@launch
        withContext(Dispatchers.Main) { installUpdate(context, file) }
    }
}

/** A download's progress cut to its whole percent, so the screen only redraws when the number shown changes. */
private fun wholePercent(state: Updates.State): Updates.State =
    if (state is Updates.State.Downloading && state.progress >= 0) state.copy(progress = (state.progress * 100).toInt() / 100f) else state

@Composable
private fun DownloadProgress(s: Updates.State.Downloading) {
    Column {
        Text(if (s.progress >= 0) "Downloading… ${(s.progress * 100).roundToInt()} %" else "Downloading…")
        if (s.progress >= 0) LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** The pop-up for a new version: what's new, then Update now / Later / Skip; the download; Install now once checked. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val app = context.applicationContext as SmartNotesApp
    val updates = app.updates
    val state by remember(updates) { updates.state.map(::wholePercent).distinctUntilChanged() }
        .collectAsStateWithLifecycle(wholePercent(updates.state.value))
    val dismissed by updates.dismissed.collectAsStateWithLifecycle()
    val release = state.offered()
    if (dismissed || release == null || !updates.supported) return
    val s = state
    AlertDialog(
        onDismissRequest = updates::later,
        title = { Text("SmartNotes ${release.version} is available") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You have ${updates.installedVersion}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.notes.isNotBlank()) Text(release.notes)
                when (s) {
                    is Updates.State.Downloading -> DownloadProgress(s)
                    is Updates.State.Ready -> Text(
                        "Downloaded and checked. Android asks before installing; your notes stay as they are.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is Updates.State.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (s) {
                is Updates.State.Available -> TextButton(onClick = { app.appScope.launch { updates.download(release) } }) { Text("Update now") }
                is Updates.State.Ready -> TextButton(onClick = { installReady(context) }) { Text("Install now") }
                is Updates.State.Failed -> TextButton(onClick = { app.appScope.launch { updates.download(release) } }) { Text("Try again") }
                else -> Unit
            }
        },
        dismissButton = {
            Row {
                if (s is Updates.State.Available || s is Updates.State.Ready) {
                    TextButton(onClick = { updates.skip(release) }) { Text("Skip this version") }
                }
                TextButton(onClick = updates::later) { Text("Later") }
            }
        },
    )
}

@Composable
private fun UpdatesSection(app: SmartNotesApp) {
    val context = LocalContext.current
    val updates = app.updates
    val onStart by updates.checkOnStart.collectAsStateWithLifecycle()
    val auto by updates.autoDownload.collectAsStateWithLifecycle()
    val state by remember(updates) { updates.state.map(::wholePercent).distinctUntilChanged() }
        .collectAsStateWithLifecycle(wholePercent(updates.state.value))
    Heading("Updates")
    if (!updates.supported) {
        Hint("Updates aren't available in this build: only the SmartNotes app from GitHub updates itself.")
        return
    }
    SwitchRow("Check for updates when SmartNotes starts", onStart, updates::setCheckOnStart)
    SwitchRow("Download updates automatically", auto, updates::setAutoDownload)
    Hint("SmartNotes asks GitHub once a day at most, and Android always asks you before installing.")
    when (val s = state) {
        is Updates.State.Ready -> OutlinedButton(onClick = { installReady(context) }) { Text("Install SmartNotes ${s.release.version}") }
        is Updates.State.Available -> OutlinedButton(onClick = { app.appScope.launch { updates.download(s.release) } }) {
            Text("Update to SmartNotes ${s.release.version}")
        }
        is Updates.State.Downloading -> DownloadProgress(s)
        else -> Unit
    }
    OutlinedButton(onClick = { app.appScope.launch { updates.check() } }, enabled = state !is Updates.State.Checking) {
        Text(if (state is Updates.State.Checking) "Checking…" else "Check now")
    }
    val line = when (val s = state) {
        is Updates.State.UpToDate -> "You have the latest version."
        is Updates.State.Available -> "SmartNotes ${s.release.version} is available."
        is Updates.State.Ready -> "SmartNotes ${s.release.version} is ready to install."
        is Updates.State.Failed -> s.message
        else -> null
    }
    line?.let {
        Text(it, color = if (state is Updates.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
    if (updates.lastChecked > 0) {
        Hint("Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updates.lastChecked)))
    }
}

// ---------- Debug log ----------

private fun logWithHeader(app: SmartNotesApp): String =
    "SmartNotes ${app.versionName} (${app.versionCode}) ${app.packageName}\n" +
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), " +
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
        "Exported ${LocalDateTime.now()}\n\n" + AppLog.read()

@Composable
private fun LogSection(app: SmartNotesApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(logWithHeader(app).toByteArray()) } ?: error("no stream")
                }.onFailure { AppLog.w("Log", "Export failed", it) }.isSuccess
            }
            Toast.makeText(context, if (ok) "Log exported" else "The log couldn't be saved there", Toast.LENGTH_SHORT).show()
        }
    }

    Heading("Debug log")
    Hint(
        "SmartNotes keeps a log on this phone of what it did (saves, reminders, updates, errors and crashes), " +
            "including note titles. It's never sent anywhere; export or copy it to share it for debugging."
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            export.launch("smartnotes-log-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now()) + ".txt")
        }) { Text("Export") }
        OutlinedButton(onClick = {
            scope.launch {
                val text = withContext(Dispatchers.IO) { logWithHeader(app) }
                // The clipboard can't hold very large texts, so only the newest part is copied; Export gives everything.
                val clip = if (text.length > COPY_LIMIT) "(older lines left out; use Export for the full log)\n" + text.takeLast(COPY_LIMIT) else text
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SmartNotes log", clip))
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Copy") }
        OutlinedButton(onClick = { preview = !preview; refresh++ }) { Text(if (preview) "Hide" else "Show") }
    }
    TextButton(onClick = { confirmClear = true }) { Text("Clear log") }
    if (preview) {
        val text = remember(refresh) { AppLog.read().takeLast(20_000).ifEmpty { "(empty)" } }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the debug log?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    AppLog.clear()
                    AppLog.i("Log", "Log cleared")
                    refresh++
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
