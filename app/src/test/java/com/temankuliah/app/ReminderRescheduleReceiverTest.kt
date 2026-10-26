@file:Suppress("DEPRECATION")

package com.temankuliah.app

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.notifications.ReminderRescheduleReceiver
import com.temankuliah.app.data.notifications.ReminderScheduler
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderRescheduleReceiverTest {
    private lateinit var app: Application
    private lateinit var db: TemanKuliahDatabase
    private lateinit var receiverScope: CoroutineScope

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ReminderScheduler.receiverScope = receiverScope
        ReminderScheduler.databaseProvider = { db }
    }

    @After
    fun tearDown() {
        ReminderScheduler.receiverScope = CoroutineScope(Dispatchers.IO)
        ReminderScheduler.databaseProvider = { TemanKuliahDatabase.getDatabase(it) }
        receiverScope.cancel()
        db.close()
    }

    private suspend fun insertUser(name: String, nim: String, loggedIn: Boolean): Long =
        db.userDao().insertUser(
            UserEntity(fullName = name, nim = nim, studyProgram = "Informatika", faculty = "Teknik", university = "USP", password = "hash", isLoggedIn = loggedIn)
        )

    private fun scheduledTaskIds(): Set<Long> {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return shadowOf(alarmManager).scheduledAlarms.mapNotNull { alarm ->
            val intent = alarm.operation?.let { shadowOf(it).savedIntent } ?: return@mapNotNull null
            if (intent.getStringExtra("kind") == "task") intent.getLongExtra("row_id", -1L) else null
        }.toSet()
    }

    private fun awaitReceiverWork() = runBlocking {
        withTimeout(5_000) { receiverScope.coroutineContext[Job]!!.children.toList().joinAll() }
    }

    @Test
    fun bootCompletedTriggersReschedule() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = TaskEntity(userId = userId, title = "Tugas Boot", dueAtMillis = System.currentTimeMillis() + 86_400_000L)
        val taskId = db.taskDao().insert(task)
        assertTrue(scheduledTaskIds().isEmpty())

        ReminderRescheduleReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitReceiverWork()

        assertEquals(setOf(taskId), scheduledTaskIds())
    }

    @Test
    fun myPackageReplacedTriggersReschedule() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = TaskEntity(userId = userId, title = "Tugas Update", dueAtMillis = System.currentTimeMillis() + 86_400_000L)
        val taskId = db.taskDao().insert(task)
        assertTrue(scheduledTaskIds().isEmpty())

        ReminderRescheduleReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        awaitReceiverWork()

        assertEquals(setOf(taskId), scheduledTaskIds())
    }

    @Test
    fun otherActionIsIgnored() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = TaskEntity(userId = userId, title = "Tugas Battery", dueAtMillis = System.currentTimeMillis() + 86_400_000L)
        db.taskDao().insert(task)
        assertTrue(scheduledTaskIds().isEmpty())

        ReminderRescheduleReceiver().onReceive(app, Intent(Intent.ACTION_BATTERY_LOW))
        awaitReceiverWork()

        assertTrue("Action selain boot/replace tidak boleh menjadwalkan alarm", scheduledTaskIds().isEmpty())
    }
}
