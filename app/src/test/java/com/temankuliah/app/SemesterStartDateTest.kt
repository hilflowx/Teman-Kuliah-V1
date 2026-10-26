package com.temankuliah.app

import com.temankuliah.app.domain.parseSemesterStartDate
import com.temankuliah.app.domain.semesterStartDateKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemesterStartDateTest {
  @Test
  fun `key uses the stored prefix and semester number`() {
    assertEquals("semester_start_date_7", semesterStartDateKey(7))
  }

  @Test
  fun `valid ISO date is parsed, surrounding spaces ignored`() {
    assertEquals(LocalDate.of(2026, 9, 7), parseSemesterStartDate("2026-09-07"))
    assertEquals(LocalDate.of(2026, 9, 7), parseSemesterStartDate(" 2026-09-07 "))
  }

  @Test
  fun `null, blank and invalid values mean not configured`() {
    assertNull(parseSemesterStartDate(null))
    assertNull(parseSemesterStartDate(""))
    assertNull(parseSemesterStartDate("   "))
    assertNull(parseSemesterStartDate("not-a-date"))
    assertNull(parseSemesterStartDate("2026-13-40"))
    assertNull(parseSemesterStartDate("2026-9-7"))
  }
}
