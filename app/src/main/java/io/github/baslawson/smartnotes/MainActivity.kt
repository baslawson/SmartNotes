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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
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
class PendingOpen : ViewModel() {
    /** A navigation route ("note/5?title=", "todo/3", "shopping?list=2"). */
    val route = MutableStateFlow<String?>(null)
    /** On a plain start: the screens to reopen where the app was last (see AppState). Used once. */
    var restore: List<String>? = null
}

class MainActivity : ComponentActivity() {
    private val pending: PendingOpen by viewModels()

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
                                // A shopping list opens as the Shopping tab, not on top of it. A route the app doesn't
                                // know (another app can start this screen) is logged and ignored, not a crash.
                                runCatching {
                                    if (route.startsWith("shopping")) nav.navigate(route) { popUpTo(NOTES); launchSingleTop = true }
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

    /**
     * Text or a link shared from another app becomes a new note straight away (so it's kept even if the editor is left
     * at once), then opens. The shared subject, when there is one, is the title.
     */
    private fun receiveShare(intent: Intent) {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().replace("\r\n", "\n").trim()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        if (text.isEmpty() && subject.isEmpty()) {
            AppLog.w("Share", "Received a share without text (type ${intent.type})")
            android.widget.Toast.makeText(this, "Only text and links can be shared into SmartNotes", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val app = application as SmartNotesApp
        val from = referrer?.host ?: "unknown app"
        val target = pending.route
        // In the app's scope, so turning the phone or switching theme right now can't cancel the save.
        app.appScope.launch {
            val note = app.notes.saveFinal(0, subject.take(200), text)
            AppLog.i("Share", "Made note ${note?.id} from shared text (${text.length} characters, from $from)")
            note?.let { target.value = noteRoute(it.id) }
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
    // Back on every screen but Notes: to wherever you came from (from a tab, that is Notes).
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(NOTES) }
    NavHost(nav, startDestination = NOTES) {
        composable(NOTES) {
            val vm = viewModel { NotesViewModel(app.notes, app.bin, app.appScope, app.state, app.settings) }
            NotesScreen(
                vm, onOpen = open, onNew = { nav.navigate(noteRoute(0)) },
                onSettings = { nav.navigate(SETTINGS) }, onBin = { nav.navigate(BIN) }, bottomBar = bottomBar,
            )
        }
        composable(TASKS) {
            val vm = viewModel { TodoViewModel(app.todos, app.bin, app.appScope, app.settings) }
            TasksScreen(vm, onOpen = { nav.navigate("todo/$it") }, onBack = back, bottomBar = bottomBar)
        }
        composable(SHOPPING_ROUTE, arguments = listOf(navArgument("list") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            val start = entry.arguments?.getLong("list") ?: 0L
            val vm = viewModel { ShoppingViewModel(app.shopping, app.bin, app.appScope, app.getSharedPreferences("lists", android.content.Context.MODE_PRIVATE), start, app.settings) }
            // Opened again for another list (from a reminder): show that one.
            LaunchedEffect(start) { if (start > 0) vm.select(start) }
            val setting by app.settings.reminderSound.collectAsStateWithLifecycle()
            ShoppingScreen(vm, setting, onBack = back, bottomBar = bottomBar)
        }
        composable(TODO, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm = viewModel { TodoEditorViewModel(app.todos, app.bin, app.appScope, id, app.backups.restores) }
            val setting by app.settings.reminderSound.collectAsStateWithLifecycle()
            TodoEditorScreen(vm, setting, onBack = back)
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
            EditorScreen(
                vm,
                onBack = back,
                onOpen = open,
                onCreateLinked = { nav.navigate(noteRoute(0, it)) },
            )
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
    NavigationBar {
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
    }
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
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val density = LocalDensity.current
    val dateStyle by settings.dateStyle.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * scale),
            io.github.baslawson.smartnotes.ui.LocalDateStyle provides dateStyle,
        ) {
            content(dark)
        }
    }
}
