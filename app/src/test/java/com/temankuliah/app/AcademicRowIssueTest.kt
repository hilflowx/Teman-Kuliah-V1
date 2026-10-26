package com.temankuliah.app

import com.temankuliah.app.ui.academic.KhsLine
import com.temankuliah.app.ui.academic.KrsLine
import com.temankuliah.app.ui.academic.khsRowIssue
import com.temankuliah.app.ui.academic.krsRowIssue
import com.temankuliah.app.ui.academic.semesterSummaryLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicRowIssueTest {
    private fun krs(code: String = "A82003", name: String = "KKN", theory: Int = 3, practice: Int = 0, sks: Int = 3) =
        KrsLine(code, name, theory, practice, sks, "Dosen", "Senin", "19:00", "21:30", 150)

    @Test
    fun `normal KRS row has no issue`() {
        assertNull(krsRowIssue(krs()))
    }

    @Test
    fun `KRS row with implausible practice and sks is flagged but can still be saved`() {
        val issue = krsRowIssue(krs(code = "A82401", name = "Expert System", theory = 2, practice = 30, sks = 32))
        assertNotNull(issue)
        assertEquals("Praktik 30 dan SKS 32 tampak tidak wajar", issue!!.message)
        assertFalse(issue.blocking)
    }

    @Test
    fun `KRS row without code blocks saving`() {
        val issue = krsRowIssue(krs(code = " "))
        assertEquals("Kode MK belum diisi", issue!!.message)
        assertTrue(issue.blocking)
    }

    @Test
    fun `KRS row without name blocks saving before code is checked`() {
        val issue = krsRowIssue(krs(code = "", name = ""))
        assertEquals("Nama mata kuliah belum diisi", issue!!.message)
        assertTrue(issue.blocking)
    }

    @Test
    fun `KHS row requires sks and flags an implausible one`() {
        assertNull(khsRowIssue(KhsLine("A02101", "Pengantar Teknologi Informasi", 2, "A")))
        assertTrue(khsRowIssue(KhsLine("A02101", "PTI", 0, "A"))!!.blocking)
        val odd = khsRowIssue(KhsLine("A02101", "PTI", 32, "A"))!!
        assertEquals("SKS 32 tampak tidak wajar", odd.message)
        assertFalse(odd.blocking)
    }

    @Test
    fun `semester summary line is empty-safe`() {
        assertEquals("Belum ada mata kuliah", semesterSummaryLine(emptyList()))
    }
}
