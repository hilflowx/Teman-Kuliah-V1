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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Uji migrasi skema Room 1 -> 2 (BL-07):
 * Membuktikan bahwa database v1 yang dibuat dari skema resmi 1.json berhasil dimigrasi ke v2
 * menggunakan [MIGRATION_1_2] tanpa ada data yang hilang:
 * 1. Seluruh entitas dan indeks v1 terbentuk dari app/schemas/.../1.json.
 * 2. Baris jadwal yang cocok dengan nama mata kuliah KRS di semester & user yang sama otomatis ditautkan (sumber=KRS, krsId, sks).
 * 3. Baris jadwal manual tetap berstatus MANUAL, krsId null, dan sks 0.
 * 4. Seluruh data lama (jam, hari, ruang, dosen, tanggal, status selesai, pengingat) tetap utuh.
 * 5. Database hasil migrasi lulus validasi skema runtime Room [TemanKuliahDatabase].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration1To2Test {

    private val dbName = "migration_test_v1_to_v2.db"
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

    private fun createV1Database(): SupportSQLiteDatabase {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(app)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val schemaFile = File("schemas/com.temankuliah.app.data.local.TemanKuliahDatabase/1.json")
                    val json = JSONObject(schemaFile.readText())
                    val databaseObj = json.getJSONObject("database")
                    val entities = databaseObj.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val createTableSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(createTableSql)

                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                val indexObj = indices.getJSONObject(j)
                                val indexSql = indexObj.getString("createSql").replace("\${TABLE_NAME}", tableName)
                                db.execSQL(indexSql)
                            }
                        }
                    }
                    val setupQueries = databaseObj.optJSONArray("setupQueries")
                    if (setupQueries != null) {
                        for (i in 0 until setupQueries.length()) {
                            db.execSQL(setupQueries.getString(i))
                        }
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = helperFactory.create(config)
        return helper.writableDatabase
    }

    @Test
    fun `migrasi 1 ke 2 menambahkan kolom baru dan menautkan jadwal ke krs dengan benar`() {
        // 1. Buat database skema v1 dari schema json resmi
        val v1Db = createV1Database()

        // Masukkan mata kuliah KRS v1 (id = 42, userId = 1, semester = 3, 4 SKS)
        v1Db.execSQL(
            "INSERT INTO `courses` (`id`, `userId`, `code`, `name`, `credits`, `gradeLetter`, `gradePoint`, `semester`, `academicYear`, `lecturer`, `classTime`) " +
                "VALUES (42, 1, 'IF2101', 'Pemrograman Berorientasi Objek', 4, 'A', 4.0, 3, '2026/2027', 'Dr. Budi Santoso', 'Senin 08:00-11:20')"
        )

        // Jadwal 1: Cocok dengan mata kuliah KRS (userId 1, semester 3, nama sama)
        v1Db.execSQL(
            "INSERT INTO `schedules` (`id`, `userId`, `courseName`, `dayOfWeek`, `startTime`, `endTime`, `room`, `lecturer`, `type`, `semester`, `alarmEnabled`, `reminderMinutesBefore`, `scheduledDate`, `isCompleted`, `recurrenceGroupId`) " +
                "VALUES (101, 1, 'Pemrograman Berorientasi Objek', 1, '08:00', '11:20', 'Lab Komputer 3', 'Dr. Budi Santoso', 'Kuliah', 3, 1, 15, '2026-10-26', 0, 'rec_group_1')"
        )

        // Jadwal 2: Jadwal manual yang tidak ada di KRS
        v1Db.execSQL(
            "INSERT INTO `schedules` (`id`, `userId`, `courseName`, `dayOfWeek`, `startTime`, `endTime`, `room`, `lecturer`, `type`, `semester`, `alarmEnabled`, `reminderMinutesBefore`, `scheduledDate`, `isCompleted`, `recurrenceGroupId`) " +
                "VALUES (102, 1, 'Seminar Organisasi Kemahasiswaan', 5, '13:30', '15:00', 'Aula Rektorat', 'Pembicara Tamu', 'Kuliah', 3, 0, 10, '2026-10-30', 1, '')"
        )

        // Jadwal 3: User berbeda (userId 2), mata kuliah sama tapi beda user sehingga tidak boleh bocor
        v1Db.execSQL(
            "INSERT INTO `schedules` (`id`, `userId`, `courseName`, `dayOfWeek`, `startTime`, `endTime`, `room`, `lecturer`, `type`, `semester`, `alarmEnabled`, `reminderMinutesBefore`, `scheduledDate`, `isCompleted`, `recurrenceGroupId`) " +
                "VALUES (103, 2, 'Pemrograman Berorientasi Objek', 1, '08:00', '11:20', 'Lab Komputer 3', 'Dr. Budi', 'Kuliah', 3, 1, 15, '2026-10-26', 0, '')"
        )

        // Tutup database v1 pada versi 1 agar Room menjalankan migrasi saat dibuka
        v1Db.version = 1
        v1Db.close()

        // 2. Buka dengan Room TemanKuliahDatabase v2 yang meregistrasikan MIGRATION_1_2
        // Room akan mendeteksi DB versi 1, mengeksekusi MIGRATION_1_2.migrate(db),
        // memperbarui room_master_table ke identity hash v2, dan menaikkan versi SQLite ke 2.
        val roomDb = Room.databaseBuilder(app, TemanKuliahDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val helper = roomDb.openHelper.readableDatabase
            assertEquals(3, helper.version)

            // Periksa Jadwal 1 (KRS match)
            val cursor1 = helper.query("SELECT * FROM `schedules` WHERE `id` = 101")
            assertTrue(cursor1.moveToFirst())
            assertEquals("Pemrograman Berorientasi Objek", cursor1.getString(cursor1.getColumnIndexOrThrow("courseName")))
            assertEquals("08:00", cursor1.getString(cursor1.getColumnIndexOrThrow("startTime")))
            assertEquals("11:20", cursor1.getString(cursor1.getColumnIndexOrThrow("endTime")))
            assertEquals("Lab Komputer 3", cursor1.getString(cursor1.getColumnIndexOrThrow("room")))
            assertEquals("KRS", cursor1.getString(cursor1.getColumnIndexOrThrow("sumber")))
            assertEquals(42L, cursor1.getLong(cursor1.getColumnIndexOrThrow("krsId")))
            assertEquals(4, cursor1.getInt(cursor1.getColumnIndexOrThrow("sks")))
            assertEquals("OFFLINE", cursor1.getString(cursor1.getColumnIndexOrThrow("mode")))
            assertEquals(1, cursor1.getInt(cursor1.getColumnIndexOrThrow("alarmEnabled")))
            assertEquals(0, cursor1.getInt(cursor1.getColumnIndexOrThrow("isCompleted")))
            assertEquals("rec_group_1", cursor1.getString(cursor1.getColumnIndexOrThrow("recurrenceGroupId")))
            cursor1.close()

            // Periksa Jadwal 2 (Manual match)
            val cursor2 = helper.query("SELECT * FROM `schedules` WHERE `id` = 102")
            assertTrue(cursor2.moveToFirst())
            assertEquals("Seminar Organisasi Kemahasiswaan", cursor2.getString(cursor2.getColumnIndexOrThrow("courseName")))
            assertEquals("MANUAL", cursor2.getString(cursor2.getColumnIndexOrThrow("sumber")))
            assertTrue(cursor2.isNull(cursor2.getColumnIndexOrThrow("krsId")))
            assertEquals(0, cursor2.getInt(cursor2.getColumnIndexOrThrow("sks")))
            assertEquals("OFFLINE", cursor2.getString(cursor2.getColumnIndexOrThrow("mode")))
            assertEquals(1, cursor2.getInt(cursor2.getColumnIndexOrThrow("isCompleted")))
            cursor2.close()

            // Periksa Jadwal 3 (User 2, tidak boleh tertaut ke matkul user 1)
            val cursor3 = helper.query("SELECT * FROM `schedules` WHERE `id` = 103")
            assertTrue(cursor3.moveToFirst())
            assertEquals("MANUAL", cursor3.getString(cursor3.getColumnIndexOrThrow("sumber")))
            assertTrue(cursor3.isNull(cursor3.getColumnIndexOrThrow("krsId")))
            assertEquals(0, cursor3.getInt(cursor3.getColumnIndexOrThrow("sks")))
            cursor3.close()

            // Periksa data courses tetap utuh
            val courseCursor = helper.query("SELECT * FROM `courses` WHERE `id` = 42")
            assertTrue(courseCursor.moveToFirst())
            assertEquals("IF2101", courseCursor.getString(courseCursor.getColumnIndexOrThrow("code")))
            assertEquals("Pemrograman Berorientasi Objek", courseCursor.getString(courseCursor.getColumnIndexOrThrow("name")))
            assertEquals(4, courseCursor.getInt(courseCursor.getColumnIndexOrThrow("credits")))
            courseCursor.close()

            // Validasi melalui DAO Room tingkat tinggi
            val scheduleEntities = kotlinx.coroutines.runBlocking {
                roomDb.scheduleDao().getSchedulesByUserAndSemester(1L, 3)
            }
            val schedule1 = scheduleEntities.firstOrNull { it.id == 101L }
            assertTrue("Jadwal 101 harus ditemukan via DAO", schedule1 != null)
            assertEquals("KRS", schedule1?.sumber)
            assertEquals(42L, schedule1?.krsId)
            assertEquals(4, schedule1?.sks)
            assertEquals("OFFLINE", schedule1?.mode)
            assertEquals("Lab Komputer 3", schedule1?.room)
            assertEquals("Dr. Budi Santoso", schedule1?.lecturer)
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `migrasi 1 ke 2 menangani whitespace dan case insensitive pada penautan krs serta multi jadwal`() {
        val v1Db = createV1Database()

        // Kursus KRS dengan spasi di awal/akhir dan huruf kapital acak
        v1Db.execSQL(
            "INSERT INTO `courses` (`id`, `userId`, `code`, `name`, `credits`, `gradeLetter`, `gradePoint`, `semester`, `academicYear`, `lecturer`, `classTime`) " +
                "VALUES (88, 5, 'CS301', ' Sistem Operasi ', 3, 'B+', 3.5, 4, '2026/2027', 'Prof. Wijaya', 'Kamis 10:00-12:30')"
        )

        // Jadwal sesi teori: huruf kecil tanpa spasi berlebih
        v1Db.execSQL(
            "INSERT INTO `schedules` (`id`, `userId`, `courseName`, `dayOfWeek`, `startTime`, `endTime`, `room`, `lecturer`, `type`, `semester`, `alarmEnabled`, `reminderMinutesBefore`, `scheduledDate`, `isCompleted`, `recurrenceGroupId`) " +
                "VALUES (201, 5, 'sistem operasi', 4, '10:00', '11:40', 'R. 402', 'Prof. Wijaya', 'Kuliah', 4, 1, 15, '2026-10-29', 0, 'group_os')"
        )

        // Jadwal sesi praktikum: huruf besar semua dengan spasi acak
        v1Db.execSQL(
            "INSERT INTO `schedules` (`id`, `userId`, `courseName`, `dayOfWeek`, `startTime`, `endTime`, `room`, `lecturer`, `type`, `semester`, `alarmEnabled`, `reminderMinutesBefore`, `scheduledDate`, `isCompleted`, `recurrenceGroupId`) " +
                "VALUES (202, 5, ' SISTEM OPERASI ', 5, '13:00', '15:00', 'Lab OS', 'Asisten Lab', 'Praktikum', 4, 1, 30, '2026-10-30', 0, 'group_os')"
        )

        v1Db.version = 1
        v1Db.close()

        val roomDb = Room.databaseBuilder(app, TemanKuliahDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val helper = roomDb.openHelper.readableDatabase
            assertEquals(3, helper.version)

            val schedules = kotlinx.coroutines.runBlocking {
                roomDb.scheduleDao().getSchedulesByUserAndSemester(5L, 4)
            }
            assertEquals(2, schedules.size)

            val teori = schedules.firstOrNull { it.id == 201L }
            val praktikum = schedules.firstOrNull { it.id == 202L }

            assertTrue("Teori harus ditemukan", teori != null)
            assertEquals("KRS", teori?.sumber)
            assertEquals(88L, teori?.krsId)
            assertEquals(3, teori?.sks)

            assertTrue("Praktikum harus ditemukan", praktikum != null)
            assertEquals("KRS", praktikum?.sumber)
            assertEquals(88L, praktikum?.krsId)
            assertEquals(3, praktikum?.sks)
        } finally {
            roomDb.close()
        }
    }
}
