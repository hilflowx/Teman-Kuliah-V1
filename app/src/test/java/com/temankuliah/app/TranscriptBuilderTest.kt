package com.temankuliah.app

import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.domain.GpaCalculator
import com.temankuliah.app.domain.TranscriptBuilder
import com.temankuliah.app.domain.TranscriptHtml
import com.temankuliah.app.domain.TranscriptStudent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBuilderTest {
    private val student = TranscriptStudent("Budi Santoso", "123456", "Teknik Informatika", "", "Universitas Contoh")

    @Test
    fun semestersRunFromFirstToLastGradedSemesterAndSkipUngradedOnes() {
        val transcript = TranscriptBuilder.build(
            student,
            listOf(
                course("C3", "Kalkulus", 2, "C", semester = 3),
                course("A2", "Fisika", 3, "B", semester = 1),
                course("A1", "Algoritma", 3, "A", semester = 1),
                course("B1", "Basis Data", 3, "-", semester = 2),
                course("D1", "Statistika", 3, "-", semester = 3)
            )
        )
        assertEquals(listOf(1, 3), transcript.semesters.map { it.semester })
        assertEquals(listOf("A1", "A2"), transcript.semesters[0].courses.map { it.code })
        assertEquals(3.50, transcript.semesters[0].ips, 0.001)
        assertEquals(3.50, transcript.semesters[0].cumulativeGpa, 0.001)
        assertEquals(2.00, transcript.semesters[1].ips, 0.001)
        assertEquals(3.13, transcript.semesters[1].cumulativeGpa, 0.001)
        assertEquals(8, transcript.totalCredits)
        assertEquals(25.0, transcript.totalQualityPoints, 0.001)
        assertEquals(3.13, transcript.gpa, 0.001)
        assertEquals("Sangat Memuaskan", transcript.predicate)
    }

    @Test
    fun transcriptIsEmptyWhenNoCourseHasAGrade() {
        val transcript = TranscriptBuilder.build(student, listOf(course("A1", "Algoritma", 3, "-", semester = 1)))
        assertTrue(transcript.isEmpty)
    }

    @Test
    fun htmlEscapesUserTextAndOmitsBlankIdentityFields() {
        val transcript = TranscriptBuilder.build(
            student,
            listOf(course("A1", "<b>Seni & Desain</b>", 3, "A", semester = 1))
        )
        val html = TranscriptHtml.render(transcript, "21 Oktober 2026")
        assertTrue(html.contains("&lt;b&gt;Seni &amp; Desain&lt;/b&gt;"))
        assertFalse(html.contains("<b>Seni"))
        assertTrue(html.contains("Semester 1"))
        assertTrue(html.contains("123456"))
        assertFalse(html.contains("Fakultas"))
        assertTrue(html.contains("bukan transkrip resmi"))
    }

    private fun course(code: String, name: String, credits: Int, grade: String, semester: Int) = CourseEntity(
        userId = 1,
        code = code,
        name = name,
        credits = credits,
        gradeLetter = grade,
        gradePoint = GpaCalculator.gradeLetterToPoint(grade),
        semester = semester
    )
}
