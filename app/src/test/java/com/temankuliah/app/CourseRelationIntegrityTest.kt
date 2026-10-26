package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.AppStorage
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.ui.FeatureContext
import com.temankuliah.app.ui.courses.CourseController
import com.temankuliah.app.ui.profile.ReminderPreferences
import com.temankuliah.app.ui.tasks.TaskController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Relasi mata kuliah <-> jadwal (lewat teks courseName + semester) dan mata kuliah <-> tugas (courseId)
 * tidak dijaga foreign key, jadi repository/controller yang menjaganya. Tes ini mengunci perilakunya.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseRelationIntegrityTest {
  private lateinit var app: Application
  private lateinit var db: TemanKuliahDatabase
  private lateinit var repo: TemanKuliahRepository
  private lateinit var scope: CoroutineScope
  private lateinit var snackbar: MutableStateFlow<String?>
  private lateinit var courseController: CourseController
  private lateinit var taskController: TaskController
  private var dialogClosedCount = 0

  private val user = UserEntity(id = 1, fullName = "Tester", nim = "1", studyProgram = "TI", faculty = "FIK", university = "USP", password = "")

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    repo = TemanKuliahRepository.from(db)
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    snackbar = MutableStateFlow(null)
    dialogClosedCount = 0
    val ctx = FeatureContext(
      application = app, database = db, repository = repo, scope = scope,
      currentUser = MutableStateFlow(user), activeSemester = MutableStateFlow(1),
      snackbar = snackbar, profileEditError = MutableStateFlow(null), closeDialog = { dialogClosedCount++ }
    )
    courseController = CourseController(ctx)
    taskController = TaskController(ctx, MutableStateFlow(ReminderPreferences()))
    AppStorage.getPreferences(app).edit().clear().commit()
  }

  @After
  fun tearDown() {
    scope.cancel()
    db.close()
  }

  private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

  private fun course(code: String, name: String, semester: Int = 1, userId: Long = 1) = CourseEntity(
    userId = userId, code = code, name = name, credits = 3, gradeLetter = "A", gradePoint = 4.0, semester = semester
  )

  private fun schedule(name: String, semester: Int = 1, userId: Long = 1, date: String = "2030-01-07") = ScheduleEntity(
    userId = userId, courseName = name, dayOfWeek = 1, startTime = "08:00", endTime = "09:40", room = "R1",
    lecturer = "", semester = semester, scheduledDate = date
  )

  // --- 1. updateCourse memperbarui jadwal terkait ---

  @Test
  fun `updateCourse rename moves only matching schedules and returns them for alarm rescheduling`() = runBlocking {
    val id = repo.addCourse(course("IF1", "Algoritma"))
    repo.addCourse(course("IF2", "Basis Data"))
    val a1 = repo.addSchedule(schedule("Algoritma"))
    val a2 = repo.addSchedule(schedule("  algoritma ", date = "2030-01-14")) // beda huruf/spasi tetap terhubung
    val otherCourse = repo.addSchedule(schedule("Basis Data"))
    val otherSemester = repo.addSchedule(schedule("Algoritma", semester = 2))
    val otherUser = repo.addSchedule(schedule("Algoritma", userId = 2))

    val updated = repo.courses.updateCourse(repo.getCourseById(id)!!.copy(name = " Algoritma Lanjut "))

    assertEquals(setOf(a1, a2), updated.map { it.id }.toSet())
    assertTrue(updated.all { it.courseName == "Algoritma Lanjut" })
    val rows = db.scheduleDao().getAllSchedulesOnce().associateBy { it.id }
    assertEquals("Algoritma Lanjut", rows[a1]!!.courseName)
    assertEquals("Algoritma Lanjut", rows[a2]!!.courseName)
    assertEquals("Basis Data", rows[otherCourse]!!.courseName)
    assertEquals("Algoritma", rows[otherSemester]!!.courseName)
    assertEquals("Algoritma", rows[otherUser]!!.courseName)
  }

  @Test
  fun `updateCourse semester change moves schedules to the new semester`() = runBlocking {
    val id = repo.addCourse(course("IF1", "Algoritma", semester = 1))
    val sid = repo.addSchedule(schedule("Algoritma", semester = 1))

    val updated = repo.courses.updateCourse(repo.getCourseById(id)!!.copy(semester = 3))

    assertEquals(listOf(sid), updated.map { it.id })
    assertEquals(3, db.scheduleDao().getById(sid)!!.semester)
    assertEquals("Algoritma", db.scheduleDao().getById(sid)!!.courseName)
  }

  @Test
  fun `updateCourse without name or semester change leaves schedules alone`() = runBlocking {
    val id = repo.addCourse(course("IF1", "Algoritma"))
    val sid = repo.addSchedule(schedule("Algoritma"))

    val updated = repo.courses.updateCourse(repo.getCourseById(id)!!.copy(credits = 4, gradeLetter = "B"))

    assertTrue(updated.isEmpty())
    assertEquals("Algoritma", db.scheduleDao().getById(sid)!!.courseName)
  }

  @Test
  fun `updateCourse rejects duplicate code and does not touch schedules`() = runBlocking {
    repo.addCourse(course("IF1", "Algoritma"))
    val id = repo.addCourse(course("IF2", "Basis Data"))
    val sid = repo.addSchedule(schedule("Basis Data"))
    try {
      repo.courses.updateCourse(repo.getCourseById(id)!!.copy(code = "if1", name = "Baru"))
      fail("Kode duplikat harus ditolak")
    } catch (expected: IllegalArgumentException) {
      // ok
    }
    assertEquals("Basis Data", db.scheduleDao().getById(sid)!!.courseName)
  }

  @Test
  fun `controller updateCourse renames schedules`() {
    courseController.addCourse("IF1", "Algoritma", 3, "A", 1)
    val saved = await { courseController.courses.first { it.isNotEmpty() } }.single()
    val sid = runBlocking { repo.addSchedule(schedule("Algoritma")) }

    courseController.updateCourse(saved, "IF1", "Algoritma Lanjut", 3, "A", 1)

    await { snackbar.first { it == "Mata kuliah berhasil diperbarui." } }
    assertEquals("Algoritma Lanjut", runBlocking { db.scheduleDao().getById(sid)!!.courseName })
  }

  // --- 2. deleteCoursesCascade melepas tautan tugas ---

  @Test
  fun `deleteCoursesCascade nulls courseId of related tasks only`() = runBlocking {
    val doomed = repo.addCourse(course("IF1", "Algoritma"))
    val kept = repo.addCourse(course("IF2", "Basis Data"))
    val sid = repo.addSchedule(schedule("Algoritma"))
    val t1 = repo.addTask(TaskEntity(userId = 1, title = "T1", dueAtMillis = 1L, courseId = doomed))
    val t2 = repo.addTask(TaskEntity(userId = 1, title = "T2", dueAtMillis = 1L, courseId = kept))
    val t3 = repo.addTask(TaskEntity(userId = 1, title = "T3", dueAtMillis = 1L))

    val deletedSchedules = repo.courses.deleteCoursesCascade(listOf(doomed))

    assertEquals(listOf(sid), deletedSchedules)
    val tasks = db.taskDao().getAllOnce().associateBy { it.id }
    assertEquals(3, tasks.size) // tugas tidak ikut terhapus
    assertNull(tasks[t1]!!.courseId)
    assertEquals(kept, tasks[t2]!!.courseId)
    assertNull(tasks[t3]!!.courseId)
    assertNull(repo.getCourseById(doomed))
  }

  // --- 3. courseId tugas ---

  @Test
  fun `tasks reject a course that belongs to another user or does not exist`() = runBlocking {
    val foreign = repo.addCourse(course("IF1", "Algoritma", userId = 2))
    for (bad in listOf(foreign, 999L)) {
      try {
        repo.addTask(TaskEntity(userId = 1, title = "T", dueAtMillis = 1L, courseId = bad))
        fail("courseId $bad harus ditolak")
      } catch (expected: IllegalArgumentException) {
        // ok
      }
    }
    assertTrue(db.taskDao().getAllOnce().isEmpty())
  }

  @Test
  fun `orphaned courseId does not block updating the task but cannot be newly assigned`() = runBlocking {
    // Data lama: courseId menunjuk mata kuliah yang tidak ada (dibuat langsung lewat DAO).
    val id = db.taskDao().insert(TaskEntity(userId = 1, title = "Lama", dueAtMillis = 1L, courseId = 999L))
    val task = db.taskDao().getById(id)!!

    repo.updateTask(task.copy(status = "Selesai")) // status tetap bisa diubah

    assertEquals("Selesai", db.taskDao().getById(id)!!.status)
    repo.updateTask(task.copy(courseId = null)) // dan tautan yatim bisa dilepas
    assertNull(db.taskDao().getById(id)!!.courseId)
  }

  @Test
  fun `taskController passes courseId on add and update and keeps it by default`() {
    val courseId = runBlocking { repo.addCourse(course("IF1", "Algoritma")) }

    taskController.addTask("Laporan", "", 4_000_000_000_000L, "Sedang", courseId)
    val added = await { taskController.tasks.first { it.isNotEmpty() } }.single()
    assertEquals(courseId, added.courseId)

    taskController.updateTask(added, "Laporan 2", "", 4_000_000_000_000L, "Tinggi") // courseId tidak disebut
    await { taskController.tasks.first { list -> list.singleOrNull()?.title == "Laporan 2" } }
    assertEquals(courseId, runBlocking { db.taskDao().getById(added.id)!!.courseId })

    taskController.updateTask(added, "Laporan 3", "", 4_000_000_000_000L, "Tinggi", courseId = null)
    await { taskController.tasks.first { list -> list.singleOrNull()?.title == "Laporan 3" } }
    assertNull(runBlocking { db.taskDao().getById(added.id)!!.courseId })
  }

  // --- 4. addCourse atomik ---

  @Test
  fun `addCourseWithWeeklySchedules rolls the course back when the schedule fails`() = runBlocking {
    try {
      repo.addCourseWithWeeklySchedules(course("IF1", "Algoritma"), schedule("Algoritma").copy(endTime = "07:00"), 16)
      fail("Jam selesai sebelum jam mulai harus ditolak")
    } catch (expected: IllegalArgumentException) {
      // ok
    }
    assertTrue(db.courseDao().getAllCoursesOnce().isEmpty())
    assertTrue(db.scheduleDao().getAllSchedulesOnce().isEmpty())
  }

  @Test
  fun `addCourseWithWeeklySchedules rolls the course back when saving the schedule throws mid-transaction`() = runBlocking {
    // addCourse sudah berhasil di dalam transaksi; addWeeklySchedules lalu menolak meetingCount 0.
    try {
      repo.addCourseWithWeeklySchedules(course("IF1", "Algoritma"), schedule("Algoritma"), 0)
      fail("meetingCount 0 harus ditolak")
    } catch (expected: IllegalArgumentException) {
      // ok
    }
    assertTrue(db.courseDao().getAllCoursesOnce().isEmpty())
  }

  @Test
  fun `addCourseWithWeeklySchedules saves both on success`() = runBlocking {
    val result = repo.addCourseWithWeeklySchedules(course("IF1", "Algoritma"), schedule("Algoritma"), 4)

    assertEquals(4, result.schedules.size)
    assertTrue(result.schedules.all { it.id > 0 })
    assertEquals(1, db.courseDao().getAllCoursesOnce().size)
    assertEquals(4, db.scheduleDao().getAllSchedulesOnce().size)
  }

  @Test
  fun `controller addCourse saves nothing and keeps the dialog open when the schedule is invalid`() {
    AppStorage.getPreferences(app).edit().putString("semester_start_date_1", "2030-01-07").commit()

    courseController.addCourse(
      "IF1", "Algoritma", 3, "A", 1,
      autoCreateSchedule = true, scheduleDayOfWeek = 1, scheduleStartTime = "10:00", scheduleEndTime = "09:00"
    )

    await { snackbar.first { it != null } }
    assertEquals("Jam selesai harus setelah jam mulai.", snackbar.value)
    assertEquals(0, dialogClosedCount)
    assertTrue(runBlocking { db.courseDao().getAllCoursesOnce() }.isEmpty())
    assertTrue(runBlocking { db.scheduleDao().getAllSchedulesOnce() }.isEmpty())
  }

  @Test
  fun `controller addCourse saves course and 16 weekly schedules together`() {
    AppStorage.getPreferences(app).edit().putString("semester_start_date_1", "2030-01-07").commit()

    courseController.addCourse(
      "IF1", "Algoritma", 3, "A", 1,
      autoCreateSchedule = true, scheduleDayOfWeek = 1, scheduleStartTime = "08:00", scheduleEndTime = "09:40"
    )

    await { snackbar.first { it != null } }
    assertEquals(1, dialogClosedCount)
    assertEquals(1, runBlocking { db.courseDao().getAllCoursesOnce() }.size)
    val schedules = runBlocking { db.scheduleDao().getAllSchedulesOnce() }
    assertEquals(16, schedules.size)
    assertTrue(schedules.all { it.courseName == "Algoritma" && it.semester == 1 })
  }
}
