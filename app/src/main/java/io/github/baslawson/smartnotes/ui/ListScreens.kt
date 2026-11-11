package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import io.github.baslawson.smartnotes.data.orderLists
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import io.github.baslawson.smartnotes.data.nextDue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import io.github.baslawson.smartnotes.data.ItemSort
import io.github.baslawson.smartnotes.data.TaskSort
import io.github.baslawson.smartnotes.data.Sorting
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.baslawson.smartnotes.DateStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import io.github.baslawson.smartnotes.data.Week
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.reminders.AlarmPlanner
import io.github.baslawson.smartnotes.reminders.ReminderSound
import io.github.baslawson.smartnotes.reminders.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.mutableIntStateOf
import io.github.baslawson.smartnotes.parse.PastItem
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.InputChip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baslawson.smartnotes.parse.Shopping
import io.github.baslawson.smartnotes.parse.Search
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalTime
import java.time.ZoneOffset

// ---------- Shared pieces ----------

/** Settings → Date format, provided around the whole app. */
val LocalDateStyle = staticCompositionLocalOf { DateStyle.DEFAULT }
/** 12- or 24-hour, from Settings → Time format: every time shown or picked. */
val LocalTimeStyle = staticCompositionLocalOf { io.github.baslawson.smartnotes.TimeStyle(true) }

private fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun parseTime(s: String?): LocalTime? = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/** The back arrow every screen but Notes has in its top bar. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, earliest: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: earliest ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                earliest == null || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(earliest)
            override fun isSelectableYear(year: Int): Boolean = earliest == null || year >= earliest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = LocalTimeStyle.current.h24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A row that opens a menu of choices. */
@Composable
fun <T> ChoiceRow(label: String, value: String, choices: List<T>, choiceLabel: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c -> DropdownMenuItem(text = { Text(choiceLabel(c)) }, onClick = { open = false; onPick(c) }) }
        }
    }
}

/** Default shows what it means now: "Default (Ring 10 seconds)". */
fun soundLabel(sound: ReminderSound, setting: ReminderSound): String =
    if (sound == ReminderSound.DEFAULT) "Default (${setting.label})" else sound.label

/** Today's date, moving on at midnight while the screen is open and checked again whenever it comes back into view. */
@Composable
private fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        // Wakes at midnight (at least hourly, in case the clock or time zone changes meanwhile).
        while (true) {
            val now = LocalDateTime.now()
            delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis().coerceIn(1_000, 3_600_000))
            today = LocalDate.now()
        }
    }
    return today
}

/** Search results: matching items grouped under their list (lists in chip order); a tap opens that list. */
@Composable
private fun SearchResults(
    query: String,
    results: List<ShoppingItem>,
    lists: List<ShoppingList>,
    label: (ShoppingList) -> String,
    onOpen: (Long) -> Unit,
) {
    if (query.isBlank()) {
        EmptyState(Icons.Default.Search, "Search your lists", "Type to find items in all your lists, past ones too.")
        return
    }
    val byList = results.groupBy { it.listId }
    val groups = lists.filter { it.id in byList }
    if (groups.isEmpty()) {
        EmptyState(Icons.Default.Search, "Nothing found", "No item matches \"${query.trim()}\".")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { l ->
            item(key = "h:${l.id}") { Header(label(l)) }
            items(byList.getValue(l.id), key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(l.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.name, Modifier.weight(1f),
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.checked) Text("ticked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                    if (item.quantity.isNotEmpty()) Text(item.quantity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ---------- To-do ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TodoViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit, bottomBar: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selection = vm.selection
    val sort by vm.sort.collectAsStateWithLifecycle()
    val showDone by vm.showDone.collectAsStateWithLifecycle()
    // Only rows on screen can be selected (Select all leaves hidden done tasks alone).
    val shownIds = state.todos.filter { showDone || !it.done }.map { it.id }
    LaunchedEffect(shownIds) { if (state.loaded) selection.keepOnly(shownIds) }
    // Handles in every order; a drop switches to My order.
    val canArrange = !selection.active
    val party = rememberCelebration()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    BackHandler(enabled = query != null) { vm.searchQuery.value = null }

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionBar(selection, shownIds) {
                    IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            } else if (query != null) SearchTopBar(query.orEmpty(), "Search tasks", { vm.searchQuery.value = it }) { vm.searchQuery.value = null }
            else TopAppBar(
                title = { Text("Tasks") }, navigationIcon = { BackButton(onBack) },
                actions = {
                    SortButton(SortGroup(null, TaskSort.entries, sort, { it.label }, vm::setSort)) { close ->
                        ShowCompletedItem(showDone) { close(); vm.toggleShowDone() }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (query != null) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                // Ticking off the last open task from here celebrates too (as in the list).
                val onCheck: (Todo, Boolean) -> Unit = { t, now ->
                    if (now && !t.done && state.todos.count { !it.done } == 1 && nextDue(t) == null) party.fire(Celebration.TASKS.random())
                    vm.setDone(t, now)
                }
                TaskResults(query.orEmpty(), searchTasks(state.todos, query.orEmpty(), sort), onCheck) { id ->
                    onOpen(id)
                }
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = vm.input, onValueChange = vm::type,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = PillShape, colors = pillColors(),
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                placeholder = { Text("Add a task, e.g. Call Sam tomorrow 3pm") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.add() }),
                // On the right: add what is typed, and search all tasks (for what is typed, if anything).
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.input.isNotBlank()) IconButton(onClick = vm::add) { Icon(Icons.Default.Check, contentDescription = "Add") }
                        IconButton(onClick = { selection.clear(); vm.searchQuery.value = vm.input.trim() }) { Icon(Icons.Default.Search, contentDescription = "Search tasks") }
                    }
                },
            )
            // A date and time typed at the end become the task's reminder; ✕ keeps them as words in the title.
            vm.typedDate()?.let { found ->
                val style = LocalDateStyle.current
                InputChip(
                    selected = true, onClick = { vm.dateOff = true },
                    // A moment already gone by is still saved (as typed), but won't ring: the chip says so.
                    label = { Text("⏰ ${style.dayLabel(found.date)} ${LocalTimeStyle.current.format(found.time ?: java.time.LocalTime.of(9, 0))}" + if (found.passed) " · passed, won't remind" else "") },
                    trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Keep as text", Modifier.size(18.dp)) },
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
            if (state.loaded && state.todos.isEmpty()) {
                EmptyState(Icons.Default.CheckCircle, "No tasks yet", "Type one above. Tap a task to add details, a priority or a reminder.")
                return@Column
            }
            // Open tasks in the chosen order (see the sort button); ticked ones below, latest first.
            val open = Sorting.tasks(state.todos.filter { !it.done }, sort)
            val done = state.todos.filter { it.done }.sortedByDescending { it.doneAt }
            ProgressLine(done.size, state.todos.size, "done", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            // Ticking off the last open task: a little party.
            val tick: (Todo, Boolean) -> Unit = { t, now ->
                // Not for a repeating task: it comes back with its next date, so there's still something to do.
                if (now && !t.done && open.size == 1 && nextDue(t) == null) party.fire(Celebration.TASKS.random())
                vm.setDone(t, now)
            }
            val list = rememberLastScroll("tasks")
            val byId = open.associateBy { it.id }
            val order = rememberDragOrder(open.map { it.id }, sort == TaskSort.MINE)
            val reorder = rememberReorderableLazyListState(list) { from, to ->
                val a = (from.key as? String)?.removePrefix("t:")?.toLongOrNull()
                val b = (to.key as? String)?.removePrefix("t:")?.toLongOrNull()
                if (a != null && b != null) order.move(a, b)
            }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "tip") {
                    OneTimeTip("tasks", "Tap a task to add details, a priority or a reminder. Drag ≡ to reorder; long-press to select.")
                }
                items(order.ids.mapNotNull { byId[it] }, key = { "t:${it.id}" }) { t ->
                    ReorderableItem(reorder, key = "t:${t.id}", enabled = canArrange) { dragging ->
                        // Swipe right to tick, left to delete.
                        SwipeActions(!selection.active && !dragging, t.done, onTick = { tick(t, true) }, onDelete = { vm.delete(t.id) }) { actions ->
                        RowCard(dragging = dragging) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TodoLine(t, onCheck = { tick(t, it) }, Modifier.weight(1f).selectable(selection, t.id) { onOpen(t.id) }.then(actions))
                                if (canArrange) DragHandleIcon(t.title, Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrange) }).padding(12.dp))
                            }
                        }
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "h:done") {
                        CompletedHeader("Done (${done.size})", showDone, vm::toggleShowDone) {
                            if (!selection.active) TextButton(onClick = { vm.clearDone() }) { Text("Clear done") }
                        }
                    }
                    if (showDone) items(done, key = { "t:${it.id}" }) { t ->
                        SwipeActions(!selection.active, t.done, onTick = { vm.setDone(t, false) }, onDelete = { vm.delete(t.id) }) { actions ->
                            RowCard(faded = true) {
                                TodoLine(t, onCheck = { vm.setDone(t, it) }, Modifier.selectable(selection, t.id) { onOpen(t.id) }.then(actions))
                            }
                        }
                    }
                }
            }
        }
    }
    CelebrationOverlay(party)
}

/**
 * The top bar while searching (Tasks and Shopping): the field, ← to close, ✕ to clear. The cursor starts after any
 * prefilled text; the keyboard comes up when the search opens (not again on coming back to it), and its Search key hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, placeholder: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    // The query can change from outside (✕): then put the cursor at its end.
    val value = if (field.text == query) field else TextFieldValue(query, TextRange(query.length))
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        title = {
            TextField(
                value = value, onValueChange = { field = it; if (it.text != query) onChange(it.text) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(placeholder) }, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            LaunchedEffect(Unit) { if (!focused) { focused = true; focus.requestFocus() } }
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") } },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
        },
    )
}

/** Tasks search results: matching tasks (open first, then done), the matches highlighted; a tap opens one. */
@Composable
private fun TaskResults(query: String, results: List<Todo>, onCheck: (Todo, Boolean) -> Unit, onOpen: (Long) -> Unit) {
    if (query.isBlank()) {
        EmptyState(Icons.Default.Search, "Search your tasks", "Type to find tasks by their title or details, done ones too.")
        return
    }
    if (results.isEmpty()) {
        EmptyState(Icons.Default.Search, "Nothing found", "No task matches \"${query.trim()}\".")
        return
    }
    val terms = Search.terms(query, limit = Int.MAX_VALUE)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results, key = { it.id }) { t ->
            RowCard(faded = t.done) {
                TodoLine(t, onCheck = { onCheck(t, it) }, Modifier.clickable { onOpen(t.id) }, terms)
            }
        }
    }
}

/** A task's reminder time, when it has one (and isn't done). */
private fun Todo.reminder(): LocalDateTime? = AlarmPlanner.todoTime(remind, done, dueDate, dueTime)

@Composable
private fun TodoLine(t: Todo, onCheck: (Boolean) -> Unit, modifier: Modifier, terms: List<String> = emptyList()) {
    Row(Modifier.fillMaxWidth().then(modifier).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FunCheckbox(checked = t.done, onCheckedChange = onCheck)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.priority == 2) Text("! ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(
                    highlighted(t.title, terms),
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.priority == 0 || t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            val style = LocalDateStyle.current
            val clock = LocalTimeStyle.current
            val reminder = t.reminder()?.let { at -> "⏰ ${style.dayLabel(at.toLocalDate())} ${clock.format(at.toLocalTime())}" + if (Repeat.of(t.repeat) != Repeat.NONE) " ↻" else "" }
            // Searching: the part of the details with the match (as typed); otherwise their first line.
            val about = if (terms.isNotEmpty() && Search.matches(t.details, terms).isNotEmpty()) Search.snippet(t.details, terms, 60, readable = false)
                else t.details.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)
            // Only the details are highlighted: "Today" in the reminder isn't a match.
            val aboutText = about?.let { highlighted(it, terms) }
            if (reminder != null || aboutText != null) {
                Text(
                    buildAnnotatedString {
                        reminder?.let { append(it) }
                        if (reminder != null && aboutText != null) append("  ·  ")
                        aboutText?.let { append(it) }
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(vm: TodoEditorViewModel, setting: ReminderSound, onBack: () -> Unit) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var addReminder by rememberSaveable { mutableStateOf(false) }
    var newDate by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.finish() }
    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }
    val t = vm.todo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task") },
                navigationIcon = { BackButton(onBack) },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete task") } },
            )
        },
    ) { padding ->
        if (t == null) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FunCheckbox(checked = t.done, onCheckedChange = { vm.setDone(it) })
                OutlinedTextField(
                    t.title, { v -> vm.update(text = true) { it.copy(title = v) } }, Modifier.weight(1f),
                    label = { Text("Task") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            OutlinedTextField(
                t.details, { v -> vm.update(text = true) { it.copy(details = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Details") }, minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("Priority")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(0 to "Low", 1 to "Normal", 2 to "High").forEachIndexed { i, (p, label) ->
                    SegmentedButton(selected = t.priority == p, onClick = { vm.update { it.copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
            HorizontalDivider()
            val at = if (t.remind) parseDate(t.dueDate)?.atTime(parseTime(t.dueTime) ?: LocalTime.of(9, 0)) else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                if (at == null) {
                    Text("Reminder", Modifier.weight(1f))
                    // Adding one: the date, then the time.
                    OutlinedButton(onClick = { addReminder = true; pickDate = true }) { Text("Add") }
                } else {
                    OutlinedButton(onClick = { pickDate = true }) { Text(LocalDateStyle.current.format(at.toLocalDate())) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { pickTime = true }) { Text(LocalTimeStyle.current.format(at.toLocalTime())) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.update { it.copy(dueDate = null, dueTime = null, remind = false, repeat = Repeat.NONE.name) } }) {
                        Icon(Icons.Default.Clear, contentDescription = "Remove reminder")
                    }
                }
            }
            if (at != null) {
                if (!t.done && at.isBefore(LocalDateTime.now())) {
                    Text("That time has passed, so it won't remind.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ChoiceRow("Sound", soundLabel(ReminderSound.of(t.sound), setting), ReminderSound.entries, { soundLabel(it, setting) }) { s ->
                    vm.update { it.copy(sound = s.name) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        ChoiceRow("Repeat", Repeat.of(t.repeat).label, Repeat.entries, { it.label }) { r -> vm.update { it.copy(repeat = r.name) } }
                    }
                }
                if (Repeat.of(t.repeat) == Repeat.EVERY_N_DAYS) {
                    OutlinedTextField(
                        t.repeatEvery.toString(),
                        { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let { n -> vm.update(text = true) { it.copy(repeatEvery = n.coerceAtLeast(1)) } } },
                        label = { Text("Every how many days") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (Repeat.of(t.repeat) != Repeat.NONE) Hint("Ticking it off moves the reminder to its next time.")
            }
        }
        if (pickDate) DatePickDialog(
            if (t.remind) parseDate(t.dueDate) else null,
            onPick = { d ->
                if (addReminder) {
                    // Kept for the time picker that follows; the reminder is saved once its time is chosen.
                    newDate = d.toString()
                    pickTime = true
                } else vm.update { it.copy(dueDate = d.toString()) }
            },
            onDismiss = { pickDate = false; if (!pickTime) addReminder = false },
        )
        if (pickTime) TimePickDialog(
            if (addReminder) LocalTime.of(9, 0) else parseTime(t.dueTime) ?: LocalTime.of(9, 0),
            onPick = { tm ->
                val date = newDate
                if (addReminder && date != null) vm.update { it.copy(dueDate = date, dueTime = tm.toString(), remind = true) }
                else vm.update { it.copy(dueTime = tm.toString()) }
            },
            onDismiss = { pickTime = false; addReminder = false; newDate = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text("It goes to the recycle bin, where it can be restored.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------- Shopping ----------

/** A list is its date (Today, Tomorrow or in the chosen format); list 1 without a date is "Anytime". */
fun ShoppingList.label(style: DateStyle, relative: Boolean = true): String =
    parseDate(date)?.let { if (relative) style.dayLabel(it) else style.format(it) } ?: if (id == 1L) "Anytime" else name

/** A day as the Shopping screen names it: Today, Tomorrow, Yesterday, else its weekday ("Saturday"). */
private fun dayName(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
}

/** "Sat 21 Nov" (with the year when it isn't this year's): where things were moved, for the Undo bar. */
private fun shortDay(day: LocalDate?, today: LocalDate): String = when (day) {
    null -> "Anytime"
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    else -> DateStyle.SHORT.format(day, today)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel, setting: ReminderSound, onBack: () -> Unit, bottomBar: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val listId by vm.listId.collectAsStateWithLifecycle()
    val style = LocalDateStyle.current
    val today = rememberToday()
    // Midnight (or back after a day away): carry over and tidy as on opening, and "today" moves on with the date.
    var seenDay by rememberSaveable { mutableStateOf(today.toString()) }
    LaunchedEffect(today) {
        if (today.toString() != seenDay) {
            if (day?.toString() == seenDay) vm.show(today)
            seenDay = today.toString()
            vm.dayChanged()
        }
    }
    val list = state.lists.firstOrNull { it.id == listId }
    // ✎: the day's date and reminder (a day without a list gets one when saved). Kept through a rotation.
    var editing by rememberSaveable { mutableStateOf(false) }
    // The item being renamed (kept through a rotation); closes if the item goes or another day is shown.
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val renaming = renamingId?.let { id -> (state.items + state.anytime).firstOrNull { it.id == id } }
    LaunchedEffect(state.loaded, renaming == null) { if (state.loaded && renamingId != null && renaming == null) renamingId = null }
    val itemSort by vm.itemSort.collectAsStateWithLifecycle()
    val showTicked by vm.showTicked.collectAsStateWithLifecycle()
    // One per day: switching days ends a party that belongs to the last one.
    val party = remember(day) { Celebration() }
    val open = Sorting.items(state.items.filter { !it.checked }, itemSort)
    val checked = state.items.filter { it.checked }
    // On today: what is on Anytime shows below, to tick off in the shop too.
    val anytimeOpen = if (day == today) Sorting.items(state.anytime.filter { !it.checked }, itemSort) else emptyList()
    val selection = vm.selection
    val shown = open + (if (showTicked) checked else emptyList()) + anytimeOpen
    LaunchedEffect(shown.map { it.id }) { if (state.loaded) selection.keepOnly(shown.map { it.id }) }
    var moving by rememberSaveable { mutableStateOf(false) }
    val openCounts by vm.openCounts.collectAsStateWithLifecycle()
    // Days with something still to buy: a dot in the strip.
    val marked = state.lists.mapNotNull { l -> parseDate(l.date)?.takeIf { (openCounts[l.id] ?: 0) > 0 } }.toSet()
    val anytimeCount = openCounts[1L] ?: 0
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    BackHandler(enabled = query != null) { vm.searchQuery.value = null }

    Scaffold(
        topBar = {
            if (selection.active) SelectionBar(selection, shown.map { it.id }) {
                IconButton(onClick = { moving = true }) { Icon(Icons.Default.DateRange, contentDescription = "Move to another day") }
                IconButton(onClick = { vm.deleteSelected(shown) }) { Icon(Icons.Default.Delete, contentDescription = "Remove items") }
            } else if (query != null) SearchTopBar(query.orEmpty(), "Search all lists", { vm.searchQuery.value = it }) { vm.searchQuery.value = null }
            else TopAppBar(
                title = { Text("Shopping") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (day != null) IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, contentDescription = "Day and reminder") }
                    SortButton(
                        SortGroup("Items", ItemSort.entries, itemSort, { it.label }, vm::setItemSort),
                        extra = { close -> ShowCompletedItem(showTicked) { close(); vm.toggleShowTicked() } },
                    )
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (query != null) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                SearchResults(query.orEmpty(), results, orderLists(state.lists, today), { it.label(style) }) { id ->
                    state.lists.firstOrNull { it.id == id }?.let(vm::openList)
                    vm.searchQuery.value = null
                }
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            WeekStrip(today, day, marked, anytimeCount, onPick = vm::show, revision = vm.shownTimes, calendar = true)
            DayHeader(day, today, list, done = checked.size, total = state.items.size)
            // Suggestions pop up right under the box while typing (the box keeps the keyboard).
            val suggestions = vm.suggestions(state, withAnytime = day == today)
            var fieldWidth by remember { mutableIntStateOf(0) }
            var focused by remember { mutableStateOf(false) }
            val hint = when (day) {
                null -> "Add to Anytime, e.g. batteries"
                today -> "Add for today, e.g. 2x milk"
                else -> "Add for ${day?.let { dayName(it, today) }}, e.g. 2x milk"
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                TextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.fillMaxWidth().onSizeChanged { fieldWidth = it.width }.onFocusChanged { focused = it.isFocused },
                    shape = PillShape, colors = pillColors(),
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    // Done adds the item and keeps the keyboard open for the next one.
                    keyboardActions = KeyboardActions(onDone = { vm.add() }),
                    // On the right: add what is typed, and search all lists (for what is typed, if anything).
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (vm.input.isNotBlank()) IconButton(onClick = { vm.add() }) { Icon(Icons.Default.Check, contentDescription = "Add") }
                            IconButton(onClick = { vm.searchQuery.value = Shopping.parse(vm.input)?.name.orEmpty() }) { Icon(Icons.Default.Search, contentDescription = "Search all lists") }
                        }
                    },
                )
                SuggestionsPopup(if (focused && !selection.active && !moving) suggestions else emptyList(), vm.input, fieldWidth, onPick = vm::pick, onForget = { vm.forget(it) })
            }
            if (state.loaded && state.items.isEmpty() && anytimeOpen.isEmpty()) {
                val d = day
                if (d == null) EmptyState(Icons.Default.ShoppingCart, "Nothing on Anytime", "Things to buy whenever go here.\nThey show on today's list too.")
                else EmptyState(Icons.Default.DateRange, "Nothing planned yet", "Type above to start ${if (d == today) "today" else dayName(d, today)}'s list.\nA day with shopping gets a dot.")
                return@Column
            }
            val scroll = rememberLastScroll("shopping:${day ?: ANYTIME}")
            val canArrange = !selection.active
            val byId = open.associateBy { it.id }
            val order = key(listId) { rememberDragOrder(open.map { it.id }, itemSort == ItemSort.MINE) }
            val reorder = rememberReorderableLazyListState(scroll) { from, to ->
                val a = from.key as? Long
                val b = to.key as? Long
                // Only among the day's items still to buy.
                if (a != null && b != null && a in byId && b in byId) order.move(a, b)
            }
            LazyColumn(Modifier.fillMaxSize(), scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "tip") {
                    OneTimeTip("shopping-week", "Tap a day to plan its shopping. Long-press items to move them to another day; tap one to rename it. Things not bought move to today by themselves.")
                }
                items(order.ids.mapNotNull { byId[it] }, key = { it.id }) { item ->
                    ReorderableItem(reorder, key = item.id, enabled = canArrange) { dragging ->
                        val check: (Boolean) -> Unit = { now ->
                            // Ticking off the last thing to buy: a little party.
                            if (now && open.size == 1) party.fire(Celebration.SHOPPING.random())
                            vm.setChecked(item, now)
                        }
                        // Swipe right to tick, left to remove.
                        SwipeActions(!selection.active && !dragging, item.checked, onTick = { check(true) }, onDelete = { vm.delete(item) }) { actions ->
                            RowCard(dragging = dragging) {
                                ItemLine(item, selection, onCheck = check, onDelete = { vm.delete(item) }, onEdit = { renamingId = item.id }, onStep = { vm.step(item, it) }, actions = actions) {
                                    if (canArrange) DragHandleIcon(item.name, Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrangeItems) }).padding(12.dp))
                                }
                            }
                        }
                    }
                }
                if (anytimeOpen.isNotEmpty()) {
                    item(key = "h:anytime") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Also on Anytime (${anytimeOpen.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text("buy whenever", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(anytimeOpen, key = { "a:${it.id}" }) { item ->
                        SwipeActions(!selection.active, item.checked, onTick = { vm.setChecked(item, true) }, onDelete = { vm.delete(item) }) { actions ->
                            RowCard(faded = true) {
                                ItemLine(item, selection, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) }, onEdit = { renamingId = item.id }, onStep = { vm.step(item, it) }, actions = actions)
                            }
                        }
                    }
                }
                if (checked.isNotEmpty()) {
                    item(key = "h:basket") {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        CompletedHeader("In the basket (${checked.size})", showTicked, vm::toggleShowTicked) {
                            if (!selection.active) TextButton(onClick = { vm.clearChecked() }) { Text("Clear ticked") }
                        }
                    }
                    if (showTicked) items(checked, key = { it.id }) { item ->
                        // Ticked already: swiping right unticks it.
                        SwipeActions(!selection.active, item.checked, onTick = { vm.setChecked(item, false) }, onDelete = { vm.delete(item) }) { actions ->
                            RowCard(faded = true) {
                                ItemLine(item, selection, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) }, onEdit = { renamingId = item.id }, onStep = { vm.step(item, it) }, actions = actions)
                            }
                        }
                    }
                }
            }
        }
    }
    val d = day
    if (editing && d != null) {
        val l = list ?: ShoppingList(name = d.toString(), date = d.toString())
        key(l.id, l.date) {
            ShoppingListDialog(
                l, setting, taken = state.lists.filter { it.id != l.id }.mapNotNull { it.date }.toSet(),
                onSave = { saved ->
                    // A day without a list gets one only for a reminder: otherwise a list comes with its first item.
                    if (saved.id != 0L || saved.remindAt != null) vm.saveList(saved)
                    else parseDate(saved.date)?.let { if (it != d) vm.show(it) }
                },
                onDelete = { vm.deleteList(it) }, onDismiss = { editing = false },
            )
        }
    }
    // Where the selected items are now: a day that holds them all can't be picked (nothing would move).
    val sources = shown.filter { it.id in selection.ids }.map { if (it.listId == 1L) null else day }.toSet()
    if (moving && selection.active) MoveSheet(
        count = selection.ids.size, today = today, from = sources.singleOrNull(), fromKnown = sources.size == 1,
        marked = marked, anytimeCount = anytimeCount,
        onMove = { to -> moving = false; vm.moveSelected(shown, to, shortDay(to, today)) },
        onDismiss = { moving = false },
    )
    renaming?.let { item ->
        val others = (if (item.listId == 1L) state.anytime else state.items).filter { it.id != item.id }.map { it.key }.toSet()
        EditItemDialog(item, others = others, onSave = { vm.rename(item, it) }) { renamingId = null }
    }
    if (vm.nameTaken) {
        AlertDialog(
            onDismissRequest = { vm.nameTaken = false },
            title = { Text("Not renamed") },
            text = { Text("That's already on this list.") },
            confirmButton = { TextButton(onClick = { vm.nameTaken = false }) { Text("OK") } },
        )
    }
    CelebrationOverlay(party)
    if (vm.dateTaken) {
        AlertDialog(
            onDismissRequest = { vm.dateTaken = false },
            title = { Text("Not saved") },
            text = { Text("That day already has a list. Long-press items to move them there instead.") },
            confirmButton = { TextButton(onClick = { vm.dateTaken = false }) { Text("OK") } },
        )
    }
}

/** Pages in the week strip: this week in the middle, about 19 years either side. */
private const val WEEKS = 2001
private const val THIS_WEEK = 1000

/**
 * The Anytime tile and a week of days (swipe, or ‹ ›, for others): [selected] filled (null: Anytime), today ringed,
 * [marked] days with a dot. "Today" comes back to today. [allowPast] false: days gone by can't be picked (Move to…).
 */
@Composable
private fun WeekStrip(
    today: LocalDate,
    selected: LocalDate?,
    marked: Set<LocalDate>,
    anytimeCount: Int,
    onPick: (LocalDate?) -> Unit,
    allowPast: Boolean = true,
    anytimeSelected: Boolean = selected == null,
    revision: Int = 0,
    calendar: Boolean = false,
) {
    val first = remember { Week.firstDay() }
    var monthOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    key(today) {
        val pager = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = (THIS_WEEK + (selected?.let { Week.offset(today, it, first) } ?: 0)).coerceIn(0, WEEKS - 1),
        ) { WEEKS }
        // Showing a day (a search result, Today, even the same day again) brings its week into view.
        LaunchedEffect(selected, revision) {
            val page = THIS_WEEK + (selected?.let { Week.offset(today, it, first) } ?: return@LaunchedEffect)
            if (page in 0 until WEEKS && pager.currentPage != page) pager.animateScrollToPage(page)
        }
        val shownStart = Week.startAt(today, pager.currentPage - THIS_WEEK, first)
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // With [calendar], the month opens a month calendar (the Move sheet's strip has its own date picker).
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).then(
                        if (calendar) Modifier.clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "Open the month calendar") { focusManager.clearFocus(); monthOpen = true } else Modifier,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Week.monthLabel(shownStart), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (calendar) Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (pager.currentPage != THIS_WEEK || (selected != today && allowPast)) {
                    OutlinedButton(
                        onClick = { onPick(today); scope.launch { pager.animateScrollToPage(THIS_WEEK) } },
                        contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 32.dp),
                    ) { Text("Go to today", maxLines = 1) }
                }
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(WEEKS - 1)) } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                AnytimeTile(anytimeSelected, anytimeCount) { onPick(null) }
                Box(Modifier.padding(horizontal = 6.dp, vertical = 6.dp).width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant))
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f)) { page ->
                    val start = Week.startAt(today, page - THIS_WEEK, first)
                    Row(Modifier.fillMaxWidth()) {
                        Week.days(start).forEach { date ->
                            DayCell(
                                Modifier.weight(1f),
                                date, today, picked = date == selected && !anytimeSelected, marked = date in marked,
                                enabled = allowPast || !date.isBefore(today),
                            ) { onPick(date) }
                        }
                    }
                }
            }
        }
        // Opens on the month of the day shown (or today) when it's in the shown week, else the week's middle day's.
        val anchor = listOfNotNull(selected.takeIf { !anytimeSelected }, today)
            .firstOrNull { !it.isBefore(shownStart) && it.isBefore(shownStart.plusDays(7)) } ?: shownStart.plusDays(3)
        if (monthOpen) MonthCalendar(
            start = java.time.YearMonth.from(anchor), today = today, first = first,
            // Only days the strip can show.
            range = Week.startAt(today, -THIS_WEEK, first)..Week.startAt(today, WEEKS - 1 - THIS_WEEK, first).plusDays(6),
            selected = selected.takeIf { !anytimeSelected }, marked = marked,
            onPick = { date ->
                monthOpen = false
                onPick(date)
                scope.launch { pager.animateScrollToPage((THIS_WEEK + Week.offset(today, date, first)).coerceIn(0, WEEKS - 1)) }
            },
            onDismiss = { monthOpen = false },
        )
    }
}

/**
 * The month calendar (tap the month over the week strip), a popup in the middle of the screen: swipe (or ‹ ›) for
 * other months, a dot on each day with something to buy. Tapping a day shows it; *Go to today* shows today. Only the
 * days the strip can show ([range]).
 */
@Composable
private fun MonthCalendar(
    start: java.time.YearMonth, today: LocalDate, first: java.time.DayOfWeek, selected: LocalDate?, marked: Set<LocalDate>,
    range: ClosedRange<LocalDate>, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit,
) {
    val locale = java.util.Locale.getDefault()
    val firstMonth = java.time.YearMonth.from(range.start)
    val months = Week.monthsBetween(firstMonth, java.time.YearMonth.from(range.endInclusive)) + 1
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = Week.monthsBetween(firstMonth, start).coerceIn(0, months - 1),
    ) { months }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shown = firstMonth.plusMonths(pager.currentPage.toLong())
    // A card in the middle with a margin each side (not the platform's dialog width, which can fill a phone). The
    // window fills the screen, so a tap anywhere around the card closes it, and the window doesn't resize with a month.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
        androidx.compose.material3.Surface(
            // Taps on the card itself (its title, blank cells, a disabled day) don't reach the close-on-tap around it.
            modifier = Modifier.widthIn(max = 420.dp).pointerInput(Unit) { detectTapGestures { } },
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp,
        ) {
            // Scrolls when the screen is too short for it (a phone on its side).
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.targetPage - 1) } }, enabled = pager.targetPage > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(shown),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.targetPage + 1) } }, enabled = pager.targetPage < months - 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Week.days(Week.start(today, first)).forEach { d ->
                        Text(
                            d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale), Modifier.weight(1f).clearAndSetSemantics { },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                }
                // Swipe for other months; every month gets six rows, so the popup keeps its height.
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().testTag("month-pager")) { page ->
                    val grid = Week.monthGrid(firstMonth.plusMonths(page.toLong()), first)
                    Column {
                        (grid + List(42 - grid.size) { null }).chunked(7).forEach { week ->
                            // Every row the same height, with or without days in it.
                            Row(Modifier.fillMaxWidth().height(50.dp)) {
                                week.forEach { date ->
                                    if (date == null) Spacer(Modifier.weight(1f))
                                    else MonthDay(
                                        Modifier.weight(1f), date, today, picked = date == selected, marked = date in marked, enabled = date in range,
                                    ) { onPick(date) }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onPick(today) }) { Text("Go to today") }
                }
            }
        }
        }
    }
}

/** A day in the month calendar: as in the strip, today ringed, the shown day filled, a dot when it has shopping. */
@Composable
private fun MonthDay(modifier: Modifier, date: LocalDate, today: LocalDate, picked: Boolean, marked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val locale = java.util.Locale.getDefault()
    Column(
        modifier.heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)).append(' ')
                    append(DateStyle.SHORT.format(date, today))
                    if (date == today) append(", today")
                    if (marked) append(", has shopping")
                }
                selected = picked
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 3.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(36.dp)
                .background(if (picked) c.primary else Color.Transparent, CircleShape)
                .border(2.dp, if (date == today && !picked) c.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                color = when {
                    picked -> c.onPrimary
                    date.isBefore(today) -> c.onSurfaceVariant
                    else -> c.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(5.dp).background(if (marked) c.primary else Color.Transparent, CircleShape))
    }
}

@Composable
private fun AnytimeTile(picked: Boolean, count: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box {
        Column(
            Modifier.size(width = 56.dp, height = 64.dp)
                .background(if (picked) c.primaryContainer else c.surfaceContainerLowest, MaterialTheme.shapes.medium)
                .border(1.5.dp, if (picked) c.primary else c.outlineVariant, MaterialTheme.shapes.medium)
                .clearAndSetSemantics {
                    contentDescription = "Anytime" + if (count > 0) ", $count to buy" else ""
                    selected = picked
                    role = Role.Tab
                    onClick { onClick(); true }
                }
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("∞", style = MaterialTheme.typography.titleLarge, color = if (picked) c.onPrimaryContainer else c.onSurfaceVariant)
            Text("Anytime", style = MaterialTheme.typography.labelSmall, color = if (picked) c.onPrimaryContainer else c.onSurfaceVariant)
        }
        if (count > 0) {
            Text(
                count.toString(), style = MaterialTheme.typography.labelSmall, color = c.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                    .background(c.primary, PillShape).padding(horizontal = 6.dp, vertical = 1.dp).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier, date: LocalDate, today: LocalDate, picked: Boolean, marked: Boolean, enabled: Boolean, onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val locale = java.util.Locale.getDefault()
    val past = date.isBefore(today)
    val name = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    Column(
        modifier.heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)).append(' ')
                    append(DateStyle.SHORT.format(date, today))
                    if (date == today) append(", today")
                    if (marked) append(", has shopping")
                }
                selected = picked
                role = Role.Tab
                if (enabled) onClick { onClick(); true } else disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = if (date == today) c.primary else c.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(34.dp)
                .background(if (picked) c.primary else Color.Transparent, CircleShape)
                .border(2.dp, if (date == today && !picked) c.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                color = when {
                    picked -> c.onPrimary
                    past -> c.onSurfaceVariant
                    else -> c.onSurface
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(5.dp).background(if (marked) c.primary else Color.Transparent, CircleShape))
    }
}

/** "Today" / "Saturday" over the date and its reminder, with how much is bought. */
@Composable
private fun DayHeader(day: LocalDate?, today: LocalDate, list: ShoppingList?, done: Int, total: Int) {
    val style = LocalDateStyle.current
    val clock = LocalTimeStyle.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day?.let { dayName(it, today) } ?: "Anytime", style = MaterialTheme.typography.titleLarge)
            val sub = if (day == null) "Buy whenever: shown on today's list too" else buildString {
                append(style.format(day, today))
                parseTime(list?.remindAt)?.let { append(" · ⏰ ").append(clock.format(it)) }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProgressLine(done, total, "bought")
    }
}

/** Move to…: Anytime, Today, Tomorrow, any day in the strip, or another date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(
    count: Int,
    today: LocalDate,
    from: LocalDate?,
    fromKnown: Boolean,
    marked: Set<LocalDate>,
    anytimeCount: Int,
    onMove: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    // [from] holds every selected item: moving there would do nothing. A day gone by isn't offered (things not bought
    // come back to today by themselves).
    val can = { to: LocalDate? -> !(fromKnown && to == from) && (to == null || !to.isBefore(today)) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Move ${if (count == 1) "1 item" else "$count items"} to…",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Pair<String, LocalDate?>>("Anytime" to null, "Today" to today, "Tomorrow" to today.plusDays(1)).forEach { (label, to) ->
                    FilterChip(selected = false, enabled = can(to), onClick = { onMove(to) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            WeekStrip(
                today, selected = if (fromKnown) from else null, marked = marked, anytimeCount = anytimeCount,
                onPick = { to -> if (can(to)) onMove(to) }, allowPast = false, anytimeSelected = fromKnown && from == null,
            )
            TextButton(onClick = { pickDate = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Another date…") }
        }
    }
    if (pickDate) DatePickDialog(null, onPick = { if (can(it)) onMove(it) }, onDismiss = { pickDate = false }, earliest = today)
}

/**
 * A day's list settings (✎): its date (one list per day, [taken] are the others'), that day an optional reminder, or
 * delete it. A day without a list yet ([initial] id 0) gets one when saved.
 */
@Composable
private fun ShoppingListDialog(
    initial: ShoppingList,
    setting: ReminderSound,
    taken: Set<String>,
    onSave: (ShoppingList) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val style = LocalDateStyle.current
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var time by rememberSaveable { mutableStateOf(initial.remindAt) }
    var sound by rememberSaveable { mutableStateOf(initial.sound) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val past = parseDate(date)?.let { d -> parseTime(time)?.let { d.atTime(it).isBefore(LocalDateTime.now()) } } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(parseDate(initial.date)?.let { style.format(it) } ?: "Shopping list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", Modifier.weight(1f))
                    OutlinedButton(onClick = { pickDate = true }) { Text(parseDate(date)?.let { style.format(it) } ?: "Set a date") }
                }
                if (date in taken) Text("There's already a list for that day.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (date != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reminder", Modifier.weight(1f))
                        OutlinedButton(onClick = { pickTime = true }) { Text(parseTime(time)?.let { LocalTimeStyle.current.format(it) } ?: "Add") }
                        if (time != null) IconButton(onClick = { time = null }) { Icon(Icons.Default.Clear, contentDescription = "No reminder") }
                    }
                    if (time != null) {
                        ChoiceRow("Sound", soundLabel(ReminderSound.of(sound), setting), ReminderSound.entries, { soundLabel(it, setting) }) { sound = it.name }
                    }
                    if (past) Text("That time has passed, so it won't remind.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (initial.id > 1L) TextButton(onClick = { confirmDelete = true }) { Text("Delete this list") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(name = date.orEmpty(), date = date, remindAt = time.takeIf { date != null }, sound = sound))
                onDismiss()
            }, enabled = date != null && date !in taken) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) DatePickDialog(parseDate(date), onPick = { date = it.toString() }, onDismiss = { pickDate = false })
    if (pickTime) TimePickDialog(parseTime(time), onPick = { time = it.toString() }, onDismiss = { pickTime = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the list for ${initial.label(style, relative = false)}?") },
            text = { Text("It goes to the recycle bin with its items, where it can be restored.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial.id); onDismiss() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * An item: only the box ticks; a tap on the name renames it (no ticking by accident while scrolling or dragging); a long
 * press selects it (to move to another day, or remove), after which taps select too.
 */
@Composable
private fun ItemLine(
    item: ShoppingItem,
    selection: Selection,
    onCheck: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onStep: (Int) -> Unit = {},
    actions: Modifier = Modifier,
    handle: @Composable () -> Unit = {},
) {
    // Tap the quantity for – / + (they go again after a few seconds untouched).
    var stepping by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(stepping, item.quantity) { if (stepping) { kotlinx.coroutines.delay(4_000); stepping = false } }
    val canStep = !selection.active && Shopping.step(item.quantity, 1) != null
    Row(Modifier.fillMaxWidth().selectable(selection, item.id, onEdit).then(actions), verticalAlignment = Alignment.CenterVertically) {
        FunCheckbox(checked = item.checked, onCheckedChange = onCheck)
        Text(
            item.name,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (stepping && canStep) {
            IconButton(onClick = { onStep(-1) }, Modifier.semantics { contentDescription = "One less ${item.name}" }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (item.quantity.isNotEmpty()) {
            Text(
                item.quantity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = if (canStep) Modifier.clip(PillShape).clickable(onClickLabel = "Change the quantity") { stepping = !stepping }
                    .padding(horizontal = 8.dp, vertical = 12.dp) else Modifier,
            )
        }
        if (stepping && canStep) {
            IconButton(onClick = { onStep(1) }) { Icon(Icons.Default.Add, contentDescription = "One more ${item.name}") }
        }
        if (!selection.active) IconButton(onClick = onDelete) { Icon(Icons.Default.Clear, contentDescription = "Remove ${item.name}") }
        handle()
    }
}

/**
 * Past items matching what is typed, in a small menu under the add box ([width] px wide): tap one to add it, ✕ to
 * forget it. It doesn't take the keyboard; tapping elsewhere hides it until the text changes.
 */
@Composable
private fun SuggestionsPopup(
    suggestions: List<PastItem>,
    input: String,
    width: Int,
    onPick: (PastItem) -> Unit,
    onForget: (PastItem) -> Unit,
) {
    // Hidden by a tap elsewhere until the text changes.
    var dismissed by remember(input) { mutableStateOf(false) }
    val typed = Shopping.parse(input)?.name.orEmpty()
    DropdownMenu(
        expanded = suggestions.isNotEmpty() && !dismissed,
        onDismissRequest = { dismissed = true },
        properties = PopupProperties(focusable = false),
        modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
    ) {
        suggestions.forEach { s ->
            DropdownMenuItem(
                text = { Text(boldMatch(s.name, typed)) },
                onClick = { onPick(s) },
                trailingIcon = {
                    IconButton(onClick = { onForget(s) }) { Icon(Icons.Default.Clear, contentDescription = "Forget ${s.name}", Modifier.size(18.dp)) }
                },
            )
        }
    }
}

/** [name] with the part matching [typed] in bold ("mi" in "Oat milk"). */
private fun boldMatch(name: String, typed: String): AnnotatedString = buildAnnotatedString {
    val match = Shopping.matchIn(name, typed)
    if (match == null) { append(name); return@buildAnnotatedString }
    append(name.substring(0, match.first))
    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name.substring(match)) }
    append(name.substring(match.last + 1))
}

/** Rename an item: its name and quantity as typed ("2x oat milk"); [others] are the keys of the list's other items. */
@Composable
private fun EditItemDialog(item: ShoppingItem, others: Set<String>, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val start = Shopping.editText(item.name, item.quantity)
    // Started afresh if the item changes underneath (say a backup restored meanwhile).
    var text by rememberSaveable(item.id, item.key, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(start, selection = TextRange(0, start.length)))
    }
    val parsed = Shopping.parse(text.text)
    val taken = parsed != null && Shopping.key(parsed.name) in others
    val focus = remember { FocusRequester() }
    val save = { if (parsed != null && !taken) { onSave(text.text); onDismiss() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true, isError = taken,
                    supportingText = { Text(if (taken) "That's already on this list." else "A quantity can go with it, e.g. 2x milk.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = save, enabled = parsed != null && !taken) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Done (3)  Show" / "Hide": the line over completed rows, which are hidden to start with. [extra]: Clear done, say. */
@Composable
private fun CompletedHeader(title: String, shown: Boolean, onToggle: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onToggle) { Text(if (shown) "Hide" else "Show") }
        extra()
    }
}

/** The sort menu's "Show completed" switch (ticked when they show). */
@Composable
private fun ShowCompletedItem(shown: Boolean, onClick: () -> Unit) {
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Show completed") }, onClick = onClick,
        trailingIcon = { if (shown) Icon(Icons.Default.Check, contentDescription = "On") },
    )
}
