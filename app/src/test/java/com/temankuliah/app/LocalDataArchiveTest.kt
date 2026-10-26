package com.temankuliah.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.backup.LocalDataArchive
import com.temankuliah.app.data.local.AUTH_GUEST
import com.temankuliah.app.data.local.AUTH_LOCAL
import com.temankuliah.app.data.local.AcademicDocumentEntity
import com.temankuliah.app.data.local.AppStorage
import com.temankuliah.app.data.local.DATABASE_SCHEMA_VERSION
import com.temankuliah.app.data.repository.CourseRepository
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.AcademicEventEntity
import com.temankuliah.app.data.local.ProjectEntity
import com.temankuliah.app.data.local.ProjectMilestoneEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.domain.LocalPasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalDataArchiveTest {
    @Test
    fun exportPreviewAndReplaceRestoreRoundTripPreserveLocalRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        try {
            val id = database.userDao().insertUser(UserEntity(fullName = "Mahasiswa", nim = "NIM-01", studyProgram = "Informatika", faculty = "Teknik", university = "Universitas", password = "test-hash", isLoggedIn = true))
            database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF101", name = "Dasar", credits = 3, gradeLetter = "A", gradePoint = 4.0, semester = 1))
            val scheduleId = database.scheduleDao().insertSchedule(ScheduleEntity(userId = id, courseName = "Dasar", dayOfWeek = 1, startTime = "08:00", endTime = "09:00", room = "R1", lecturer = "Dosen"))
            database.scheduleDao().getById(scheduleId)!!.let { database.scheduleDao().updateSchedule(it.copy(isCompleted = true)) }
            val deadline = System.currentTimeMillis() + 86_400_000L
            val projectId = database.projectDao().insert(ProjectEntity(userId = id, semester = 1, title = "Proyek Basis Data", description = "Rancang schema", deadlineAtMillis = deadline, progress = 35))
            database.projectMilestoneDao().insert(ProjectMilestoneEntity(userId = id, projectId = projectId, title = "ERD", deadlineAtMillis = deadline))
            database.academicEventDao().insert(AcademicEventEntity(userId = id, semester = 1, title = "Ujian Tengah Semester", description = "", type = "Ujian", scheduledAtMillis = deadline))
            database.taskDao().insert(TaskEntity(userId = id, semester = 1, title = "Makalah", description = "Bab 1", dueAtMillis = deadline, projectId = projectId))
            val output = ByteArrayOutputStream()
            val exported = LocalDataArchive.export(context, database, output)
            assertEquals(1, exported.users)
            assertEquals(1, exported.courses)
            assertEquals(1, exported.tasks)
            assertEquals(1, exported.projects)
            assertEquals(1, exported.milestones)
            assertEquals(1, exported.academicEvents)

            val archive = output.toByteArray()
            val preview = LocalDataArchive.preview(context, ByteArrayInputStream(archive))
            assertEquals(1, preview.schedules)
            assertEquals(1, preview.tasks)
            assertEquals(1, preview.projects)
            assertEquals(1, preview.milestones)
            assertEquals(1, preview.academicEvents)

            database.courseDao().deleteAllCourses()
            database.taskDao().deleteAll()
            assertEquals(0, database.courseDao().getAllCoursesOnce().size)
            val restored = LocalDataArchive.restore(context, database, ByteArrayInputStream(archive))
            assertEquals(1, restored.courses)
            assertEquals(1, restored.tasks)
            assertEquals("IF101", database.courseDao().getAllCoursesOnce().first().code)
            assertEquals("Makalah", database.taskDao().getAllOnce().first().title)
            assertEquals(projectId, database.taskDao().getAllOnce().first().projectId)
            assertEquals("Proyek Basis Data", database.projectDao().getAllOnce().first().title)
            assertEquals("ERD", database.projectMilestoneDao().getAllOnce().first().title)
            assertEquals("Ujian Tengah Semester", database.academicEventDao().getAllOnce().first().title)
            assertTrue("Completed classes must remain in the schedule history", database.scheduleDao().getById(scheduleId)!!.isCompleted)
            assertTrue("The exact class date must survive backup and restore", database.scheduleDao().getById(scheduleId)!!.scheduledDate.isNotBlank())
            assertNull("Restore must not resume an authentication session from a backup", database.userDao().getActiveUser())
        } finally { database.close() }
    }

    @Test
    fun corruptBackupIsRejectedBeforeDatabaseChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        try {
            database.userDao().insertUser(UserEntity(fullName = "Stay", nim = "KEEP", studyProgram = "Prodi", faculty = "Fakultas", university = "Universitas", password = "hash", isLoggedIn = true))
            val before = database.userDao().getAllUsersOnce()
            val error = runCatching { LocalDataArchive.restore(context, database, ByteArrayInputStream("not a zip".toByteArray())) }.exceptionOrNull()
            assertTrue(error != null)
            assertEquals(before, database.userDao().getAllUsersOnce())
        } finally { database.close() }
    }

    @Test
    fun exportKeepsPasswordHashOnlyForLocalAccountsAndDropsRecoveryCode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        try {
            val hash = LocalPasswordHasher.hash("rahasia-123")
            database.userDao().insertUser(UserEntity(fullName = "Lokal", nim = "N1", studyProgram = "Prodi", faculty = "Fakultas", university = "Universitas", password = hash, email = "lokal@example.com", authType = AUTH_LOCAL))
            database.userDao().insertUser(UserEntity(fullName = "Tamu", nim = "N2", studyProgram = "Prodi", faculty = "Fakultas", university = "Universitas", password = "sisa-lama", email = "tamu@example.com", authType = AUTH_GUEST))
            val output = ByteArrayOutputStream()
            LocalDataArchive.export(context, database, output)
            LocalDataArchive.restore(context, database, ByteArrayInputStream(output.toByteArray()))

            val users = database.userDao().getAllUsersOnce().associateBy { it.email }
            assertTrue("Akun lokal harus tetap bisa masuk setelah dipulihkan", LocalPasswordHasher.verify("rahasia-123", users.getValue("lokal@example.com").password))
            assertEquals("Akun tamu tidak boleh membawa hash sandi", "", users.getValue("tamu@example.com").password)
        } finally { database.close() }
    }

    // ---------------------------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------------------------

    private fun newDatabase(context: Context) =
        Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()

    private suspend fun seedBasic(database: TemanKuliahDatabase, profileImagePath: String? = null): Long {
        val id = database.userDao().insertUser(UserEntity(fullName = "Mahasiswa", nim = "NIM-01", studyProgram = "Informatika", faculty = "Teknik", university = "Universitas", password = "test-hash", isLoggedIn = true, profileImagePath = profileImagePath))
        database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF101", name = "Dasar", credits = 3, gradeLetter = "A", gradePoint = 4.0, semester = 1))
        database.scheduleDao().insertSchedule(ScheduleEntity(userId = id, courseName = "Dasar", dayOfWeek = 1, startTime = "08:00", endTime = "09:00", room = "R1", lecturer = "Dosen"))
        return id
    }

    private suspend fun exportBytes(context: Context, database: TemanKuliahDatabase): ByteArray {
        val output = ByteArrayOutputStream()
        LocalDataArchive.export(context, database, output)
        return output.toByteArray()
    }

    private fun entryNames(archive: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) { names += entry.name; entry = zip.nextEntry }
        }
        return names
    }

    private fun readEntry(archive: ByteArray, name: String): ByteArray {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        throw AssertionError("Entri $name tidak ada di arsip")
    }

    /** Menyalin arsip dan mengubah satu entri JSON (manifest.json atau data.json) untuk mensimulasikan cadangan rusak/berbahaya. */
    private fun rewriteArchive(archive: ByteArray, jsonEntry: String, transform: (JSONObject) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zipOut ->
            ZipInputStream(ByteArrayInputStream(archive)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    var bytes = zipIn.readBytes()
                    if (entry.name == jsonEntry) {
                        val json = JSONObject(String(bytes, Charsets.UTF_8))
                        transform(json)
                        bytes = json.toString().toByteArray(Charsets.UTF_8)
                    }
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipOut.write(bytes)
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return out.toByteArray()
    }

    private fun leftoverTempFiles(context: Context): List<String> =
        context.cacheDir.list().orEmpty().filter { it.startsWith("backup_") }

    private suspend fun restoreError(context: Context, database: TemanKuliahDatabase, archive: ByteArray): Throwable? =
        runCatching { LocalDataArchive.restore(context, database, ByteArrayInputStream(archive)) }.exceptionOrNull()

    // ---------------------------------------------------------------------------------------------
    // 1. Kunci preferensi berbahaya
    // ---------------------------------------------------------------------------------------------

    @Test
    fun restoreRejectsDangerousPreferenceEntriesAndKeepsOtherPreferencesIntact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val prefs = AppStorage.getPreferences(context)
            prefs.edit().clear().putInt("target_sks", 24).putString("semester_start_date_1", "2026-02-02").commit()
            val archive = exportBytes(context, database)

            val bad = listOf(
                "target_sks" to "2026-01-01",                       // kunci lain: tipe Int tidak boleh jadi String
                "notifications_reminder_minutes" to "2026-01-01",
                "semester_start_date_" to "2026-01-01",             // sisa kunci kosong
                "semester_start_date_0" to "2026-01-01",            // < 1
                "semester_start_date_-1" to "2026-01-01",
                "semester_start_date_abc" to "2026-01-01",          // bukan integer
                "semester_start_date_01" to "2026-01-01",           // bukan bentuk kanonik
                "semester_start_date_1x" to "2026-01-01",
                "semester_start_date_1" to "not-a-date",            // nilai bukan tanggal
                "semester_start_date_1" to "2026-13-40",            // tanggal mustahil
                "semester_start_date_1" to "2026-1-5",              // bukan format ISO
                "semester_start_date_1" to ""
            )
            for ((key, value) in bad) {
                val tampered = rewriteArchive(archive, "data.json") { it.getJSONObject("preferences").put("startDates", JSONObject().put(key, value)) }
                assertTrue("'$key' = '$value' harus ditolak", restoreError(context, database, tampered) is IOException)
                assertTrue("Preview juga harus menolak '$key' = '$value'", runCatching { LocalDataArchive.preview(context, ByteArrayInputStream(tampered)) }.exceptionOrNull() is IOException)
            }
            // Nilai non-string (angka) tidak boleh dipaksa menjadi teks.
            val numeric = rewriteArchive(archive, "data.json") { it.getJSONObject("preferences").put("startDates", JSONObject().put("semester_start_date_1", 20260105)) }
            assertTrue(restoreError(context, database, numeric) is IOException)
            // startDates bukan objek.
            val notObject = rewriteArchive(archive, "data.json") { it.getJSONObject("preferences").put("startDates", "target_sks") }
            assertTrue(restoreError(context, database, notObject) is IOException)

            assertEquals("target_sks harus tetap Int", 24, prefs.getInt("target_sks", -1))
            assertEquals("2026-02-02", prefs.getString("semester_start_date_1", null))
            assertEquals("Penolakan harus terjadi sebelum data diubah", 1, database.userDao().getAllUsersOnce().size)
            assertTrue("Folder sementara harus bersih walau restore ditolak", leftoverTempFiles(context).isEmpty())
        } finally { database.close() }
    }

    @Test
    fun restoreAcceptsValidSemesterStartDatesWithoutTouchingTargetSks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val prefs = AppStorage.getPreferences(context)
            prefs.edit().clear().putInt("target_sks", 20).commit()
            val archive = exportBytes(context, database)
            val tampered = rewriteArchive(archive, "data.json") {
                it.getJSONObject("preferences").put("startDates", JSONObject().put("semester_start_date_3", "2026-08-31").put("semester_start_date_12", "2029-02-05"))
            }
            LocalDataArchive.restore(context, database, ByteArrayInputStream(tampered))
            assertEquals("2026-08-31", prefs.getString("semester_start_date_3", null))
            assertEquals("2029-02-05", prefs.getString("semester_start_date_12", null))
            assertEquals(20, prefs.getInt("target_sks", -1))
        } finally { database.close() }
    }

    @Test
    fun exportOnlyWritesWellFormedSemesterStartDates(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            AppStorage.getPreferences(context).edit().clear()
                .putString("semester_start_date_1", "2026-02-02")
                .putString("semester_start_date_abc", "2026-01-01")
                .putString("semester_start_date_2", "bukan-tanggal")
                .putString("semester_start_date_0", "2026-01-01")
                .commit()
            val archive = exportBytes(context, database)
            val startDates = JSONObject(String(readEntry(archive, "data.json"), Charsets.UTF_8)).getJSONObject("preferences").getJSONObject("startDates")
            assertEquals(setOf("semester_start_date_1"), startDates.keys().asSequence().toSet())
            // Dan hasil export tetap bisa dipulihkan.
            LocalDataArchive.restore(context, database, ByteArrayInputStream(archive))
        } finally { database.close() }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. SKS 0 bolak-balik, tanpa perbaikan diam-diam, kebijakan userId
    // ---------------------------------------------------------------------------------------------

    @Test
    fun zeroCreditsWithDashGradeSurviveExportPreviewRestoreAndRepositoryAgrees() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            val id = seedBasic(database)
            database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF201", name = "Belum Ada SKS", credits = 0, gradeLetter = "-", gradePoint = 0.0, semester = 2))
            val archive = exportBytes(context, database)
            assertEquals(2, LocalDataArchive.preview(context, ByteArrayInputStream(archive)).courses)

            database.courseDao().deleteAllCourses()
            LocalDataArchive.restore(context, database, ByteArrayInputStream(archive))
            val restored = database.courseDao().getAllCoursesOnce().first { it.code == "IF201" }
            assertEquals("SKS 0 + nilai '-' harus tetap 0, bukan dipaksa jadi 1", 0, restored.credits)
            assertEquals("-", restored.gradeLetter)

            // CourseRepository memakai aturan yang sama.
            val repository = CourseRepository(database, database.courseDao())
            repository.addCourse(CourseEntity(userId = id, code = "IF202", name = "Juga Belum", credits = 0, gradeLetter = "-", gradePoint = 0.0, semester = 2))
            val rejected = runCatching {
                repository.addCourse(CourseEntity(userId = id, code = "IF203", name = "Bernilai Tanpa SKS", credits = 0, gradeLetter = "A", gradePoint = 4.0, semester = 2))
            }.exceptionOrNull()
            assertTrue("SKS 0 dengan nilai huruf harus ditolak repository", rejected is IllegalArgumentException)
            assertTrue(runCatching {
                repository.addCourse(CourseEntity(userId = id, code = "IF204", name = "SKS Terlalu Besar", credits = 31, gradeLetter = "A", gradePoint = 4.0, semester = 2))
            }.exceptionOrNull() is IllegalArgumentException)
        } finally { database.close() }
    }

    @Test
    fun exportRejectsZeroCreditsWithLetterGradeInsteadOfRewritingIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            val id = seedBasic(database)
            database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF301", name = "Rusak", credits = 0, gradeLetter = "A", gradePoint = 4.0, semester = 3))
            val output = ByteArrayOutputStream()
            val error = runCatching { LocalDataArchive.export(context, database, output) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertTrue("Pesan harus menyebut SKS: ${error?.message}", error?.message.orEmpty().contains("SKS 0"))
            assertEquals("Tidak boleh ada arsip parsial", 0, output.size())
            assertEquals("Data di database tidak boleh diubah", 0, database.courseDao().getAllCoursesOnce().first { it.code == "IF301" }.credits)
        } finally { database.close() }
    }

    @Test
    fun restoreRejectsInvalidCoursesAndSchedulesInsteadOfFixingThemSilently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val originalCourse = database.courseDao().getAllCoursesOnce().single()
            val originalSchedule = database.scheduleDao().getAllSchedulesOnce().single()
            val archive = exportBytes(context, database)

            // SKS 0 dengan nilai huruf.
            val zeroCredits = rewriteArchive(archive, "data.json") { it.getJSONArray("courses").getJSONObject(0).put("credits", 0) }
            assertTrue(restoreError(context, database, zeroCredits)?.message.orEmpty().contains("SKS"))
            // SKS di luar 1..30 (dulu dipotong diam-diam).
            val tooMany = rewriteArchive(archive, "data.json") { it.getJSONArray("courses").getJSONObject(0).put("credits", 99) }
            assertTrue(restoreError(context, database, tooMany) is IOException)
            // Nilai huruf tidak dikenal (dulu diganti "-").
            val badGrade = rewriteArchive(archive, "data.json") { it.getJSONArray("courses").getJSONObject(0).put("gradeLetter", "Z") }
            assertTrue(restoreError(context, database, badGrade) is IOException)
            // Tanggal jadwal tidak cocok dengan hari (dulu diganti "hari ini").
            val wrongDate = rewriteArchive(archive, "data.json") {
                val schedule = it.getJSONArray("schedules").getJSONObject(0)
                schedule.put("scheduledDate", LocalDate.parse(schedule.getString("scheduledDate")).plusDays(1).toString())
            }
            assertTrue(restoreError(context, database, wrongDate)?.message.orEmpty().contains("hari"))
            // Tanggal jadwal hilang (dulu diisi otomatis).
            val noDate = rewriteArchive(archive, "data.json") { it.getJSONArray("schedules").getJSONObject(0).remove("scheduledDate") }
            assertTrue(restoreError(context, database, noDate) is IOException)
            // Jam tidak valid (dulu diganti 08:00).
            val badTime = rewriteArchive(archive, "data.json") { it.getJSONArray("schedules").getJSONObject(0).put("startTime", "25:99") }
            assertTrue(restoreError(context, database, badTime) is IOException)
            // Kode ganda pada semester yang sama (dulu diberi akhiran diam-diam).
            val duplicateCode = rewriteArchive(archive, "data.json") {
                val courses = it.getJSONArray("courses")
                val copy = JSONObject(courses.getJSONObject(0).toString()).put("id", 999)
                courses.put(copy)
            }
            assertTrue(restoreError(context, database, duplicateCode) is IOException)

            assertEquals(originalCourse, database.courseDao().getAllCoursesOnce().single())
            assertEquals(originalSchedule, database.scheduleDao().getAllSchedulesOnce().single())
        } finally { database.close() }
    }

    @Test
    fun restoreDoesNotMoveCoursesOrSchedulesToAnotherUser() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val archive = exportBytes(context, database)
            val orphanCourse = rewriteArchive(archive, "data.json") { it.getJSONArray("courses").getJSONObject(0).put("userId", 9_999) }
            val courseError = restoreError(context, database, orphanCourse)
            assertTrue(courseError is IOException)
            assertTrue("Pesan harus jelas: ${courseError?.message}", courseError?.message.orEmpty().contains("tidak dipindahkan"))
            val orphanSchedule = rewriteArchive(archive, "data.json") { it.getJSONArray("schedules").getJSONObject(0).put("userId", 9_999) }
            val scheduleError = restoreError(context, database, orphanSchedule)
            assertTrue(scheduleError is IOException)
            assertTrue(scheduleError?.message.orEmpty().contains("tidak dipindahkan"))
            // Data lama tetap utuh.
            assertEquals(1, database.courseDao().getAllCoursesOnce().size)
            assertEquals(1, database.scheduleDao().getAllSchedulesOnce().size)
        } finally { database.close() }
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Versi skema manifest
    // ---------------------------------------------------------------------------------------------

    @Test
    fun manifestUsesRealDatabaseVersionAndRestoreAcceptsOlderOrEqualOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val archive = exportBytes(context, database)
            val manifest = JSONObject(String(readEntry(archive, "manifest.json"), Charsets.UTF_8))
            assertEquals("Manifest harus memakai versi database yang sebenarnya", database.openHelper.writableDatabase.version, manifest.getInt("databaseSchemaVersion"))
            assertEquals(DATABASE_SCHEMA_VERSION, manifest.getInt("databaseSchemaVersion"))

            for (version in 1..DATABASE_SCHEMA_VERSION) {
                val older = rewriteArchive(archive, "manifest.json") { it.put("databaseSchemaVersion", version) }
                assertEquals(1, LocalDataArchive.preview(context, ByteArrayInputStream(older)).users)
            }
            val newer = rewriteArchive(archive, "manifest.json") { it.put("databaseSchemaVersion", DATABASE_SCHEMA_VERSION + 1) }
            assertTrue(restoreError(context, database, newer)?.message.orEmpty().contains("lebih baru"))
            for (invalid in listOf(0, -3)) {
                val broken = rewriteArchive(archive, "manifest.json") { it.put("databaseSchemaVersion", invalid) }
                assertTrue(restoreError(context, database, broken) is IOException)
            }
        } finally { database.close() }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Lampiran hilang -> dilewati dengan peringatan
    // ---------------------------------------------------------------------------------------------

    @Test
    fun exportSkipsDocumentsWithMissingFilesAndReportsWarnings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        val docsDir = File(context.filesDir, "academic_documents").apply { mkdirs() }
        val good = File(docsDir, "good_krs.pdf").apply { writeBytes("%PDF-1.4\n%%EOF\n".toByteArray()) }
        try {
            val id = seedBasic(database, profileImagePath = "profile_images/hilang.jpg")
            database.academicDocumentDao().insertAll(listOf(
                AcademicDocumentEntity(userId = id, semester = 1, type = "KRS", displayName = "KRS.pdf", storedPath = "academic_documents/good_krs.pdf"),
                AcademicDocumentEntity(userId = id, semester = 1, type = "KHS", displayName = "KHS.pdf", storedPath = "academic_documents/hilang_khs.pdf")
            ))
            val output = ByteArrayOutputStream()
            val summary = LocalDataArchive.export(context, database, output)
            val archive = output.toByteArray()

            assertEquals("Hanya dokumen yang filenya ada yang diekspor", 1, summary.documents)
            assertEquals(1, summary.skippedDocuments)
            assertEquals(1, summary.skippedProfileImages)
            assertEquals(1, summary.attachments)
            assertTrue(summary.hasWarnings)
            assertEquals(listOf("manifest.json", "data.json", "assets/academic_documents/good_krs.pdf"), entryNames(archive))
            val dataJson = String(readEntry(archive, "data.json"), Charsets.UTF_8)
            assertFalse("Metadata dokumen tanpa file tidak boleh ikut", dataJson.contains("hilang_khs"))
            assertEquals(1, JSONObject(dataJson).getJSONArray("documents").length())
            assertEquals(1, JSONObject(String(readEntry(archive, "manifest.json"), Charsets.UTF_8)).getInt("attachmentCount"))
            assertEquals("Database sumber tidak boleh diubah oleh export", 2, database.academicDocumentDao().getAllOnce().size)

            // Arsip hasilnya konsisten dan bisa dipulihkan.
            val restored = LocalDataArchive.restore(context, database, ByteArrayInputStream(archive))
            assertEquals(1, restored.documents)
            assertFalse(restored.hasWarnings)
            val document = database.academicDocumentDao().getAllOnce().single()
            assertTrue(File(context.filesDir, document.storedPath).isFile)
            assertNull(database.userDao().getAllUsersOnce().single().profileImagePath)
            assertTrue("Folder sementara restore harus dibersihkan", leftoverTempFiles(context).isEmpty())
        } finally {
            good.delete()
            database.close()
        }
    }

    @Test
    fun exportWithoutWarningsReportsNone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        try {
            seedBasic(database)
            val summary = LocalDataArchive.export(context, database, ByteArrayOutputStream())
            assertEquals(0, summary.skippedDocuments)
            assertEquals(0, summary.skippedProfileImages)
            assertFalse(summary.hasWarnings)
        } finally { database.close() }
    }

    // ---------------------------------------------------------------------------------------------
    // 4 & 6. Batas ukuran dan file sementara
    // ---------------------------------------------------------------------------------------------

    @Test
    fun exportFailsEarlyWithClearMessageWhenTotalExceedsLimit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        val docsDir = File(context.filesDir, "academic_documents").apply { mkdirs() }
        val big = File(docsDir, "besar.pdf").apply {
            writeBytes("%PDF-1.4\n".toByteArray() + ByteArray(200_000) { 'x'.code.toByte() })
        }
        try {
            val id = seedBasic(database)
            database.academicDocumentDao().insertAll(listOf(
                AcademicDocumentEntity(userId = id, semester = 1, type = "KRS", displayName = "Besar.pdf", storedPath = "academic_documents/besar.pdf")
            ))

            val output = ByteArrayOutputStream()
            val error = runCatching { LocalDataArchive.export(context, database, output, maxUncompressedBytes = 100_000L) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertTrue("Pesan harus menyebut batas: ${error?.message}", error?.message.orEmpty().contains("melebihi batas"))
            assertEquals("Tidak ada byte yang boleh ditulis ke tujuan", 0, output.size())
            assertTrue("File sementara export harus dibersihkan", leftoverTempFiles(context).isEmpty())

            // exportToFile gagal sebelum menulis apa pun (bukan setelah setengah jalan).
            val target = File.createTempFile("backup_early_", ".tmp", context.cacheDir)
            try {
                assertTrue(runCatching { LocalDataArchive.exportToFile(context, database, target, maxUncompressedBytes = 100_000L) }.exceptionOrNull() is IOException)
                assertEquals("Harus gagal sebelum menulis ZIP", 0L, target.length())
            } finally { target.delete() }

            // Dengan batas bawaan, data yang sama berhasil diekspor.
            val ok = LocalDataArchive.export(context, database, ByteArrayOutputStream())
            assertEquals(1, ok.documents)
            assertEquals(1, ok.attachments)
        } finally {
            big.delete()
            database.close()
        }
    }

    @Test
    fun sizeLimitsAreAlignedWithTheDocumentUploadLimit() {
        val uploadLimit = 40L * 1024L * 1024L
        assertTrue("Satu entri harus muat dokumen 40 MB", LocalDataArchive.MAX_ENTRY_BYTES >= uploadLimit)
        assertTrue("Batas total harus lebih besar dari satu dokumen maksimum", LocalDataArchive.MAX_ARCHIVE_UNCOMPRESSED > 2 * uploadLimit)
    }

    @Test
    fun exportToFileProducesReadableArchiveAndNoTempLeftovers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = newDatabase(context)
        val target = File.createTempFile("backup_file_", ".tkbackup", context.cacheDir)
        try {
            seedBasic(database)
            val summary = LocalDataArchive.exportToFile(context, database, target)
            assertEquals(1, summary.courses)
            assertTrue(target.length() > 0)
            val preview = target.inputStream().use { LocalDataArchive.preview(context, it) }
            assertEquals(1, preview.courses)
            assertEquals(1, preview.schedules)
        } finally {
            target.delete()
            database.close()
        }
        assertTrue(leftoverTempFiles(context).isEmpty())
    }
}
