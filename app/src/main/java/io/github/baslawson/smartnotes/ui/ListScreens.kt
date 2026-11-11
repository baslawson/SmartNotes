package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baslawson.smartnotes.data.ShoppingItem
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TasksViewModel, onOpen: (Long) -> Unit, bottomBar: @Composable () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Tasks") }) }, bottomBar = bottomBar) { padding ->
        val all = tasks ?: return@Scaffold
        if (all.isEmpty()) {
            Column(Modifier.padding(padding)) { Empty("No tasks yet. Write \"- [ ] something\" in any note.") }
            return@Scaffold
        }
        val now = LocalDateTime.now()
        val open = all.filter { !it.task.done }
        val overdue = open.filter { it.task.due?.isBefore(now) == true }.sortedBy { it.task.due }
        val today = open.filter { it.task.due?.let { d -> !d.isBefore(now) && d.toLocalDate() == LocalDate.now() } == true }.sortedBy { it.task.due }
        val later = open.filter { it.task.due?.let { d -> d.toLocalDate().isAfter(LocalDate.now()) } == true }.sortedBy { it.task.due }
        val noDate = open.filter { it.task.due == null }
        val done = all.filter { it.task.done }

        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            taskSection("Overdue", overdue, vm, onOpen)
            taskSection("Today", today, vm, onOpen)
            taskSection("Coming up", later, vm, onOpen)
            taskSection("No date", noDate, vm, onOpen)
            if (done.isNotEmpty()) {
                item {
                    TextButton(onClick = { showDone = !showDone }, Modifier.padding(horizontal = 8.dp)) {
                        Text(if (showDone) "Hide done (${done.size})" else "Show done (${done.size})")
                    }
                }
                if (showDone) taskSection(null, done, vm, onOpen)
            }
        }
    }
}

private fun LazyListScope.taskSection(title: String?, rows: List<TaskRow>, vm: TasksViewModel, onOpen: (Long) -> Unit) {
    if (rows.isEmpty()) return
    if (title != null) item { Header(title) }
    items(rows, key = { "${it.noteId}:${it.task.line}:${it.task.done}" }) { row ->
        TaskLine(row, onCheck = { vm.toggle(row) }, onOpen = { onOpen(row.noteId) })
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TaskLine(row: TaskRow, onCheck: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = row.task.done, onCheckedChange = { onCheck() })
        Column(Modifier.weight(1f)) {
            Text(
                row.task.text.ifBlank { "(empty task)" },
                textDecoration = if (row.task.done) TextDecoration.LineThrough else null,
            )
            val sub = listOfNotNull(row.task.due?.let(::formatWhen), row.noteTitle).joinToString("  ·  ")
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel, onOpen: (Long) -> Unit, bottomBar: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val open = state.items.filter { !it.checked }
    val checked = state.items.filter { it.checked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                actions = {
                    if (checked.isNotEmpty()) TextButton(onClick = { vm.clearChecked() }) { Text("Clear ticked") }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(suggestions, key = { it.name }) { s ->
                        InputChip(
                            selected = false,
                            onClick = { vm.pick(s) },
                            label = { Text(s.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear, contentDescription = "Forget ${s.name}",
                                    Modifier.clickable { vm.forget(s) },
                                )
                            },
                        )
                    }
                }
            }
            if (state.loaded && state.items.isEmpty() && state.fromNotes.isEmpty()) {
                Empty("The list is empty. Type an item above.\n\nTasks in notes tagged #shopping show up here too.")
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(open, key = { it.id }) { item ->
                    ItemLine(item, onCheck = { vm.setChecked(item, it) }, onDelete = { vm.delete(item) })
                }
                if (state.fromNotes.isNotEmpty()) {
                    item { Header("From notes") }
                    items(state.fromNotes, key = { "note:${it.noteId}:${it.task.line}" }) { row ->
                        TaskLine(row, onCheck = { vm.tickNoteTask(row) }, onOpen = { onOpen(row.noteId) })
                    }
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
}

@Composable
private fun ItemLine(item: ShoppingItem, onCheck: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheck(!item.checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
