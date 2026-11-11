package io.github.baslawson.smartnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import io.github.baslawson.smartnotes.SmartNotesApp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.baslawson.smartnotes.AppSettings
import io.github.baslawson.smartnotes.data.BinEntry
import io.github.baslawson.smartnotes.data.BinKind
import io.github.baslawson.smartnotes.data.RecycleBin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

// ---------- Selecting (shared by Notes, Tasks, Shopping and the bin) ----------

/** A row that opens on a tap and is selected with a long press; while selecting, a tap selects too. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.selectable(selection: Selection, id: Long, onOpen: () -> Unit): Modifier {
    val picked = id in selection.ids
    return this
        .background(if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .combinedClickable(
            onClick = { if (selection.active) selection.toggle(id) else onOpen() },
            onLongClick = { selection.toggle(id) },
            onLongClickLabel = "Select",
        )
}

/**
 * The top bar while selecting: ✕ (or Back) stops, "3 selected", Select all, and [actions] (Delete, …).
 * [allIds]: everything shown, for Select all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(selection: Selection, allIds: List<Long>, actions: @Composable () -> Unit) {
    BackHandler(enabled = selection.active) { selection.clear() }
    TopAppBar(
        title = { Text("${selection.ids.size} selected") },
        navigationIcon = { IconButton(onClick = selection::clear) { Icon(Icons.Default.Close, contentDescription = "Stop selecting") } },
        actions = {
            if (selection.ids.size < allIds.size) TextButton(onClick = { selection.selectAll(allIds) }) { Text("Select all") }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

/** A list chip that also takes a long press (to select it). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableChip(text: String, current: Boolean, picked: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            picked -> colors.tertiaryContainer
            current -> colors.secondaryContainer
            else -> Color.Transparent
        },
        border = if (current || picked) null else BorderStroke(1.dp, colors.outline),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { "Select" }),
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Scrolling kept for next time ----------

/** A list's scroll position, kept through a rotation and, in AppState, for the next time the app opens. */
@OptIn(FlowPreview::class)
@Composable
fun rememberLastScroll(key: String): LazyListState {
    val app = LocalContext.current.applicationContext as SmartNotesApp
    val list = rememberSaveable(key, saver = LazyListState.Saver) {
        val (index, offset) = app.state.scroll(key)
        LazyListState(index, offset)
    }
    LaunchedEffect(list, key) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .drop(1).debounce(400)
            .collect { (index, offset) -> app.state.setScroll(key, index, offset) }
    }
    // Leaving (or switching lists) within the pause above still keeps the last position.
    DisposableEffect(list, key) {
        onDispose { app.state.setScroll(key, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
    }
    return list
}

// ---------- Recycle bin ----------

private fun BinEntry.key(): Long = kind.ordinal * 1_000_000_000_000L + id

class BinViewModel(private val bin: RecycleBin, private val appScope: CoroutineScope) : ViewModel() {
    val entries: StateFlow<List<BinEntry>?> = bin.observe().map<List<BinEntry>, List<BinEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val selection = Selection()

    private fun picked(): Map<BinKind, List<Long>> {
        val keys = selection.ids
        return entries.value.orEmpty().filter { it.key() in keys }.groupBy({ it.kind }, { it.id })
    }

    fun restore(entries: Map<BinKind, List<Long>> = picked()) {
        selection.clear()
        appScope.launch { entries.forEach { (kind, ids) -> bin.restore(kind, ids) } }
    }

    fun erase(entries: Map<BinKind, List<Long>> = picked()) {
        selection.clear()
        appScope.launch { entries.forEach { (kind, ids) -> bin.erase(kind, ids) } }
    }

    fun empty() {
        selection.clear()
        appScope.launch { bin.empty() }
    }

    fun keyOf(entry: BinEntry) = entry.key()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(vm: BinViewModel, settings: AppSettings, onBack: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val keep by settings.keepFor.collectAsStateWithLifecycle()
    val style = LocalDateStyle.current
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) } // "erase" (the selected) or "empty"
    val all = entries.orEmpty()
    LaunchedEffect(all) { vm.selection.keepOnly(all.map(vm::keyOf)) }

    Scaffold(
        topBar = {
            if (vm.selection.active) {
                SelectionBar(vm.selection, all.map(vm::keyOf)) {
                    IconButton(onClick = { vm.restore() }) { Icon(Icons.Default.Refresh, contentDescription = "Restore") }
                    IconButton(onClick = { confirm = "erase" }) { Icon(Icons.Default.Delete, contentDescription = "Delete forever") }
                }
            } else {
                TopAppBar(
                    title = { Text("Recycle bin") },
                    navigationIcon = { BackButton(onBack) },
                    actions = { if (all.isNotEmpty()) TextButton(onClick = { confirm = "empty" }) { Text("Empty bin") } },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                if (keep.days == 0) "Deleted things are kept until you delete them here."
                else "Deleted things are kept for ${keep.label}, then erased (Settings → Recycle bin).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when {
                entries == null -> Unit
                all.isEmpty() -> EmptyState(Icons.Default.Delete, "The recycle bin is empty", "Deleted notes, tasks and lists wait here for a while, in case you want them back.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    BinKind.entries.forEach { kind ->
                        val those = all.filter { it.kind == kind }
                        if (those.isNotEmpty()) {
                            item(key = "h:$kind") {
                                Text(
                                    kind.plural.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(those, key = { vm.keyOf(it) }) { e ->
                                val now = System.currentTimeMillis()
                                val deleted = Instant.ofEpochMilli(e.deletedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                val left = keep.daysLeft(e.deletedAt, now)
                                val title = if (e.kind == BinKind.LIST) e.title.let { runCatching { style.format(java.time.LocalDate.parse(it)) }.getOrDefault(it) } else e.title
                                Column(
                                    Modifier.fillMaxWidth().selectable(vm.selection, vm.keyOf(e)) { opened = vm.keyOf(e) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (e.detail.isNotBlank()) {
                                        Text(e.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "Deleted ${style.dayLabel(deleted)}" + when (left) {
                                            null -> ""
                                            0 -> " · erased at the next start"
                                            1 -> " · 1 day left"
                                            else -> " · $left days left"
                                        },
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    all.firstOrNull { vm.keyOf(it) == opened }?.let { e ->
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("Restore this ${e.kind.label}, or delete it forever?") },
            confirmButton = { TextButton(onClick = { opened = null; vm.restore(mapOf(e.kind to listOf(e.id))) }) { Text("Restore") } },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { opened = null; vm.erase(mapOf(e.kind to listOf(e.id))) }) {
                        Text("Delete forever", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { opened = null }) { Text("Cancel") }
                }
            },
        )
    }
    confirm?.let { what ->
        val count = if (what == "empty") all.size else vm.selection.ids.size
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "empty") "Empty the recycle bin?" else "Delete $count forever?") },
            text = { Text("${if (count == 1) "It" else "All $count"} will be erased. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirm = null; if (what == "empty") vm.empty() else vm.erase() }) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
