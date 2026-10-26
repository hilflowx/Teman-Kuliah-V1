package com.temankuliah.app

import com.temankuliah.app.data.files.AcademicDocumentProcessor
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.domain.GpaCalculator
import com.temankuliah.app.domain.MAX_SKS_PER_COURSE
import com.temankuliah.app.data.repository.isValidCourseCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaPolicyTest {
    private fun course(credits: Int, grade: String, semester: Int = 1) = CourseEntity(
        userId = 1L, code = "MK", name = "Mata kuliah", credits = credits,
        gradeLetter = grade, gradePoint = GpaCalculator.gradeLetterToPoint(grade), semester = semester
    )

    @Test
    fun `predicate uses the GPA after rounding to two decimals`() {
        // 2 SKS A + 198 SKS AB = 701 / 200 = 3.505 -> dibulatkan 3.51 (HALF_UP) -> Cum Laude.
        // Versi lama membandingkan 3.505 yang belum dibulatkan (double 3.50499...) sehingga salah predikat.
        val summary = GpaCalculator.calculateSummary(listOf(course(2, "A"), course(198, "AB")))
        assertEquals(3.51, summary.cumulativeGpa, 0.0)
        assertEquals("Dengan Pujian (Cum Laude)", summary.honorsPredicate)
    }

    @Test
    fun `cumulative GPA and IPS come from the same rounding policy`() {
        val courses = listOf(course(3, "A"), course(2, "B+"), course(3, "C"), course(2, "E"))
        val summary = GpaCalculator.calculateSummary(courses)
        val totals = GpaCalculator.semesterTotals(courses.map { it.credits to it.gradeLetter })
        assertEquals(totals.ips, summary.semesterGpaMap.getValue(1), 0.0)
        assertEquals(totals.ips, summary.cumulativeGpa, 0.0)
    }

    @Test
    fun `semester GPA in the summary matches the per-semester IPS for uneven credits`() {
        val courses = listOf(course(3, "B"), course(3, "A-"), course(2, "C+", semester = 2), course(3, "A", semester = 2))
        val summary = GpaCalculator.calculateSummary(courses)
        assertEquals(
            GpaCalculator.semesterTotals(listOf(3 to "B", 3 to "A-")).ips,
            summary.semesterGpaMap.getValue(1), 0.0
        )
        assertEquals(
            GpaCalculator.semesterTotals(listOf(2 to "C+", 3 to "A")).ips,
            summary.semesterGpaMap.getValue(2), 0.0
        )
    }

    @Test
    fun `processor and calculator share one grade table`() {
        listOf("A", "A-", "AB", "B+", "B", "B-", "BC", "C+", "C", "D", "E", "-", "X", " b+ ").forEach { letter ->
            assertEquals("nilai $letter", GpaCalculator.gradeLetterToPoint(letter), AcademicDocumentProcessor.gradeLetterToPoint(letter), 0.0)
        }
    }

    @Test
    fun `one SKS limit is used for validation`() {
        assertEquals(30, MAX_SKS_PER_COURSE)
        assertTrue(isValidCourseCredits(MAX_SKS_PER_COURSE, "A"))
        assertFalse(isValidCourseCredits(MAX_SKS_PER_COURSE + 1, "A"))
        assertTrue(isValidCourseCredits(0, "-"))
    }

    @Test
    fun `KHS import keeps credits above the old 12 limit`() {
        val rows = AcademicDocumentProcessor.findAcademicRows("IF101 Basis Data 20 SKS A", isKrs = false)
        assertEquals(1, rows.size)
        assertEquals(20, rows.single().credits)
        assertEquals("A", rows.single().grade)
    }
}
