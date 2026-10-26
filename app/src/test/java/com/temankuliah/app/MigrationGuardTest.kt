package com.temankuliah.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.ALL_MIGRATIONS
import com.temankuliah.app.data.local.TemanKuliahDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Penjaga migrasi: gagal bila versi database dinaikkan tanpa file skema atau tanpa rantai migrasi
 * dari versi 1 sampai versi sekarang.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationGuardTest {
  private val schemaDir = File("schemas/com.temankuliah.app.data.local.TemanKuliahDatabase")

  private fun currentVersion(): Int {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    return try {
      db.openHelper.writableDatabase.version
    } finally {
      db.close()
    }
  }

  @Test
  fun `schema json exists for every version up to the current one`() {
    val current = currentVersion()
    for (v in 1..current) {
      assertTrue("Skema $v.json tidak ditemukan di ${schemaDir.path}", File(schemaDir, "$v.json").isFile)
    }
  }

  @Test
  fun `migration chain covers every step from version 1 to the current version`() {
    val current = currentVersion()
    var version = 1
    while (version < current) {
      val step = ALL_MIGRATIONS.firstOrNull { it.startVersion == version }
      assertTrue("Tidak ada migrasi dari versi $version", step != null)
      assertTrue("Migrasi $version harus menaikkan versi", step!!.endVersion > version)
      version = step.endVersion
    }
    assertEquals(current, version)
  }
}
