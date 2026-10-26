package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.ui.FeatureContext
import com.temankuliah.app.ui.courses.CourseController
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Menguji logika state di CourseController (lapisan controller) memakai Room in-memory. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseControllerTest {
  private lateinit var db: TemanKuliahDatabase
  private lateinit var scope: CoroutineScope
  private lateinit var user: MutableStateFlow<UserEntity?>
  private lateinit var snackbar: MutableStateFlow<String?>
  private lateinit var controller: CourseController
  private var dialogClosedCount = 0

  private val testUser = UserEntity(
    id = 1, fullName = "Tester", nim = "123", studyProgram = "TI",
    faculty = "FIK", university = "USP", password = ""
  )

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    val repo = TemanKuliahRepository(
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
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    user = MutableStateFlow(testUser)
    snackbar = MutableStateFlow(null)
    dialogClosedCount = 0
    controller = CourseController(
      FeatureContext(
        application = app,
        database = db,
        repository = repo,
        scope = scope,
        currentUser = user,
        activeSemester = MutableStateFlow(1),
        snackbar = snackbar,
        profileEditError = MutableStateFlow(null),
        closeDialog = { dialogClosedCount++ }
      )
    )
  }

  @After
  fun tearDown() {
    scope.cancel()
    db.close()
  }

  private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

  private fun awaitCourses(predicate: (List<CourseEntity>) -> Boolean): List<CourseEntity> =
    await { controller.courses.first(predicate) }

  @Test
  fun `addCourse normalizes input, computes grade point, closes dialog and shows snackbar`() {
    controller.addCourse(code = " if101 ", name = "  Basis Data ", credits = 3, gradeLetter = " b+ ", semester = 2)

    val saved = awaitCourses { it.isNotEmpty() }.single()
    assertEquals("IF101", saved.code)
    assertEquals("Basis Data", saved.name)
    assertEquals("B+", saved.gradeLetter)
    assertEquals(3.5, saved.gradePoint, 0.0)
    assertEquals(2, saved.semester)
    assertEquals(1L, saved.userId)

    await { snackbar.first { it != null } }
    assertEquals("Mata kuliah 'Basis Data' berhasil ditambahkan.", snackbar.value)
    assertEquals(1, dialogClosedCount)
  }

  @Test
  fun `addCourse does nothing when no user is signed in`() {
    user.value = null

    controller.addCourse("IF1", "Algoritma", 3, "A", 1)
    Thread.sleep(300)

    assertTrue(runBlocking { db.courseDao().getCoursesByUser(1).first() }.isEmpty())
    assertEquals(null, snackbar.value)
    assertEquals(0, dialogClosedCount)
  }

  @Test
  fun `updateCourse rewrites fields and recalculates grade point`() {
    controller.addCourse("IF1", "Algoritma", 3, "C", 1)
    val original = awaitCourses { it.isNotEmpty() }.single()

    controller.updateCourse(original, " if2 ", "Struktur Data", 4, "a", 3)

    val updated = awaitCourses { list -> list.singleOrNull()?.code == "IF2" }.single()
    assertEquals(original.id, updated.id)
    assertEquals("Struktur Data", updated.name)
    assertEquals(4, updated.credits)
    assertEquals("A", updated.gradeLetter)
    assertEquals(4.0, updated.gradePoint, 0.0)
    assertEquals(3, updated.semester)
  }

  @Test
  fun `deleteCourse removes the course and reports it`() {
    controller.addCourse("IF1", "Algoritma", 3, "A", 1)
    val saved = awaitCourses { it.isNotEmpty() }.single()

    controller.deleteCourse(saved.id)

    awaitCourses { it.isEmpty() }
    await { snackbar.first { it == "Mata kuliah telah dihapus." } }
  }

  @Test
  fun `gpaSummary follows the course list`() {
    controller.addCourse("IF1", "Algoritma", 3, "A", 1)
    controller.addCourse("IF2", "Basis Data", 3, "B", 1)

    val summary = await { controller.gpaSummary.first { it.totalCourses == 2 } }

    assertEquals(3.5, summary.cumulativeGpa, 0.001)
    assertEquals(6, summary.totalCredits)
    assertNotNull(summary.semesterGpaMap[1])
    assertEquals(3.5, summary.semesterGpaMap[1]!!, 0.001)
  }

  @Test
  fun `courses are empty after the user signs out`() {
    controller.addCourse("IF1", "Algoritma", 3, "A", 1)
    awaitCourses { it.isNotEmpty() }

    user.value = null

    awaitCourses { it.isEmpty() }
  }

  @Test
  fun `setSearchQuery updates the query flow`() {
    controller.setSearchQuery("basis")
    assertEquals("basis", controller.courseSearchQuery.value)
    controller.setSearchQuery("")
    assertEquals("", controller.courseSearchQuery.value)
  }
}
