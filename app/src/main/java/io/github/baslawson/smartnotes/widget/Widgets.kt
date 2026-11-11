package io.github.baslawson.smartnotes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.DateStyle
import io.github.baslawson.smartnotes.MainActivity
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.TimeStyle
import io.github.baslawson.smartnotes.data.ShoppingItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val ID = ActionParameters.Key<Long>("id")
private val LIST = ActionParameters.Key<Long>("list")
private val DATE = ActionParameters.Key<String>("date")

/** Opens the app, on [route] when given ("tasks", "todo/3", "shopping?list=2"). */
private fun openApp(context: Context, route: String): Intent =
    Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_ROUTE, route)

// ---------- Today's tasks ----------

/** "Today's tasks": open tasks due today or overdue, ticked off with their boxes; a tap on one opens it. */
class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SmartNotesApp
        val first = app.todos.observeAll().first()
        // Follows the database (and the date) while the widget's session lasts: a redraw then shows what's there now.
        provideContent {
            val todos by remember { app.todos.observeAll() }.collectAsState(first)
            val today by remember { Widgets.days() }.collectAsState(LocalDate.now())
            val dates by app.settings.dateStyle.collectAsState()
            val clock = TimeStyle.of(context)
            GlanceTheme { TasksContent(WidgetData.todayTasks(todos, today), today, dates, clock) }
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()

    /** The first widget of its kind placed: set the midnight redraw (the app may have started without widgets). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.scheduleMidnight(context)
    }
}

@Composable
fun TasksContent(tasks: List<WidgetData.TaskLine>, today: LocalDate, dates: DateStyle, clock: TimeStyle) {
    val context = LocalContext.current
    WidgetFrame("Today's tasks", tasks.size, openApp(context, "tasks")) {
        if (tasks.isEmpty()) Hint("Nothing due today")
        else LazyColumn {
            items(tasks, itemId = { it.id }) { t ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<TickTask>(actionParametersOf(ID to t.id, DATE to t.date.toString())),
                        modifier = GlanceModifier.semantics { contentDescription = "Tick ${t.title}" },
                    )
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, "todo/${t.id}")))) {
                        Text(
                            (if (t.priority == 2) "! " else "") + t.title, maxLines = 2,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                        )
                        val time = t.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let(clock::format)
                        Text(
                            if (t.overdue) "Overdue · " + dates.dayLabel(t.date, today) + (time?.let { " $it" } ?: "") else time ?: "Today",
                            style = TextStyle(color = if (t.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A task ticked on the widget: done (a repeating one moves on to its next date), then the widget is drawn again. Only
 * while it's still open and due on the date its row showed: tapping an old row again can't skip a repeat's dates.
 */
class TickTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        val app = context.applicationContext as SmartNotesApp
        AppLog.i("Widget", "Ticked task $id")
        app.todos.setDone(id, true, onlyIfOpenOn = parameters[DATE] ?: return)
        TasksWidget().update(context, glanceId)
    }
}

// ---------- Today's shopping ----------

/**
 * "Today's shopping": what's still to buy today (today's list, and what past days still have: it carries over to today),
 * ticked off with the boxes; a tap opens today in Shopping.
 */
class ShoppingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SmartNotesApp
        val first = Widgets.shopping(app).first()
        // Follows the database (and the date) while the widget's session lasts, as the tasks widget.
        provideContent {
            val shown by remember { Widgets.shopping(app) }.collectAsState(first)
            GlanceTheme { ShoppingContent(shown.first, shown.second) }
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()

    /** The first widget of its kind placed: set the midnight redraw (the app may have started without widgets). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.scheduleMidnight(context)
    }
}

@Composable
fun ShoppingContent(listId: Long?, items: List<ShoppingItem>) {
    val context = LocalContext.current
    // Today's list when there is one; else Shopping opens on today ("shopping?list=-1").
    val route = "shopping?list=${listId ?: -1}"
    WidgetFrame("Today's shopping", items.size, openApp(context, route)) {
        if (items.isEmpty()) Hint("Nothing to buy today")
        else LazyColumn {
            items(items, itemId = { it.id }) { item ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<TickItem>(actionParametersOf(ID to item.id, LIST to item.listId)),
                        modifier = GlanceModifier.semantics { contentDescription = "Tick ${item.name}" },
                    )
                    Text(
                        item.name, maxLines = 2,
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, route))),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    )
                    if (item.quantity.isNotEmpty()) Text(
                        item.quantity, modifier = GlanceModifier.padding(start = 6.dp),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

/** An item ticked on the widget: in the basket, then the widget is drawn again. */
class TickItem : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        val list = parameters[LIST] ?: return
        val app = context.applicationContext as SmartNotesApp
        AppLog.i("Widget", "Ticked item $id")
        app.shopping.tickFromWidget(id, list)
        ShoppingWidget().update(context, glanceId)
    }
}

// ---------- Shared ----------

/** A widget's card: its [title] (with [count]; a tap opens [open]) above its [content]. */
@Composable
private fun WidgetFrame(title: String, count: Int, open: Intent, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            if (count > 0) Text("$count", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
}

/** Just after midnight: the widgets show the new day, and the next midnight's redraw is set. */
class MidnightReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SmartNotesApp
        val result = goAsync()
        app.appScope.launch {
            try {
                Widgets.redrawAll(app)
                // Only while a widget is still placed: removed widgets mustn't keep waking the phone.
                if (Widgets.placed(app)) Widgets.scheduleMidnight(app)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * Keeps the widgets current: after any change to tasks or to today's shopping while the app runs, and just after
 * midnight (an alarm, also when the app isn't running). Android also redraws them every hour and after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
object Widgets {
    /** Today, and again just after each midnight. */
    fun days() = flow {
        while (true) {
            emit(LocalDate.now())
            val next = LocalDate.now().plusDays(1).atStartOfDay()
            delay(Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0) + 1_000)
        }
    }.distinctUntilChanged()

    /** Today's list (null: none yet) and what's still to buy today, following the database, the date and the sort. */
    fun shopping(app: SmartNotesApp) = combine(app.shopping.observeLists(), days()) { lists, today -> lists to today }
        .flatMapLatest { (lists, today) ->
            val relevant = WidgetData.listsForToday(lists, today)
            val items = if (relevant.isEmpty()) flowOf(emptyList())
                else combine(relevant.map { app.shopping.observeItems(it.id) }) { all -> all.flatMap { it.toList() } }
            combine(items, app.settings.itemSort, app.shopping.observeAisles()) { all, sort, aisles ->
                WidgetData.todayList(lists, today)?.id to WidgetData.toBuyToday(lists, all, today, sort, aisles = aisles)
            }
        }

    fun keepUpdated(app: SmartNotesApp) {
        app.appScope.launch {
            combine(app.todos.observeAll(), days()) { _, _ -> }.debounce(300).collect { redraw(app, TasksWidget()) }
        }
        app.appScope.launch {
            shopping(app).debounce(300).collect { redraw(app, ShoppingWidget()) }
        }
        // While a widget is placed: a redraw just after midnight even if the app isn't running then.
        app.appScope.launch { if (placed(app)) scheduleMidnight(app) }
    }

    internal suspend fun placed(context: Context): Boolean = runCatching {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(TasksWidget::class.java).isNotEmpty() || manager.getGlanceIds(ShoppingWidget::class.java).isNotEmpty()
    }.getOrDefault(false)

    /** An alarm (inexact, no permission needed) just after the coming midnight: [MidnightReceiver] redraws the widgets. */
    fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val at = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = android.app.PendingIntent.getBroadcast(
            context, 0, Intent(context, MidnightReceiver::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pending) }
            .onFailure { AppLog.w("Widget", "Couldn't set the midnight redraw: $it") }
    }

    /** Redraws both widgets wherever they are placed. */
    suspend fun redrawAll(context: Context) {
        redraw(context, TasksWidget())
        redraw(context, ShoppingWidget())
    }

    /** Draws [widget] again where it's on a home screen (nothing when it isn't). */
    private suspend fun redraw(context: Context, widget: GlanceAppWidget) {
        runCatching {
            if (GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()) widget.updateAll(context)
        }.onFailure { AppLog.w("Widget", "Couldn't update ${widget.javaClass.simpleName}: $it") }
    }
}
