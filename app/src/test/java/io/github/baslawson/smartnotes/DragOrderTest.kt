package io.github.baslawson.smartnotes

import io.github.baslawson.smartnotes.ui.DragOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class DragOrderTest {
    @Test fun aSecondDragStartsFromTheFirstOnesResult() {
        val order = DragOrder(listOf(1, 2, 3))
        order.start(); order.move(1, 3)
        assertEquals(listOf(2L, 3L, 1L), order.stop())
        // The saved order arrives from the database; the next drag starts from it, and its drop is what is saved.
        order.sync(listOf(2, 3, 1))
        order.start(); order.move(1, 2)
        assertEquals(listOf(1L, 2L, 3L), order.stop())
    }

    @Test fun theDraggedOrderStaysUntilTheStoredOneChanges() {
        val order = DragOrder(listOf(1, 2, 3))
        order.start(); order.move(3, 1)
        // A database change during the drag doesn't undo it.
        order.sync(listOf(1, 2, 3, 4))
        assertEquals(listOf(3L, 1L, 2L), order.ids)
        order.stop()
        // After the drop, the stored order shows again once it changes.
        order.sync(listOf(3, 1, 2, 4))
        assertEquals(listOf(3L, 1L, 2L, 4L), order.ids)
    }

    @Test fun aRowAddedDuringADragIsntLeftHidden() {
        val order = DragOrder(listOf(1, 2))
        order.start()
        order.sync(listOf(1, 2, 3))
        // Dropped without a move: the new row is in what is saved, and the stored order shows.
        assertEquals(listOf(1L, 2L, 3L), order.stop())
        assertEquals(listOf(1L, 2L, 3L), order.ids)
    }

    @Test fun aRowAddedDuringADragKeepsTheDraggedMove() {
        val order = DragOrder(listOf(1, 2, 3))
        order.start(); order.move(3, 1)
        order.sync(listOf(4, 1, 2, 3))
        assertEquals(listOf(4L, 3L, 1L, 2L), order.stop())
    }
}
