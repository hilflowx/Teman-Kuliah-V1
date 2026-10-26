package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.files.AcademicDocumentProcessor.AcademicRow
import com.temankuliah.app.data.local.AppStorage
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.ui.FeatureContext
import com.temankuliah.app.ui.academic.documents.DocumentController
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Menyimpan KRS yang sama berulang kali (mode gabung) tidak boleh menggandakan jadwal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentScheduleMergeTest {
  private lateinit var app: Application
  private lateinit var db: TemanKuliahDatabase
  private lateinit var repo: TemanKuliahRepository
  private lateinit var scope: CoroutineScope
  private lateinit var controller: DocumentController

  private val user = UserEntity(id = 1, fullName = "Tester", nim = "1", studyProgram = "TI", faculty = "FIK", university = "USP", password = "")

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    repo = TemanKuliahRepository.from(db)
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    controller = DocumentController(
      FeatureContext(
        application = app, database = db, repository = repo, scope = scope,
        currentUser = MutableStateFlow(user), activeSemester = MutableStateFlow(1),
        snackbar = MutableStateFlow(null), profileEditError = MutableStateFlow(null), closeDialog = {}
      )
    ) { semester, iso -> AppStorage.getPreferences(app).edit().putString("semester_start_date_$semester", iso).commit() }
    AppStorage.getPreferences(app).edit().clear().commit()
  }

  @After
  fun tearDown() {
    scope.cancel()
    db.close()
  }

  private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

  private fun template(room: String = "R1", endTime: String = "09:40") = ScheduleEntity(
    userId = 1, courseName = "Algoritma", dayOfWeek = 1, startTime = "08:00", endTime = endTime, room = room,
    lecturer = "Dr. A", semester = 1, scheduledDate = "2030-01-07", alarmEnabled = true
  )

  @Test
  fun `upsertWeeklySchedules twice keeps the count and the ids`() = runBlocking {
    val first = repo.upsertWeeklySchedules(template(), 16)
    val second = repo.upsertWeeklySchedules(template(), 16)

    assertEquals(16, first.inserted.size)
    assertTrue(first.updated.isEmpty())
    assertTrue(second.inserted.isEmpty())
    assertEquals(first.inserted.map { it.id }, second.updated.map { it.id })
    assertEquals(16, db.scheduleDao().getAllSchedulesOnce().size)
  }

  @Test
  fun `upsertWeeklySchedules updates details but keeps completion and series`() = runBlocking {
    val first = repo.upsertWeeklySchedules(template(), 4).inserted
    repo.setScheduleCompleted(first[0].id, true)

    repo.upsertWeeklySchedules(template(room = "R2", endTime = "10:30"), 4)

    val rows = db.scheduleDao().getAllSchedulesOnce().sortedBy { it.scheduledDate }
    assertEquals(4, rows.size)
    assertTrue(rows.all { it.room == "R2" && it.endTime == "10:30" })
    assertTrue(rows[0].isCompleted)
    assertTrue(rows.drop(1).none { it.isCompleted })
    assertEquals(1, rows.map { it.recurrenceGroupId }.distinct().size)
  }

  @Test
  fun `upsertWeeklySchedules only adds meetings that do not exist yet`() = runBlocking {
    repo.upsertWeeklySchedules(template(), 4)

    val result = repo.upsertWeeklySchedules(template(), 6)

    assertEquals(2, result.inserted.size)
    assertEquals(4, result.updated.size)
    assertEquals(6, db.scheduleDao().getAllSchedulesOnce().size)
  }

  @Test
  fun `upsertWeeklySchedules keeps newly added meetings in the same series`() = runBlocking {
    repo.upsertWeeklySchedules(template(), 4)

    repo.upsertWeeklySchedules(template(), 6)

    val rows = db.scheduleDao().getAllSchedulesOnce()
    assertEquals(6, rows.size)
    assertEquals(1, rows.map { it.recurrenceGroupId }.distinct().size)
  }

  @Test
  fun `upsertWeeklySchedules ignores other users, semesters, courses and times`() = runBlocking {
    repo.upsertWeeklySchedules(template(), 2)

    repo.upsertWeeklySchedules(template().copy(userId = 2), 2)
    repo.upsertWeeklySchedules(template().copy(semester = 2), 2)
    repo.upsertWeeklySchedules(template().copy(courseName = "Basis Data"), 2)
    repo.upsertWeeklySchedules(template().copy(startTime = "13:00", endTime = "14:40"), 2)

    assertEquals(10, db.scheduleDao().getAllSchedulesOnce().size)
  }

  private fun saveKrsOnce(merge: Boolean) {
    controller.processAcademicDocumentText("", semester = 1, type = "KRS")
    await { controller.documentImportPreview.first { it != null } }
    controller.addPreviewRow(AcademicRow("Algoritma", 3, "-", 1, "IF1", lecturer = "Dr. A", schedule = "Senin 08:00-09:40"))
    controller.savePreviewCourses(replaceExisting = !merge, customStartDateIso = "2030-01-07")
    await { controller.documentImportPreview.first { it == null } }
  }

  @Test
  fun `saving the same KRS twice in merge mode does not duplicate schedules`() {
    saveKrsOnce(merge = true)
    val afterFirst = runBlocking { db.scheduleDao().getAllSchedulesOnce() }
    assertEquals(16, afterFirst.size)

    saveKrsOnce(merge = true)

    val afterSecond = runBlocking { db.scheduleDao().getAllSchedulesOnce() }
    assertEquals(16, afterSecond.size)
    assertEquals(afterFirst.map { it.id }.toSet(), afterSecond.map { it.id }.toSet())
    assertEquals(1, runBlocking { db.courseDao().getAllCoursesOnce() }.size)
  }

  @Test
  fun `saving the same KRS twice in replace mode also keeps 16 schedules`() {
    saveKrsOnce(merge = false)
    saveKrsOnce(merge = false)

    assertEquals(16, runBlocking { db.scheduleDao().getAllSchedulesOnce() }.size)
  }
}
