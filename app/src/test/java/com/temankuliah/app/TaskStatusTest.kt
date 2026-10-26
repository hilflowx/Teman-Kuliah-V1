package com.temankuliah.app

import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStatusTest {

    @Test
    fun taskStatus_constantValues_matchExpectedStrings() {
        assertEquals("Belum Dikerjakan", TaskStatus.BELUM)
        assertEquals("Sedang Dikerjakan", TaskStatus.SEDANG)
        assertEquals("Selesai", TaskStatus.SELESAI)
    }

    @Test
    fun taskEntity_defaultStatus_matchesTaskStatusBelum() {
        val task = TaskEntity(userId = 1L, title = "Tugas Baru", dueAtMillis = 1_000_000L)
        assertEquals(TaskStatus.BELUM, task.status)
    }

    @Test
    fun taskStatus_validStatuses_containAllThreeConstants() {
        val validStatuses = setOf(TaskStatus.BELUM, TaskStatus.SEDANG, TaskStatus.SELESAI)
        assertEquals(3, validStatuses.size)
        assertTrue(validStatuses.contains("Belum Dikerjakan"))
        assertTrue(validStatuses.contains("Sedang Dikerjakan"))
        assertTrue(validStatuses.contains("Selesai"))
    }
}
