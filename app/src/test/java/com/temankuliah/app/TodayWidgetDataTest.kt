package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.domain.TaskStatus
import com.temankuliah.app.widget.TodayWidgetData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayWidgetDataTest {

    private lateinit var app: Application
    private lateinit var db: TemanKuliahDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        TodayWidgetData.databaseProvider = { db }
    }

    @After
    fun tearDown() {
        TodayWidgetData.databaseProvider = { TemanKuliahDatabase.getDatabase(it) }
        db.close()
    }

    private suspend fun insertUser(name: String, loggedIn: Boolean): Long =
        db.userDao().insertUser(
            UserEntity(
                fullName = name, nim = "12345", studyProgram = "Informatika",
                faculty = "Teknik", university = "USP", password = "hash",
                isLoggedIn = loggedIn
            )
        )

    @Test
    fun load_noActiveUser_returnsSignedOut() = runBlocking {
        val snapshot = TodayWidgetData.load(app)
        assertEquals(TodayWidgetData.State.SIGNED_OUT, snapshot.state)
        assertTrue(snapshot.classes.isEmpty())
        assertTrue(snapshot.tasks.isEmpty())
    }

    @Test
    fun load_classesFilteredCorrectly_onlyIncompleteTodayClassesShown() = runBlocking {
        val userId = insertUser("Mhs", loggedIn = true)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStr = today.toString()
        val yesterdayStr = today.minusDays(1).toString()
        val tomorrowStr = today.plusDays(1).toString()

        // 1. Kelas hari ini yang belum selesai (HARUS TAMPIL)
        db.scheduleDao().insertSchedule(
            ScheduleEntity(
                userId = userId, courseName = "Basis Data", dayOfWeek = today.dayOfWeek.value,
                startTime = "08:00", endTime = "09:40", room = "Lab 1", lecturer = "Dosen A",
                scheduledDate = todayStr, isCompleted = false
            )
        )
        // 2. Kelas hari ini yang sudah selesai (TIDAK TAMPIL)
        db.scheduleDao().insertSchedule(
            ScheduleEntity(
                userId = userId, courseName = "Pemrograman Web", dayOfWeek = today.dayOfWeek.value,
                startTime = "10:00", endTime = "11:40", room = "Lab 2", lecturer = "Dosen B",
                scheduledDate = todayStr, isCompleted = true
            )
        )
        // 3. Kelas kemarin (TIDAK TAMPIL)
        db.scheduleDao().insertSchedule(
            ScheduleEntity(
                userId = userId, courseName = "Algoritma Kemarin", dayOfWeek = today.minusDays(1).dayOfWeek.value,
                startTime = "08:00", endTime = "09:40", room = "Lab 1", lecturer = "Dosen C",
                scheduledDate = yesterdayStr, isCompleted = false
            )
        )
        // 4. Kelas besok (TIDAK TAMPIL)
        db.scheduleDao().insertSchedule(
            ScheduleEntity(
                userId = userId, courseName = "Jaringan Besok", dayOfWeek = today.plusDays(1).dayOfWeek.value,
                startTime = "13:00", endTime = "14:40", room = "Lab 3", lecturer = "Dosen D",
                scheduledDate = tomorrowStr, isCompleted = false
            )
        )

        val snapshot = TodayWidgetData.load(app)
        assertEquals(TodayWidgetData.State.READY, snapshot.state)
        assertEquals(1, snapshot.classes.size)
        assertTrue(snapshot.classes[0].title.contains("Basis Data"))
        assertEquals("Lab 1", snapshot.classes[0].detail)
    }

    @Test
    fun load_tasksFilteredAndFormattedCorrectly() = runBlocking {
        val userId = insertUser("Mhs", loggedIn = true)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val clock = DateTimeFormatter.ofPattern("HH:mm")

        // 1. Tugas dengan status Selesai (TIDAK TAMPIL)
        val completedDue = System.currentTimeMillis() + 10 * 60_000L
        db.taskDao().insert(
            TaskEntity(userId = userId, title = "Tugas Selesai", dueAtMillis = completedDue, status = TaskStatus.SELESAI)
        )

        // 2. Tugas terlambat (dueAt < sekarang, sebelum hari ini) (TAMPIL dengan "Terlambat · HH:mm")
        val overdueDue = startOfToday - 2 * 3600_000L
        val overdueClock = Instant.ofEpochMilli(overdueDue).atZone(zone).toLocalTime().format(clock)
        db.taskDao().insert(
            TaskEntity(userId = userId, title = "Tugas Kemarin Terlambat", dueAtMillis = overdueDue, status = TaskStatus.BELUM)
        )

        // 3. Tugas jatuh tempo hari ini (TAMPIL dengan "Deadline HH:mm")
        val nowMillis = System.currentTimeMillis()
        val todayDue = if (nowMillis + 60_000L < endOfToday) nowMillis + 60_000L else endOfToday - 1000L
        val todayClock = Instant.ofEpochMilli(todayDue).atZone(zone).toLocalTime().format(clock)
        db.taskDao().insert(
            TaskEntity(userId = userId, title = "Tugas Hari Ini", dueAtMillis = todayDue, status = TaskStatus.SEDANG)
        )

        // 4. Tugas besok (TIDAK TAMPIL)
        val tomorrowDue = endOfToday + 3600_000L
        db.taskDao().insert(
            TaskEntity(userId = userId, title = "Tugas Besok", dueAtMillis = tomorrowDue, status = TaskStatus.BELUM)
        )

        val snapshot = TodayWidgetData.load(app)
        assertEquals(TodayWidgetData.State.READY, snapshot.state)
        assertEquals(2, snapshot.tasks.size)

        val overdueRow = snapshot.tasks.find { it.title == "Tugas Kemarin Terlambat" }
        assertNotNull(overdueRow)
        assertEquals("Terlambat · $overdueClock", overdueRow?.detail)

        val todayRow = snapshot.tasks.find { it.title == "Tugas Hari Ini" }
        assertNotNull(todayRow)
        assertEquals("Deadline $todayClock", todayRow?.detail)

        assertTrue(snapshot.tasks.none { it.title == "Tugas Selesai" })
        assertTrue(snapshot.tasks.none { it.title == "Tugas Besok" })
    }

    @Test
    fun load_databaseException_returnsError() = runBlocking {
        TodayWidgetData.databaseProvider = { throw RuntimeException("Simulasi error database") }
        val snapshot = TodayWidgetData.load(app)
        assertEquals(TodayWidgetData.State.ERROR, snapshot.state)
        assertTrue(snapshot.classes.isEmpty())
        assertTrue(snapshot.tasks.isEmpty())
    }

    // --- Urutan, batas, dan penanda "+N lainnya" ---

    private val utc: ZoneId = ZoneOffset.UTC
    private val now: Long = LocalDateTime.of(2026, 10, 21, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val startOfToday: Long = LocalDate.of(2026, 10, 21).atStartOfDay(utc).toInstant().toEpochMilli()
    private val day = 24 * 3600_000L

    private fun task(title: String, due: Long, status: String = TaskStatus.BELUM) =
        TaskEntity(userId = 1, title = title, dueAtMillis = due, status = status)

    private fun arrange(vararg tasks: TaskEntity) = TodayWidgetData.arrangeTasks(tasks.toList(), now, utc)

    @Test
    fun arrangeTasks_todayComesBeforeOverdueEvenWhenOverdueIsOlder() {
        val result = arrange(
            task("Terlambat 5 hari", startOfToday - 5 * day),
            task("Terlambat 4 hari", startOfToday - 4 * day),
            task("Terlambat 3 hari", startOfToday - 3 * day),
            task("Hari ini", startOfToday + 12 * 3600_000L)
        )

        assertEquals(listOf("Hari ini", "Terlambat 3 hari", "Terlambat 4 hari"), result.rows.map { it.title })
        assertEquals(1, result.more)
    }

    @Test
    fun arrangeTasks_overdueIsCappedAndShowsMostRecentFirst() {
        val result = arrange(
            task("Hari ini", startOfToday + 12 * 3600_000L),
            task("Terlambat 1", startOfToday - 1 * day),
            task("Terlambat 2", startOfToday - 2 * day),
            task("Terlambat 3", startOfToday - 3 * day),
            task("Terlambat 4", startOfToday - 4 * day)
        )

        assertEquals(listOf("Hari ini", "Terlambat 1", "Terlambat 2"), result.rows.map { it.title })
        assertEquals(2, result.more)
    }

    @Test
    fun arrangeTasks_onlyOverdueIsLimitedToTheOverdueCap() {
        val result = arrange(*(1..5).map { task("Terlambat $it", startOfToday - it * day) }.toTypedArray())

        assertEquals(TodayWidgetData.MAX_OVERDUE_ROWS, result.rows.size)
        assertEquals(listOf("Terlambat 1", "Terlambat 2"), result.rows.map { it.title })
        assertEquals(3, result.more)
    }

    @Test
    fun arrangeTasks_manyTodayTasksFillTheSlotsAndHideOverdue() {
        val result = arrange(
            task("Hari ini D", startOfToday + 20 * 3600_000L),
            task("Hari ini B", startOfToday + 12 * 3600_000L),
            task("Hari ini A", startOfToday + 8 * 3600_000L),
            task("Hari ini C", startOfToday + 15 * 3600_000L),
            task("Terlambat 1", startOfToday - day),
            task("Terlambat 2", startOfToday - 2 * day)
        )

        assertEquals(listOf("Hari ini A", "Hari ini B", "Hari ini C"), result.rows.map { it.title })
        assertEquals(3, result.more)
    }

    @Test
    fun arrangeTasks_noMarkerWhenEverythingFits_andIgnoresDoneAndFutureTasks() {
        val result = arrange(
            task("Hari ini", startOfToday + 12 * 3600_000L),
            task("Terlambat", startOfToday - day),
            task("Selesai", startOfToday + 12 * 3600_000L, TaskStatus.SELESAI),
            task("Besok", startOfToday + day + 3600_000L)
        )

        assertEquals(listOf("Hari ini", "Terlambat"), result.rows.map { it.title })
        assertEquals(0, result.more)
    }

    @Test
    fun arrangeTasks_labelsFollowTheCurrentTime() {
        val result = arrange(
            task("Sudah lewat jamnya", startOfToday + 8 * 3600_000L),
            task("Belum", startOfToday + 12 * 3600_000L),
            task("Kemarin", startOfToday - day + 9 * 3600_000L)
        )

        assertEquals("Terlambat · 08:00", result.rows.first { it.title == "Sudah lewat jamnya" }.detail)
        assertEquals("Deadline 12:00", result.rows.first { it.title == "Belum" }.detail)
        assertEquals("Terlambat · 09:00", result.rows.first { it.title == "Kemarin" }.detail)
    }

    @Test
    fun arrangeTasks_empty() {
        val result = arrange()
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.more)
    }

    @Test
    fun limitClasses_capsRowsAndCountsTheRest() {
        val five = (1..5).map { TodayWidgetData.Row("0$it:00 · MK $it", "R$it") }

        val limited = TodayWidgetData.limitClasses(five)
        assertEquals(five.take(3), limited.rows)
        assertEquals(2, limited.more)

        val exact = TodayWidgetData.limitClasses(five.take(3))
        assertEquals(3, exact.rows.size)
        assertEquals(0, exact.more)
    }

    @Test
    fun load_overdueDoesNotCoverTodayTasks_andReportsHiddenCount() = runBlocking {
        val userId = insertUser("Mhs", loggedIn = true)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        db.taskDao().insert(TaskEntity(userId = userId, title = "Terlambat 3", dueAtMillis = start - 3 * day, status = TaskStatus.BELUM))
        db.taskDao().insert(TaskEntity(userId = userId, title = "Terlambat 9", dueAtMillis = start - 9 * day, status = TaskStatus.BELUM))
        db.taskDao().insert(TaskEntity(userId = userId, title = "Terlambat 5", dueAtMillis = start - 5 * day, status = TaskStatus.BELUM))
        db.taskDao().insert(TaskEntity(userId = userId, title = "Hari Ini", dueAtMillis = start + 60_000L, status = TaskStatus.BELUM))

        val snapshot = TodayWidgetData.load(app)

        assertEquals(TodayWidgetData.State.READY, snapshot.state)
        assertEquals(listOf("Hari Ini", "Terlambat 3", "Terlambat 5"), snapshot.tasks.map { it.title })
        assertEquals(1, snapshot.moreTasks)
        assertEquals(0, snapshot.moreClasses)
        assertFalse(snapshot.tasks.any { it.title == "Terlambat 9" })
    }

    @Test
    fun load_manyClasses_showsThreeAndCountsTheRest() = runBlocking {
        val userId = insertUser("Mhs", loggedIn = true)
        val today = LocalDate.now(ZoneId.systemDefault())
        (1..5).forEach { index ->
            db.scheduleDao().insertSchedule(
                ScheduleEntity(
                    userId = userId, courseName = "MK $index", dayOfWeek = today.dayOfWeek.value,
                    startTime = "0$index:00", endTime = "0$index:50", room = "R$index", lecturer = "Dosen",
                    scheduledDate = today.toString(), isCompleted = false
                )
            )
        }

        val snapshot = TodayWidgetData.load(app)

        assertEquals(listOf("01:00 · MK 1", "02:00 · MK 2", "03:00 · MK 3"), snapshot.classes.map { it.title })
        assertEquals(2, snapshot.moreClasses)
    }

    // --- Ringkasan widget redesain: kuliah berikutnya dan tugas mendesak ---

    private fun lecture(start: String, end: String, name: String = "Pemrograman Mobile", room: String = "3.2") =
        ScheduleEntity(userId = 1, courseName = name, dayOfWeek = 3, startTime = start, endTime = end, room = room, lecturer = "")

    @Test
    fun pickNextClass_upcomingClassIsBerikutnya() {
        val (next, finished) = TodayWidgetData.pickNextClass(listOf(lecture("08:00", "09:40")), LocalTime.of(7, 0))

        assertFalse(finished)
        assertEquals("Pemrograman Mobile", next?.title)
        assertEquals("08.00–09.40 · Ruang 3.2", next?.detail)
        assertEquals("Berikutnya", next?.badge)
    }

    @Test
    fun pickNextClass_runningClassIsBerlangsungAndFinishedClassIsSkipped() {
        val list = listOf(lecture("08:00", "09:40", "Pagi"), lecture("10:00", "11:40", "Siang"))

        assertEquals("Berlangsung", TodayWidgetData.pickNextClass(list, LocalTime.of(10, 30)).first?.badge)
        assertEquals("Siang", TodayWidgetData.pickNextClass(list, LocalTime.of(9, 50)).first?.title)
    }

    @Test
    fun pickNextClass_allClassesPassedReportsFinished() {
        val (next, finished) = TodayWidgetData.pickNextClass(listOf(lecture("08:00", "09:40", room = "")), LocalTime.of(15, 0))

        assertEquals(null, next)
        assertTrue(finished)
        assertEquals(Pair(null, false), TodayWidgetData.pickNextClass(emptyList(), LocalTime.of(15, 0)))
        assertEquals("08.00–09.40 · Ruang belum diatur", TodayWidgetData.pickNextClass(listOf(lecture("08:00", "09:40", room = "")), LocalTime.of(7, 0)).first?.detail)
    }

    @Test
    fun pickUrgentTask_tomorrowIsUrgentWithDayLabel() {
        val due = startOfToday + day + 23 * 3600_000L + 59 * 60_000L

        val result = TodayWidgetData.pickUrgentTask(listOf(task("Laporan Basis Data", due)), now, utc)

        assertEquals("Laporan Basis Data", result?.title)
        assertEquals("Tenggat besok, 23.59", result?.detail)
        assertEquals("Mendesak", result?.badge)
        assertFalse(result!!.overdue)
    }

    @Test
    fun pickUrgentTask_ignoresFinishedAndFarTasks() {
        val result = TodayWidgetData.pickUrgentTask(
            listOf(
                task("Selesai", startOfToday + 12 * 3600_000L, TaskStatus.SELESAI),
                task("Lusa", startOfToday + 2 * day + 3600_000L)
            ),
            now, utc
        )

        assertEquals(null, result)
    }

    @Test
    fun pickUrgentTask_upcomingBeatsOverdueAndOverdueIsLabelled() {
        val upcoming = task("Hari ini", startOfToday + 20 * 3600_000L)
        val overdue = task("Kemarin", startOfToday - 3600_000L)

        assertEquals("Hari ini", TodayWidgetData.pickUrgentTask(listOf(overdue, upcoming), now, utc)?.title)

        val onlyOverdue = TodayWidgetData.pickUrgentTask(listOf(overdue), now, utc)
        assertEquals("Terlambat", onlyOverdue?.badge)
        assertTrue(onlyOverdue!!.overdue)
        assertEquals("Terlambat sejak 20 Okt", onlyOverdue.detail)
    }
}
