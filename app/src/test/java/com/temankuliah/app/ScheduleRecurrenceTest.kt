package com.temankuliah.app

import com.temankuliah.app.domain.ScheduleRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleRecurrenceTest {
    @Test
    fun `16 pertemuan mingguan berselang 7 hari dan jatuh pada hari yang sama`() {
        val first = LocalDate.of(2026, 9, 7) // Senin
        val dates = ScheduleRecurrence.weeklyDates(first, 16)

        assertEquals(16, dates.size)
        assertEquals(first, dates.first())
        assertEquals(LocalDate.of(2026, 12, 21), dates.last())
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(dates.zipWithNext().all { (a, b) -> a.plusDays(7) == b })
    }

    @Test
    fun `pergantian bulan dan tahun dihitung benar`() {
        val dates = ScheduleRecurrence.weeklyDates(LocalDate.of(2026, 12, 28), 3)
        assertEquals(listOf(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 11)), dates)
    }

    @Test
    fun `jumlah di luar 1 sampai 16 ditolak`() {
        listOf(0, -1, 17, 100).forEach { count ->
            try {
                ScheduleRecurrence.weeklyDates(LocalDate.of(2026, 9, 7), count)
                fail("Jumlah $count seharusnya ditolak")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
