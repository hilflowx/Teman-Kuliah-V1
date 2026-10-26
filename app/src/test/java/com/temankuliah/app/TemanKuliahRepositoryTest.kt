package com.temankuliah.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.files.AcademicDocumentProcessor.AcademicRow
import com.temankuliah.app.data.local.ProjectEntity
import com.temankuliah.app.data.local.ProjectMilestoneEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.AUTH_GUEST
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.repository.TemanKuliahRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.temankuliah.app.domain.MAX_SKS_PER_COURSE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TemanKuliahRepositoryTest {
  private lateinit var db: TemanKuliahDatabase
  private lateinit var repo: TemanKuliahRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    repo = TemanKuliahRepository(
      database = db,
      userDao = db.userDao(),
      courseDao = db.courseDao(),
      scheduleDao = db.scheduleDao(),
      academicDocumentDao = db.academicDocumentDao(),
      taskDao = db.taskDao(),
      academicEventDao = db.academicEventDao(),
      projectDao = db.projectDao(),
      projectMilestoneDao = db.projectMilestoneDao()
    )
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun `deleteProject removes milestones and unlinks tasks`() = runBlocking {
    val projectId = repo.addProject(ProjectEntity(userId = 1, title = "P", deadlineAtMillis = 1L))
    val project = db.projectDao().getAllOnce().single()
    repo.addProjectMilestone(ProjectMilestoneEntity(userId = 1, projectId = project.id, title = "M", deadlineAtMillis = 1L))
    val taskId = repo.addTask(TaskEntity(userId = 1, title = "T", dueAtMillis = 1L, projectId = project.id))

    repo.deleteProject(project)

    assertTrue(db.projectDao().getAllOnce().isEmpty())
    assertTrue(db.projectMilestoneDao().getAllOnce().isEmpty())
    assertNull(db.taskDao().getAllOnce().single { it.id == taskId }.projectId)
  }

  @Test
  fun `importCourses replace is all or nothing when a row is invalid`() = runBlocking {
    repo.importCourses(1, 2, listOf(AcademicRow("Basis Data", 3, "A", 1, "IF1")), replaceExisting = true)
    try {
      repo.importCourses(
        1, 2,
        listOf(AcademicRow("Jaringan", 3, "B", 1, "IF2"), AcademicRow("Rusak", 3, "X", 2, "IF3")),
        replaceExisting = true
      )
      fail("Nilai tidak valid harus ditolak")
    } catch (expected: IllegalArgumentException) {
    }
    val courses = repo.getCoursesFlow(1).first()
    assertEquals(listOf("Basis Data"), courses.map { it.name })
  }

  @Test
  fun `importCourses merge counts inserted and updated and clamps credits`() = runBlocking {
    repo.importCourses(1, 1, listOf(AcademicRow("Basis Data", 3, "-", 1, "IF1")), replaceExisting = true)
    val result = repo.importCourses(
      1, 1,
      listOf(AcademicRow("Basis Data", 3, "A", 1, "IF1"), AcademicRow("Jaringan", 99, "B", 2, "IF2")),
      replaceExisting = false
    )
    assertEquals(1, result.inserted)
    assertEquals(1, result.updated)
    val courses = repo.getCoursesFlow(1).first().associateBy { it.code }
    assertEquals("A", courses.getValue("IF1").gradeLetter)
    assertEquals(MAX_SKS_PER_COURSE, courses.getValue("IF2").credits)
  }

  @Test
  fun `setTaskProject rejects another user's task or project`() = runBlocking {
    repo.addProject(ProjectEntity(userId = 1, title = "P", deadlineAtMillis = 1L))
    val project = db.projectDao().getAllOnce().single()
    val id = repo.addTask(TaskEntity(userId = 1, title = "T", dueAtMillis = 1L))
    val task = db.taskDao().getAllOnce().single { it.id == id }

    try { repo.setTaskProject(2, task, project.id); fail() } catch (_: IllegalArgumentException) {}
    try { repo.setTaskProject(1, task, 999L); fail() } catch (_: IllegalArgumentException) {}
    repo.setTaskProject(1, task, project.id)
    assertEquals(project.id, db.taskDao().getAllOnce().single { it.id == id }.projectId)
  }

  @Test
  fun `importing a KRS after a KHS keeps existing grades and codes`() = runBlocking {
    repo.importCourses(1, 3, listOf(AcademicRow("Basis Data", 3, "A", 1, "IF301")), replaceExisting = true)
    repo.importCourses(1, 3, listOf(AcademicRow("Basis Data", 3, "-", 1, "")), replaceExisting = false)
    val course = repo.getCoursesFlow(1).first().single()
    assertEquals("A", course.gradeLetter)
    assertEquals(4.0, course.gradePoint, 0.01)
    assertEquals("IF301", course.code)
  }

  @Test
  fun `importCourses never invents a course code`() = runBlocking {
    try {
      repo.importCourses(1, 1, listOf(AcademicRow("Basis Data", 3, "A", 1, "")), replaceExisting = true)
      fail("Kode kosong harus ditolak")
    } catch (expected: IllegalArgumentException) {
    }
    assertTrue(repo.getCoursesFlow(1).first().isEmpty())
  }

  @Test
  fun `importing a KRS stores lecturer and class time and keeps SKS and grade from a KHS`() = runBlocking {
    repo.importCourses(1, 3, listOf(AcademicRow("Basis Data", 4, "A", 1, "IF301")), replaceExisting = true)
    repo.importCourses(
      1, 3,
      listOf(AcademicRow("Basis Data", 0, "-", 1, "IF301", lecturer = "Dr. Budi", schedule = "Senin 08:00-09:40")),
      replaceExisting = false,
      isKrs = true
    )
    val course = repo.getCoursesFlow(1).first().single()
    assertEquals(4, course.credits)
    assertEquals("A", course.gradeLetter)
    assertEquals("Dr. Budi", course.lecturer)
    assertEquals("Senin 08:00-09:40", course.classTime)
  }

  @Test
  fun `importing a KHS after a KRS keeps lecturer and class time`() = runBlocking {
    repo.importCourses(
      1, 3,
      listOf(AcademicRow("Basis Data", 0, "-", 1, "IF301", lecturer = "Dr. Budi", schedule = "Senin 08:00-09:40")),
      replaceExisting = true,
      isKrs = true
    )
    repo.importCourses(1, 3, listOf(AcademicRow("Basis Data", 3, "B+", 1, "IF301")), replaceExisting = false)
    val course = repo.getCoursesFlow(1).first().single()
    assertEquals(3, course.credits)
    assertEquals("B+", course.gradeLetter)
    assertEquals("Dr. Budi", course.lecturer)
    assertEquals("Senin 08:00-09:40", course.classTime)
  }


  @Test
  fun `KRS without SKS does not invent a credit value`() = runBlocking {
    repo.importCourses(
      1, 2,
      listOf(AcademicRow("Pemrograman Dasar", 0, "-", 1, "IF201")),
      replaceExisting = true,
      isKrs = true
    )
    val course = repo.getCoursesFlow(1).first().single()
    assertEquals(0, course.credits)
    assertEquals("-", course.gradeLetter)
  }

  @Test
  fun `a new KHS course without SKS is rejected instead of inventing a value`() = runBlocking {
    try {
      repo.importCourses(1, 1, listOf(AcademicRow("Basis Data", 0, "A", 1, "IF1")), replaceExisting = true)
      fail("SKS kosong pada KHS harus ditolak")
    } catch (expected: IllegalArgumentException) {
    }
    assertTrue(repo.getCoursesFlow(1).first().isEmpty())
  }

  @Test
  fun `guest sign in reuses the same guest account`() = runBlocking {
    val first = repo.signInAsGuest().getOrThrow()
    repo.logout()
    val second = repo.signInAsGuest().getOrThrow()

    assertEquals(first.id, second.id)
    assertEquals(AUTH_GUEST, second.authType)
    assertEquals(1, db.userDao().getAllUsersOnce().size)
    assertEquals(second.id, db.userDao().getActiveUser()?.id)
  }

  @Test
  fun `updateProfile allows blank NIM but rejects a duplicate NIM`() = runBlocking {
    repo.registerUser("Mahasiswa A", "999", "", "", "", 1, "a@example.com", "pass12345").getOrThrow()
    repo.logout()
    val guest = repo.signInAsGuest().getOrThrow()

    assertTrue(repo.updateProfile(guest.id, "Tamu", "", "", "", "").isSuccess)
    assertFalse(repo.updateProfile(guest.id, "Tamu", "999", "", "", "").isSuccess)
    assertTrue(repo.updateProfile(guest.id, "", "", "", "", "").isFailure)
  }
  @Test
  fun `semester beyond 8 can be added, selected, and keeps data of every semester`() = runBlocking {
    val user = repo.signInAsGuest().getOrThrow()
    assertEquals(8, db.userDao().getActiveUser()!!.semesterCount)

    assertTrue(repo.updateSemesterCount(user.id, 10).isSuccess)
    assertEquals(10, db.userDao().getActiveUser()!!.semesterCount)

    // Data tiap semester tetap terpisah, termasuk semester 9 dan 10.
    repo.importCourses(user.id, 3, listOf(AcademicRow("Basis Data", 3, "A", 1, "IF301")), false, isKrs = false)
    repo.importCourses(user.id, 10, listOf(AcademicRow("Skripsi", 6, "-", 1, "IF1001")), false, isKrs = true)

    assertTrue(repo.updateActiveSemester(user.id, 10).isSuccess)
    assertEquals(10, db.userDao().getActiveUser()!!.activeSemester)
    assertTrue(repo.updateActiveSemester(user.id, 0).isFailure)

    val all = repo.getCoursesFlow(user.id).first()
    assertEquals(listOf(3, 10), all.map { it.semester }.sorted())
  }

  @Test
  fun `semester count cannot drop below the active semester`() = runBlocking {
    val user = repo.signInAsGuest().getOrThrow()
    assertTrue(repo.updateSemesterCount(user.id, 12).isSuccess)
    assertTrue(repo.updateActiveSemester(user.id, 11).isSuccess)
    assertTrue(repo.updateSemesterCount(user.id, 9).isFailure)
    assertEquals(12, db.userDao().getActiveUser()!!.semesterCount)
  }

  private fun scheduleTemplate(date: String, start: String = "08:00", end: String = "10:00") = ScheduleEntity(
    userId = 1,
    courseName = "Basis Data",
    dayOfWeek = java.time.LocalDate.parse(date).dayOfWeek.value,
    scheduledDate = date,
    startTime = start,
    endTime = end,
    room = "Lab 1",
    lecturer = "",
    type = "Offline · Kuliah Teori",
    semester = 3
  )

  @Test
  fun `jadwal berulang menyimpan 16 pertemuan dengan tanggal hari dan jam yang benar`() = runBlocking {
    val saved = repo.addWeeklySchedules(scheduleTemplate("2026-09-07", "13:00", "14:40"), 16)

    assertEquals(16, saved.size)
    assertEquals(16, saved.map { it.id }.distinct().size)
    val stored = db.scheduleDao().getAllSchedulesOnce().sortedBy { it.scheduledDate }
    assertEquals(16, stored.size)
    assertEquals("2026-09-07", stored.first().scheduledDate)
    assertEquals("2026-12-21", stored.last().scheduledDate)
    assertTrue(stored.all { it.dayOfWeek == 1 && it.startTime == "13:00" && it.endTime == "14:40" && it.semester == 3 })
    assertTrue(stored.all { java.time.LocalDate.parse(it.scheduledDate).dayOfWeek.value == it.dayOfWeek })
    assertEquals(saved.map { it.scheduledDate }, stored.map { it.scheduledDate })
  }

  @Test
  fun `jadwal berulang lebih dari 16 pertemuan atau data tidak valid tidak menyimpan apa pun`() = runBlocking {
    assertTrue(runCatching { repo.addWeeklySchedules(scheduleTemplate("2026-09-07"), 17) }.isFailure)
    assertTrue(runCatching { repo.addWeeklySchedules(scheduleTemplate("2026-09-07", "10:00", "08:00"), 4) }.isFailure)
    assertTrue(runCatching { repo.addWeeklySchedules(scheduleTemplate("2026-09-07").copy(courseName = " "), 4) }.isFailure)
    assertTrue(db.scheduleDao().getAllSchedulesOnce().isEmpty())
  }

  @Test
  fun `jadwal sekali tetap menyimpan satu baris`() = runBlocking {
    repo.addSchedule(scheduleTemplate("2026-09-09"))
    val stored = db.scheduleDao().getAllSchedulesOnce()
    assertEquals(1, stored.size)
    assertEquals("2026-09-09", stored.single().scheduledDate)
  }

  @Test
  fun `jadwal lampau dan semester lain tetap tersimpan dan terbaca berurutan`() = runBlocking {
    repo.addSchedule(scheduleTemplate("2020-03-02"))                                   // sudah lewat
    repo.addSchedule(scheduleTemplate("2026-09-09").copy(semester = 1))                // semester lain
    repo.addWeeklySchedules(scheduleTemplate("2026-09-07"), 16)

    val all = repo.getSchedulesFlow(1).first()
    assertEquals(18, all.size)
    assertEquals("2020-03-02", all.first().scheduledDate)
    assertEquals(all.map { it.scheduledDate }, all.map { it.scheduledDate }.sorted())
    assertEquals(setOf(1, 3), all.map { it.semester }.toSet())
  }

  @Test
  fun `satu seri berulang berbagi id seri dan jadwal sekali tidak punya id seri`() = runBlocking {
    repo.addSchedule(scheduleTemplate("2026-09-09"))
    repo.addWeeklySchedules(scheduleTemplate("2026-09-07"), 16)
    repo.addWeeklySchedules(scheduleTemplate("2026-10-01"), 1)

    val all = db.scheduleDao().getAllSchedulesOnce()
    val series = all.filter { it.recurrenceGroupId.isNotBlank() }
    assertEquals(16, series.size)
    assertEquals(1, series.map { it.recurrenceGroupId }.distinct().size)
    assertEquals(2, all.count { it.recurrenceGroupId.isBlank() })

    // Menandai selesai hanya mengubah status; baris tetap tersimpan.
    val once = all.first { it.recurrenceGroupId.isBlank() }
    repo.setScheduleCompleted(once.id, true)
    val after = repo.getSchedulesFlow(1).first()
    assertEquals(18, after.size)
    assertTrue(after.first { it.id == once.id }.isCompleted)
  }

  @Test
  fun `centang selesai pada satu pertemuan berulang tidak mencentang pertemuan minggu lain`() = runBlocking {
    val saved = repo.addWeeklySchedules(scheduleTemplate("2026-09-07"), 16)
    assertTrue(saved.none { it.isCompleted })

    repo.setScheduleCompleted(saved[0].id, true) // pertemuan minggu ini

    val after = repo.getSchedulesFlow(1).first().sortedBy { it.scheduledDate }
    assertEquals(16, after.size)
    assertTrue(after[0].isCompleted)
    assertTrue(after.drop(1).none { it.isCompleted }) // minggu depannya tetap belum
  }
}

