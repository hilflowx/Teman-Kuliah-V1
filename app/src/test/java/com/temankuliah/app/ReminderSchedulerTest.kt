@file:Suppress("DEPRECATION")

package com.temankuliah.app

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.backup.LocalDataArchive
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.notifications.ReminderScheduler
import com.temankuliah.app.data.notifications.ScheduleNotificationReceiver
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.domain.LocalPasswordHasher
import com.temankuliah.app.ui.FeatureContext
import com.temankuliah.app.ui.auth.AuthController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import com.temankuliah.app.domain.TaskStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderSchedulerTest {
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

    private val dayMillis = 86_400_000L

    private suspend fun insertUser(name: String, nim: String, loggedIn: Boolean, email: String = "", password: String = "test-hash"): Long =
        db.userDao().insertUser(
            UserEntity(fullName = name, nim = nim, studyProgram = "Informatika", faculty = "Teknik", university = "USP", password = password, isLoggedIn = loggedIn, email = email)
        )

    private suspend fun insertFutureTask(userId: Long, title: String = "Tugas"): TaskEntity {
        val task = TaskEntity(userId = userId, title = title, dueAtMillis = System.currentTimeMillis() + dayMillis)
        return task.copy(id = db.taskDao().insert(task))
    }

    private suspend fun insertFutureClass(userId: Long, name: String = "Basis Data"): ScheduleEntity {
        val date = LocalDate.now().plusDays(2)
        val schedule = ScheduleEntity(
            userId = userId, courseName = name, dayOfWeek = date.dayOfWeek.value, startTime = "08:00", endTime = "09:40",
            room = "R1", lecturer = "Dosen", alarmEnabled = true, reminderMinutesBefore = 10, scheduledDate = date.toString()
        )
        return schedule.copy(id = db.scheduleDao().insertSchedule(schedule))
    }

    /** Alarm yang terjadwal, dinyatakan sebagai pasangan (jenis, id baris) dari extras PendingIntent. */
    private fun scheduledKeys(): Set<Pair<String, Long>> {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return shadowOf(alarmManager).scheduledAlarms.mapNotNull { alarm ->
            val intent = alarm.operation?.let { shadowOf(it).savedIntent } ?: return@mapNotNull null
            (intent.getStringExtra("kind") ?: return@mapNotNull null) to intent.getLongExtra("row_id", -1L)
        }.toSet()
    }

    private fun activeNotificationCount(): Int =
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications.size

    private fun firedIntent(kind: String, id: Long) = Intent(app, ScheduleNotificationReceiver::class.java)
        .putExtra("kind", kind).putExtra("row_id", id).putExtra("title", "Kuliah Basis Data").putExtra("message", "Kelas dimulai 10 menit lagi")

    private fun awaitReceiverWork() = runBlocking {
        withTimeout(5_000) { receiverScope.coroutineContext[Job]!!.children.toList().joinAll() }
    }

    // --- Bug 1: notifikasi kuliah hilang segera setelah muncul ---

    @Test
    fun classNotificationStaysAfterReceiverFinishes() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val schedule = insertFutureClass(userId)
        ReminderScheduler.scheduleClass(app, schedule)
        assertEquals(setOf("class" to schedule.id), scheduledKeys())

        ScheduleNotificationReceiver().onReceive(app, firedIntent("class", schedule.id))
        awaitReceiverWork()

        assertEquals("Notifikasi kelas harus tetap tampil setelah receiver selesai", 1, activeNotificationCount())
        assertTrue("Alarm kelas yang sudah berbunyi harus dibersihkan", scheduledKeys().isEmpty())
    }

    @Test
    fun cancelAlarmKeepsNotificationButCancelClassRemovesBoth() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val schedule = insertFutureClass(userId)
        ReminderScheduler.scheduleClass(app, schedule)
        ReminderScheduler.showReminder(app, "class", schedule.id, "Judul", "Pesan")
        assertEquals(1, activeNotificationCount())

        ReminderScheduler.cancelAlarm(app, "class", schedule.id)
        assertTrue(scheduledKeys().isEmpty())
        assertEquals("cancelAlarm tidak boleh menghapus notifikasi", 1, activeNotificationCount())

        ReminderScheduler.scheduleClass(app, schedule)
        ReminderScheduler.cancelClass(app, schedule.id)
        assertTrue(scheduledKeys().isEmpty())
        assertEquals("Jadwal dihapus: notifikasi ikut dihapus", 0, activeNotificationCount())
    }

    @Test
    fun rescheduleAfterReminderTimePassedKeepsShownNotification() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val overdue = TaskEntity(id = 1, userId = userId, title = "Lewat", dueAtMillis = System.currentTimeMillis() - 1_000L)
        ReminderScheduler.showReminder(app, "task", overdue.id, "Deadline: Lewat", "Segera")
        assertEquals(1, activeNotificationCount())

        ReminderScheduler.scheduleTask(app, overdue, 10)

        assertEquals("Waktu pengingat yang sudah lewat tidak boleh menghapus notifikasi yang tampil", 1, activeNotificationCount())
    }

    @Test
    fun completedTaskAndDeletedTaskRemoveShownNotification() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = insertFutureTask(userId)
        ReminderScheduler.showReminder(app, "task", task.id, "Deadline", "Pesan")
        assertEquals(1, activeNotificationCount())
        ReminderScheduler.scheduleTask(app, task.copy(status = "Selesai"), 10)
        assertEquals(0, activeNotificationCount())

        ReminderScheduler.showReminder(app, "task", task.id, "Deadline", "Pesan")
        ReminderScheduler.cancelTask(app, task.id)
        assertEquals(0, activeNotificationCount())
    }

    // --- Bug 2: alarm akun lain / logout ---

    @Test
    fun rescheduleAllSchedulesOnlyActiveUserAndCancelsOtherAccountAlarms() = runBlocking {
        val active = insertUser("Aktif", "N1", loggedIn = true)
        val other = insertUser("Lain", "N2", loggedIn = false)
        val myTask = insertFutureTask(active, "Milik saya")
        val myClass = insertFutureClass(active)
        val otherTask = insertFutureTask(other, "Milik akun lain")
        val otherClass = insertFutureClass(other)
        // Keadaan lama: alarm kedua akun sudah terjadwal.
        ReminderScheduler.scheduleTask(app, myTask, 10)
        ReminderScheduler.scheduleClass(app, myClass)
        ReminderScheduler.scheduleTask(app, otherTask, 10)
        ReminderScheduler.scheduleClass(app, otherClass)
        assertEquals(4, scheduledKeys().size)

        ReminderScheduler.rescheduleAll(app, db, active)

        assertEquals(setOf("task" to myTask.id, "class" to myClass.id), scheduledKeys())
    }

    @Test
    fun rescheduleForActiveUserWithNoActiveUserCancelsEverything() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = insertFutureTask(userId)
        val schedule = insertFutureClass(userId)
        ReminderScheduler.scheduleTask(app, task, 10)
        ReminderScheduler.scheduleClass(app, schedule)
        db.userDao().logoutAll()

        ReminderScheduler.rescheduleForActiveUser(app, db)

        assertTrue(scheduledKeys().isEmpty())
    }

    @Test
    fun logoutCancelsAllAlarms() {
        val ctx = authContext()
        val userId = runBlocking { insertUser("Mhs", "N1", loggedIn = true) }
        runBlocking {
            ReminderScheduler.scheduleTask(app, insertFutureTask(userId), 10)
            ReminderScheduler.scheduleClass(app, insertFutureClass(userId))
        }
        assertEquals(2, scheduledKeys().size)

        AuthController(ctx.first).logout()
        runBlocking { withTimeout(5_000) { ctx.second.first { it != null } } }

        assertTrue("Alarm harus dibatalkan saat logout", scheduledKeys().isEmpty())
    }

    @Test
    fun loginReschedulesNewAccountAndDropsPreviousAccountAlarms() {
        val ctx = authContext()
        val nextTask = runBlocking {
            val previous = insertUser("Lama", "N1", loggedIn = true, email = "lama@example.com")
            val next = insertUser("Baru", "N2", loggedIn = false, email = "baru@example.com", password = LocalPasswordHasher.hash("rahasia-123"))
            ReminderScheduler.scheduleTask(app, insertFutureTask(previous, "Tugas lama"), 10)
            insertFutureTask(next, "Tugas baru")
        }
        assertEquals(1, scheduledKeys().size)

        AuthController(ctx.first).loginWithEmail("baru@example.com", "rahasia-123")
        runBlocking { withTimeout(10_000) { ctx.second.first { it != null } } }

        assertEquals(setOf("task" to nextTask.id), scheduledKeys())
    }

    // --- Bug 3: restore ---

    @Test
    fun restoreCancelsAlarmsOfOldRowsAndSchedulesRestoredData() = runBlocking {
        val userId = insertUser("Mhs", "NIM-01", loggedIn = true)
        val restoredTask = insertFutureTask(userId, "Ada di cadangan")
        val output = ByteArrayOutputStream()
        LocalDataArchive.export(app, db, output)

        // Setelah cadangan dibuat: tugas itu dihapus, lalu ada tugas dan jadwal baru yang beralarm.
        db.taskDao().delete(restoredTask)
        val oldTask = insertFutureTask(userId, "Tugas lama")
        val oldClass = insertFutureClass(userId)
        ReminderScheduler.scheduleTask(app, oldTask, 10)
        ReminderScheduler.scheduleClass(app, oldClass)
        assertEquals(setOf("task" to oldTask.id, "class" to oldClass.id), scheduledKeys())

        LocalDataArchive.restore(app, db, ByteArrayInputStream(output.toByteArray()), activeUserEmail = "mhs@example.com")

        assertTrue(oldTask.id != restoredTask.id)
        assertEquals("Alarm tugas/jadwal lama harus hilang dan hanya data hasil restore yang terjadwal", setOf("task" to restoredTask.id), scheduledKeys())
    }

    // --- Target 1: ReminderScheduler.scheduleTask ---

    @Test
    fun scheduleTask_deadline10MinReminder15Min_windowOpen_showsNotificationWithClock() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val now = System.currentTimeMillis()
        val dueAt = now + 10 * 60_000L
        val task = TaskEntity(id = 101L, userId = userId, title = "Tugas Laprak", dueAtMillis = dueAt)

        ReminderScheduler.scheduleTask(app, task, reminderMinutes = 15, notifyIfWindowOpen = true)

        val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = shadowOf(notifManager).allNotifications.lastOrNull()
        assertNotNull("Notifikasi harus tampil saat notifyIfWindowOpen=true dan deadline belum lewat", notif)
        val clock = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val text = notif?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertEquals("Deadline pukul $clock.", text)
    }

    @Test
    fun scheduleTask_deadline10MinReminder15Min_windowClosed_noNotificationNoAlarm() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val now = System.currentTimeMillis()
        val dueAt = now + 10 * 60_000L
        val task = TaskEntity(id = 102L, userId = userId, title = "Tugas Lain", dueAtMillis = dueAt)

        ReminderScheduler.scheduleTask(app, task, reminderMinutes = 15, notifyIfWindowOpen = false)

        val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("Tidak boleh ada notifikasi saat notifyIfWindowOpen=false", shadowOf(notifManager).allNotifications.none { it.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("Tugas Lain") == true })
        assertTrue("Tidak boleh ada alarm saat waktu pengingat sudah lewat", scheduledKeys().none { it.second == 102L })
    }

    @Test
    fun scheduleTask_deadlineAlreadyPassed_noNotificationAndAlarmCancelled() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val pastDue = System.currentTimeMillis() - 5_000L
        val task = TaskEntity(id = 103L, userId = userId, title = "Tugas Terlambat", dueAtMillis = pastDue)
        ReminderScheduler.scheduleTask(app, task.copy(dueAtMillis = System.currentTimeMillis() + 3_600_000L), reminderMinutes = 10)
        assertTrue(scheduledKeys().any { it.second == 103L })

        ReminderScheduler.scheduleTask(app, task, reminderMinutes = 15, notifyIfWindowOpen = true)

        val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("Tidak boleh ada notifikasi untuk deadline yang sudah lewat", shadowOf(notifManager).allNotifications.none { it.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("Tugas Terlambat") == true })
        assertTrue("Alarm harus dibatalkan bila deadline sudah lewat", scheduledKeys().none { it.second == 103L })
    }

    @Test
    fun scheduleTask_statusSelesai_clearsNotificationAndAlarm() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val dueAt = System.currentTimeMillis() + 60 * 60_000L
        val task = TaskEntity(id = 104L, userId = userId, title = "Tugas Rampung", dueAtMillis = dueAt)
        ReminderScheduler.scheduleTask(app, task, 15)
        ReminderScheduler.showReminder(app, "task", 104L, "Deadline: Tugas Rampung", "Deadline pukul 12:00.")
        assertEquals(1, activeNotificationCount())
        assertTrue(scheduledKeys().any { it.second == 104L })

        ReminderScheduler.scheduleTask(app, task.copy(status = TaskStatus.SELESAI), 15)

        assertEquals("Notifikasi harus dibersihkan saat status Selesai", 0, activeNotificationCount())
        assertTrue("Alarm harus dibersihkan saat status Selesai", scheduledKeys().none { it.second == 104L })
    }

    // --- Target 2: ReminderScheduler.handleTaskAction ---

    @Test
    fun handleTaskAction_snoozeFalse_updatesStatusToSelesaiAndCancelsNotification() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val task = insertFutureTask(userId, "Tugas Selesai Action")
        ReminderScheduler.showReminder(app, "task", task.id, "Deadline: Tugas Selesai Action", "Pesan")
        assertEquals(1, activeNotificationCount())

        ReminderScheduler.handleTaskAction(app, task.id, snooze = false, db)

        val updated = db.taskDao().getById(task.id)
        assertNotNull(updated)
        assertEquals(TaskStatus.SELESAI, updated?.status)
        assertEquals("Notifikasi harus dibatalkan saat aksi selesai", 0, activeNotificationCount())
    }

    @Test
    fun handleTaskAction_snoozeTrue_deadlineMoreThan10Min_schedulesNowPlus10Min() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val now = System.currentTimeMillis()
        val task = TaskEntity(userId = userId, title = "Tugas Tunda Panjang", dueAtMillis = now + 30 * 60_000L)
        val taskId = db.taskDao().insert(task)
        ReminderScheduler.showReminder(app, "task", taskId, "Deadline", "Pesan")

        ReminderScheduler.handleTaskAction(app, taskId, snooze = true, db)

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = shadowOf(alarmManager).scheduledAlarms.firstOrNull { a ->
            val intent = a.operation?.let { shadowOf(it).savedIntent }
            intent?.getStringExtra("kind") == "task" && intent.getLongExtra("row_id", -1L) == taskId
        }
        assertNotNull("Alarm tunda harus dijadwalkan", alarm)
        val expected = now + ReminderScheduler.SNOOZE_MINUTES * 60_000L
        assertTrue("Alarm harus dijadwalkan pada now + 10 menit", Math.abs(alarm!!.triggerAtTime - expected) < 2_000L)
    }

    @Test
    fun handleTaskAction_snoozeTrue_deadlineLessThan10Min_schedulesExactlyAtDeadline() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val now = System.currentTimeMillis()
        val deadline = now + 4 * 60_000L
        val task = TaskEntity(userId = userId, title = "Tugas Tunda Mepet", dueAtMillis = deadline)
        val taskId = db.taskDao().insert(task)
        ReminderScheduler.showReminder(app, "task", taskId, "Deadline", "Pesan")

        ReminderScheduler.handleTaskAction(app, taskId, snooze = true, db)

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = shadowOf(alarmManager).scheduledAlarms.firstOrNull { a ->
            val intent = a.operation?.let { shadowOf(it).savedIntent }
            intent?.getStringExtra("kind") == "task" && intent.getLongExtra("row_id", -1L) == taskId
        }
        assertNotNull("Alarm tunda harus dijadwalkan", alarm)
        assertEquals("Alarm harus dijadwalkan tepat pada deadline bila sisa waktu < 10 menit", deadline, alarm!!.triggerAtTime)
    }

    @Test
    fun handleTaskAction_snoozeTrue_overdueOrNonExistent_noNewAlarm() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val pastTask = TaskEntity(userId = userId, title = "Tugas Kedaluwarsa", dueAtMillis = System.currentTimeMillis() - 10_000L)
        val taskId = db.taskDao().insert(pastTask)

        ReminderScheduler.handleTaskAction(app, taskId, snooze = true, db)
        assertTrue(scheduledKeys().none { it.second == taskId })

        ReminderScheduler.handleTaskAction(app, 999_999L, snooze = true, db)
        assertTrue(scheduledKeys().none { it.second == 999_999L })
    }

    @Test
    fun handleTaskAction_nonExistentTask_doesNotCrash() = runBlocking {
        ReminderScheduler.handleTaskAction(app, 888_888L, snooze = false, db)
        ReminderScheduler.handleTaskAction(app, 888_888L, snooze = true, db)
    }

    // --- Target 3: ReminderScheduler.scheduleClass ---

    @Test
    fun scheduleClass_reminderMinutesZero_containsSekarang() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val futureDate = LocalDate.now().plusDays(2).toString()
        val schedule = ScheduleEntity(
            userId = userId, courseName = "Algoritma", dayOfWeek = 1,
            startTime = "08:00", endTime = "09:40", room = "Lab 1", lecturer = "Dosen",
            alarmEnabled = true, reminderMinutesBefore = 0, scheduledDate = futureDate
        )
        val scheduleId = db.scheduleDao().insertSchedule(schedule)
        val saved = schedule.copy(id = scheduleId)

        ReminderScheduler.scheduleClass(app, saved)

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = shadowOf(alarmManager).scheduledAlarms.firstOrNull { a ->
            val intent = a.operation?.let { shadowOf(it).savedIntent }
            intent?.getStringExtra("kind") == "class" && intent.getLongExtra("row_id", -1L) == scheduleId
        }
        assertNotNull(alarm)
        val message = shadowOf(alarm!!.operation).savedIntent.getStringExtra("message") ?: ""
        assertTrue("Pesan harus memuat '(sekarang)': $message", message.contains("(sekarang)"))
    }

    @Test
    fun scheduleClass_reminderMinutes15_containsMulaiPukul080015MenitLagi() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val futureDate = LocalDate.now().plusDays(2).toString()
        val schedule = ScheduleEntity(
            userId = userId, courseName = "Jaringan", dayOfWeek = 2,
            startTime = "08:00", endTime = "09:40", room = "R201", lecturer = "Dosen",
            alarmEnabled = true, reminderMinutesBefore = 15, scheduledDate = futureDate
        )
        val scheduleId = db.scheduleDao().insertSchedule(schedule)
        val saved = schedule.copy(id = scheduleId)

        ReminderScheduler.scheduleClass(app, saved)

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = shadowOf(alarmManager).scheduledAlarms.firstOrNull { a ->
            val intent = a.operation?.let { shadowOf(it).savedIntent }
            intent?.getStringExtra("kind") == "class" && intent.getLongExtra("row_id", -1L) == scheduleId
        }
        assertNotNull(alarm)
        val message = shadowOf(alarm!!.operation).savedIntent.getStringExtra("message") ?: ""
        assertTrue("Pesan harus memuat 'Mulai pukul 08:00 (15 menit lagi)': $message", message.contains("Mulai pukul 08:00 (15 menit lagi)"))
    }

    @Test
    fun scheduleClass_isCompletedTrue_cancelsAlarm() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        val futureDate = LocalDate.now().plusDays(2).toString()
        val schedule = ScheduleEntity(
            userId = userId, courseName = "Sistem Operasi", dayOfWeek = 3,
            startTime = "10:00", endTime = "11:40", room = "R301", lecturer = "Dosen",
            alarmEnabled = true, reminderMinutesBefore = 10, scheduledDate = futureDate
        )
        val scheduleId = db.scheduleDao().insertSchedule(schedule)
        val saved = schedule.copy(id = scheduleId)

        ReminderScheduler.scheduleClass(app, saved)
        assertTrue(scheduledKeys().any { it.second == scheduleId })

        ReminderScheduler.scheduleClass(app, saved.copy(isCompleted = true))
        assertTrue("Alarm jadwal selesai harus dibatalkan", scheduledKeys().none { it.second == scheduleId })
    }

    @Test
    fun scheduleClass_beyondAlarmWindow_isNotArmed_andWithinWindowIs() = runBlocking {
        val userId = insertUser("Mhs", "N1", loggedIn = true)
        fun classOn(date: LocalDate) = ScheduleEntity(
            userId = userId, courseName = "Jarak Jauh", dayOfWeek = date.dayOfWeek.value,
            startTime = "08:00", endTime = "09:40", room = "R1", lecturer = "Dosen",
            alarmEnabled = true, reminderMinutesBefore = 10, scheduledDate = date.toString()
        )
        val near = classOn(LocalDate.now().plusDays(5)).let { it.copy(id = db.scheduleDao().insertSchedule(it)) }
        val far = classOn(LocalDate.now().plusDays(60)).let { it.copy(id = db.scheduleDao().insertSchedule(it)) }

        ReminderScheduler.scheduleClass(app, near)
        ReminderScheduler.scheduleClass(app, far)

        assertTrue(scheduledKeys().any { it.second == near.id })
        assertTrue("Pertemuan di luar jendela tidak boleh memakai slot alarm", scheduledKeys().none { it.second == far.id })
    }

    // --- Target 4: ReminderScheduler.canScheduleExactAlarms ---

    @Test
    @Config(sdk = [30])
    fun canScheduleExactAlarms_sdkBelow31_alwaysTrue() {
        assertTrue(ReminderScheduler.canScheduleExactAlarms(app))
    }

    @Test
    @Config(sdk = [31])
    fun canScheduleExactAlarms_sdk31AndAbove_followsAlarmManager() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(ReminderScheduler.canScheduleExactAlarms(app))

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ReminderScheduler.canScheduleExactAlarms(app))
    }

    private fun authContext(): Pair<FeatureContext, MutableStateFlow<String?>> {
        val snackbar = MutableStateFlow<String?>(null)
        val context = FeatureContext(
            application = app,
            database = db,
            repository = TemanKuliahRepository.from(db),
            scope = receiverScope,
            currentUser = MutableStateFlow(null),
            activeSemester = MutableStateFlow(1),
            snackbar = snackbar,
            profileEditError = MutableStateFlow(null),
            closeDialog = {}
        )
        return context to snackbar
    }
}
