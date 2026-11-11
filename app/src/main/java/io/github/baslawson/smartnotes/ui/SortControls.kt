package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import io.github.baslawson.smartnotes.data.Sorting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The sort icon: three lines, shorter each time (the core icon set has none). */
val SortIcon: ImageVector by lazy {
    ImageVector.Builder("Sort", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
            moveTo(3f, 6f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
            moveTo(3f, 11f); horizontalLineTo(15f); verticalLineTo(13f); horizontalLineTo(3f); close()
            moveTo(3f, 16f); horizontalLineTo(9f); verticalLineTo(18f); horizontalLineTo(3f); close()
        }
    }.build()
}

/** One group of choices in a sort menu ([title] shown above it when the menu has several). */
class SortGroup<T>(val title: String?, val options: List<T>, val selected: T, val label: (T) -> String, val onPick: (T) -> Unit)

/** The ⇅ button: a menu of sort orders, the chosen one ticked; [extra] adds items below (Arrange lists…). */
@Composable
fun SortButton(vararg groups: SortGroup<*>, extra: (@Composable (close: () -> Unit) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(SortIcon, contentDescription = "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            groups.forEachIndexed { i, group ->
                if (i > 0) HorizontalDivider()
                @Suppress("UNCHECKED_CAST")
                SortGroupItems(group as SortGroup<Any?>) { open = false }
            }
            extra?.invoke { open = false }
        }
    }
}

@Composable
private fun <T> SortGroupItems(group: SortGroup<T>, close: () -> Unit) {
    group.title?.let {
        DropdownMenuItem(
            text = { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
            onClick = {}, enabled = false,
        )
    }
    group.options.forEach { option ->
        DropdownMenuItem(
            text = { Text(group.label(option)) },
            onClick = { close(); group.onPick(option) },
            trailingIcon = { if (option == group.selected) Icon(Icons.Default.Check, contentDescription = "Chosen") },
        )
    }
}

/** The ≡ handle a row is dragged by in "My order". */
@Composable
fun DragHandleIcon(name: String, modifier: Modifier = Modifier) {
    Icon(Icons.Default.Menu, contentDescription = "Drag $name", modifier = modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The order of rows being dragged. It shows the stored order ([sync]), except from the start of a drag until the stored
 * order matches the drop: then the order as dragged. A drop in another sort switches to My order, and the screen may
 * first see My order with the old places (the save not back yet); the dragged order stays shown through that, so the
 * rows don't jump. One holder for the screen's life, so the drop always reads the latest order (the drag library keeps
 * the first stop handler it was given).
 */
@Stable
class DragOrder(initial: List<Long>, private var mine: Boolean = false) {
    private var stored by mutableStateOf(initial)
    private var dragged by mutableStateOf<List<Long>?>(null)
    private var dragging = false
    /** What was on screen when this drag started: a drop back there changes nothing. */
    private var before: List<Long> = initial
    /** This drag started over an earlier drop not saved yet. */
    private var pending = false

    /** The ids in the order to show. */
    val ids: List<Long> get() = dragged ?: stored

    /** The stored order (from the database) and whether the section is in My order. */
    fun sync(order: List<Long>, mine: Boolean) {
        val sortChanged = mine != this.mine
        this.mine = mine
        if (order == stored && !sortChanged) return
        stored = order
        if (dragging) return
        val d = dragged ?: return
        // Done waiting: the drop is stored; rows came or went; or another order was chosen.
        if (order == d || order.toSet() != d.toSet() || (sortChanged && !mine)) dragged = null
    }

    /** The dragged order with rows added meanwhile put in at their stored place, and rows gone left out. */
    private fun reconciled(d: List<Long>): List<Long> {
        val present = stored.toSet()
        val result = d.filter { it in present }.toMutableList()
        val known = d.toSet()
        stored.forEachIndexed { i, id -> if (id !in known) result.add(i.coerceAtMost(result.size), id) }
        return result
    }

    fun start() {
        dragging = true
        pending = dragged != null
        if (dragged == null) dragged = stored
        before = dragged ?: stored
    }
    /** [moved] goes where [target] is (both must be in the order). */
    fun move(moved: Long, target: Long) { dragged = Sorting.move(ids, moved, target) }

    /**
     * The drop: the order to save (with any row added during the drag), or null when nothing moved, so a drop in place
     * neither saves nor changes the sort.
     */
    fun stop(): List<Long>? {
        dragging = false
        val result = dragged?.let(::reconciled) ?: stored
        // Nothing moved since the drag started (compared with what was on screen then, which may be an earlier drop
        // not saved yet: a drag that puts that back to the old order is a move to save).
        if (result == reconciled(before)) {
            // Still waiting for the earlier drop's save: keep showing it (with any rows that came meanwhile).
            dragged = if (!pending || result == stored) null else result
            return null
        }
        dragged = result
        return result
    }
}

/** A [DragOrder] kept in step with [stored] (the order from the database) and [mine] (the section is in My order). */
@Composable
fun rememberDragOrder(stored: List<Long>, mine: Boolean): DragOrder {
    val order = remember { DragOrder(stored, mine) }
    SideEffect { order.sync(stored, mine) }
    return order
}
