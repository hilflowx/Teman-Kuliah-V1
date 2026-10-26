package com.temankuliah.app

import com.temankuliah.app.data.local.SCHEDULE_MODE_ONLINE
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.domain.isOnlineMeetingLink
import com.temankuliah.app.domain.isOnlineSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleOnlineTest {
    private fun schedule(room: String = "", mode: String = "OFFLINE", type: String = "Kuliah Teori") = ScheduleEntity(
        userId = 1L, courseName = "Basis Data", dayOfWeek = 1, startTime = "08:00", endTime = "09:40",
        room = room, lecturer = "", type = type, mode = mode
    )

    @Test
    fun `mode column decides online or offline`() {
        assertTrue(schedule(mode = SCHEDULE_MODE_ONLINE).isOnlineSchedule())
        assertFalse(schedule(mode = "OFFLINE").isOnlineSchedule())
    }

    @Test
    fun `legacy rows default to offline regardless of room or type text`() {
        assertFalse(schedule(room = "https://meet.google.com/abc-defg-hij").isOnlineSchedule())
        assertFalse(schedule(room = "Ruang 301", type = "Online · Praktikum").isOnlineSchedule())
    }

    @Test
    fun `meeting host helper still rejects lookalike hosts`() {
        assertTrue(isOnlineMeetingLink("https://us02web.zoom.us/j/123"))
        assertFalse(isOnlineMeetingLink("https://meet.google.com.evil.example/abc"))
        assertFalse(isOnlineMeetingLink("meet.google.com/abc"))
        assertFalse(isOnlineMeetingLink("Meeting Room"))
    }
}
