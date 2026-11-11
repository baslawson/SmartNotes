package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShoppingListsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val zone = java.time.ZoneOffset.UTC
    private fun made(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun list(id: Long, date: String?, deleted: Long? = null, createdAt: Long = 0) =
        ShoppingList(id, date ?: "Shopping", date = date, createdAt = createdAt, deletedAt = deleted)

    @Test fun chipsShowAnytimeThenTodayAndComingThenPastNewestFirst() {
        val lists = listOf(
            list(2, "2026-11-01"), list(3, "2026-11-12"), list(1, null), list(4, "2026-11-11"),
            list(5, "2026-11-10"), list(6, "2026-12-24"),
        )
        assertEquals(listOf(1L, 4L, 3L, 6L, 5L, 2L), orderLists(lists, today).map { it.id })
    }

    @Test fun pastListsWithNothingLeftToBuyAreTidied() {
        val lists = listOf(
            list(1, null),                // Anytime: never
            list(2, "2026-11-10"),        // yesterday, all bought: tidied
            list(3, "2026-11-09"),        // still has things to buy: stays
            list(4, "2026-11-11"),        // today: stays
            list(5, "2026-11-12"),        // tomorrow: stays
            list(6, "2026-11-01", deleted = 5), // already in the bin
            list(7, "2026-11-08", createdAt = made("2026-11-08")), // made on its day, all bought: tidied
            list(8, "2026-11-07", createdAt = made("2026-11-10")), // written up afterwards, or restored: kept
        )
        assertEquals(listOf(2L, 7L), listsToTidy(lists, today, withOpenItems = setOf(3L), zone = zone))
    }

    @Test fun pastMeansADayBeforeToday() {
        assertEquals(true, list(2, "2026-11-10").isPast(today))
        assertEquals(false, list(3, "2026-11-11").isPast(today))
        assertEquals(false, list(4, "2026-11-12").isPast(today))
        assertEquals(false, list(1, null).isPast(today))
        assertEquals(false, list(5, "not a date").isPast(today))
    }

    @Test fun searchTextMatchesOnlyItselfInLike() {
        assertEquals("milk", likeEscaped("milk"))
        assertEquals("50\\%", likeEscaped("50%"))
        assertEquals("a\\_b", likeEscaped("a_b"))
        assertEquals("c\\\\d", likeEscaped("c\\d"))
    }

    @Test fun renamingMovesOneUseToTheNewName() {
        val typo = ShoppingHistory("mlik", "mlik", 1, 5)
        // Used once (just this item): forgotten; the new name gets its first use.
        assertEquals(null to ShoppingHistory("milk", "milk", 1, 9), renamedHistory("mlik", typo, null, "milk", "milk", 9))
        // Used more: one use fewer; the new name's count goes up.
        val oat = ShoppingHistory("oat milk", "Oat milk", 4, 2)
        assertEquals(
            typo.copy(useCount = 2) to oat.copy(name = "oat milk", useCount = 5, lastUsed = 9),
            renamedHistory("mlik", typo.copy(useCount = 3), oat, "oat milk", "oat milk", 9),
        )
        // Only capitals changed: same entry, new spelling; nothing made up when there was none.
        val milk = ShoppingHistory("milk", "milk", 3, 2)
        assertEquals(milk to milk.copy(name = "Milk"), renamedHistory("milk", milk, milk, "milk", "Milk", 9))
        assertEquals(null to null, renamedHistory("milk", null, null, "milk", "Milk", 9))
    }

    private fun item(id: Long, listId: Long, name: String, quantity: String = "", checked: Boolean = false) =
        ShoppingItem(id, listId, name, name.lowercase(), quantity, checked, 0, 0, 0)

    @Test fun whichListsCarryOver() {
        assertEquals(true, carriesOver(list(2, "2026-11-10", createdAt = made("2026-11-09")), today, zone))
        assertEquals(false, carriesOver(list(3, "2026-11-11"), today, zone))             // today
        assertEquals(false, carriesOver(list(1, null), today, zone))                     // Anytime
        assertEquals(false, carriesOver(list(4, "2026-11-01", deleted = 5), today, zone)) // in the bin
        assertEquals(false, carriesOver(list(5, "2026-11-01", createdAt = made("2026-11-05")), today, zone)) // made afterwards
    }

    @Test fun carryOverMovesOnceAndKeepsTodaysItem() {
        val open = listOf(
            item(10, 2, "Milk"), item(11, 2, "Bread"),            // oldest past list
            item(12, 3, "milk", "2x"), item(13, 3, "Eggs", "x12"), // a later one: milk again, with a quantity
            item(14, 3, "Apples", "3"),
        )
        val todays = listOf(item(20, 9, "eggs"), item(21, 9, "Apples", "1kg"), item(22, 9, "Bread", checked = true))
        val plan = planCarryOver(open, todays)
        // Milk once (the oldest, given the later quantity); eggs and apples and bread already on today's list.
        assertEquals(listOf(item(10, 2, "Milk", "2x")), plan.move)
        assertEquals(listOf(item(20, 9, "eggs", "x12")), plan.update)
        assertEquals(listOf(11L, 12L, 13L, 14L), plan.drop)
    }

    @Test fun nothingToCarry() {
        assertEquals(CarryPlan(emptyList(), emptyList(), emptyList()), planCarryOver(emptyList(), listOf(item(1, 9, "milk"))))
    }
}
