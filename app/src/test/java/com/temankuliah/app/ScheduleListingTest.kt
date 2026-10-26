package com.temankuliah.app

import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.domain.visibleInScheduleList
import com.temankuliah.app.domain.sortedChronologically
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleListingTest {
    private fun schedule(id: Long, date: String, start: String, semester: Int = 3, completed: Boolean = false, group: String = "") = ScheduleEntity(
        id = id, userId = 1, courseName = "MK $id",
        dayOfWeek = LocalDate.parse(date).dayOfWeek.value,
        startTime = start, endTime = "23:00", room = "", lecturer = "",
        semester = semester, scheduledDate = date, isCompleted = completed, recurrenceGroupId = group
    )

    @Test
    fun `urutan mengikuti tanggal lalu jam, bukan hari dalam minggu`() {
        val list = listOf(
            schedule(1, "2026-09-14", "08:00"), // Senin minggu ke-2
            schedule(2, "2026-09-08", "08:00"), // Selasa minggu ke-1
            schedule(3, "2026-09-07", "13:00"), // Senin minggu ke-1, siang
            schedule(4, "2026-09-07", "08:00")  // Senin minggu ke-1, pagi
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), list.sortedChronologically().map { it.id })
    }

    @Test
    fun `jadwal sekali yang selesai disembunyikan dari daftar Jadwal tetapi jadwal berulang tidak`() {
        val list = listOf(
            schedule(1, "2026-09-07", "08:00"),                                   // sekali, belum selesai
            schedule(2, "2026-09-08", "08:00", completed = true),                 // sekali, selesai
            schedule(3, "2026-09-09", "08:00", completed = true, group = "seri"), // berulang, selesai
            schedule(4, "2026-09-16", "08:00", group = "seri")                    // berulang, belum selesai
        )
        assertEquals(listOf(1L, 3L, 4L), list.visibleInScheduleList().map { it.id })
    }
}
