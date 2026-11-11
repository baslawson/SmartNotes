package io.github.baslawson.smartnotes.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderRulesTest {
    private fun t(s: String) = LocalDateTime.parse(s)

    @Test fun planKeepsWantedRowsCancelsGoneOnesAndAddsOnlyFutureTimes() {
        val now = t("2026-11-11T12:00")
        val rows = listOf(
            LedgerRow(1, t("2026-11-11T09:00"), t("2026-11-11T09:00")), // past, still wanted, Android late: kept
            LedgerRow(2, t("2026-11-12T09:00"), t("2026-11-12T09:30")), // snoozed, still wanted: kept with its snooze
            LedgerRow(3, t("2026-11-13T09:00"), t("2026-11-13T09:00")), // no longer wanted: cancelled
        )
        val wanted = setOf(t("2026-11-11T09:00"), t("2026-11-12T09:00"), t("2026-11-14T08:00"), t("2026-11-10T08:00"))
        val plan = AlarmPlanner.plan(wanted, rows, now)
        assertEquals(listOf(3L), plan.cancel)
        assertEquals(listOf(1L, 2L), plan.keep)
        // The past 2026-11-10 one never gets a new alarm (it would show again).
        assertEquals(listOf(t("2026-11-14T08:00")), plan.add)
    }

    @Test fun missedAfterRestart() {
        val now = t("2026-11-11T12:00")
        val rows = listOf(LedgerRow(1, t("2026-11-11T09:00"), t("2026-11-11T11:00")), LedgerRow(2, t("2026-11-11T13:00"), t("2026-11-11T13:00")))
        assertEquals(listOf(1L), AlarmPlanner.missed(rows, now).map { it.id })
    }

    @Test fun todoAndListTimes() {
        assertEquals(t("2026-11-20T09:00"), AlarmPlanner.todoTime(true, false, "2026-11-20", null))
        assertEquals(t("2026-11-20T17:30"), AlarmPlanner.todoTime(true, false, "2026-11-20", "17:30"))
        assertNull(AlarmPlanner.todoTime(false, false, "2026-11-20", "17:30"))
        assertNull(AlarmPlanner.todoTime(true, true, "2026-11-20", "17:30"))
        assertNull(AlarmPlanner.todoTime(true, false, null, "17:30"))
        assertEquals(t("2026-11-14T08:15"), AlarmPlanner.listTime("2026-11-14", "08:15"))
        assertNull(AlarmPlanner.listTime("2026-11-14", null))
        assertNull(AlarmPlanner.listTime("garbage", "08:15"))
    }

    @Test fun repeatNextDates() {
        val d = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), Repeat.DAILY.next(d, 1))
        assertEquals(LocalDate.of(2026, 2, 7), Repeat.WEEKLY.next(d, 1))
        // Monthly keeps the day where the month has it, else the month's last day.
        assertEquals(LocalDate.of(2026, 2, 28), Repeat.MONTHLY.next(d, 1))
        assertEquals(LocalDate.of(2026, 3, 31), Repeat.MONTHLY.next(LocalDate.of(2026, 2, 28), 1, originalDay = 31))
        assertEquals(LocalDate.of(2026, 2, 3), Repeat.EVERY_N_DAYS.next(d, 3))
        assertEquals(LocalDate.of(2026, 2, 1), Repeat.EVERY_N_DAYS.next(d, 0))
        assertNull(Repeat.NONE.next(d, 1))
    }

    @Test fun snoozeChoices() {
        val evening = t("2026-11-11T20:15")
        assertEquals(t("2026-11-11T20:25"), SnoozeChoice.TEN_MINUTES.until(evening))
        assertEquals(t("2026-11-11T21:15"), SnoozeChoice.ONE_HOUR.until(evening))
        assertEquals(t("2026-11-12T09:00"), SnoozeChoice.TOMORROW.until(evening))
        // In the small hours "tomorrow" is the coming morning.
        assertEquals(t("2026-11-11T09:00"), SnoozeChoice.TOMORROW.until(t("2026-11-11T01:30")))
        assertEquals("This morning at 9 am", SnoozeChoice.TOMORROW.label(t("2026-11-11T01:30")))
    }

    @Test fun soundResolvesDefault() {
        assertEquals(ReminderSound.MINUTE, ReminderSound.DEFAULT.resolve(ReminderSound.MINUTE))
        assertEquals(ReminderSound.SECONDS_10, ReminderSound.DEFAULT.resolve(ReminderSound.DEFAULT))
        assertEquals(ReminderSound.NOTIFICATION, ReminderSound.NOTIFICATION.resolve(ReminderSound.MINUTE))
        assertEquals(false, ReminderSound.NOTIFICATION.rings)
        assertEquals(0, ReminderSound.UNTIL_STOPPED.ringSeconds)
        assertEquals(ReminderSound.DEFAULT, ReminderSound.of("nonsense"))
    }
}
