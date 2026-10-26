package com.temankuliah.app

import com.temankuliah.app.data.local.AcademicEventEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.domain.NotificationGroup
import com.temankuliah.app.domain.NotificationKind
import com.temankuliah.app.domain.TaskStatus
import com.temankuliah.app.domain.buildNotificationFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationFeedTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val today = LocalDate.of(2026, 10, 25)
    private val now = LocalDateTime.of(2026, 10, 25, 10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun schedule(
        id: Long,
        date: LocalDate,
        start: String = "08:00",
        completed: Boolean = false,
        semester: Int = 7,
        room: String = "B2.4"
    ) = ScheduleEntity(
        id = id,
        userId = 1,
        courseName = "Expert System",
        dayOfWeek = 1,
        startTime = start,
        endTime = "09:40",
        room = room,
        lecturer = "Dosen",
        semester = semester,
        scheduledDate = date.toString(),
        isCompleted = completed
    )

    private fun task(id: Long, due: Long, status: String = TaskStatus.BELUM, semester: Int = 7, courseId: Long? = null) =
        TaskEntity(id = id, userId = 1, semester = semester, title = "Latihan $id", dueAtMillis = due, status = status, courseId = courseId)

    private fun feed(
        schedules: List<ScheduleEntity> = emptyList(),
        tasks: List<TaskEntity> = emptyList(),
        events: List<AcademicEventEntity> = emptyList(),
        includeSchedules: Boolean = true,
        includeTasks: Boolean = true
    ) = buildNotificationFeed(
        nowMillis = now,
        zone = zone,
        semester = 7,
        schedules = schedules,
        tasks = tasks,
        events = events,
        courseNames = mapOf(5L to "Data Mining"),
        includeSchedules = includeSchedules,
        includeTasks = includeTasks
    )

    @Test
    fun `empty data gives an empty feed and never invents items`() {
        assertTrue(feed().isEmpty())
    }

    @Test
    fun `class tomorrow shows as upcoming with start time and room`() {
        val item = feed(schedules = listOf(schedule(1, today.plusDays(1)))).single()
        assertEquals(NotificationKind.JADWAL, item.kind)
        assertEquals(NotificationGroup.HARI_INI, item.group)
        assertEquals("Kuliah besok pukul 08.00", item.title)
        assertEquals("Expert System · B2.4", item.subtitle)
        assertEquals(1, item.targetTab)
    }

    @Test
    fun `class that already started finished or is in another semester is skipped`() {
        val started = schedule(1, today, start = "09:00")
        val completed = schedule(2, today.plusDays(1), completed = true)
        val otherSemester = schedule(3, today.plusDays(1), semester = 6)
        val tooFar = schedule(4, today.plusDays(2))
        assertTrue(feed(schedules = listOf(started, completed, otherSemester, tooFar)).isEmpty())
    }

    @Test
    fun `task due in four days says so and carries the course name`() {
        val item = feed(tasks = listOf(task(1, millis(today.plusDays(4), 8), courseId = 5))).single()
        assertEquals("Tenggat 4 hari lagi", item.title)
        assertEquals("Latihan 1", item.subtitle)
        assertEquals("Data Mining · 08.00", item.detail)
        assertEquals(2, item.targetTab)
    }

    @Test
    fun `finished tasks and tasks too far ahead are hidden`() {
        val done = task(1, millis(today.plusDays(1), 8), status = TaskStatus.SELESAI)
        val far = task(2, millis(today.plusDays(8), 8))
        assertTrue(feed(tasks = listOf(done, far)).isEmpty())
    }

    @Test
    fun `overdue task is grouped by how long ago it was due`() {
        val yesterday = task(1, millis(today.minusDays(1), 16, 45))
        val older = task(2, millis(today.minusDays(3), 9))
        val tooOld = task(3, millis(today.minusDays(9), 9))
        val items = feed(tasks = listOf(older, yesterday, tooOld))
        assertEquals(listOf(NotificationGroup.KEMARIN, NotificationGroup.SEBELUMNYA), items.map { it.group })
        assertEquals("Tugas melewati tenggat", items.first().title)
        assertEquals("Tenggat kemarin, 16.45", items.first().detail)
    }

    @Test
    fun `task read key changes when the countdown changes but not when overdue`() {
        val dueTomorrow = millis(today.plusDays(1), 8)
        val keyToday = feed(tasks = listOf(task(1, dueTomorrow))).single().key
        val keyOverdue = feed(tasks = listOf(task(1, millis(today.minusDays(1), 8)))).single().key
        val keyOverdueLater = feed(tasks = listOf(task(1, millis(today.minusDays(2), 8)))).single().key
        assertTrue(keyToday != keyOverdue)
        assertEquals(keyOverdue, keyOverdueLater)
    }

    @Test
    fun `agenda tomorrow uses description as place and type as fallback`() {
        val withPlace = AcademicEventEntity(
            id = 1, userId = 1, semester = 7, title = "Seminar proposal",
            description = "Aula Utama", type = "Kegiatan Akademik", scheduledAtMillis = millis(today.plusDays(1), 13)
        )
        val noPlace = withPlace.copy(id = 2, description = "", type = "Ujian")
        val items = feed(events = listOf(withPlace, noPlace))
        assertEquals("Seminar proposal besok", items[0].title)
        assertEquals("13.00 · Aula Utama", items[0].subtitle)
        assertEquals("13.00 · Ujian", items[1].subtitle)
        assertTrue(items.all { it.kind == NotificationKind.AGENDA })
    }

    @Test
    fun `reminder switches in Profile hide the matching kinds`() {
        val schedules = listOf(schedule(1, today.plusDays(1)))
        val tasks = listOf(task(1, millis(today.plusDays(1), 8)))
        assertEquals(listOf(NotificationKind.TUGAS), feed(schedules, tasks, includeSchedules = false).map { it.kind })
        assertEquals(listOf(NotificationKind.JADWAL), feed(schedules, tasks, includeTasks = false).map { it.kind })
    }

    @Test
    fun `today group is sorted soonest first and earlier groups newest first`() {
        val items = feed(
            schedules = listOf(schedule(1, today.plusDays(1), start = "08:00")),
            tasks = listOf(
                task(2, millis(today, 23, 59)),
                task(3, millis(today.minusDays(2), 9)),
                task(4, millis(today.minusDays(1), 9)),
                task(5, millis(today.minusDays(4), 9))
            )
        )
        assertEquals(
            listOf("tugas_2_d0", "jadwal_1_d1", "tugas_4_late", "tugas_3_late", "tugas_5_late"),
            items.map { it.key }
        )
    }
}
