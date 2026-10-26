@file:Suppress("DEPRECATION")

package com.temankuliah.app

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.notifications.ReminderScheduler
import com.temankuliah.app.data.notifications.ReminderSelfTest
import com.temankuliah.app.data.notifications.ScheduleNotificationReceiver
import com.temankuliah.app.domain.CheckStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderSelfTestTest {
    private lateinit var app: Application
    private lateinit var receiverScope: CoroutineScope

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ReminderScheduler.receiverScope = receiverScope
    }

    @After
    fun tearDown() {
        ReminderScheduler.receiverScope = CoroutineScope(Dispatchers.IO)
        receiverScope.cancel()
    }

    private fun testAlarmIntents(): List<Intent> {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return shadowOf(alarmManager).scheduledAlarms.mapNotNull { alarm ->
            alarm.operation?.let { shadowOf(it).savedIntent }
        }.filter { it.action == ReminderScheduler.ACTION_TEST_ALARM }
    }

    private fun activeTestNotifications() =
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications
            .filter { it.id == ReminderScheduler.TEST_NOTIFICATION_ID }

    private fun awaitReceiverWork() = runBlocking {
        withTimeout(5_000) { receiverScope.coroutineContext[Job]!!.children.toList().joinAll() }
    }

    @Test
    fun armTestAlarmSchedulesOneAlarmAndCancelRemovesIt() {
        val mode = ReminderScheduler.armTestAlarm(app, token = 7L, triggerAtMillis = System.currentTimeMillis() + 15_000L)
        assertTrue(mode != ReminderScheduler.AlarmMode.FAILED)
        val intents = testAlarmIntents()
        assertEquals(1, intents.size)
        assertEquals(7L, intents.first().getLongExtra(ReminderScheduler.EXTRA_TEST_TOKEN, 0L))

        ReminderScheduler.cancelTestAlarm(app)
        assertTrue(testAlarmIntents().isEmpty())
    }

    @Test
    fun firedTestAlarmPostsNotificationAndRecordsVerifiedResult() {
        val token = 4242L
        val intent = Intent(app, ScheduleNotificationReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_TEST_ALARM)
            .putExtra(ReminderScheduler.EXTRA_TEST_TOKEN, token)

        ScheduleNotificationReceiver().onReceive(app, intent)
        awaitReceiverWork()

        val fire = ReminderSelfTest.lastFire.value
        assertNotNull(fire)
        assertEquals(token, fire!!.token)
        assertTrue(fire.posted)
        assertEquals(true, fire.visible)
        assertEquals(1, activeTestNotifications().size)
    }

    @Test
    fun notificationTestReportsPostedAndVisibleNotification() = runBlocking {
        val checks = ReminderSelfTest.runNotificationTest(app)
        val delivery = checks.first { it.title == "Notifikasi uji" }
        assertEquals(CheckStatus.OK, delivery.status)
        assertEquals(1, activeTestNotifications().size)
        assertTrue("Tes notifikasi tidak menilai alarm presisi", checks.none { it.title == "Alarm presisi" })
    }

    @Test
    fun repeatedNotificationTestsKeepSingleTestNotification() = runBlocking {
        ReminderSelfTest.runNotificationTest(app)
        ReminderSelfTest.runNotificationTest(app)
        assertEquals(1, activeTestNotifications().size)
    }
}
