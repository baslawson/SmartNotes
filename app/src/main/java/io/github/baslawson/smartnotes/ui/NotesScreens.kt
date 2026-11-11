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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Surface
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import sh.calvin.reorderable.ReorderableItem
import io.github.baslawson.smartnotes.data.Sorting
import io.github.baslawson.smartnotes.data.NoteSort
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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
    onOpenTask: (Long) -> Unit = {},
    onOpenItem: (io.github.baslawson.smartnotes.data.ShoppingItem, io.github.baslawson.smartnotes.data.ShoppingList) -> Unit = { _, _ -> },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // The search finds tasks and shopping items too: listed below the notes, under their own headings.
    val found by vm.found.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(found.query) { mutableStateOf(emptyList<String>()) }
    val actions = HitActions(
        openNote = onOpen, openTask = onOpenTask, checkTask = vm::setTaskDone,
        openItem = onOpenItem, showAll = { expanded = expanded + it.name },
    )
    // Only once the search has results for what's in the box (not for what was typed before).
    val typed by vm.query.collectAsStateWithLifecycle()
    val searching = found.terms.isNotEmpty() && found.loaded && found.query == typed.trim()
    val others = if (searching) Hits.of(found, Section.NOTES, expanded.map(Section::valueOf).toSet(), withOwn = false) else emptyList()
    var menu by remember { mutableStateOf(false) }
    val selection = vm.selection
    LaunchedEffect(state.rows) { if (state.loaded) selection.keepOnly(state.rows.map { it.note.id }) }
    val query by vm.query.collectAsStateWithLifecycle()
    val selectedTag by vm.tag.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    // Handles in every order (a drop switches to My order), for the whole list: not while searching, filtering by a tag
    // or selecting. (state.terms / state.tag: the rows shown still belong to a search or filter until it has cleared.)
    val canArrange = query.isBlank() && state.terms.isEmpty() && selectedTag == null && state.tag == null && !selection.active

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
                        IconButton(onClick = { vm.setGrid(!grid) }) {
                            if (grid) Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show as list")
                            else Icon(GridIcon, contentDescription = "Show as grid")
                        }
                        SortButton(SortGroup(null, NoteSort.entries, sort, { it.label }, vm::setSort))
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Select notes") }, onClick = {
                                    menu = false
                                    state.rows.firstOrNull()?.let { selection.toggle(it.note.id) }
                                })
                                DropdownMenuItem(text = { Text("Recycle bin") }, onClick = { menu = false; onBin() })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
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
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = PillShape, colors = pillColors(),
                placeholder = { Text("Search everything") },
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
                state.rows.isEmpty() && others.isNotEmpty() ->
                    // No note matches, but tasks or shopping items do.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        hits(listOf(Hit.Head(Section.NOTES, 0), Hit.NoneIn(Section.NOTES)) + others, state.terms, actions)
                    }
                state.rows.isEmpty() ->
                    if (query.isBlank() && selectedTag == null) EmptyState(Icons.Default.Edit, "No notes yet", "Tap + to write your first one.")
                    else if (searching && selectedTag == null) EmptyState(Icons.Default.Search, "Nothing found", "Nothing in Notes, Tasks or Shopping matches \"${found.query}\".")
                    else EmptyState(Icons.Default.Search, "Nothing found", "Try other words, or another tag.")
                else -> {
                    val byId = state.rows.associateBy { it.note.id }
                    val order = rememberDragOrder(state.rows.map { it.note.id }, sort == NoteSort.MINE)
                    // Pinned notes move among the pinned, the others among the others.
                    val move = { from: Any, to: Any ->
                        val a = from as? Long
                        val b = to as? Long
                        if (a != null && b != null && byId[a]?.note?.pinned == byId[b]?.note?.pinned) order.move(a, b)
                    }
                    val tip = "Write #tags, [[links]] to other notes and - [ ] checklists right in a note. Drag ≡ to reorder; long-press to select."
                    // Both kept while switching between list and grid.
                    val cards = rememberLastGridScroll("notes-grid")
                    val list = rememberLastScroll("notes")
                    if (grid) {
                        val reorder = rememberReorderableLazyStaggeredGridState(cards) { from, to -> move(from.key, to.key) }
                        LazyVerticalStaggeredGrid(
                            NoteColumns, Modifier.fillMaxSize(), cards,
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 88.dp),
                        ) {
                            item(key = "tip", span = StaggeredGridItemSpan.FullLine) { OneTimeTip("notes", tip, horizontal = 4.dp) }
                            if (searching) item(key = "s:NOTES", span = StaggeredGridItemSpan.FullLine) {
                                HitLine(Hit.Head(Section.NOTES, state.rows.size), state.terms, actions)
                            }
                            items(order.ids.mapNotNull { byId[it] }, key = { it.note.id }) { row ->
                                ReorderableItem(reorder, key = row.note.id, enabled = canArrange) { dragging ->
                                    NoteCard(row, state.terms, dragging, canArrange, Modifier.selectable(selection, row.note.id) { onOpen(row.note.id) }) {
                                        DragHandleIcon(
                                            row.title,
                                            Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrange) })
                                                .padding(12.dp),
                                        )
                                    }
                                }
                            }
                            // Tasks and shopping items found too, across the whole width.
                            others.forEach { hit ->
                                item(key = hit.key, span = StaggeredGridItemSpan.FullLine) { HitLine(hit, state.terms, actions) }
                            }
                        }
                        return@Column
                    }
                    val reorder = rememberReorderableLazyListState(list) { from, to -> move(from.key, to.key) }
                    LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 88.dp)) {
                        item(key = "tip") { OneTimeTip("notes", tip) }
                        if (searching) item(key = "s:NOTES") { HitLine(Hit.Head(Section.NOTES, state.rows.size), state.terms, actions) }
                        items(order.ids.mapNotNull { byId[it] }, key = { it.note.id }) { row ->
                            ReorderableItem(reorder, key = row.note.id, enabled = canArrange) { dragging ->
                                RowCard(dragging = dragging) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        NoteRowItem(row, state.terms, Modifier.weight(1f).selectable(selection, row.note.id) { onOpen(row.note.id) })
                                        if (canArrange) DragHandleIcon(
                                            row.title,
                                            Modifier.draggableHandle(onDragStarted = { order.start() }, onDragStopped = { order.stop()?.let(vm::arrange) }).padding(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // Tasks and shopping items found too.
                        hits(others, state.terms, actions)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoteRowItem(row: NoteRow, terms: List<String>, modifier: Modifier) {
    Column(Modifier.fillMaxWidth().then(modifier).padding(horizontal = 16.dp, vertical = 12.dp)) {
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

/**
 * A note as a card in the grid: title, a longer preview, tasks and tags. [handle] (when [withHandle]) sits in the top
 * right corner, outside the card's tap and long-press area, so holding it drags rather than selects.
 */
@Composable
private fun NoteCard(
    row: NoteRow, terms: List<String>, dragging: Boolean, withHandle: Boolean, modifier: Modifier, handle: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(4.dp),
        shape = MaterialTheme.shapes.medium,
        color = c.surfaceContainerLowest,
        shadowElevation = if (dragging) 8.dp else if (LocalDark.current) 0.dp else 1.dp,
    ) {
        Box {
            Column(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (row.note.pinned) {
                        Icon(Icons.Default.Star, contentDescription = "Pinned", Modifier.padding(top = 3.dp, end = 4.dp).size(16.dp), tint = c.primary)
                    }
                    Text(
                        highlighted(row.title, terms), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    // Room for the handle.
                    if (withHandle) Spacer(Modifier.width(24.dp))
                }
                if (row.snippet.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        highlighted(row.snippet, terms), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6, overflow = TextOverflow.Ellipsis, color = c.onSurfaceVariant,
                    )
                }
                if (row.openTasks > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("☐ ${row.openTasks} open task${if (row.openTasks == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = c.primary)
                }
                if (row.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(row.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = c.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (withHandle) Box(Modifier.align(Alignment.TopEnd)) { handle() }
        }
    }
}

/** Grid columns: two on any phone (even a narrow one), one more for each further 200 dp or so on wider screens. */
private val NoteColumns = object : StaggeredGridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): IntArray {
        val count = maxOf(2, (availableSize / 200.dp.toPx()).toInt())
        val width = (availableSize - spacing * (count - 1)) / count
        val extra = (availableSize - spacing * (count - 1)) % count
        return IntArray(count) { width + if (it < extra) 1 else 0 }
    }
}

/** Four squares: the "show as grid" button (not in the small built-in icon set). */
private val GridIcon: ImageVector = materialIcon(name = "SmartNotes.Grid") {
    materialPath {
        moveTo(4f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
        moveTo(13f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
        moveTo(4f, 13f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
        moveTo(13f, 13f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
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
        // A new note opens ready for typing: the cursor in the text and the keyboard up (once, not again after turning).
        val textFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        var focused by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (vm.isNew && !focused) {
                focused = true
                textFocus.requestFocus()
                keyboard?.show()
            }
        }
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
                colors = fieldColors, modifier = Modifier.fillMaxWidth().focusRequester(textFocus),
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
                // Only the box ticks (a tap on the text does nothing).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FunCheckbox(checked = task.done, onCheckedChange = { onTick(task.line, task.text) })
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
