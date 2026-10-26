package com.temankuliah.app

import com.temankuliah.app.ui.components.combineDeadlineDatePreservingTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DeadlineDateTimeTest {
    @Test
    fun selectingFebruaryFromThirtyFirstPreservesTheExactChosenDateAndClock() {
        val zone = ZoneId.systemDefault()
        val original = LocalDateTime.of(2026, 1, 31, 14, 35).atZone(zone).toInstant().toEpochMilli()

        val result = combineDeadlineDatePreservingTime(original, LocalDate.of(2026, 2, 28))
        val actual = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(result), zone)

        assertEquals(LocalDateTime.of(2026, 2, 28, 14, 35), actual)
    }
}
