package com.temankuliah.app

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.files.AcademicDocumentProcessor
import com.temankuliah.app.data.files.AcademicDocumentProcessor.AcademicRow
import com.temankuliah.app.data.files.StagedAcademicDocument
import com.temankuliah.app.data.files.StudentFileStore
import com.temankuliah.app.data.local.AcademicDocumentEntity
import com.temankuliah.app.data.local.AppStorage
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.ui.FeatureContext
import com.temankuliah.app.ui.academic.documents.DocumentController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Menyajikan file dari cacheDir sebagai content:// supaya `getType`, `query` nama file, dan `openInputStream` berjalan nyata. */
class StagingTestDocumentProvider : ContentProvider() {
  override fun onCreate(): Boolean = true
  override fun getType(uri: Uri): String = "text/plain"
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
    MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf<Any?>(uri.lastPathSegment)) }
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
    ParcelFileDescriptor.open(File(context!!.cacheDir, uri.lastPathSegment!!), ParcelFileDescriptor.MODE_READ_ONLY)
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/**
 * Dokumen KRS/KHS lama tidak boleh diganti/dihapus saat file baru baru dipilih. File baru berstatus draf;
 * penggantian terjadi setelah impor dikonfirmasi, dan pembatalan hanya membuang draf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AcademicDocumentStagingTest {
  private lateinit var app: Application
  private lateinit var db: TemanKuliahDatabase
  private lateinit var scope: CoroutineScope
  private lateinit var controller: DocumentController

  private val user = UserEntity(id = 1, fullName = "Tester", nim = "1", studyProgram = "TI", faculty = "FIK", university = "USP", password = "")

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    Robolectric.setupContentProvider(StagingTestDocumentProvider::class.java, AUTHORITY)
    db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    controller = DocumentController(
      FeatureContext(
        application = app, database = db, repository = TemanKuliahRepository.from(db), scope = scope,
        currentUser = MutableStateFlow(user), activeSemester = MutableStateFlow(1),
        snackbar = MutableStateFlow(null), profileEditError = MutableStateFlow(null), closeDialog = {}
      )
    ) { _, _ -> }
    AppStorage.getPreferences(app).edit().clear().commit()
  }

  @After
  fun tearDown() {
    scope.cancel()
    db.close()
  }

  private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

  private fun fileOf(relative: String) = File(app.filesDir, relative)

  private fun draftFiles(): List<File> = File(app.filesDir, "academic_drafts").listFiles().orEmpty().toList()

  private fun documentFiles(): List<String> = File(app.filesDir, "academic_documents").listFiles().orEmpty().map { it.name }.sorted()

  private fun pickFile(name: String, content: String): Uri {
    File(app.cacheDir, name).writeText(content)
    return Uri.parse("content://$AUTHORITY/$name")
  }

  /** KRS semester 1 yang sudah tersimpan, lengkap dengan filenya. */
  private fun seedOldDocument(): AcademicDocumentEntity {
    val directory = File(app.filesDir, "academic_documents").apply { mkdirs() }
    File(directory, "old_krs.txt").writeText("lama")
    val row = AcademicDocumentEntity(
      userId = 1, semester = 1, type = "KRS", displayName = "lama.txt",
      storedPath = "academic_documents/old_krs.txt", extractedText = "lama"
    )
    return row.copy(id = runBlocking { db.academicDocumentDao().upsert(row) })
  }

  private fun stage(name: String = "baru.txt", content: String = "baru"): StagedAcademicDocument = await {
    StudentFileStore.stageAcademicDocument(app, app.contentResolver, 1, 1, "KRS", pickFile(name, content), name)
  }

  private fun currentRow(): AcademicDocumentEntity? = runBlocking { db.academicDocumentDao().find(1, 1, "KRS") }

  private fun assertOldDocumentIntact(old: AcademicDocumentEntity) {
    val row = currentRow()
    assertNotNull(row)
    assertEquals(old.id, row!!.id)
    assertEquals(old.storedPath, row.storedPath)
    assertEquals("lama", row.extractedText)
    assertEquals("lama", fileOf(old.storedPath).readText())
  }

  @Test
  fun `staging a picked file keeps the old document and its file untouched`() {
    val old = seedOldDocument()

    val staged = stage()

    assertOldDocumentIntact(old)
    assertEquals("baru", fileOf(staged.draftPath).readText())
    assertTrue(staged.draftPath.startsWith("academic_drafts/"))
    assertFalse("File final belum boleh ada sebelum dikonfirmasi", fileOf(staged.finalPath).exists())
    assertEquals(listOf("old_krs.txt"), documentFiles())
    assertEquals("baru", staged.extractedText)

    await { StudentFileStore.discardStagedAcademicDocument(app, staged) }
  }

  @Test
  fun `committing the draft replaces the old document and deletes the old file`() {
    val old = seedOldDocument()
    val staged = stage()

    val saved = await { StudentFileStore.commitStagedAcademicDocument(app, db.academicDocumentDao(), staged) }

    assertEquals(old.id, saved.id)
    assertEquals(staged.finalPath, saved.storedPath)
    val row = currentRow()!!
    assertEquals(staged.finalPath, row.storedPath)
    assertEquals("baru", row.extractedText)
    assertEquals("baru", fileOf(staged.finalPath).readText())
    assertFalse("File dokumen lama harus dihapus setelah konfirmasi", fileOf(old.storedPath).exists())
    assertFalse("Draf harus sudah dipindahkan", fileOf(staged.draftPath).exists())
    assertEquals(1, runBlocking { db.academicDocumentDao().getAllOnce() }.size)
  }

  @Test
  fun `committing without an existing document inserts a new row`() {
    val staged = stage()

    val saved = await { StudentFileStore.commitStagedAcademicDocument(app, db.academicDocumentDao(), staged) }

    assertTrue(saved.id > 0)
    assertEquals(staged.finalPath, currentRow()!!.storedPath)
    assertTrue(fileOf(staged.finalPath).isFile)
  }

  @Test
  fun `discarding the draft removes it and leaves the old document alone`() {
    val old = seedOldDocument()
    val staged = stage()

    await { StudentFileStore.discardStagedAcademicDocument(app, staged) }

    assertFalse(fileOf(staged.draftPath).exists())
    assertTrue(draftFiles().isEmpty())
    assertOldDocumentIntact(old)
    assertEquals(listOf("old_krs.txt"), documentFiles())
  }

  @Test
  fun `committing a draft that disappeared fails and keeps the old document`() {
    val old = seedOldDocument()
    val staged = stage()
    assertTrue(fileOf(staged.draftPath).delete())

    try {
      await { StudentFileStore.commitStagedAcademicDocument(app, db.academicDocumentDao(), staged) }
      fail("Commit harus gagal bila draf sudah hilang")
    } catch (expected: IOException) {
      // diharapkan
    }

    assertOldDocumentIntact(old)
    await { StudentFileStore.discardStagedAcademicDocument(app, staged) }
  }

  @Test
  fun `staging an empty file fails, leaves no draft and keeps the old document`() {
    val old = seedOldDocument()

    try {
      await { StudentFileStore.stageAcademicDocument(app, app.contentResolver, 1, 1, "KRS", pickFile("kosong.txt", ""), "kosong.txt") }
      fail("File kosong harus ditolak")
    } catch (expected: IllegalArgumentException) {
      // diharapkan
    }

    assertTrue(draftFiles().isEmpty())
    assertOldDocumentIntact(old)
  }

  @Test
  fun `cleanupOrphans keeps an active draft but removes stale drafts and orphans`() {
    val old = seedOldDocument()
    val staged = stage()
    File(app.filesDir, "academic_drafts").mkdirs()
    val staleDraft = File(app.filesDir, "academic_drafts/sisa_proses_lama.txt").apply { writeText("sisa") }
    val orphan = File(app.filesDir, "academic_documents/yatim.txt").apply { writeText("yatim") }

    await { StudentFileStore.cleanupOrphans(app, db) }

    assertTrue("Draf yang sedang dipakai tidak boleh terhapus", fileOf(staged.draftPath).isFile)
    assertEquals("baru", fileOf(staged.draftPath).readText())
    assertFalse("Draf sisa proses lama harus dibersihkan", staleDraft.exists())
    assertFalse("File yatim harus dibersihkan", orphan.exists())
    assertOldDocumentIntact(old)

    // Draf yang sama masih bisa dikonfirmasi setelah cleanup, dan hasilnya tidak dihapus cleanup berikutnya.
    await { StudentFileStore.commitStagedAcademicDocument(app, db.academicDocumentDao(), staged) }
    await { StudentFileStore.cleanupOrphans(app, db) }
    assertEquals("baru", fileOf(staged.finalPath).readText())
    assertEquals(staged.finalPath, currentRow()!!.storedPath)
  }

  @Test
  fun `cleanupOrphans removes a draft once it is discarded`() {
    val staged = stage()
    await { StudentFileStore.discardStagedAcademicDocument(app, staged) }
    // Simulasi sisa file yang jalurnya sama dengan draf yang sudah dibuang (tidak lagi terdaftar).
    File(app.filesDir, "academic_drafts").mkdirs()
    fileOf(staged.draftPath).writeText("sisa")

    await { StudentFileStore.cleanupOrphans(app, db) }

    assertFalse(fileOf(staged.draftPath).exists())
  }

  @Test
  fun `camera photo is staged without touching the old document`() {
    val old = seedOldDocument()
    val photo = File(app.cacheDir, "capture.jpg").apply { writeText("foto") }

    val staged = await {
      StudentFileStore.stageAcademicPhoto(app, 1, 1, "KRS", photo, "Foto Kamera KRS Semester 1.jpg") {
        AcademicDocumentProcessor.Extraction("teks ocr", "ok")
      }
    }

    assertOldDocumentIntact(old)
    assertEquals("teks ocr", staged.extractedText)
    assertEquals("foto", fileOf(staged.draftPath).readText())
    assertFalse("Foto sementara dihapus setelah draf dibuat", photo.exists())
    assertEquals(listOf("old_krs.txt"), documentFiles())

    val saved = await { StudentFileStore.commitStagedAcademicDocument(app, db.academicDocumentDao(), staged) }
    assertEquals(old.id, saved.id)
    assertFalse(fileOf(old.storedPath).exists())
    assertEquals("foto", fileOf(staged.finalPath).readText())
  }

  @Test
  fun `camera staging that fails leaves no draft and keeps both the photo and the old document`() {
    val old = seedOldDocument()
    val photo = File(app.cacheDir, "capture.jpg").apply { writeText("foto") }

    try {
      await {
        StudentFileStore.stageAcademicPhoto(app, 1, 1, "KRS", photo, "Foto Kamera KRS Semester 1.jpg") {
          throw IOException("OCR gagal")
        }
      }
      fail("Staging harus gagal bila ekstraksi gagal")
    } catch (expected: IOException) {
      // diharapkan
    }

    assertTrue(draftFiles().isEmpty())
    assertTrue(photo.exists())
    assertOldDocumentIntact(old)
  }

  // --- Lewat DocumentController: pilih file -> pratinjau -> batal / konfirmasi ---

  private fun previewFromPickedFile(): StagedAcademicDocument {
    controller.processAcademicDocumentUri(pickFile("krs-baru.txt", "baru"), semester = 1, type = "KRS")
    val preview = await { controller.documentImportPreview.first { it != null } }!!
    return preview.stagedDocument!!
  }

  @Test
  fun `picking a file shows a preview without replacing the old document`() {
    val old = seedOldDocument()

    val staged = previewFromPickedFile()

    assertTrue(fileOf(staged.draftPath).isFile)
    assertOldDocumentIntact(old)
    assertEquals(listOf("old_krs.txt"), documentFiles())
  }

  @Test
  fun `cancelling the preview deletes the draft and keeps the old document`() {
    val old = seedOldDocument()
    val staged = previewFromPickedFile()

    controller.dismissDocumentPreview()

    assertNull(controller.documentImportPreview.value)
    await { while (fileOf(staged.draftPath).exists()) delay(20) }
    assertTrue(draftFiles().isEmpty())
    assertOldDocumentIntact(old)
    assertEquals(listOf("old_krs.txt"), documentFiles())
  }

  @Test
  fun `confirming the import replaces the old document and deletes its file`() {
    val old = seedOldDocument()
    val staged = previewFromPickedFile()
    controller.addPreviewRow(AcademicRow("Algoritma", 3, "-", 1, "IF1", lecturer = "Dr. A", schedule = "Senin 08:00-09:40"))

    controller.savePreviewCourses(replaceExisting = false)
    await { controller.documentImportPreview.first { it == null } }

    val row = currentRow()!!
    assertEquals(old.id, row.id)
    assertEquals(staged.finalPath, row.storedPath)
    assertEquals("baru", fileOf(staged.finalPath).readText())
    assertFalse(fileOf(old.storedPath).exists())
    assertTrue(draftFiles().isEmpty())
  }

  @Test
  fun `picking another file discards the previous draft`() {
    val old = seedOldDocument()
    val first = previewFromPickedFile()

    controller.processAcademicDocumentUri(pickFile("krs-kedua.txt", "kedua"), semester = 1, type = "KRS")
    val second = await { controller.documentImportPreview.first { it?.stagedDocument?.draftPath != first.draftPath } }!!.stagedDocument!!

    await { while (fileOf(first.draftPath).exists()) delay(20) }
    assertTrue(fileOf(second.draftPath).isFile)
    assertEquals(listOf(second.draftPath.substringAfterLast('/')), draftFiles().map { it.name })
    assertOldDocumentIntact(old)
  }

  private companion object {
    const val AUTHORITY = "com.temankuliah.app.test.documents"
  }
}
