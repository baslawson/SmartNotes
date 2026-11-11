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
}
