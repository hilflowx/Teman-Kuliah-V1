package com.temankuliah.app

import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.domain.ScheduleDraft
import com.temankuliah.app.domain.ScheduleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleRulesTest {
    private val semesterStart = LocalDate.parse("2026-09-07") // Senin

    private fun draft(
        repeating: Boolean = true,
        days: Set<Int> = setOf(3, 4),
        start: String = "08:00",
        end: String = "10:30",
        untilEnd: Boolean = true,
        rangeEnd: LocalDate? = null,
        name: String = "Expert System",
        mode: String = "OFFLINE",
        link: String = ""
    ) = ScheduleDraft(
        sumber = "MANUAL", krsId = null, courseName = name, lecturer = "", sks = 2,
        repeating = repeating, date = LocalDate.parse("2026-10-31"), repeatDays = days,
        rangeStart = semesterStart, rangeEnd = rangeEnd, untilSemesterEnd = untilEnd,
        startTime = start, endTime = end, mode = mode, room = "R1", platform = "Zoom", meetingLink = link,
        notes = "", alarmEnabled = true, reminderMinutes = 15
    )

    @Test
    fun `semester end is sixteen weeks after start`() {
        assertEquals(LocalDate.parse("2026-12-27"), ScheduleRules.semesterEnd(semesterStart))
    }

    @Test
    fun `repeating schedule makes one series per day with correct dates`() {
        val rows = ScheduleRules.buildRows(draft(), 1L, 7, semesterStart)
        assertEquals(32, rows.size)
        assertTrue(rows.all { it.dayOfWeek == LocalDate.parse(it.scheduledDate).dayOfWeek.value })
        assertEquals(2, rows.map { it.recurrenceGroupId }.distinct().size)
        assertEquals("2026-09-09", rows.filter { it.dayOfWeek == 3 }.minOf { it.scheduledDate })
    }

    @Test
    fun `one time schedule has no series id`() {
        val rows = ScheduleRules.buildRows(draft(repeating = false), 1L, 7, semesterStart)
        assertEquals(1, rows.size)
        assertEquals("", rows.single().recurrenceGroupId)
        assertEquals("2026-10-31", rows.single().scheduledDate)
    }

    @Test
    fun `validation catches name time and link`() {
        assertNotNull(ScheduleRules.validate(draft(name = " "), semesterStart))
        assertNotNull(ScheduleRules.validate(draft(start = "10:00", end = "10:00"), semesterStart))
        assertNotNull(ScheduleRules.validate(draft(mode = "ONLINE", link = "meet google com/abc"), semesterStart))
        assertNull(ScheduleRules.validate(draft(mode = "ONLINE", link = "https://meet.google.com/abc"), semesterStart))
        assertNotNull(ScheduleRules.validate(draft(days = emptySet()), semesterStart))
        assertNotNull(ScheduleRules.validate(draft(untilEnd = true), null))
    }

    @Test
    fun `online rows drop room and keep platform and link`() {
        val row = ScheduleRules.buildRows(draft(repeating = false, mode = "ONLINE", link = "https://zoom.us/j/1"), 1L, 7, semesterStart).single()
        assertEquals("", row.room)
        assertEquals("Zoom", row.platform)
        assertEquals("https://zoom.us/j/1", row.linkMeeting)
    }

    @Test
    fun `conflict needs same date and overlapping time`() {
        val existing = ScheduleRules.buildRows(draft(repeating = false, start = "08:00", end = "10:00"), 1L, 7, semesterStart)
        val overlap = ScheduleRules.buildRows(draft(repeating = false, start = "09:00", end = "11:00"), 1L, 7, semesterStart)
        val adjacent = ScheduleRules.buildRows(draft(repeating = false, start = "10:00", end = "11:00"), 1L, 7, semesterStart)
        val saved = existing.map { it.copy(id = 5) }
        assertEquals(1, ScheduleRules.findConflicts(saved, overlap).size)
        assertTrue(ScheduleRules.findConflicts(saved, adjacent).isEmpty())
        assertTrue(ScheduleRules.findConflicts(saved, overlap, ignoreIds = setOf(5L)).isEmpty())
    }

    @Test
    fun `slots merge a series and weekly hours count offline only`() {
        val offline = ScheduleRules.buildRows(draft(days = setOf(3)), 1L, 7, semesterStart).mapIndexed { i, r -> r.copy(id = i + 1L) }
        val online = ScheduleRules.buildRows(draft(days = setOf(4), mode = "ONLINE"), 1L, 7, semesterStart).mapIndexed { i, r -> r.copy(id = 100L + i) }
        val slots = ScheduleRules.buildSlots(offline + online, LocalDate.parse("2026-10-25"))
        assertEquals(2, slots.size)
        assertEquals("2026-10-28", slots.first { it.representative.dayOfWeek == 3 }.representative.scheduledDate)
        assertEquals(150, ScheduleRules.faceToFaceMinutesPerWeek(slots))
        assertEquals("2,5 jam", ScheduleRules.formatHours(150))
        assertEquals("0 jam", ScheduleRules.formatHours(0))
    }

    @Test
    fun `series info recognises run until semester end`() {
        val rows: List<ScheduleEntity> = ScheduleRules.buildRows(draft(days = setOf(3)), 1L, 7, semesterStart)
        val info = ScheduleRules.seriesInfoOf(rows, semesterStart)!!
        assertTrue(info.untilSemesterEnd)
        assertEquals(setOf(3), info.days)
        val short = ScheduleRules.buildRows(draft(days = setOf(3), untilEnd = false, rangeEnd = LocalDate.parse("2026-10-01")), 1L, 7, semesterStart)
        assertFalse(ScheduleRules.seriesInfoOf(short, semesterStart)!!.untilSemesterEnd)
    }
}
