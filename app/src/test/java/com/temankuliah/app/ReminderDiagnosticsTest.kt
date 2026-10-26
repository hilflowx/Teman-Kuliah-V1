package com.temankuliah.app

import com.temankuliah.app.domain.ChannelState
import com.temankuliah.app.domain.CheckStatus
import com.temankuliah.app.domain.ReminderCheck
import com.temankuliah.app.domain.ReminderEnvironment
import com.temankuliah.app.domain.evaluateAlarmFire
import com.temankuliah.app.domain.evaluateNotificationDelivery
import com.temankuliah.app.domain.evaluateReminderEnvironment
import com.temankuliah.app.domain.reminderTestVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDiagnosticsTest {
    private val highChannels = listOf(ChannelState("Tugas", 4, true), ChannelState("Kuliah", 4, true))

    private fun env(
        notificationsEnabled: Boolean = true,
        exactAlarmAllowed: Boolean = true,
        sound: Boolean = true,
        vibration: Boolean = true,
        channels: List<ChannelState> = highChannels,
        dnd: Boolean = false,
        ringerNormal: Boolean = true,
        volume: Int = 5
    ) = ReminderEnvironment(notificationsEnabled, exactAlarmAllowed, sound, vibration, channels, dnd, ringerNormal, volume)

    private fun List<ReminderCheck>.status(title: String): CheckStatus = first { it.title == title }.status

    @Test
    fun healthyDevicePassesEveryCheck() {
        val checks = evaluateReminderEnvironment(env(), includeAlarm = true)
        assertTrue(checks.all { it.status == CheckStatus.OK })
        assertEquals(CheckStatus.OK, reminderTestVerdict(checks))
    }

    @Test
    fun alarmCheckOnlyAppearsForAlarmTest() {
        assertTrue(evaluateReminderEnvironment(env(), includeAlarm = false).none { it.title == "Alarm presisi" })
        assertTrue(evaluateReminderEnvironment(env(), includeAlarm = true).any { it.title == "Alarm presisi" })
    }

    @Test
    fun disabledNotificationsFail() {
        val checks = evaluateReminderEnvironment(env(notificationsEnabled = false), includeAlarm = false)
        assertEquals(CheckStatus.FAILED, checks.status("Izin notifikasi"))
        assertEquals(CheckStatus.FAILED, reminderTestVerdict(checks))
    }

    @Test
    fun blockedChannelFailsAndNamesTheChannel() {
        val channels = listOf(ChannelState("Tugas", 0, true), ChannelState("Kuliah", 4, true))
        val check = evaluateReminderEnvironment(env(channels = channels), includeAlarm = false).first { it.title == "Saluran notifikasi" }
        assertEquals(CheckStatus.FAILED, check.status)
        assertTrue(check.detail.contains("Tugas"))
    }

    @Test
    fun lowImportanceChannelWarnsAboutMissingBanner() {
        val channels = listOf(ChannelState("Tugas", 4, true), ChannelState("Kuliah", 3, true))
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(channels = channels), includeAlarm = false).status("Saluran notifikasi"))
    }

    @Test
    fun noChannelsOnAndroid7SkipsChannelCheck() {
        assertTrue(evaluateReminderEnvironment(env(channels = emptyList()), includeAlarm = false).none { it.title == "Saluran notifikasi" })
    }

    @Test
    fun soundWarnsWhenDeviceWouldMuteIt() {
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(volume = 0), false).status("Bunyi"))
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(ringerNormal = false), false).status("Bunyi"))
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(dnd = true), false).status("Bunyi"))
        val mutedChannel = listOf(ChannelState("Tugas", 4, true), ChannelState("Kuliah", 4, false))
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(channels = mutedChannel), false).status("Bunyi"))
    }

    @Test
    fun soundAndVibrationTurnedOffInPreferencesAreInfoNotProblems() {
        val checks = evaluateReminderEnvironment(env(sound = false, vibration = false, volume = 0), includeAlarm = false)
        assertEquals(CheckStatus.INFO, checks.status("Bunyi"))
        assertEquals(CheckStatus.INFO, checks.status("Getaran"))
        assertEquals(CheckStatus.OK, reminderTestVerdict(checks))
    }

    @Test
    fun exactAlarmNotAllowedWarns() {
        assertEquals(CheckStatus.WARNING, evaluateReminderEnvironment(env(exactAlarmAllowed = false), true).status("Alarm presisi"))
    }

    @Test
    fun notificationDeliveryDistinguishesEveryOutcome() {
        assertEquals(CheckStatus.FAILED, evaluateNotificationDelivery(permitted = false, posted = false, visible = null).status)
        assertEquals(CheckStatus.FAILED, evaluateNotificationDelivery(permitted = true, posted = false, visible = null).status)
        assertEquals(CheckStatus.OK, evaluateNotificationDelivery(permitted = true, posted = true, visible = true).status)
        assertEquals(CheckStatus.FAILED, evaluateNotificationDelivery(permitted = true, posted = true, visible = false).status)
        assertEquals(CheckStatus.WARNING, evaluateNotificationDelivery(permitted = true, posted = true, visible = null).status)
    }

    @Test
    fun alarmFiringOnTimeWithExactAlarmPasses() {
        val check = evaluateAlarmFire(triggerAtMillis = 100_000L, firedAtMillis = 101_200L, exactRequested = true, waitedSeconds = 45)
        assertEquals(CheckStatus.OK, check.status)
        assertTrue(check.detail.contains("1,2"))
    }

    @Test
    fun lateAlarmWarnsEvenWhenItFired() {
        assertEquals(CheckStatus.WARNING, evaluateAlarmFire(100_000L, 108_000L, exactRequested = true, waitedSeconds = 45).status)
    }

    @Test
    fun inexactAlarmNeverCountsAsOnTime() {
        assertEquals(CheckStatus.WARNING, evaluateAlarmFire(100_000L, 100_100L, exactRequested = false, waitedSeconds = 90).status)
    }

    @Test
    fun alarmThatNeverFiredFailsAndReportsWaitTime() {
        val check = evaluateAlarmFire(100_000L, null, exactRequested = true, waitedSeconds = 45)
        assertEquals(CheckStatus.FAILED, check.status)
        assertTrue(check.detail.contains("45 detik"))
    }

    @Test
    fun verdictTakesWorstStatusAndIgnoresInfo() {
        val ok = ReminderCheck("a", CheckStatus.OK, "")
        val info = ReminderCheck("b", CheckStatus.INFO, "")
        val warn = ReminderCheck("c", CheckStatus.WARNING, "")
        val fail = ReminderCheck("d", CheckStatus.FAILED, "")
        assertEquals(CheckStatus.OK, reminderTestVerdict(listOf(ok, info)))
        assertEquals(CheckStatus.WARNING, reminderTestVerdict(listOf(ok, warn, info)))
        assertEquals(CheckStatus.FAILED, reminderTestVerdict(listOf(ok, warn, fail)))
    }
}
