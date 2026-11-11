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
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import io.github.baslawson.smartnotes.data.isPast
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
import io.github.baslawson.smartnotes.data.ListSort
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
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.baslawson.smartnotes.parse.Shopping
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalTime
import java.time.ZoneOffset

// ---------- Shared pieces ----------

/** Settings → Date format, provided around the whole app. */
val LocalDateStyle = staticCompositionLocalOf { DateStyle.DEFAULT }

private fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun parseTime(s: String?): LocalTime? = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/** The back arrow every screen but Notes has in its top bar. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
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
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
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

@Composable
private fun ListChips(
    names: List<Pair<Long, String>>,
    selected: Long,
    selection: Selection,
    onSelect: (Long) -> Unit,
    pastCount: Int,
    onPast: () -> Unit,
    onAdd: () -> Unit,
) {
    val row = rememberLazyListState()
    // The list being viewed is scrolled into sight (when it changes, and once the lists are there).
    val index = names.indexOfFirst { it.first == selected }
    LaunchedEffect(selected, index) {
        if (index < 0) return@LaunchedEffect
        // After the row has laid out its chips (on first showing it may not have yet).
        snapshotFlow { row.layoutInfo.totalItemsCount }.first { it > index }
        val info = row.layoutInfo
        val chip = info.visibleItemsInfo.firstOrNull { it.index == index }
        val inSight = chip != null && chip.offset >= info.viewportStartOffset && chip.offset + chip.size <= info.viewportEndOffset
        if (!inSight) row.animateScrollToItem(index)
    }
    LazyRow(
        state = row,
        contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(names, key = { it.first }) { (id, name) ->
            // A long press selects a list (Anytime, list 1, always stays); while selecting, a tap does too.
            val canPick = id != 1L
            SelectableChip(
                name, current = id == selected && !selection.active, picked = id in selection.ids,
                onClick = { if (selection.active) { if (canPick) selection.toggle(id) } else onSelect(id) },
                onLongClick = if (canPick) ({ selection.toggle(id) }) else null,
            )
        }
        if (pastCount > 0 && !selection.active) item(key = "past") {
            FilterChip(selected = false, onClick = onPast, label = { Text("Past ($pastCount)") })
        }
        if (!selection.active) item(key = "new") { FilterChip(selected = false, onClick = onAdd, label = { Text("+ New date") }) }
    }
}

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

/** The Past chip: lists whose day has gone, newest first, with what each still has to buy. */
@Composable
private fun PastListsDialog(
    lists: List<ShoppingList>,
    openCounts: Map<Long, Int>,
    selected: Long,
    label: (ShoppingList) -> String,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Past lists") },
        text = {
            LazyColumn {
                items(lists, key = { it.id }) { l ->
                    val left = openCounts[l.id] ?: 0
                    ListItem(
                        headlineContent = { Text(label(l), fontWeight = if (l.id == selected) FontWeight.Bold else null) },
                        supportingContent = { Text(if (left == 0) "Nothing left to buy" else "$left left to buy") },
                        modifier = Modifier.clickable { onPick(l.id); onDismiss() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
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
        Empty("Type to find items in all your lists, past ones too.")
        return
    }
    val byList = results.groupBy { it.listId }
    val groups = lists.filter { it.id in byList }
    if (groups.isEmpty()) {
        Empty("Nothing matches \"${query.trim()}\".")
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

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionBar(selection, shownIds) {
                    IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            } else TopAppBar(
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = vm.input, onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Add a task") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.add() }),
                trailingIcon = { if (vm.input.isNotBlank()) IconButton(onClick = vm::add) { Icon(Icons.Default.Add, contentDescription = "Add") } },
            )
            if (state.loaded && state.todos.isEmpty()) {
                Empty("No tasks yet. Type one above; tap it to add details, a priority or a reminder.")
                return@Column
            }
            // Open tasks in the chosen order (see the sort button); ticked ones below, latest first.
            val open = Sorting.tasks(state.todos.filter { !it.done }, sort)
            val done = state.todos.filter { it.done }.sortedByDescending { it.doneAt }
            val list = rememberLastScroll("tasks")
            val byId = open.associateBy { it.id }
            val order = rememberDragOrder(open.map { it.id }, sort == TaskSort.MINE)
            val reorder = rememberReorderableLazyListState(list) { from, to ->
                val a = (from.key as? String)?.removePrefix("t:")?.toLongOrNull()
                val b = (to.key as? String)?.removePrefix("t:")?.toLongOrNull()
                if (a != null && b != null) order.move(a, b)
            }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(order.ids.mapNotNull { byId[it] }, key = { "t:${it.id}" }) { t ->
                    ReorderableItem(reorder, key = "t:${t.id}", enabled = canArrange) { dragging ->
                        Surface(shadowElevation = if (dragging) 6.dp else 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TodoLine(t, onCheck = { vm.setDone(t, it) }, Modifier.weight(1f).selectable(selection, t.id) { onOpen(t.id) })
                                if (canArrange) DragHandleIcon(t.title, Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrange) }).padding(12.dp))
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
                        TodoLine(t, onCheck = { vm.setDone(t, it) }, Modifier.selectable(selection, t.id) { onOpen(t.id) })
                    }
                }
            }
        }
    }
}

/** A task's reminder time, when it has one (and isn't done). */
private fun Todo.reminder(): LocalDateTime? = AlarmPlanner.todoTime(remind, done, dueDate, dueTime)

@Composable
private fun TodoLine(t: Todo, onCheck: (Boolean) -> Unit, modifier: Modifier) {
    Row(Modifier.fillMaxWidth().then(modifier).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.done, onCheckedChange = onCheck)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.priority == 2) Text("! ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(
                    t.title,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.priority == 0 || t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            val style = LocalDateStyle.current
            val details = buildList {
                t.reminder()?.let { at -> add("⏰ ${style.dayLabel(at.toLocalDate())} ${at.toLocalTime()}" + if (Repeat.of(t.repeat) != Repeat.NONE) " ↻" else "") }
                t.details.lineSequence().firstOrNull { it.isNotBlank() }?.let { add(it.take(60)) }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Checkbox(checked = t.done, onCheckedChange = { vm.setDone(it) })
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
                    OutlinedButton(onClick = { pickTime = true }) { Text(at.toLocalTime().toString()) }
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

/** The top bar: the list's day and full date ("Today · 11/11/2026"), or "Anytime". */
private fun ShoppingList.title(style: DateStyle): String = parseDate(date)?.let { d ->
    val day = style.dayLabel(d)
    val full = style.format(d)
    if (day == full) full else "$day · $full"
} ?: label(style)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel, setting: ReminderSound, onBack: () -> Unit, bottomBar: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listId by vm.listId.collectAsStateWithLifecycle()
    val style = LocalDateStyle.current
    // The list being edited, kept through a rotation; the dialog keeps its own fields.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.lists.firstOrNull { it.id == id } }
    // + New date: a date picker; that day's list is made (or opened, if there is one).
    var pickNew by rememberSaveable { mutableStateOf(false) }
    // The item being renamed (kept through a rotation); closes if the item goes or another list is opened.
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val renaming = renamingId?.let { id -> state.items.firstOrNull { it.id == id } }
    // Gone (deleted, or another list opened): closed for good, so Undo or coming back doesn't reopen it.
    LaunchedEffect(state.loaded, renaming == null) { if (state.loaded && renamingId != null && renaming == null) renamingId = null }
    val list = state.lists.firstOrNull { it.id == listId }
    // Nothing shown yet (the first time), or the list shown has gone: today's list.
    LaunchedEffect(state.loaded, list) { if (state.loaded && listToShow(listId, state.lists) == null) vm.openToday() }
    val itemSort by vm.itemSort.collectAsStateWithLifecycle()
    val listSort by vm.listSort.collectAsStateWithLifecycle()
    var arrangeLists by rememberSaveable { mutableStateOf(false) }
    val showTicked by vm.showTicked.collectAsStateWithLifecycle()
    val open = Sorting.items(state.items.filter { !it.checked }, itemSort)
    val checked = state.items.filter { it.checked }
    val selection = vm.selection
    val today = rememberToday()
    // Midnight (or back after a day away): carry over and tidy as on opening. Not on first showing (init did it).
    var seenDay by rememberSaveable { mutableStateOf(today.toString()) }
    LaunchedEffect(today) { if (today.toString() != seenDay) { seenDay = today.toString(); vm.dayChanged() } }
    // Past lists live under the Past chip; the chips show Anytime, today and coming days (and a past list while open).
    val (pastLists, currentLists) = state.lists.partition { it.isPast(today) }
    val chipLists = Sorting.lists(currentLists, listSort, today) + listOfNotNull(pastLists.firstOrNull { it.id == listId })
    // Only chips on screen can be selected: Select all never picks past lists hidden under Past.
    val pickable = chipLists.filter { it.id != 1L }.map { it.id }
    var showPast by rememberSaveable { mutableStateOf(false) }
    val openCounts by vm.openCounts.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searchFocus = remember { FocusRequester() }
    BackHandler(enabled = query != null) { vm.searchQuery.value = null }

    LaunchedEffect(pickable) { if (state.loaded) selection.keepOnly(pickable) }

    Scaffold(
        topBar = {
            if (selection.active) SelectionBar(selection, pickable) {
                IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete lists") }
            } else if (query != null) TopAppBar(
                title = {
                    TextField(
                        value = query.orEmpty(), onValueChange = { vm.searchQuery.value = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        placeholder = { Text("Search all lists") }, singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    )
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                },
                navigationIcon = { IconButton(onClick = { vm.searchQuery.value = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") } },
                actions = {
                    if (!query.isNullOrEmpty()) IconButton(onClick = { vm.searchQuery.value = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                },
            ) else TopAppBar(
                title = { Text(list?.title(style) ?: "Shopping") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (checked.isNotEmpty()) TextButton(onClick = { vm.clearChecked() }) { Text("Clear ticked") }
                    IconButton(onClick = { vm.searchQuery.value = "" }) { Icon(Icons.Default.Search, contentDescription = "Search all lists") }
                    SortButton(
                        SortGroup("Items", ItemSort.entries, itemSort, { it.label }, vm::setItemSort),
                        SortGroup("Lists", ListSort.entries, listSort, { it.label }, vm::setListSort),
                        extra = { close ->
                            ShowCompletedItem(showTicked) { close(); vm.toggleShowTicked() }
                            DropdownMenuItem(text = { Text("Arrange lists…") }, onClick = { close(); arrangeLists = true })
                        },
                    )
                    if (list != null && list.id != 1L) IconButton(onClick = { editingId = list.id }) { Icon(Icons.Default.Edit, contentDescription = "List date and reminder") }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (query != null) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                SearchResults(query.orEmpty(), results, orderLists(state.lists, today), { it.label(style) }) { id ->
                    vm.select(id)
                    vm.searchQuery.value = null
                }
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListChips(chipLists.map { it.id to it.label(style) }, listId, selection, vm::select, pastLists.size, { showPast = true }) { pickNew = true }
            if (selection.active) {
                Text("Tap lists to select them. Anytime always stays.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            list?.let { l ->
                val date = parseDate(l.date)
                val time = parseTime(l.remindAt)
                if (date != null) {
                    Text(
                        "For ${style.dayLabel(date)}" + (time?.let { " · reminder $it (${soundLabel(ReminderSound.of(l.sound), setting)})" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Add item, e.g. 2x milk") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Done adds the item and keeps the keyboard open for the next one.
                keyboardActions = KeyboardActions(onDone = { vm.add() }),
                trailingIcon = {
                    if (vm.input.isNotBlank()) IconButton(onClick = { vm.add() }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                },
            )
            val suggestions = vm.suggestions(state)
            if (suggestions.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions, key = { it.name }) { s ->
                        InputChip(
                            selected = false,
                            onClick = { vm.pick(s) },
                            label = { Text(s.name) },
                            trailingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = "Forget ${s.name}", Modifier.size(18.dp).clickable { vm.forget(s) })
                            },
                        )
                    }
                }
            }
            if (state.loaded && state.items.isEmpty()) {
                Empty("This list is empty. Type an item above.\n\nTap ✎ to give the list a date and a reminder.")
                return@Column
            }
            val scroll = rememberLastScroll("shopping:$listId")
            val canArrange = !selection.active
            val byId = open.associateBy { it.id }
            val order = key(listId) { rememberDragOrder(open.map { it.id }, itemSort == ItemSort.MINE) }
            val reorder = rememberReorderableLazyListState(scroll) { from, to ->
                val a = from.key as? Long
                val b = to.key as? Long
                // Only among the items still to buy.
                if (a != null && b != null && a in byId && b in byId) order.move(a, b)
            }
            LazyColumn(Modifier.fillMaxSize(), scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(order.ids.mapNotNull { byId[it] }, key = { it.id }) { item ->
                    ReorderableItem(reorder, key = item.id, enabled = canArrange) { dragging ->
                        Surface(shadowElevation = if (dragging) 6.dp else 0.dp) {
                            ItemLine(item, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) }, onEdit = { renamingId = item.id }) {
                                if (canArrange) DragHandleIcon(item.name, Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrangeItems) }).padding(12.dp))
                            }
                        }
                    }
                }
                if (checked.isNotEmpty()) {
                    item(key = "h:basket") {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        CompletedHeader("In the basket (${checked.size})", showTicked, vm::toggleShowTicked)
                    }
                    if (showTicked) items(checked, key = { it.id }) { item ->
                        ItemLine(item, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) }, onEdit = { renamingId = item.id })
                    }
                }
            }
        }
    }
    editing?.let { l ->
        ShoppingListDialog(
            l, setting, taken = state.lists.filter { it.id != l.id }.mapNotNull { it.date }.toSet(),
            onSave = { vm.saveList(it) }, onDelete = { vm.deleteList(it) }, onDismiss = { editingId = null },
        )
    }
    if (showPast) PastListsDialog(
        orderLists(pastLists, today), openCounts, listId, { it.label(style) }, vm::select,
    ) { showPast = false }
    if (arrangeLists) ArrangeListsDialog(
        Sorting.lists(currentLists, listSort, today), listSort == ListSort.MINE, { it.label(style) }, vm::arrangeLists,
    ) { arrangeLists = false }
    renaming?.let { item ->
        EditItemDialog(item, others = state.items.filter { it.id != item.id }.map { it.key }.toSet(),
            onSave = { vm.rename(item, it) }) { renamingId = null }
    }
    if (vm.nameTaken) {
        AlertDialog(
            onDismissRequest = { vm.nameTaken = false },
            title = { Text("Not renamed") },
            text = { Text("That's already on this list.") },
            confirmButton = { TextButton(onClick = { vm.nameTaken = false }) { Text("OK") } },
        )
    }
    if (pickNew) DatePickDialog(null, onPick = { vm.openDate(it) }, onDismiss = { pickNew = false })
    if (vm.dateTaken) {
        AlertDialog(
            onDismissRequest = { vm.dateTaken = false },
            title = { Text("Not saved") },
            text = { Text("Another list already has that date.") },
            confirmButton = { TextButton(onClick = { vm.dateTaken = false }) { Text("OK") } },
        )
    }
}

/** A dated list's settings: its date (one list per day, [taken] are the others'), and that day an optional reminder. */
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
        title = { Text("Shopping list") },
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
                        OutlinedButton(onClick = { pickTime = true }) { Text(parseTime(time)?.toString() ?: "Add") }
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

@Composable
private fun ItemLine(
    item: ShoppingItem,
    onCheck: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    handle: @Composable () -> Unit = {},
) {
    // Only the box ticks; a tap on the name opens it to rename (no ticking by accident while scrolling or dragging).
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = onCheck)
        Text(
            item.name,
            modifier = Modifier.weight(1f).clickable(onClickLabel = "Rename", onClick = onEdit).padding(vertical = 12.dp),
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (item.quantity.isNotEmpty()) {
            Text(item.quantity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Clear, contentDescription = "Remove ${item.name}") }
        handle()
    }
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

/** Shopping → sort → Arrange lists…: the lists in "My order", dragged by their handles; each drop is saved. */
@Composable
private fun ArrangeListsDialog(
    lists: List<ShoppingList>,
    mine: Boolean,
    label: (ShoppingList) -> String,
    onArrange: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val byId = lists.associateBy { it.id }
    val order = rememberDragOrder(lists.map { it.id }, mine)
    val list = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        val a = from.key as? Long
        val b = to.key as? Long
        if (a != null && b != null) order.move(a, b)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arrange lists") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), list) {
                items(order.ids.mapNotNull { byId[it] }, key = { it.id }) { l ->
                    ReorderableItem(reorder, key = l.id) { dragging ->
                        Surface(shadowElevation = if (dragging) 6.dp else 0.dp) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label(l), Modifier.weight(1f))
                                DragHandleIcon(label(l), Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(onArrange) }).padding(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
