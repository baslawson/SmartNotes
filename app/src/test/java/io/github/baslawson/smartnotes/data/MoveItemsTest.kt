package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveItemsTest {
    private fun item(id: Long, list: Long, name: String, quantity: String = "", checked: Boolean = false, position: Long = 0) =
        ShoppingItem(id = id, listId = list, name = name, key = name.lowercase(), quantity = quantity, checked = checked,
            addedAt = id, checkedAt = if (checked) 50 else 0, position = position)

    @Test fun movedItemsGoToTheEndOfTheOtherListKeepingTheirTicks() {
        val plan = planMove(listOf(item(1, 2, "milk"), item(2, 2, "eggs", checked = true)), listOf(item(9, 5, "bread", position = 4)), 5, 4, 1000)
        assertEquals(listOf(5L, 5L), plan.move.map { it.listId })
        assertEquals(listOf(5L, 6L), plan.move.map { it.position })
        assertEquals(listOf(1000L, 1001L), plan.move.map { it.addedAt })
        assertEquals(listOf(false, true), plan.move.map { it.checked })
        assertEquals(emptyList<ShoppingItem>(), plan.merge)
        assertEquals(emptyList<Long>(), plan.drop)
    }

    @Test fun anItemTheListHasIsNotAddedTwice() {
        val there = listOf(item(9, 5, "Milk", "1L"), item(10, 5, "eggs", checked = true), item(11, 5, "tea"))
        val plan = planMove(
            listOf(item(1, 2, "milk", "2x"), item(2, 2, "eggs"), item(3, 2, "tea", checked = true)), there, 5, 3, 1000,
        )
        assertEquals(emptyList<ShoppingItem>(), plan.move)
        assertEquals(listOf(1L, 2L, 3L), plan.drop)
        val byId = plan.merge.associateBy { it.id }
        // The moved quantity wins; the one there keeps its name and place.
        assertEquals("2x", byId.getValue(9).quantity)
        assertEquals("Milk", byId.getValue(9).name)
        // Still to buy if either was: the ticked eggs there come back, to the end of "Added".
        assertEquals(false, byId.getValue(10).checked)
        assertEquals(1000L, byId.getValue(10).addedAt)
        // Tea there was still to buy: it stays so (the moved one was ticked).
        assertEquals(false, byId.getValue(11).checked)
    }

    @Test fun aQuantityOnlyTheOneThereHadIsKept() {
        val plan = planMove(listOf(item(1, 2, "milk")), listOf(item(9, 5, "milk", "1L")), 5, 0, 1000)
        assertEquals("1L", plan.merge.single().quantity)
    }

    @Test fun theSameNameMovedTwiceBecomesOneAndItemsAlreadyThereAreLeft() {
        val plan = planMove(listOf(item(1, 2, "milk"), item(2, 3, "MILK", "2x"), item(3, 5, "bread")), emptyList(), 5, 0, 1000)
        assertEquals(1, plan.move.size)
        assertEquals("2x", plan.move.single().quantity)
        assertEquals(1L, plan.move.single().id)
        assertEquals(listOf(2L), plan.drop)
    }
}
