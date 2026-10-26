package com.temankuliah.app

import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.domain.GpaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaCalculatorTest {
    @Test
    fun cumulativeGpaIsWeightedByCredits() {
        val summary = GpaCalculator.calculateSummary(listOf(
            course("CS101", credits = 3, grade = "A", semester = 1),
            course("CS102", credits = 3, grade = "B", semester = 1),
            course("CS103", credits = 4, grade = "B+", semester = 1)
        ))
        assertEquals(3.50, summary.cumulativeGpa, 0.001)
        assertEquals(10, summary.totalCredits)
        assertEquals(3.5, summary.semesterGpaMap[1]!!, 0.001)
    }

    @Test
    fun ungradedCoursesDoNotCountAsZeroAndDoNotInflateCredits() {
        val summary = GpaCalculator.calculateSummary(listOf(
            course("CS101", credits = 3, grade = "A", semester = 1),
            course("CS102", credits = 4, grade = "-", semester = 1),
            course("CS103", credits = 2, grade = "", semester = 2)
        ))
        assertEquals(4.0, summary.cumulativeGpa, 0.001)
        assertEquals(3, summary.totalCredits)
        assertEquals(3, summary.totalCourses)
        assertTrue(summary.semesterGpaMap.keys.contains(1))
        assertTrue(!summary.semesterGpaMap.keys.contains(2))
    }

    @Test
    fun failedGradeWithZeroPointStillCountsItsCredits() {
        val summary = GpaCalculator.calculateSummary(listOf(
            course("CS101", credits = 3, grade = "E", semester = 1),
            course("CS102", credits = 3, grade = "A", semester = 1)
        ))
        assertEquals(2.0, summary.cumulativeGpa, 0.001)
        assertEquals(6, summary.totalCredits)
        assertEquals(3, summary.passedCredits)
    }

    @Test
    fun repeatedCourseInDifferentSemestersCountsAsTwoAttempts() {
        val summary = GpaCalculator.calculateSummary(listOf(
            course("CS101", 3, "C", 1),
            course("CS101", 3, "A", 2)
        ))
        assertEquals(3.0, summary.cumulativeGpa, 0.001)
        assertEquals(6, summary.totalCredits)
        assertEquals(2, summary.totalCourses)
    }

    @Test
    fun emptyOrOnlyUngradedCoursesAvoidDivisionByZero() {
        val empty = GpaCalculator.calculateSummary(emptyList())
        val ungraded = GpaCalculator.calculateSummary(listOf(course("CS101", 3, "-", 1)))
        assertEquals(0.0, empty.cumulativeGpa, 0.001)
        assertEquals(0, empty.totalCredits)
        assertEquals("Belum Ada Nilai", ungraded.honorsPredicate)
        assertEquals(0, ungraded.totalCredits)
    }

    @Test
    fun gradeChangesAndCreditsRecalculateWeightedGpa() {
        val original = listOf(course("CS101", 2, "A", 1), course("CS102", 4, "B", 2))
        val changed = original.map { course ->
            when (course.code) {
                "CS101" -> course.copy(gradeLetter = "C", gradePoint = GpaCalculator.gradeLetterToPoint("C"))
                "CS102" -> course.copy(credits = 2)
                else -> course
            }
        }
        assertEquals(3.33, GpaCalculator.calculateSummary(original).cumulativeGpa, 0.01)
        assertEquals(2.5, GpaCalculator.calculateSummary(changed).cumulativeGpa, 0.01)
        assertEquals(4, GpaCalculator.calculateSummary(changed).totalCredits)
    }

    @Test
    fun qualityPointsMultipliesCreditsByGradePointExactly() {
        assertEquals(12.0, GpaCalculator.qualityPoints(3, "A"), 0.0)
        assertEquals(11.25, GpaCalculator.qualityPoints(3, "A-"), 0.0)
        assertEquals(10.5, GpaCalculator.qualityPoints(3, "AB"), 0.0)
        assertEquals(8.25, GpaCalculator.qualityPoints(3, "B-"), 0.0)
        assertEquals(0.0, GpaCalculator.qualityPoints(3, "-"), 0.0)
        assertEquals(0.0, GpaCalculator.qualityPoints(0, "A"), 0.0)
    }

    @Test
    fun semesterTotalsUseOnlyGradedCreditsAndMatchRowSums() {
        val totals = GpaCalculator.semesterTotals(
            listOf(3 to "A-", 2 to "B+", 3 to "B-", 4 to "-")
        )
        assertEquals(12, totals.totalCredits)
        assertEquals(8, totals.gradedCredits)
        // 11.25 + 7.00 + 8.25 = 26.50; total tidak boleh bergeser karena pembulatan
        assertEquals(26.5, totals.totalQualityPoints, 0.0)
        // 26.50 / 8 = 3.3125 -> 3.31
        assertEquals(3.31, totals.ips, 0.0)
    }

    @Test
    fun semesterIpsRoundsHalfUp() {
        // 3 x 3.75 = 11.25 ; 11.25 / 3 = 3.75 ; 2 SKS B = 6.00 -> (11.25 + 6) / 5 = 3.45
        assertEquals(3.45, GpaCalculator.semesterTotals(listOf(3 to "A-", 2 to "B")).ips, 0.0)
        // 2.875 harus menjadi 2.88 (dibulatkan ke atas), bukan 2.87
        assertEquals(2.88, GpaCalculator.semesterTotals(listOf(2 to "A", 3 to "C", 3 to "B")).ips, 0.0)
    }

    @Test
    fun semesterTotalsWithoutGradesHasZeroIps() {
        val totals = GpaCalculator.semesterTotals(listOf(3 to "-", 2 to ""))
        assertEquals(5, totals.totalCredits)
        assertEquals(0, totals.gradedCredits)
        assertEquals(0.0, totals.ips, 0.0)
    }

    private fun course(code: String, credits: Int, grade: String, semester: Int) = CourseEntity(
        userId = 1,
        code = code,
        name = code,
        credits = credits,
        gradeLetter = grade,
        gradePoint = GpaCalculator.gradeLetterToPoint(grade),
        semester = semester
    )
}
