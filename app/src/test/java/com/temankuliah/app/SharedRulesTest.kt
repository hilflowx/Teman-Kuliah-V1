package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.files.AcademicDocumentProcessor
import com.temankuliah.app.data.local.AppStorage
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.domain.DEFAULT_REMINDER_MINUTES
import com.temankuliah.app.domain.GpaCalculator
import com.temankuliah.app.domain.Grades
import com.temankuliah.app.domain.normalizeNim
import com.temankuliah.app.ui.profile.ReminderPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Aturan yang dulu terduplikasi (nilai huruf, default pengingat, NIM) kini punya satu sumber di package domain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedRulesTest {
  private lateinit var app: Application
  private lateinit var db: TemanKuliahDatabase
  private lateinit var repo: TemanKuliahRepository

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    repo = TemanKuliahRepository.from(db)
    AppStorage.getPreferences(app).edit().clear().commit()
  }

  @After
  fun tearDown() = db.close()

  // --- Nilai huruf ---

  @Test
  fun `every grade option has a point in GpaCalculator and is accepted by repository rules`() {
    assertEquals(listOf("A", "A-", "AB", "B+", "B", "B-", "BC", "C+", "C", "D", "E", "-"), Grades.OPTIONS)
    Grades.POINTS.forEach { (letter, point) ->
      assertEquals(letter, point, GpaCalculator.gradeLetterToPoint(letter), 0.0)
      assertTrue(GpaCalculator.isGraded(letter))
      assertTrue(Grades.isValid(letter))
    }
    assertTrue(Grades.isValid("-"))
    assertFalse(GpaCalculator.isGraded("-"))
  }

  @Test
  fun `legacy codes BL TL T K P and A+ are not valid grades`() {
    listOf("BL", "TL", "T", "K", "P", "A+", "C-", "D+", "").forEach { assertFalse(it, Grades.isValid(it)) }
  }

  @Test
  fun `letterForPoint recognizes 3_5 and 2_5 as well as the rest of the scale`() {
    assertEquals("AB", Grades.letterForPoint(3.5))
    assertEquals("BC", Grades.letterForPoint(2.5))
    assertEquals(listOf("AB", "B+"), Grades.lettersForPoint(3.5))
    assertEquals(listOf("BC", "C+"), Grades.lettersForPoint(2.5))
    assertEquals("A", Grades.letterForPoint(4.0))
    assertEquals("A-", Grades.letterForPoint(3.75))
    assertEquals("B-", Grades.letterForPoint(2.75))
    assertEquals("E", Grades.letterForPoint(0.0))
    assertNull(Grades.letterForPoint(3.2))
  }

  @Test
  fun `grade is recovered from bobot and mutu for 3_5 and 2_5`() {
    assertEquals("AB", AcademicDocumentProcessor.gradeFromNumbers(3, listOf(3.5, 10.5)))
    assertEquals("BC", AcademicDocumentProcessor.gradeFromNumbers(3, listOf(2.5, 7.5)))
    assertEquals("A", AcademicDocumentProcessor.gradeFromNumbers(3, listOf(4.0, 12.0)))
  }

  @Test
  fun `repository rejects backup-only grade codes and accepts B+`() = runBlocking {
    fun course(grade: String) = CourseEntity(userId = 1, code = "C$grade", name = "X", credits = 3, gradeLetter = grade, gradePoint = 0.0, semester = 1)
    assertTrue(runCatching { repo.addCourse(course("BL")) }.isFailure)
    assertTrue(runCatching { repo.addCourse(course("B+")) }.isSuccess)
  }

  // --- Default pengingat ---

  @Test
  fun `reminder default is 15 minutes everywhere`() {
    assertEquals(15, DEFAULT_REMINDER_MINUTES)
    assertEquals(15, ReminderPreferences().reminderMinutes)
    assertEquals(15, ReminderPreferences.from(AppStorage.getPreferences(app)).reminderMinutes)
    assertEquals(15, ScheduleEntity(userId = 1, courseName = "X", dayOfWeek = 1, startTime = "08:00", endTime = "09:00", room = "", lecturer = "").reminderMinutesBefore)
  }

  // --- NIM ---

  @Test
  fun `normalizeNim trims and lowercases`() {
    assertEquals("ti-001", normalizeNim("  TI-001 "))
    assertEquals("", normalizeNim("   "))
  }

  @Test
  fun `register rejects a NIM that differs only by case or spaces`() = runBlocking {
    assertTrue(repo.registerUser("A", "TI-001", "", "", "", 1, "a@example.com", "pass12345").isSuccess)
    val duplicate = repo.registerUser("B", " ti-001 ", "", "", "", 1, "b@example.com", "pass12345")
    assertTrue(duplicate.isFailure)
    assertEquals(1, db.userDao().getAllUsersOnce().size)
  }

  @Test
  fun `updateProfile rejects a NIM that matches another account ignoring case`() = runBlocking {
    repo.registerUser("A", "TI-001", "", "", "", 1, "a@example.com", "pass12345").getOrThrow()
    repo.logout()
    val guest = repo.signInAsGuest().getOrThrow()

    assertTrue(repo.updateProfile(guest.id, "Tamu", " ti-001", "", "", "").isFailure)
    assertTrue(repo.updateProfile(guest.id, "Tamu", "TI-002", "", "", "").isSuccess)
  }

  @Test
  fun `login by NIM ignores case and surrounding spaces`() = runBlocking {
    repo.registerUser("A", "TI-001", "", "", "", 1, "a@example.com", "pass12345").getOrThrow()
    repo.logout()

    assertTrue(repo.loginWithEmailAndPassword("  ti-001 ", "pass12345").isSuccess)
  }
}
