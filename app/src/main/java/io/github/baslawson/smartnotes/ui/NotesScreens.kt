package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baslawson.smartnotes.SmartNotesApp
import io.github.baslawson.smartnotes.data.Note
import io.github.baslawson.smartnotes.parse.NoteParser
import io.github.baslawson.smartnotes.parse.Search


/** [text] with the words matching [terms] in bold on a highlight colour. */
@Composable
fun highlighted(text: String, terms: List<String>): AnnotatedString {
    val style = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, fontWeight = FontWeight.Bold)
    return remember(text, terms, style) {
        buildAnnotatedString {
            append(text)
            Search.matches(text, terms).forEach { addStyle(style, it.first, it.last + 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onBin: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val selection = vm.selection
    LaunchedEffect(state.rows) { if (state.loaded) selection.keepOnly(state.rows.map { it.note.id }) }
    val query by vm.query.collectAsStateWithLifecycle()
    val selectedTag by vm.tag.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionBar(selection, state.rows.map { it.note.id }) {
                    IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            } else {
                TopAppBar(
                    title = { Text("Notes") },
                    actions = {
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Select notes") }, onClick = {
                                    menu = false
                                    state.rows.firstOrNull()?.let { selection.toggle(it.note.id) }
                                })
                                DropdownMenuItem(text = { Text("Recycle bin") }, onClick = { menu = false; onBin() })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (!selection.active) FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, contentDescription = "New note") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
            )
            if (state.tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tags, key = { it.first }) { (tag, count) ->
                        FilterChip(
                            selected = tag == selectedTag,
                            onClick = { vm.tag.value = if (tag == selectedTag) null else tag },
                            label = { Text("#$tag  $count") },
                        )
                    }
                }
            }
            when {
                !state.loaded -> Unit
                state.rows.isEmpty() -> Empty(
                    if (query.isBlank() && selectedTag == null) "No notes yet. Tap + to write one."
                    else "Nothing found."
                )
                else -> LazyColumn(Modifier.fillMaxSize(), rememberLastScroll("notes"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.rows, key = { it.note.id }) { row ->
                        NoteRowItem(row, state.terms, Modifier.selectable(selection, row.note.id) { onOpen(row.note.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRowItem(row: NoteRow, terms: List<String>, modifier: Modifier) {
    Column(Modifier.fillMaxWidth().then(modifier).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.note.pinned) {
                Icon(Icons.Default.Star, contentDescription = "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                highlighted(row.title, terms), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        if (row.snippet.isNotBlank()) {
            Text(
                highlighted(row.snippet, terms), style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val details = buildList {
            if (row.openTasks > 0) add("${row.openTasks} open task${if (row.openTasks == 1) "" else "s"}")
            if (row.tags.isNotEmpty()) add(row.tags.joinToString(" ") { "#$it" })
        }
        if (details.isNotEmpty()) {
            Text(details.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onCreateLinked: (String) -> Unit) {
    val info by vm.info.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.finish() }
    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }
    // A new note's route has no id: once it is saved, remember it as the place to reopen.
    val app = LocalContext.current.applicationContext as SmartNotesApp
    LaunchedEffect(vm.id) { if (vm.id > 0 && !vm.gone) app.state.place = "note/${vm.id}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = vm::togglePin) {
                        if (vm.pinned) Icon(Icons.Default.Star, contentDescription = "Unpin", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Outlined.Star, contentDescription = "Pin")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { padding ->
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        )
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            TextField(
                value = vm.title, onValueChange = vm::onTitleChange, readOnly = !vm.loaded,
                placeholder = { Text("Title") }, textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = vm.body, onValueChange = vm::onBodyChange, readOnly = !vm.loaded,
                placeholder = { Text("Type something…") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            // A link to this note itself opens nothing (it is already open).
            NoteInfo(info, onOpen = { if (it != vm.id) onOpen(it) }, onCreateLinked = onCreateLinked, onTick = vm::toggleCheckbox)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("It goes to the recycle bin, where it can be restored.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteInfo(info: EditorInfo, onOpen: (Long) -> Unit, onCreateLinked: (String) -> Unit, onTick: (Int, String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.tags.isNotEmpty()) Text(info.tags.joinToString(" ") { "#$it" }, color = MaterialTheme.colorScheme.primary)
        if (info.checklist.isNotEmpty()) {
            Section("Checklist")
            info.checklist.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onTick(task.line, task.text) }) {
                    androidx.compose.material3.Checkbox(checked = task.done, onCheckedChange = { onTick(task.line, task.text) })
                    Text(
                        task.text.ifBlank { "(empty)" },
                        textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        if (info.links.isNotEmpty()) {
            Section("Links")
            ChipRow(info.links) { link ->
                AssistChip(
                    onClick = { if (!link.self) link.noteId?.let(onOpen) ?: onCreateLinked(link.title) },
                    label = { Text(if (link.noteId == null && !link.self) "+ ${link.title}" else link.title) },
                )
            }
        }
        if (info.backlinks.isNotEmpty()) {
            Section("Linked from")
            ChipRow(info.backlinks) { n -> AssistChip(onClick = { onOpen(n.id) }, label = { Text(n.displayTitle()) }) }
        }
        if (info.related.isNotEmpty()) {
            Section("Related")
            ChipRow(info.related) { n -> AssistChip(onClick = { onOpen(n.id) }, label = { Text(n.displayTitle()) }) }
        }
        Spacer(Modifier.size(24.dp))
    }
}

private fun Note.displayTitle() = NoteParser.displayTitle(title, body)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> ChipRow(items: List<T>, chip: @Composable (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(items) { chip(it) } }
}
