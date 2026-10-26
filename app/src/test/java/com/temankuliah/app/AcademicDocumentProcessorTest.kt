package com.temankuliah.app

import com.temankuliah.app.data.files.AcademicDocumentProcessor
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.displayClassTime
import com.temankuliah.app.data.local.practiceCredits
import com.temankuliah.app.data.local.theoryCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AcademicDocumentProcessorTest {
    @Test
    fun recognizesCourseCreditAndGradeRowsWithoutInventingMissingValues() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "Matematika Diskrit 3 A\nPemrograman Dasar 4 B+\nMata Kuliah SKS Nilai\nPengantar Sistem Informasi"
        )
        assertEquals(2, rows.size)
        assertEquals("Matematika Diskrit", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals("A", rows[0].grade)
        assertEquals("B+", rows[1].grade)
        assertTrue(rows.none { it.courseName.contains("Pengantar Sistem Informasi") })
    }

    @Test
    fun recognizesNumberedRowsWithCourseCodesAndQualityPoints() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "1. IF101 Algoritma dan Pemrograman 3 A\n2. IF102 Struktur Data 4 B+"
        )
        assertEquals(2, rows.size)
        assertEquals("Algoritma dan Pemrograman", rows[0].courseName)
        assertEquals("IF101", rows[0].code)
        assertEquals(3, rows[0].credits)
        assertEquals("A", rows[0].grade)
        assertEquals(4.0, rows[0].gradePoint, 0.01)
        assertEquals(12.0, rows[0].qualityPoints, 0.01)

        assertEquals("Struktur Data", rows[1].courseName)
        assertEquals("IF102", rows[1].code)
        assertEquals(4, rows[1].credits)
        assertEquals(3.5, rows[1].gradePoint, 0.01)
        assertEquals(14.0, rows[1].qualityPoints, 0.01)
    }

    @Test
    fun recognizesKrsRowsWithDefaultGrade() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "CS201 Basis Data 3 SKS\nCS202 Jaringan Komputer 3 SKS",
            isKrs = true
        )
        assertEquals(2, rows.size)
        assertEquals("-", rows[0].grade)
        assertEquals(0.0, rows[0].gradePoint, 0.01)
        assertEquals(0.0, rows[0].qualityPoints, 0.01)
    }

    @Test
    fun recognizesPipeDelimitedTable() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "CS301 | Rekayasa Perangkat Lunak | 3 | A\nCS302 | Kecerdasan Buatan | 3 | B"
        )
        assertEquals(2, rows.size)
        assertEquals("Rekayasa Perangkat Lunak", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals("A", rows[0].grade)
    }

    @Test
    fun doesNotSkipCoursesWhoseNameContainsIps() {
        val rows = AcademicDocumentProcessor.findAcademicRows("Skripsi 6 A\nIPK 3,50\nIPS 3,60")
        assertEquals(listOf("Skripsi"), rows.map { it.courseName })
        assertEquals(6, rows[0].credits)
    }

    @Test
    fun ignoresLeadingRowNumberColumnInDelimitedRows() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "1 | IF101 | Algoritma dan Pemrograman | 3 | A\n2 | IF102 | Struktur Data | 4 | B+"
        )
        assertEquals(2, rows.size)
        assertEquals("IF101", rows[0].code)
        assertEquals("Algoritma dan Pemrograman", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals(4, rows[1].credits)
        assertEquals("B+", rows[1].grade)
    }

    @Test
    fun readsCsvWithNumberColumn() {
        val rows = AcademicDocumentProcessor.findAcademicRows("1,IF101,Basis Data,3,A,4.00,12.00")
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].credits)
        assertEquals("A", rows[0].grade)
    }

    @Test
    fun krsTabularRowKeepsOnlyCodeNameLecturerAndSchedule() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "No\tKode\tMata Kuliah\tDosen\tJam\n" +
                "1\tIF101\tAlgoritma dan Pemrograman\tDr. Budi Santoso, M.Kom.\tSenin 08.00-09.40\n" +
                "2\tIF102\tStruktur Data\tSiti Aminah, M.T.\tSelasa 10.00 - 11.40 WIB",
            isKrs = true
        )
        assertEquals(2, rows.size)
        assertEquals("IF101", rows[0].code)
        assertEquals("Algoritma dan Pemrograman", rows[0].courseName)
        assertEquals("Dr. Budi Santoso, M.Kom.", rows[0].lecturer)
        assertEquals("Senin 08:00-09:40", rows[0].schedule)
        assertEquals("-", rows[0].grade)
        assertEquals("Selasa 10:00-11:40", rows[1].schedule)
        assertEquals("Siti Aminah, M.T.", rows[1].lecturer)
    }

    @Test
    fun krsFreeTextSplitsNameAndLecturerAndLeavesMissingFieldsBlank() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF102 Struktur Data Dr. Siti Aminah, M.T. Selasa 10.00-11.40\nIF103 Jaringan Komputer",
            isKrs = true
        )
        assertEquals(2, rows.size)
        assertEquals("Struktur Data", rows[0].courseName)
        assertEquals("Dr. Siti Aminah, M.T.", rows[0].lecturer)
        assertEquals("Selasa 10:00-11:40", rows[0].schedule)
        assertEquals("Jaringan Komputer", rows[1].courseName)
        assertEquals("", rows[1].lecturer)
        assertEquals("", rows[1].schedule)
    }

    @Test
    fun krsDayHeadingIsAppliedToFollowingRowsWithoutDay() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "Senin\nIF101\tBasis Data\tDr. Budi\t08.00-09.40",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals("Senin 08:00-09:40", rows[0].schedule)
    }

    @Test
    fun khsRowWithMissingCreditsKeepsCellBlank() {
        val rows = AcademicDocumentProcessor.findAcademicRows("IF103\tJaringan Komputer\tB\nIF104\tBasis Data\t3")
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].credits)
        assertEquals("B", rows[0].grade)
        assertEquals(3, rows[1].credits)
        assertEquals("-", rows[1].grade)
    }

    @Test
    fun khsDecimalCommasAreNotTreatedAsColumns() {
        val rows = AcademicDocumentProcessor.findAcademicRows("IF101 Algoritma dan Pemrograman 3 A 4,00 12,00")
        assertEquals(1, rows.size)
        assertEquals("Algoritma dan Pemrograman", rows[0].courseName)
        assertEquals(3, rows[0].credits)
        assertEquals(12.0, rows[0].qualityPoints, 0.01)
    }

    @Test
    fun layoutRowsKeepsTableColumnsSeparatedByTabs() {
        fun word(text: String, left: Int, right: Int, top: Int) = AcademicDocumentProcessor.OcrWord(text, left, top, right, top + 20)
        val text = AcademicDocumentProcessor.layoutRows(
            listOf(
                word("3", 400, 410, 2), word("IF101", 0, 50, 0), word("Algoritma", 100, 200, 1), word("dan", 208, 240, 0),
                word("Struktur", 100, 190, 42), word("IF102", 0, 50, 40), word("4", 400, 410, 41)
            )
        )
        assertEquals("IF101\tAlgoritma dan\t3\nIF102\tStruktur\t4", text)
    }

    @Test
    fun recoversGradeFromBobotAndMutuWhenGradeColumnIsUnreadable() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF105\tPemrograman Web\t3\t3.00\t9.00\nIF106 Basis Data 2 4.00 8.00"
        )
        assertEquals(2, rows.size)
        assertEquals("B", rows[0].grade)
        assertEquals(9.0, rows[0].qualityPoints, 0.001)
        assertEquals("A", rows[1].grade)
        assertEquals("Basis Data", rows[1].courseName)
    }

    @Test
    fun ignoresKhsSummaryAndEmptyCodeRows() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "1 A02111 PENDIDIKAN AGAMA 2 AB 3.50 7.00\n" +
                "Kode kosong\t18\t-\t-\t-\n" +
                "8. Total\n" +
                "Total\t36\t71.00"
        )
        assertEquals(1, rows.size)
        assertEquals("A02111", rows.single().code)
        assertEquals("PENDIDIKAN AGAMA", rows.single().courseName)
    }

    @Test
    fun doesNotTreatCourseTitleStartingWithTotalAsSummary() {
        val rows = AcademicDocumentProcessor.findAcademicRows("Total Quality Management 3 A")
        assertEquals(1, rows.size)
        assertEquals("Total Quality Management", rows.single().courseName)
        assertEquals(3, rows.single().credits)
    }

    @Test
    fun doesNotGuessGradeFromAnAmbiguousSingleNumber() {
        // 2 SKS dengan satu angka 4.00 bisa berarti bobot A atau mutu C: dibiarkan kosong.
        val rows = AcademicDocumentProcessor.findAcademicRows("IF107\tEtika Profesi\t2\t4.00")
        assertEquals(1, rows.size)
        assertEquals("-", rows[0].grade)
    }

    @Test
    fun fixesCommonOcrMisreadsInGradeColumn() {
        val rows = AcademicDocumentProcessor.findAcademicRows("IF108\tStatistika\t3\tBt")
        assertEquals(1, rows.size)
        assertEquals("B+", rows[0].grade)
    }

    @Test
    fun readsKhsWithOneLetterCodesAndAbGrade() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "1 A02101 PENGANTAR TEKNOLOGI INFORMASI 2 A 4.00 8.00\n5 A02111 PENDIDIKAN AGAMA 2 AB 3.50 7.00\nTotal 18 71.00"
        )
        assertEquals(2, rows.size)
        assertEquals("A02101", rows[0].code)
        assertEquals("PENGANTAR TEKNOLOGI INFORMASI", rows[0].courseName)
        assertEquals("AB", rows[1].grade)
        assertEquals(7.0, rows[1].qualityPoints, 0.001)
    }

    // ---- KRS berkolom lengkap: KODE, NAMA, TEORI, PRAKTEK, DOSEN, HARI, LAMA, MULAI, SEL + Jumlah SKS ----

    private val krsTable =
        "KODE\tNAMA MATA KULIAH\tTEORI\tPRAKTEK\tDOSEN\tHARI\tLAMA\tMULAI\tSEL\n" +
            "1\tIF701\tArtificial Intelligence\t2\t1\tTachiyya Nailal Khusna, S.Kom., M.Kom.\tKamis\t150\t08:00\t10:30\n" +
            "2\tIF702\tKalkulus 2\t3\t0\tJoko Suprianto, S.Kom., M.Kom.\tSenin\t100\t08.00\t09.40\n" +
            "Jumlah SKS\t5\t1\t6"

    @Test
    fun krsReadsEveryColumnIncludingTheoryPracticeAndTimes() {
        val rows = AcademicDocumentProcessor.findAcademicRows(krsTable, isKrs = true)
        assertEquals(2, rows.size)

        val ai = rows[0]
        assertEquals("IF701", ai.code)
        assertEquals("Artificial Intelligence", ai.courseName)
        assertEquals(2, ai.theory)
        assertEquals(1, ai.practice)
        assertEquals(3, ai.credits)
        assertEquals("Tachiyya Nailal Khusna, S.Kom., M.Kom.", ai.lecturer)
        assertEquals("Kamis", ai.day)
        assertEquals(150, ai.lama)
        assertEquals("08:00", ai.start)
        assertEquals("10:30", ai.end)
        assertEquals("Kamis 08:00-10:30", ai.schedule)

        // Angka di akhir nama ("Kalkulus 2") tidak boleh tertukar dengan kolom Teori/Praktek.
        val calculus = rows[1]
        assertEquals("Kalkulus 2", calculus.courseName)
        assertEquals(3, calculus.theory)
        assertEquals(0, calculus.practice)
        assertEquals("08:00", calculus.start)
        assertEquals("09:40", calculus.end)
        assertEquals(100, calculus.lama)
    }

    @Test
    fun krsTotalRowIsReadAndNotTreatedAsCourse() {
        val rows = AcademicDocumentProcessor.findAcademicRows(krsTable, isKrs = true)
        assertTrue(rows.none { it.courseName.contains("Jumlah", ignoreCase = true) })

        val totals = AcademicDocumentProcessor.findKrsTotals(krsTable)!!
        assertEquals(5, totals.theory)
        assertEquals(1, totals.practice)
        assertEquals(6, totals.sks)
        assertEquals(rows.sumOf { it.credits }, totals.sks)
    }

    @Test
    fun krsTotalWithSingleNumberIsTotalSks() {
        val totals = AcademicDocumentProcessor.findKrsTotals("Jumlah SKS : 22")!!
        assertEquals(22, totals.sks)
        assertEquals(null, totals.theory)
    }

    @Test
    fun krsFillsMissingEndFromStartAndLama() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF703\tPemrograman Web\t2\t1\tDwi Purnomo Putro, S.Kom., M.Kom.\tSelasa\t150\t13:00",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals("13:00", rows[0].start)
        assertEquals("15:30", rows[0].end)
        assertEquals(150, rows[0].lama)
        assertEquals("Selasa 13:00-15:30", rows[0].schedule)
    }

    @Test
    fun krsFillsMissingLamaFromStartAndEnd() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF704\tBasis Data\t3\t-\tSiti Aminah, M.T.\tRabu\t10:00\t12:30",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].credits)
        assertEquals(0, rows[0].practice)
        assertEquals(150, rows[0].lama)
    }

    @Test
    fun krsJoinsCellsThatWrapOntoTheNextLine() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF701\tArtificial\t2\t1\tTachiyya Nailal Khusna,\tKamis\t150\t08:00\t10:30\n" +
                "Intelligence\tS.Kom., M.Kom.",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals("Artificial Intelligence", rows[0].courseName)
        assertEquals("Tachiyya Nailal Khusna, S.Kom., M.Kom.", rows[0].lecturer)
    }

    @Test
    fun krsWrappedLecturerDegreeOnlyLineIsAppendedToLecturer() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "IF702\tKalkulus 2\t3\t0\tJoko Suprianto,\tSenin\t100\t08:00\t09:40\nS.Kom., M.Kom.",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals("Kalkulus 2", rows[0].courseName)
        assertEquals("Joko Suprianto, S.Kom., M.Kom.", rows[0].lecturer)
    }

    @Test
    fun krsLinesAfterTotalAreNotMergedIntoLastCourse() {
        val rows = AcademicDocumentProcessor.findAcademicRows(krsTable + "\nMengetahui\tKetua Program Studi", isKrs = true)
        assertEquals(2, rows.size)
        assertEquals("Kalkulus 2", rows[1].courseName)
        assertEquals("Joko Suprianto, S.Kom., M.Kom.", rows[1].lecturer)
    }

    @Test
    fun krsStorageKeepsTheoryAndPracticeMarkerForTheKrsTable() {
        val row = AcademicDocumentProcessor.findAcademicRows(krsTable, isKrs = true)[0]
        assertEquals("[T:2, P:1] Kamis 08:00-10:30", AcademicDocumentProcessor.classTimeForStorage(row))
        val parts = AcademicDocumentProcessor.parseScheduleParts("Kamis 08:00-10:30 (Lab 2)")
        assertEquals("Kamis", parts.day)
        assertEquals(150, parts.lamaMinutes)
    }

    @Test
    fun krsEditorTimeNormalizationAcceptsDotAndColon() {
        assertEquals("08:00", AcademicDocumentProcessor.normalizeTime("8.00"))
        assertEquals("13:05", AcademicDocumentProcessor.normalizeTime("13:05 WIB"))
        assertEquals(null, AcademicDocumentProcessor.normalizeTime("25:00"))
    }

    @Test
    fun krsReadsPraktikSpellingIdenticallyToPraktek() {
        val tableWithPraktik =
            "KODE\tNAMA MATA KULIAH\tTEORI\tPRAKTIK\tDOSEN\tHARI\tLAMA\tMULAI\tSEL\n" +
                "1\tIF705\tJaringan Komputer\t2\t1\tAhmad Yani, M.Kom.\tSelasa\t150\t08:00\t10:30\n" +
                "Jumlah SKS Teori / Praktik\t2\t1\t3"

        val rows = AcademicDocumentProcessor.findAcademicRows(tableWithPraktik, isKrs = true)
        assertEquals(1, rows.size)
        assertEquals("IF705", rows[0].code)
        assertEquals(2, rows[0].theory)
        assertEquals(1, rows[0].practice)
        assertEquals(3, rows[0].credits)

        val totals = AcademicDocumentProcessor.findKrsTotals(tableWithPraktik)!!
        assertEquals(2, totals.theory)
        assertEquals(1, totals.practice)
        assertEquals(3, totals.sks)
    }

    @Test
    fun courseEntityPropertiesSupportBothPraktikAndPraktek() {
        val course1 = CourseEntity(userId = 1, code = "IF1", name = "PBO", credits = 3, gradeLetter = "-", gradePoint = 0.0, semester = 1, classTime = "[T:2, P:1] Senin 08:00-10:30 (R. 301)")
        assertEquals(2, course1.theoryCredits)
        assertEquals(1, course1.practiceCredits)
        assertEquals("Senin 08:00-10:30 (R. 301)", course1.displayClassTime)

        val course2 = CourseEntity(userId = 1, code = "IF2", name = "PBO Lanjut", credits = 3, gradeLetter = "-", gradePoint = 0.0, semester = 1, classTime = "[Teori:2, Praktek:1]")
        assertEquals(2, course2.theoryCredits)
        assertEquals(1, course2.practiceCredits)
        assertEquals("", course2.displayClassTime)

        val course3 = CourseEntity(userId = 1, code = "IF3", name = "PBO Praktik", credits = 3, gradeLetter = "-", gradePoint = 0.0, semester = 1, classTime = "[Teori:1, Praktik:2] Rabu 13:00-15:30")
        assertEquals(1, course3.theoryCredits)
        assertEquals(2, course3.practiceCredits)
        assertEquals("Rabu 13:00-15:30", course3.displayClassTime)
    }

    // ---- KRS nyata: LAMA "2 Jam 30 Menit", MULAI/SELESAI terpisah, kolom PRAKTIK/PRAKTEK ----

    private val krsSemester7 =
        "KODE\tNAMA MATA KULIAH\tTEORI\tPRAKTIK\tDOSEN\tHARI\tLAMA\tMULAI\tSELESAI\n" +
            "A02003\tKKN\t0\t3\tJoko Suprianto, S.Kom., M.Kom.\tSenin\t2 Jam 30 Menit\t19.00\t21.30\n" +
            "A02401\tEXPERT SYSTEM\t2\t1\tDwi Purnomo Putro, S.Kom.,M.Kom.\tSelasa\t2 Jam 30 Menit\t08.00\t10.30\n" +
            "A02403\tDATA MINING AND BUSINESS INTELLEGENCE\t2\t1\tTachiyya Nailal Khusna, S.Kom.,M.Kom.\tKamis\t2 Jam 30 Menit\t10.30\t13.00\n" +
            "A02404\tKepemimpinan dan Perilaku Organisasi\t2\t0\tTachiyya Nailal Khusna, S.Kom.,M.Kom.\tRabu\t2 Jam 30 Menit\t13.30\t16.00\n" +
            "A02405\tVIRTUAL SYSTEM AND CLOUD COMPUTING\t2\t1\tMokhamad Solikin, S.Kom.,M.Kom.\tSelasa\t2 Jam 30 Menit\t14.00\t16.30\n" +
            "A02406\tSkripsi\t0\t6\tTIM DOSEN FIKOM\tSabtu\t2 Jam 30 Menit\t10.00\t12.30\n" +
            "A02407\tCYBERSECURITY\t2\t1\tMokhamad Solikin, S.Kom.,M.Kom.\tKamis\t2 Jam 30 Menit\t14.00\t16.30\n" +
            "A02409\tPROPOSAL SKRIPSI\t0\t2\tTachiyya Nailal Khusna, S.Kom.,M.Kom.\tKamis\t1 Jam 40 Menit\t07.30\t09.10"

    @Test
    fun krsSemesterSevenReadsEveryRowWithDurationAndTimes() {
        val rows = AcademicDocumentProcessor.findAcademicRows(krsSemester7, isKrs = true)
        assertEquals(8, rows.size)

        val kkn = rows[0]
        assertEquals("A02003", kkn.code)
        assertEquals(0, kkn.theory)
        assertEquals(3, kkn.practice)
        assertEquals(3, kkn.credits)
        assertEquals("Joko Suprianto, S.Kom., M.Kom.", kkn.lecturer)
        assertEquals("Senin 19:00-21:30", kkn.schedule)
        assertEquals(150, kkn.lama)

        val skripsi = rows[5]
        assertEquals(6, skripsi.practice)
        assertEquals("TIM DOSEN FIKOM", skripsi.lecturer)
        assertEquals("Sabtu", skripsi.day)

        val proposal = rows[7]
        assertEquals("07:30", proposal.start)
        assertEquals("09:10", proposal.end)
        assertEquals(100, proposal.lama)

        assertTrue(rows.all { it.lecturer.isNotBlank() && it.start.isNotBlank() && it.end.isNotBlank() })
    }

    @Test
    fun krsFlatTextWithOcrLetterZeroStillReadsTheoryAndPractice() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "A02406 Skripsi O 6 TIM DOSEN FIKOM Sabtu 2 Jam 30 Menit 10.00 12.30",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals(0, rows[0].theory)
        assertEquals(6, rows[0].practice)
        assertEquals("Sabtu 10:00-12:30", rows[0].schedule)
        assertEquals(150, rows[0].lama)
    }

    @Test
    fun krsPipeTableKeepsSeparateStartAndEndColumns() {
        val rows = AcademicDocumentProcessor.findAcademicRows(
            "A02403 | DATA MINING AND BUSINESS INTELLEGENCE | 2 | 1 | Tachiyya Nailal Khusna, S.Kom.,M.Kom. | Kamis | 2 Jam 30 Menit | 10.30 | 13.00",
            isKrs = true
        )
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].theory)
        assertEquals(1, rows[0].practice)
        assertEquals("Kamis 10:30-13:00", rows[0].schedule)
        assertEquals(150, rows[0].lama)
    }

    @Test
    fun practiceTagSpellingsAreTheSameCategory() {
        fun course(tag: String) = CourseEntity(
            userId = 1, code = "A1", name = "Mata Kuliah", credits = 3, gradeLetter = "-",
            gradePoint = 0.0, semester = 1, classTime = tag
        )
        listOf("[T:2, Praktik:1] Senin", "[T:2, Praktek:1] Senin", "[T:2, P:1] Senin").forEach { tag ->
            val c = course(tag)
            assertEquals(2, c.theoryCredits)
            assertEquals(1, c.practiceCredits)
            assertEquals("Senin", c.displayClassTime)
        }
    }

    @Test
    fun krsJadwalWithCombinedTheoryPracticeCellsAndTypos() {
        val ocrSample = """
            Semester 7
            KODE	NAMA MATA KULIAH	TEORI	PRAKTEK	DOSEN	HARI	LAMA	MULAI	SELESAI
            A02003	KKN	0 3	Joko Suprianto, S.Kom., M.Kom.	Senin	2 Jam 30 Menit	19.00	21.30
            A02401	EXPERT SYSTEM	2	1	Dwi Purnomo Putro, S.Kom.,M.Kom.	Selasa	2 Jam 30 Menit	08.00	10.30
            A02403	DATA MINING AND BUSINESS INTELLEGENCE	2	1	Tachiyya Nailal Khusna, S.Kom.,M.Kom.	Kamis	2 Jam 30 Menit	10.30	13.00
            A02404	Kepemimpinan dan Perilaku Organisasi	2	0	Tachiyya Nailal Khusna, S.Kom.,M.Kom.	Rabu	2 Jam 30 Menit	13.30	16.00
            A02405	VIRTUAL SYSTEM AND CLOUD COMPUTING	2 1	Mokhamad Soikin, S.Kom.,M.Kom.	Selasa	2 Jam 30 Menit	14.00	16.30
            A02406	Skripsi	0 6	TIM DOSEN FIKOM	Sabtu	2 Jam 30 Menit	10.00	12.30
            A02407	CYBERSECURITY	2	1	Mokhamad Solkin, S.Kom., M.Kom.	Kamis	2 Jam 30 Menit	14.00	16.30
            A02409	PROPOSAL SKRIPSI	0 2	Tachiyya Nailal Khusna, S.Kom.,M.Kom.	Kamis	1 Jam 40 Menit	07.30	09.10
        """.trimIndent()

        val rows = AcademicDocumentProcessor.findAcademicRows(ocrSample, isKrs = true)
        assertEquals(8, rows.size)

        // Row 1: KKN
        assertEquals("A02003", rows[0].code)
        assertEquals("KKN", rows[0].courseName)
        assertEquals(0, rows[0].theory)
        assertEquals(3, rows[0].practice)
        assertEquals(3, rows[0].credits)
        assertEquals("Senin 19:00-21:30", rows[0].schedule)

        // Row 4: Kepemimpinan
        assertEquals("A02404", rows[3].code)
        assertEquals("Kepemimpinan dan Perilaku Organisasi", rows[3].courseName)
        assertEquals(2, rows[3].theory)
        assertEquals(0, rows[3].practice)
        assertEquals(2, rows[3].credits)
        assertEquals("Rabu 13:30-16:00", rows[3].schedule)

        // Row 5: VIRTUAL SYSTEM (corrected Soikin -> Solikin, theory 2, practice 1, credits 3)
        assertEquals("A02405", rows[4].code)
        assertEquals("VIRTUAL SYSTEM AND CLOUD COMPUTING", rows[4].courseName)
        assertEquals(2, rows[4].theory)
        assertEquals(1, rows[4].practice)
        assertEquals(3, rows[4].credits)
        assertEquals("Mokhamad Solikin, S.Kom., M.Kom.", rows[4].lecturer)
        assertEquals("Selasa 14:00-16:30", rows[4].schedule)

        // Row 6: Skripsi (theory 0, practice 6, credits 6)
        assertEquals("A02406", rows[5].code)
        assertEquals("Skripsi", rows[5].courseName)
        assertEquals(0, rows[5].theory)
        assertEquals(6, rows[5].practice)
        assertEquals(6, rows[5].credits)
        assertEquals("TIM DOSEN FIKOM", rows[5].lecturer)
        assertEquals("Sabtu 10:00-12:30", rows[5].schedule)

        // Row 7: CYBERSECURITY (corrected Solkin -> Solikin)
        assertEquals("A02407", rows[6].code)
        assertEquals("CYBERSECURITY", rows[6].courseName)
        assertEquals(2, rows[6].theory)
        assertEquals(1, rows[6].practice)
        assertEquals(3, rows[6].credits)
        assertEquals("Mokhamad Solikin, S.Kom., M.Kom.", rows[6].lecturer)
        assertEquals("Kamis 14:00-16:30", rows[6].schedule)

        // Row 8: PROPOSAL SKRIPSI (theory 0, practice 2, credits 2)
        assertEquals("A02409", rows[7].code)
        assertEquals("PROPOSAL SKRIPSI", rows[7].courseName)
        assertEquals(0, rows[7].theory)
        assertEquals(2, rows[7].practice)
        assertEquals(2, rows[7].credits)
        assertEquals("Kamis 07:30-09:10", rows[7].schedule)
    }

    @Test
    fun krsZeroAndSumTheoryPracticeRecognition() {
        // Case 1: Zero digit is directly recognized as 0 (not letter O or other numbers)
        // Case 2: OCR mistaken O or o or - is corrected to 0
        // Case 3: SKS is strictly the sum of theory + practice
        val sample = "A02410\tKAPITA SELEKTA\t0\t4\tDrs. Budi\tSenin\t10.00\t12.00\n" +
            "A02411\tSEMINAR PROPOSAL\tO\t2\tDrs. Joko\tSelasa\t08.00\t10.00\n" +
            "A02412\tTEKNOLOGI WEB\t2\t-\tDr. Dian\tRabu\t13.00\t15.00\n" +
            "A02413\tPRAKTIKUM JARINGAN\to\t3\tIr. Roni\tKamis\t09.00\t11.30"

        val rows = AcademicDocumentProcessor.findAcademicRows(sample, isKrs = true)
        assertEquals(4, rows.size)

        // Row 1: 0 theory, 4 practice -> 4 SKS (0 is recognized as 0)
        assertEquals(0, rows[0].theory)
        assertEquals(4, rows[0].practice)
        assertEquals(4, rows[0].credits)

        // Row 2: 'O' theory -> corrected to 0 theory, 2 practice -> 2 SKS
        assertEquals(0, rows[1].theory)
        assertEquals(2, rows[1].practice)
        assertEquals(2, rows[1].credits)

        // Row 3: 2 theory, '-' practice -> 0 practice -> 2 SKS
        assertEquals(2, rows[2].theory)
        assertEquals(0, rows[2].practice)
        assertEquals(2, rows[2].credits)

        // Row 4: 'o' theory -> corrected to 0 theory, 3 practice -> 3 SKS
        assertEquals(0, rows[3].theory)
        assertEquals(3, rows[3].practice)
        assertEquals(3, rows[3].credits)
    }
}
