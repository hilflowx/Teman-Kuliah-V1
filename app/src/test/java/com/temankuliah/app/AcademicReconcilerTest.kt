package com.temankuliah.app

import com.temankuliah.app.data.files.AcademicDocumentProcessor
import com.temankuliah.app.domain.AcademicReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** KRS: SKS = Teori + Praktek. KHS: Mutu = SKS x Bobot. Angka OCR yang menyalahi kaidah dipulihkan atau dibiarkan. */
@RunWith(RobolectricTestRunner::class)
class AcademicReconcilerTest {
    private fun krs(text: String) = AcademicDocumentProcessor.findAcademicRows(text, isKrs = true)
    private fun khs(text: String) = AcademicDocumentProcessor.findAcademicRows(text, isKrs = false)

    // ---- KRS ----

    @Test
    fun krsCourseNameEndingWithNumberIsNotReadAsTheoryColumn() {
        val rows = krs(
            "KODE NAMA MATA KULIAH TEORI PRAKTIK DOSEN HARI LAMA MULAI SELESAI\n" +
                "IF703 Pemrograman Web 2 2 1 Joko Suprianto, S.Kom., M.Kom. Kamis 150 08:00 10:30\n" +
                "IF704 Fisika 1 3 0 Budi Santoso, M.T. Senin 100 08:00 09:40"
        )
        assertEquals(2, rows.size)
        assertEquals("Pemrograman Web 2", rows[0].courseName)
        assertEquals(2, rows[0].theory)
        assertEquals(1, rows[0].practice)
        assertEquals(3, rows[0].credits)
        assertEquals("Fisika 1", rows[1].courseName)
        assertEquals(3, rows[1].theory)
        assertEquals(0, rows[1].practice)
        assertEquals(3, rows[1].credits)
    }

    @Test
    fun krsWithSksColumnReadsTheoryPracticeAndSksSeparately() {
        val rows = krs(
            "KODE NAMA MATA KULIAH TEORI PRAKTEK SKS DOSEN HARI LAMA MULAI SEL\n" +
                "IF703 Pemrograman Web 2 2 1 3 Joko Suprianto, S.Kom., M.Kom. Kamis 150 08:00 10:30"
        )
        assertEquals("Pemrograman Web 2", rows[0].courseName)
        assertEquals(2, rows[0].theory)
        assertEquals(1, rows[0].practice)
        assertEquals(3, rows[0].credits)
        assertEquals("Joko Suprianto, S.Kom., M.Kom.", rows[0].lecturer)
    }

    @Test
    fun krsSingleMisreadDigitIsRepairedFromPrintedSks() {
        val fixed = AcademicReconciler.repairKrsCredits(theory = 2, practice = 1, printed = 4)
        assertTrue(fixed.repaired)
        assertEquals(3, fixed.theory)
        assertEquals(1, fixed.practice)
        assertEquals(4, fixed.sks)
    }

    @Test
    fun krsAmbiguousMismatchKeepsTheorySumAndIsNotConsistent() {
        // 3 + 3 != 5: mengubah Teori atau Praktek (3 -> 2) sama-sama memenuhi, jadi tidak ditebak.
        val result = AcademicReconciler.repairKrsCredits(theory = 3, practice = 3, printed = 5)
        assertEquals(false, result.consistent)
        assertEquals(false, result.repaired)
        assertEquals(6, result.sks)
    }

    @Test
    fun krsLetterOCountsAsZeroInTheoryPracticeColumns() {
        val rows = krs("IF705 Skripsi O 6 TIM DOSEN FIKOM Sabtu 150 10:00 12:30")
        assertEquals(0, rows[0].theory)
        assertEquals(6, rows[0].practice)
        assertEquals(6, rows[0].credits)
    }

    // ---- KHS ----

    @Test
    fun khsMisreadSksIsRecoveredFromBobotAndMutu() {
        val rows = khs("1 IF103 Basis Data 8 B 3.00 9.00")
        assertEquals("Basis Data", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals("B", rows[0].grade)
        assertEquals(9.0, rows[0].qualityPoints, 0.001)
    }

    @Test
    fun khsMisreadGradeLetterIsRecoveredFromBobot() {
        val rows = khs("1 IF106 Pemrograman Web 3 8 3.00 9.00")
        assertEquals("Pemrograman Web", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals("B", rows[0].grade)
    }

    @Test
    fun khsCourseNameEndingWithNumberKeepsItsNumber() {
        val rows = khs("1 IF110 Pemrograman Web 2 3 A 4.00 12.00")
        assertEquals("Pemrograman Web 2", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals("A", rows[0].grade)
    }

    @Test
    fun khsDoubleBobotKeepsTheLetterTheOcrStartedWith() {
        // 3.50 bisa AB atau B+; "BT" adalah salah baca B+.
        val rows = khs("1 IF107 Sistem Operasi 3 BT 3.50 10.50")
        assertEquals("B+", rows[0].grade)
        assertEquals(3, rows[0].credits)
    }

    @Test
    fun khsRowWhoseMutuDoesNotMatchIsLeftToTheOlderReaders() {
        // 3 x 3.00 != 12.00 dan 12.00 / 3.00 = 4 yang bukan SKS yang tercetak: tidak dikoreksi.
        assertNull(AcademicReconciler.parseKhsTail(listOf("Basis", "Data", "3", "B", "3.00", "5.00")))
    }

    @Test
    fun khsOcrDecimalsWithLetterOAndCommaAreParsed() {
        assertEquals(12.0, AcademicReconciler.parseDecimal("12,OO")!!, 0.0001)
        assertEquals(4.0, AcademicReconciler.parseDecimal("4,00")!!, 0.0001)
        assertNull(AcademicReconciler.parseDecimal("Dasar"))
    }

    @Test
    fun khsTailIsRejectedWhenSksTokenIsPartOfTheName() {
        assertNull(AcademicReconciler.parseKhsTail(listOf("Pengantar", "Teknologi", "Dasar", "B", "3.00", "9.00")))
    }

    // ---- Ukuran gambar untuk OCR ----

    @Test
    fun smallTextTriggersUpscaleAndLargeTextDoesNot() {
        assertEquals(1f, AcademicReconciler.ocrUpscaleFactor(medianTextHeightPx = 18, longestEdgePx = 2000), 0.0f)
        val factor = AcademicReconciler.ocrUpscaleFactor(medianTextHeightPx = 10, longestEdgePx = 2000)
        assertTrue(factor > 1.5f && factor <= 3f)
        // Sisi terpanjang tidak boleh melebihi batas.
        assertEquals(1f, AcademicReconciler.ocrUpscaleFactor(medianTextHeightPx = 10, longestEdgePx = 3900), 0.0f)
    }
}
