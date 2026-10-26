package com.temankuliah.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Uji migrasi skema Room 2 -> 3 (BL-12): kolom `recoveryCodeHash` dihapus dari `users` lewat pembuatan ulang tabel.
 * Database v2 dibangun dari skema resmi 2.json, lalu dibuka dengan Room v3 memakai [MIGRATION_2_3].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration2To3Test {

    private val dbName = "migration_test_v2_to_v3.db"
    private lateinit var app: Application
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        dbFile = app.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) dbFile.delete()
    }

    private fun createV2Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(app)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val schemaFile = File("schemas/com.temankuliah.app.data.local.TemanKuliahDatabase/2.json")
                    val databaseObj = JSONObject(schemaFile.readText()).getJSONObject("database")
                    val entities = databaseObj.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                            }
                        }
                    }
                    val setupQueries = databaseObj.optJSONArray("setupQueries")
                    if (setupQueries != null) {
                        for (i in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(i))
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migrasi 2 ke 3 membuang recoveryCodeHash dan mempertahankan semua data akun`() {
        val v2Db = createV2Database()
        v2Db.execSQL(
            "INSERT INTO `users` (`id`, `fullName`, `nim`, `studyProgram`, `faculty`, `university`, `password`, `isLoggedIn`, " +
                "`createdAt`, `activeSemester`, `profileImagePath`, `recoveryCodeHash`, `email`, `authType`, `semesterCount`) " +
                "VALUES (7, 'Guys', '2024001', 'Teknik Informatika', 'Teknik', 'Universitas Safin Pati', 'pbkdf2-sha256\$abc', 1, " +
                "1700000000000, 3, 'profile_images/a.jpg', 'pbkdf2-sha256\$lama', 'guys@example.com', 'local', 10)"
        )
        v2Db.execSQL(
            "INSERT INTO `users` (`id`, `fullName`, `nim`, `studyProgram`, `faculty`, `university`, `password`, `isLoggedIn`, " +
                "`createdAt`, `activeSemester`, `profileImagePath`, `recoveryCodeHash`, `email`, `authType`, `semesterCount`) " +
                "VALUES (9, 'Tamu', '', '', '', '', '', 0, 1700000001000, 1, NULL, NULL, '', 'guest', 8)"
        )
        // Baris dengan id lebih tinggi lalu dihapus: nomor AUTOINCREMENT tidak boleh mundur setelah migrasi.
        v2Db.execSQL(
            "INSERT INTO `users` (`id`, `fullName`, `nim`, `studyProgram`, `faculty`, `university`, `password`, `isLoggedIn`, " +
                "`createdAt`, `activeSemester`, `email`, `authType`, `semesterCount`) " +
                "VALUES (50, 'Sementara', '', '', '', '', '', 0, 1700000002000, 1, '', 'guest', 8)"
        )
        v2Db.execSQL("DELETE FROM `users` WHERE `id` = 50")
        v2Db.version = 2
        v2Db.close()

        val roomDb = Room.databaseBuilder(app, TemanKuliahDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val db = roomDb.openHelper.writableDatabase
            assertEquals(3, db.version)

            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info(`users`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertFalse("Kolom recoveryCodeHash harus hilang", "recoveryCodeHash" in columns)
            assertEquals(14, columns.size)

            val indexes = mutableListOf<String>()
            db.query("PRAGMA index_list(`users`)").use { c ->
                while (c.moveToNext()) indexes += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertTrue("index_users_nim" in indexes)
            assertTrue("index_users_email" in indexes)

            val users = kotlinx.coroutines.runBlocking { roomDb.userDao().getAllUsersOnce() }.associateBy { it.id }
            assertEquals(2, users.size)
            val guys = users.getValue(7L)
            assertEquals("Guys", guys.fullName)
            assertEquals("2024001", guys.nim)
            assertEquals("pbkdf2-sha256\$abc", guys.password)
            assertTrue(guys.isLoggedIn)
            assertEquals(1700000000000L, guys.createdAt)
            assertEquals(3, guys.activeSemester)
            assertEquals("profile_images/a.jpg", guys.profileImagePath)
            assertEquals("guys@example.com", guys.email)
            assertEquals(AUTH_LOCAL, guys.authType)
            assertEquals(10, guys.semesterCount)
            assertEquals(AUTH_GUEST, users.getValue(9L).authType)

            val newId = kotlinx.coroutines.runBlocking {
                roomDb.userDao().insertUser(UserEntity(fullName = "Baru", nim = "", studyProgram = "", faculty = "", university = "", password = ""))
            }
            assertTrue("Id baru tidak boleh memakai ulang id lama ($newId)", newId > 50L)
        } finally {
            roomDb.close()
        }
    }
}
