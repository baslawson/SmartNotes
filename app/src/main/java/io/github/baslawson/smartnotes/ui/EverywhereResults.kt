package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.baslawson.smartnotes.data.ShoppingItem
import io.github.baslawson.smartnotes.data.ShoppingList
import io.github.baslawson.smartnotes.data.Todo
import io.github.baslawson.smartnotes.parse.Aisle
import io.github.baslawson.smartnotes.parse.Aisles

/** One line of the search results: a section's heading, a note, a task, an item, "Show all", or "nothing here". */
sealed interface Hit {
    val key: String

    data class Head(val section: Section, val count: Int) : Hit { override val key get() = "s:${section.name}" }
    data class NoteHit(val row: NoteRow) : Hit { override val key get() = "n:${row.note.id}" }
    data class TaskHit(val todo: Todo) : Hit { override val key get() = "t:${todo.id}" }
    data class ItemHit(val item: ShoppingItem, val list: ShoppingList, val aisle: Aisle = Aisle.OTHER) : Hit { override val key get() = "i:${item.id}" }
    data class More(val section: Section, val hidden: Int) : Hit { override val key get() = "m:${section.name}" }
    data class NoneIn(val section: Section) : Hit { override val key get() = "z:${section.name}" }
}

object Hits {
    /** How many results the other sections show before "Show all". */
    const val PREVIEW = 5

    /**
     * The result lines for [found], searched from [own]: that section first (all of its results, or "nothing here"),
     * then the others that found something, each its first [PREVIEW] unless [expanded]. [withOwn] false leaves the own
     * section out (Notes lists its notes itself).
     */
    fun of(found: Found, own: Section, expanded: Set<Section>, withOwn: Boolean = true): List<Hit> = buildList {
        val order = listOf(own) + Section.entries.filter { it != own }
        for (section in order) {
            val isOwn = section == own
            if (isOwn && !withOwn) continue
            val count = found.count(section)
            if (count == 0 && !isOwn) continue
            add(Hit.Head(section, count))
            if (count == 0) { add(Hit.NoneIn(section)); continue }
            val shown = if (isOwn || section in expanded) count else minOf(count, PREVIEW)
            when (section) {
                Section.NOTES -> found.notes.take(shown).forEach { add(Hit.NoteHit(it)) }
                Section.TASKS -> found.tasks.take(shown).forEach { add(Hit.TaskHit(it)) }
                Section.SHOPPING -> found.items.take(shown).forEach { (item, list) -> add(Hit.ItemHit(item, list, Aisles.of(item.key, item.name, found.aisles))) }
            }
            if (shown < count) add(Hit.More(section, count - shown))
        }
    }
}

/** What a tap on a result does. */
class HitActions(
    val openNote: (Long) -> Unit,
    val openTask: (Long) -> Unit,
    val checkTask: (Todo, Boolean) -> Unit,
    val openItem: (ShoppingItem, ShoppingList) -> Unit,
    val showAll: (Section) -> Unit,
)

/**
 * The whole results screen of a section's search: an empty state before anything is typed, "nothing anywhere" when
 * no section found anything, else the [Hits] list.
 */
@Composable
fun EverywhereResults(found: Found, own: Section, expanded: Set<Section>, actions: HitActions, emptyHint: String) {
    if (found.terms.isEmpty()) {
        EmptyState(Icons.Default.Search, "Search everything", emptyHint)
        return
    }
    if (!found.loaded) return
    if (found.total == 0) {
        EmptyState(Icons.Default.Search, "Nothing found", "Nothing in Notes, Tasks or Shopping matches \"${found.query}\".")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        hits(Hits.of(found, own, expanded), found.terms, actions)
    }
}

/** The result lines as list items (also used below Notes' own list). */
fun LazyListScope.hits(lines: List<Hit>, terms: List<String>, actions: HitActions) {
    items(lines, key = { it.key }) { HitLine(it, terms, actions) }
}

@Composable
fun HitLine(hit: Hit, terms: List<String>, actions: HitActions) {
    when (hit) {
        is Hit.Head -> Text(
            "In ${hit.section.label} (${hit.count})", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
        is Hit.NoneIn -> Text(
            "Nothing in ${hit.section.label}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        is Hit.More -> TextButton(onClick = { actions.showAll(hit.section) }, Modifier.padding(start = 8.dp)) {
            Text("Show all (${hit.hidden} more)")
        }
        is Hit.NoteHit -> RowCard {
            NoteRowItem(hit.row, terms, Modifier.clickable { actions.openNote(hit.row.note.id) })
        }
        is Hit.TaskHit -> RowCard(faded = hit.todo.done) {
            TodoLine(hit.todo, onCheck = { actions.checkTask(hit.todo, it) }, Modifier.clickable { actions.openTask(hit.todo.id) }, terms)
        }
        is Hit.ItemHit -> RowCard(faded = hit.item.checked) { ItemHitLine(hit.item, hit.list, hit.aisle, terms, actions) }
    }
}

/** A shopping item found: its name, quantity, the list it's on and its aisle ("Shopping · Fri 14 Nov · Dairy & eggs"), ticked or not. */
@Composable
private fun ItemHitLine(item: ShoppingItem, list: ShoppingList, aisle: Aisle, terms: List<String>, actions: HitActions) {
    val style = LocalDateStyle.current
    Row(
        Modifier.fillMaxWidth().clickable { actions.openItem(item, list) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(item.name, terms),
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Shopping · ${list.label(style)}" + (if (aisle != Aisle.OTHER) " · ${aisle.label}" else "") + if (item.checked) " · ticked" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.quantity.isNotEmpty()) Text(item.quantity, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
