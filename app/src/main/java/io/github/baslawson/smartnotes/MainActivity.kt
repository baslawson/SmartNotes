package io.github.baslawson.smartnotes

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.baslawson.smartnotes.ui.Accent
import io.github.baslawson.smartnotes.ui.AccentTheme
import io.github.baslawson.smartnotes.ui.LocalDark
import io.github.baslawson.smartnotes.ui.SmartShapes
import io.github.baslawson.smartnotes.ui.SmartTypography
import io.github.baslawson.smartnotes.ui.smartColors
import io.github.baslawson.smartnotes.ui.RecycleBinScreen
import io.github.baslawson.smartnotes.ui.BinViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.baslawson.smartnotes.reminders.Notifications
import io.github.baslawson.smartnotes.ui.EditorScreen
import io.github.baslawson.smartnotes.ui.EditorViewModel
import io.github.baslawson.smartnotes.ui.NotesScreen
import io.github.baslawson.smartnotes.ui.NotesViewModel
import io.github.baslawson.smartnotes.ui.ShoppingScreen
import io.github.baslawson.smartnotes.ui.SettingsScreen
import io.github.baslawson.smartnotes.ui.ShoppingViewModel
import io.github.baslawson.smartnotes.ui.UpdateDialog
import io.github.baslawson.smartnotes.ui.TasksScreen
import io.github.baslawson.smartnotes.ui.TodoEditorScreen
import io.github.baslawson.smartnotes.ui.TodoEditorViewModel
import io.github.baslawson.smartnotes.ui.TodoViewModel
import io.github.baslawson.smartnotes.reminders.AlarmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * A note this window should open: from a tapped reminder, or just made from text shared into this window. Kept per
 * window (and across turning the phone), so with two SmartNotes windows open the right one opens it.
 */
class PendingOpen(app: android.app.Application, private val saved: androidx.lifecycle.SavedStateHandle) : androidx.lifecycle.AndroidViewModel(app) {
    /** A navigation route ("note/5?title=", "todo/3", "shopping?list=2"). */
    val route = MutableStateFlow<String?>(null)
    /** On a plain start: the screens to reopen where the app was last (see AppState). Used once. */
    var restore: List<String>? = null

    /**
     * Text shared in, waiting for "Save as a note, a task or shopping". Kept in a private file named in the saved state
     * (the text can be too big for the saved state itself), so the choice is still there if Android stops the app
     * while it waits. One file per window; it goes once the text is saved or cancelled, or (on the next share) once its
     * window has gone.
     */
    val share = MutableStateFlow(saved.get<String>(SHARE)?.let { read(it) })

    /** Sets (or with null, drops) the text waiting in the window of task [task]. */
    fun share(s: Shared?, task: Int = 0) {
        share.value = s
        saved.get<String>(SHARE)?.let { file(it).delete() }
        saved[SHARE] = s?.let { write(it, task) }
    }

    private val dir get() = java.io.File(getApplication<android.app.Application>().filesDir, "pending-shares")
    private fun file(name: String) = java.io.File(dir, name)

    private fun read(name: String): Shared? = runCatching {
        val j = org.json.JSONObject(file(name).readText())
        Shared(j.getString("subject"), j.getString("text"), j.getString("from"))
    }.onFailure { AppLog.w("Share", "Couldn't get back a pending share: $it") }.getOrNull()

    private fun write(s: Shared, task: Int): String? = runCatching {
        dir.mkdirs()
        sweep(task)
        // Named after its window's task, so one left behind (its window gone from Recents) can be told apart.
        val name = "$task-" + java.util.UUID.randomUUID() + ".json"
        file(name).writeText(org.json.JSONObject().put("subject", s.subject).put("text", s.text).put("from", s.from).toString())
        name
    }.onFailure { AppLog.w("Share", "Couldn't keep a pending share: $it") }.getOrNull()

    /**
     * Removes text left waiting by windows that are gone (closed or removed from Recents while the app wasn't running);
     * a window still in Recents keeps its text, however old. A share can open SmartNotes inside the sending app's task,
     * which isn't listed among SmartNotes' own tasks: a day's grace keeps such a waiting text from going too.
     */
    private fun sweep(current: Int) {
        val app = getApplication<android.app.Application>()
        val tasks = app.getSystemService(android.app.ActivityManager::class.java)?.appTasks ?: return
        val live = tasks.mapNotNull { runCatching { taskId(it.taskInfo) }.getOrNull() }.toSet() + current
        val dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000
        dir.listFiles()?.filter { it.name.substringBefore('-').toIntOrNull() !in live && it.lastModified() < dayAgo }?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    private fun taskId(info: android.app.ActivityManager.RecentTaskInfo): Int =
        if (android.os.Build.VERSION.SDK_INT >= 29) info.taskId else info.persistentId

    private companion object { const val SHARE = "share" }
}

/** Text shared in from another app: its [subject] (may be empty), [text] and the app it came [from]. */
data class Shared(val subject: String, val text: String, val from: String)

class MainActivity : ComponentActivity() {
    // Made without the starting intent's extras: by default they go into its saved state, and a shared text can be too
    // big for that (its file name is kept there instead).
    private val pending: PendingOpen by viewModels(extrasProducer = {
        androidx.lifecycle.viewmodel.MutableCreationExtras(defaultViewModelCreationExtras).apply {
            set(androidx.lifecycle.DEFAULT_ARGS_KEY, Bundle())
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SmartNotesApp
        if (savedInstanceState == null) {
            handle(intent)
            // Opened from the launcher or Recents (not a reminder or a share): back where it was last.
            if (intent?.action != Intent.ACTION_SEND && intent?.getStringExtra(EXTRA_ROUTE) == null) {
                pending.restore = AppState.routesFor(app.state.place)
            }
            // A new version on GitHub, at most once a day (Settings → Updates); it only asks.
            app.appScope.launch { app.updates.checkIfDue() }
        }
        setContent {
            SmartNotesTheme(app.settings) { dark ->
                // Status and navigation bar icons follow the chosen theme, not only the phone's.
                LaunchedEffect(dark) {
                    val style = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark }
                    enableEdgeToEdge(style, style)
                }
                AskForNotifications()
                UpdateDialog()
                val shared by pending.share.collectAsStateWithLifecycle()
                shared?.let { s -> ShareChooser(s, onPick = { kind -> pending.share(null); saveShare(s, kind) }) { pending.share(null) } }
                val nav = rememberNavController()
                // Navigates only while visible; a note made while the window is hidden opens when it comes back.
                LaunchedEffect(Unit) {
                    pending.restore?.let { routes ->
                        pending.restore = null
                        routes.forEach { route ->
                            runCatching {
                                if (route == TASKS || route == SHOPPING) nav.navigate(route) {
                                    popUpTo(NOTES) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                } else nav.navigate(route)
                            }.onFailure { AppLog.w("App", "Couldn't reopen $route", it) }
                        }
                    }
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        pending.route.collect { route ->
                            if (route != null) {
                                pending.route.value = null
                                // A shopping list (or Tasks, from its widget) opens as that tab, not on top of it. A route the app doesn't
                                // know (another app can start this screen) is logged and ignored, not a crash.
                                runCatching {
                                    if (route.startsWith("shopping") || route == TASKS) nav.navigate(route) { popUpTo(NOTES); launchSingleTop = true }
                                    else nav.navigate(route)
                                }.onFailure { AppLog.w("App", "Ignored an unknown screen: $route", it) }
                            }
                        }
                    }
                }
                // Remembers where the app is, to open there next time.
                DisposableEffect(nav) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, args ->
                        AppState.placeOf(destination.route, args?.getLong("id") ?: 0L)?.let { app.state.place = it }
                    }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose { nav.removeOnDestinationChangedListener(listener) }
                }
                Box(Modifier.fillMaxSize()) {
                    AppNavHost(nav, app)
                    BinSnackbar(app, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Reopened from Recents, Android hands back the intent that first started the task: already handled then.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.action == Intent.ACTION_SEND) return receiveShare(intent)
        // A tapped reminder: stops its ringing, then opens what it is about.
        AlarmService.stopFromTap(this, intent.getStringExtra(AlarmService.EXTRA_STOP_TOKEN))
        intent.getStringExtra(EXTRA_ROUTE)?.let { pending.route.value = it }
    }

    /** Text or a link shared from another app: a small dialog asks whether it becomes a note, a task or shopping. */
    private fun receiveShare(intent: Intent) {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().replace("\r\n", "\n").trim()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        if (text.isEmpty() && subject.isEmpty()) {
            AppLog.w("Share", "Received a share without text (type ${intent.type})")
            android.widget.Toast.makeText(this, "Only text and links can be shared into SmartNotes", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        pending.share(Shared(subject, text, referrer?.host ?: "unknown app"), taskId)
    }

    /**
     * Saves shared text as chosen, then opens it. A note: the subject is its title. A task: the subject (or the first
     * line) is its title, the rest its details (all of the text when the first line is too long for a title); shared
     * text gets no reminder from its words. Shopping: each line of the text (the subject if there's no text) an item on
     * today's list.
     */
    private fun saveShare(s: Shared, kind: ShareKind) {
        val app = application as SmartNotesApp
        val target = pending.route
        // In the app's scope, so turning the phone or switching theme right now can't cancel the save.
        app.appScope.launch {
            when (kind) {
                ShareKind.NOTE -> {
                    val note = app.notes.saveFinal(0, s.subject.take(200), s.text)
                    AppLog.i("Share", "Made note ${note?.id} from shared text (${s.text.length} characters, from ${s.from})")
                    note?.let { target.value = noteRoute(it.id) }
                }
                ShareKind.TASK -> {
                    val lines = s.text.trim().lines()
                    val line = lines.first().trim()
                    val title = s.subject.ifEmpty { line }.take(200)
                    // With no subject, the first line is the title; one too long to fit keeps its whole text in the details.
                    val details = (if (s.subject.isEmpty() && line.length <= 200) lines.drop(1).joinToString("\n") else s.text).trim()
                    val id = app.todos.add(title, details = details)
                    AppLog.i("Share", "Made task $id from shared text (from ${s.from})")
                    target.value = "todo/$id"
                }
                ShareKind.SHOPPING -> {
                    val today = java.time.LocalDate.now()
                    val lines = io.github.baslawson.smartnotes.parse.Shopping.sharedLines(s.text.ifBlank { s.subject })
                    lines.forEach { app.shopping.addOn(today, it) }
                    AppLog.i("Share", "Added ${lines.size} shopping items from shared text (from ${s.from})")
                    target.value = "shopping?list=${app.shopping.listForDate(today)}"
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

private const val NOTES = "notes"
private const val TASKS = "tasks"
private const val SHOPPING = "shopping"
private const val SHOPPING_ROUTE = "shopping?list={list}"
private const val TODO = "todo/{id}"
private const val NOTE = "note/{id}?title={title}"
private const val SETTINGS = "settings"
private const val BIN = "bin"

private fun noteRoute(id: Long, title: String = "") = "note/$id?title=${Uri.encode(title)}"

@Composable
private fun AppNavHost(nav: NavHostController, app: SmartNotesApp) {
    val bottomBar: @Composable () -> Unit = { BottomBar(nav) }
    val open: (Long) -> Unit = { nav.navigate(noteRoute(it)) }
    // From a search result elsewhere: a task opens on top; a shopping list opens as the Shopping tab (as from a reminder).
    val openTask: (Long) -> Unit = { nav.navigate("todo/$it") }
    val openList: (Long) -> Unit = { nav.navigate("shopping?list=$it") { popUpTo(NOTES); launchSingleTop = true } }
    // An item found on a past day's list that's still to buy: opening Shopping carries it over to today, so show today.
    val openItem: (io.github.baslawson.smartnotes.data.ShoppingItem, io.github.baslawson.smartnotes.data.ShoppingList) -> Unit = { item, list ->
        // An open item on a past list that carries over is on today's list once Shopping opens; others stay where they are.
        val carried = !item.checked && io.github.baslawson.smartnotes.data.carriesOver(list, java.time.LocalDate.now(), java.time.ZoneId.systemDefault())
        openList(if (carried) -1L else list.id)
    }
    // Back on every screen but Notes: to wherever you came from (from a tab, that is Notes).
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(NOTES) }
    // Screens fade in and rise a little; going back, they fade and sink.
    NavHost(
        nav, startDestination = NOTES,
        enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 } },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 24 } },
    ) {
        composable(NOTES) { AccentTheme(Accent.NOTES) {
            val vm = viewModel { NotesViewModel(app.notes, app.bin, app.appScope, app.state, app.settings, app.everywhere) }
            NotesScreen(
                vm, onOpen = open, onNew = { nav.navigate(noteRoute(0)) },
                onSettings = { nav.navigate(SETTINGS) }, onBin = { nav.navigate(BIN) }, bottomBar = bottomBar,
                onOpenTask = openTask, onOpenItem = openItem,
            )
        } }
        composable(TASKS) { AccentTheme(Accent.TASKS) {
            val vm = viewModel { TodoViewModel(app.todos, app.bin, app.appScope, app.settings, app.everywhere) }
            TasksScreen(vm, onOpen = openTask, onBack = back, bottomBar = bottomBar, onOpenNote = open, onOpenItem = openItem)
        } }
        composable(SHOPPING_ROUTE, arguments = listOf(navArgument("list") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            val start = entry.arguments?.getLong("list") ?: 0L
            val vm = viewModel { ShoppingViewModel(app.shopping, app.bin, app.appScope, app.getSharedPreferences("lists", android.content.Context.MODE_PRIVATE), start, app.settings, app.everywhere) }
            // Opened again for another list (a reminder, a search result, a widget): show that one; -1 is today.
            LaunchedEffect(start) { if (start > 0) vm.openListId(start) else if (start == -1L) vm.show(java.time.LocalDate.now()) }
            val setting by app.settings.reminderSound.collectAsStateWithLifecycle()
            AccentTheme(Accent.SHOPPING) { ShoppingScreen(vm, setting, onBack = back, bottomBar = bottomBar, onOpenNote = open, onOpenTask = openTask) }
        }
        composable(TODO, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm = viewModel { TodoEditorViewModel(app.todos, app.bin, app.appScope, id, app.backups.restores) }
            val setting by app.settings.reminderSound.collectAsStateWithLifecycle()
            AccentTheme(Accent.TASKS) { TodoEditorScreen(vm, setting, onBack = back) }
        }
        composable(SETTINGS) { SettingsScreen(onBack = back, onBin = { nav.navigate(BIN) }) }
        composable(BIN) {
            val vm = viewModel { BinViewModel(app.bin, app.appScope) }
            RecycleBinScreen(vm, app.settings, onBack = back)
        }
        composable(
            NOTE,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val title = entry.arguments?.getString("title").orEmpty()
            val vm = viewModel { EditorViewModel(app.notes, app.bin, app.appScope, id, title, app.backups.restores) }
            AccentTheme(Accent.NOTES) {
                EditorScreen(
                    vm,
                    onBack = back,
                    onOpen = open,
                    onCreateLinked = { nav.navigate(noteRoute(0, it)) },
                )
            }
        }
    }
}

/** "Moved to the recycle bin · Undo" (and "Removed milk · Undo") after any delete, above the bottom bar. */
@Composable
private fun BinSnackbar(app: SmartNotesApp, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        // Only the window on screen takes the events (another, in the background, would swallow them).
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.bin.events.collectLatest { event ->
                if (System.currentTimeMillis() - event.at > 10_000) return@collectLatest
                val result = host.showSnackbar(event.text, actionLabel = "Undo", withDismissAction = false, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) app.appScope.launch { app.bin.undo(event) }
            }
        }
    }
    SnackbarHost(host, modifier.navigationBarsPadding().imePadding().padding(bottom = 80.dp))
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route?.substringBefore('?')
    // The bar takes the colour of the tab shown.
    val accent = when (route) { TASKS -> Accent.TASKS; SHOPPING -> Accent.SHOPPING; else -> Accent.NOTES }
    AccentTheme(accent) { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        listOf(
            Triple(NOTES, "Notes", Icons.AutoMirrored.Filled.List),
            Triple(TASKS, "Tasks", Icons.Default.CheckCircle),
            Triple(SHOPPING, "Shopping", Icons.Default.ShoppingCart),
        ).forEach { (dest, label, icon) ->
            NavigationBarItem(
                selected = route == dest,
                onClick = {
                    if (route != dest) nav.navigate(dest) {
                        popUpTo(NOTES) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    } }
}

/** Reminders need permission to notify on Android 13+; asked once, on first start. */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
        if (!Notifications.canNotify(context) && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Settings → Appearance: System / Light / Dark, and the text size on top of the phone's own. */
@Composable
private fun SmartNotesTheme(settings: AppSettings, content: @Composable (dark: Boolean) -> Unit) {
    val mode by settings.theme.collectAsStateWithLifecycle()
    val scale by settings.textScale.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // SmartNotes' own colours (not the wallpaper's); each tab sets its accent (see AccentTheme).
    val colors = smartColors(dark, Accent.BRAND)
    val density = LocalDensity.current
    val dateStyle by settings.dateStyle.collectAsStateWithLifecycle()
    val timeFormat by settings.timeFormat.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme(colorScheme = colors, typography = SmartTypography, shapes = SmartShapes) {
        CompositionLocalProvider(
            LocalDark provides dark,
            LocalDensity provides Density(density.density, density.fontScale * scale),
            io.github.baslawson.smartnotes.ui.LocalDateStyle provides dateStyle,
            io.github.baslawson.smartnotes.ui.LocalTimeStyle provides timeFormat.style(context),
        ) {
            content(dark)
        }
    }
}

enum class ShareKind { NOTE, TASK, SHOPPING }

/** "Save shared text as": a note, a task or shopping (today), with a peek at the text. Cancel keeps nothing. */
@Composable
private fun ShareChooser(s: Shared, onPick: (ShareKind) -> Unit, onCancel: () -> Unit) {
    val items = remember(s) { io.github.baslawson.smartnotes.parse.Shopping.sharedLines(s.text.ifBlank { s.subject }).size }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        // A stray tap beside it mustn't throw the shared text away: Cancel or Back does.
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { androidx.compose.material3.Text("Save shared text as") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    listOf(s.subject, s.text).filter { it.isNotEmpty() }.joinToString("\n"), maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                listOf(
                    Triple(ShareKind.NOTE, androidx.compose.material.icons.Icons.AutoMirrored.Filled.List, "A note"),
                    Triple(ShareKind.TASK, androidx.compose.material.icons.Icons.Default.CheckCircle, "A task"),
                    Triple(ShareKind.SHOPPING, androidx.compose.material.icons.Icons.Default.ShoppingCart, if (items == 1) "Shopping for today" else "Shopping for today ($items items)"),
                ).forEach { (kind, icon, label) ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text(label) },
                        leadingContent = { androidx.compose.material3.Icon(icon, contentDescription = null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(androidx.compose.material3.MaterialTheme.shapes.medium).clickable { onPick(kind) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancel) { androidx.compose.material3.Text("Cancel") } },
    )
}
