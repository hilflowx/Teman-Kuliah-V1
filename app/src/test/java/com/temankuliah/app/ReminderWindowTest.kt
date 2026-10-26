package com.temankuliah.app

import com.temankuliah.app.domain.ReminderWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderWindowTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `past or current trigger is not armed`() {
        assertFalse(ReminderWindow.shouldArmClassAlarm(now - 1, now))
        assertFalse(ReminderWindow.shouldArmClassAlarm(now, now))
    }

    @Test
    fun `trigger inside the window is armed, including the boundary`() {
        assertTrue(ReminderWindow.shouldArmClassAlarm(now + day, now))
        assertTrue(ReminderWindow.shouldArmClassAlarm(now + ReminderWindow.CLASS_ALARM_WINDOW_DAYS * day, now))
    }

    @Test
    fun `trigger beyond the window is not armed`() {
        assertFalse(ReminderWindow.shouldArmClassAlarm(now + ReminderWindow.CLASS_ALARM_WINDOW_DAYS * day + 1, now))
    }
}
