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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
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
    onAdd: () -> Unit,
) {
    LazyRow(
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
        if (!selection.active) item { FilterChip(selected = false, onClick = onAdd, label = { Text("+ New list") }) }
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
    LaunchedEffect(state.todos) { if (state.loaded) selection.keepOnly(state.todos.map { it.id }) }

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionBar(selection, state.todos.map { it.id }) {
                    IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            } else TopAppBar(title = { Text("Tasks") }, navigationIcon = { BackButton(onBack) })
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
            // Open tasks: high priority first, newest first within a priority. Ticked ones below.
            val open = state.todos.filter { !it.done }.sortedWith(compareByDescending<Todo> { it.priority }.thenByDescending { it.createdAt })
            val done = state.todos.filter { it.done }.sortedByDescending { it.doneAt }
            LazyColumn(Modifier.fillMaxSize(), rememberLastScroll("tasks"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(open, key = { "t:${it.id}" }) { t -> TodoLine(t, onCheck = { vm.setDone(t, it) }, Modifier.selectable(selection, t.id) { onOpen(t.id) }) }
                if (done.isNotEmpty()) {
                    item(key = "h:done") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Done (${done.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            if (!selection.active) TextButton(onClick = { vm.clearDone() }) { Text("Clear done") }
                        }
                    }
                    items(done, key = { "t:${it.id}" }) { t -> TodoLine(t, onCheck = { vm.setDone(t, it) }, Modifier.selectable(selection, t.id) { onOpen(t.id) }) }
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

/** Anytime first, then the dated lists by date. */
private fun List<ShoppingList>.inOrder() = sortedWith(compareBy<ShoppingList>({ it.date != null }, { it.date }, { it.id }))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel, setting: ReminderSound, onBack: () -> Unit, bottomBar: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listId by vm.listId.collectAsStateWithLifecycle()
    val style = LocalDateStyle.current
    // The list being edited, kept through a rotation; the dialog keeps its own fields.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.lists.firstOrNull { it.id == id } }
    // + New list: a date picker; that day's list is made (or opened, if there is one).
    var pickNew by rememberSaveable { mutableStateOf(false) }
    val list = state.lists.firstOrNull { it.id == listId }
    // Nothing shown yet (the first time), or the list shown has gone: today's list.
    LaunchedEffect(state.loaded, list) { if (state.loaded && listToShow(listId, state.lists) == null) vm.openToday() }
    val open = state.items.filter { !it.checked }
    val checked = state.items.filter { it.checked }
    val selection = vm.selection
    val pickable = state.lists.filter { it.id != 1L }.map { it.id }
    LaunchedEffect(state.lists) { if (state.loaded) selection.keepOnly(pickable) }

    Scaffold(
        topBar = {
            if (selection.active) SelectionBar(selection, pickable) {
                IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete lists") }
            } else TopAppBar(
                title = { Text(list?.title(style) ?: "Shopping") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (checked.isNotEmpty()) TextButton(onClick = { vm.clearChecked() }) { Text("Clear ticked") }
                    if (list != null && list.id != 1L) IconButton(onClick = { editingId = list.id }) { Icon(Icons.Default.Edit, contentDescription = "List date and reminder") }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListChips(state.lists.inOrder().map { it.id to it.label(style) }, listId, selection, vm::select) { pickNew = true }
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
            LazyColumn(Modifier.fillMaxSize(), rememberLastScroll("shopping:$listId"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(open, key = { it.id }) { item ->
                    ItemLine(item, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) })
                }
                if (checked.isNotEmpty()) {
                    item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
                    item { Header("In the basket") }
                    items(checked, key = { it.id }) { item ->
                        ItemLine(item, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) })
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
private fun ItemLine(item: ShoppingItem, onCheck: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheck(!item.checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = onCheck)
        Text(
            item.name,
            modifier = Modifier.weight(1f),
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (item.quantity.isNotEmpty()) {
            Text(item.quantity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Clear, contentDescription = "Remove ${item.name}") }
    }
}
