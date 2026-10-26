package com.temankuliah.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.backup.LocalDataArchive
import com.temankuliah.app.data.local.AUTH_LOCAL
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Hash data.json dipakai auto-sync untuk melewati unggahan bila isi tidak berubah. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalDataArchiveHashTest {
    @Test
    fun payloadHashIsStableUntilDataChangesAndMatchesExportSummary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        val temp = File.createTempFile("hash_test_", ".tkbackup", context.cacheDir)
        try {
            // Akun tanpa sandi: tidak ada hash sandi berberkas garam acak yang membuat isi berubah-ubah.
            val id = database.userDao().insertUser(
                UserEntity(fullName = "Mahasiswa", nim = "", studyProgram = "Informatika", faculty = "Teknik", university = "Universitas", password = "", isLoggedIn = true, email = "mhs@example.com", authType = AUTH_LOCAL)
            )
            database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF101", name = "Dasar", credits = 3, gradeLetter = "A", gradePoint = 4.0, semester = 1))

            val first = LocalDataArchive.payloadSha256(context, database)
            val second = LocalDataArchive.payloadSha256(context, database)
            assertEquals("Data sama harus menghasilkan hash sama", first, second)
            assertEquals(64, first.length)

            // Hash dari export penuh sama dengan hash pra-cek (yang dipakai auto-sync untuk melewati unggahan).
            val summary = LocalDataArchive.exportToFile(context, database, temp)
            assertEquals(first, summary.payloadSha256)
            assertTrue(temp.length() > 0)

            database.courseDao().insertCourse(CourseEntity(userId = id, code = "IF102", name = "Lanjut", credits = 3, gradeLetter = "B", gradePoint = 3.0, semester = 1))
            val changed = LocalDataArchive.payloadSha256(context, database)
            assertNotEquals("Perubahan data harus mengubah hash", first, changed)
        } finally {
            temp.delete()
            database.close()
        }
    }
}
