package com.temankuliah.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.temankuliah.app.data.local.AUTH_LOCAL
import com.temankuliah.app.data.local.CourseEntity
import com.temankuliah.app.data.local.ScheduleEntity
import com.temankuliah.app.data.local.TaskEntity
import com.temankuliah.app.data.local.TemanKuliahDatabase
import com.temankuliah.app.data.local.UserEntity
import com.temankuliah.app.data.repository.TemanKuliahRepository
import com.temankuliah.app.ui.academic.KhsScreen
import com.temankuliah.app.ui.academic.KrsScreen
import com.temankuliah.app.ui.auth.AuthScreen
import com.temankuliah.app.ui.components.TkAlertDialog
import com.temankuliah.app.ui.courses.CoursesScreen
import com.temankuliah.app.ui.notifications.NotificationsScreen
import com.temankuliah.app.ui.schedule.CourseScheduleScreen
import com.temankuliah.app.ui.theme.BackgroundLight
import com.temankuliah.app.ui.theme.TemanKuliahTheme
import com.temankuliah.app.ui.theme.maxContentWidth
import com.temankuliah.app.ui.theme.rememberTkWidthClass
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Merender layar utama pada empat lebar (360, 411, 448, 800 dp), masing-masing pada fontScale 1.0 dan 2.0,
 * lalu memastikan (1) tidak ada crash komposisi/pengukuran dan (2) elemen kunci tiap layar ada di pohon semantik.
 *
 * Satu kelas dasar dijalankan oleh empat subclass; tiap subclass hanya menetapkan qualifier lebar layar Robolectric.
 *
 * Batasan yang disengaja:
 * - Test ini TIDAK memeriksa teks terpotong atau tumpang tindih secara visual; ia hanya menangkap crash dan elemen yang hilang.
 * - fontScale diterapkan lewat LocalDensity DI DALAM TemanKuliahTheme, karena tema itu membatasi fontScale ke 1.3x.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
abstract class AdaptiveScreensRenderBase {

    /** Lebar layar (dp) yang harus benar-benar terpasang; menjaga agar qualifier Robolectric tidak diam-diam diabaikan. */
    protected abstract val expectedWidthDp: Int

    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: TemanKuliahDatabase
    private lateinit var viewModel: TemanKuliahViewModel
    private val fontScaleState = mutableFloatStateOf(1f)
    private var observedWidthDp = -1
    private var observedFontScale = -1f

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, TemanKuliahDatabase::class.java).allowMainThreadQueries().build()
        viewModel = TemanKuliahViewModel(app, db, TemanKuliahRepository.from(db))
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    // ------------------------------------------------------------------------------------------
    // Pengujian
    // ------------------------------------------------------------------------------------------

    @Test
    fun authScreen_rendersWithKeyElements() {
        show { AuthScreen(viewModel = viewModel) }
        forEachFontScale {
            expectTexts("Selamat Datang Kembali", "Masuk", "Lupa kata sandi?")
        }
    }

    @Test
    fun appShell_mainTabsRenderWithKeyElements() {
        seedStudent()
        show { TemanKuliahApp(viewModel = viewModel) }
        // Splash tampil dulu; tunggu sampai bilah navigasi (atau rail) muncul.
        awaitText("Beranda", timeoutMs = SHELL_TIMEOUT_MS)

        val keyTextPerTab = listOf(
            0 to "Pencapaian Akademik",
            1 to "Kuliah, tugas, ujian, dan agenda",
            2 to "Proyek Kuliah",
            3 to "Teknik Informatika",
            4 to "Budi Santoso"
        )
        forEachFontScale {
            keyTextPerTab.forEach { (tab, keyText) ->
                composeRule.runOnIdle { viewModel.setTab(tab) }
                awaitText(keyText, timeoutMs = SHELL_TIMEOUT_MS)
                expectTexts("Beranda", "Kalender", "Tugas", "Akademik", "Profil")
            }
        }
    }

    @Test
    fun academicSubScreens_renderWithKeyElements() {
        seedStudent()
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
            "Kartu Hasil Studi" to { KhsScreen(viewModel = viewModel, onBack = {}) },
            "Kartu Rencana Studi" to { KrsScreen(viewModel = viewModel, onBack = {}) },
            "Transkrip nilai" to { CoursesScreen(viewModel = viewModel, onBack = {}) },
            "Jadwal kuliah" to { CourseScheduleScreen(viewModel = viewModel, onBack = {}) }
        )
        screens.forEach { (keyText, screen) ->
            showFresh(screen)
            forEachFontScale { awaitText(keyText) }
        }
    }

    @Test
    fun notificationsScreen_rendersWithKeyElements() {
        seedStudent()
        show { NotificationsScreen(viewModel = viewModel, onBack = {}, onOpenTab = {}) }
        forEachFontScale { awaitText("Notifikasi") }
    }

    @Test
    fun alertDialog_keepsActionButtonsVisibleWithLongText() {
        val longText = List(40) { "Kalimat panjang nomor $it untuk menguji isi dialog yang dapat digulir." }.joinToString(" ")
        show {
            TkAlertDialog(
                onDismissRequest = {},
                title = "Hapus semua data?",
                text = longText,
                confirmLabel = "Ya, hapus",
                dismissLabel = "Batalkan",
                isDestructive = true,
                onConfirm = {}
            )
        }
        forEachFontScale {
            awaitText("Hapus semua data?")
            // Tombol aksi harus tetap di layar walau isi sangat panjang.
            composeRule.onNodeWithText("Ya, hapus").assertIsDisplayed()
            composeRule.onNodeWithText("Batalkan").assertIsDisplayed()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pembantu
    // ------------------------------------------------------------------------------------------

    /** Memasang [content] dalam tema aplikasi dengan lebar konten dibatasi seperti di TemanKuliahApp. */
    @Suppress("ConfigurationScreenWidthHeight")
    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            TemanKuliahTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScaleState.floatValue)
                ) {
                    val configuration = LocalConfiguration.current
                    val density = LocalDensity.current
                    SideEffect {
                        observedWidthDp = configuration.screenWidthDp
                        observedFontScale = density.fontScale
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(BackgroundLight),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(Modifier.widthIn(max = rememberTkWidthClass().maxContentWidth()).fillMaxSize()) {
                            content()
                        }
                    }
                }
            }
        }
    }

    /**
     * Mengganti layar yang sedang tampil. setContent hanya boleh sekali per rule, jadi layar dipertukarkan lewat state;
     * `key` memastikan state `remember` layar sebelumnya tidak terpakai ulang oleh layar berikutnya.
     */
    private fun showFresh(content: @Composable () -> Unit) {
        if (!hostInstalled) {
            hostInstalled = true
            show { key(screenKey) { currentScreen?.invoke() } }
        }
        composeRule.runOnIdle {
            currentScreen = content
            screenKey += 1
        }
        composeRule.waitForIdle()
    }

    private var hostInstalled = false
    private var currentScreen: (@Composable () -> Unit)? by mutableStateOf(null)
    private var screenKey by mutableIntStateOf(0)

    /** Menjalankan [block] pada fontScale 1.0 lalu 2.0, setelah memastikan ukuran dan skala benar-benar terpasang. */
    private fun forEachFontScale(block: () -> Unit) {
        FONT_SCALES.forEach { scale ->
            composeRule.runOnIdle { fontScaleState.floatValue = scale }
            composeRule.waitForIdle()
            assertEquals("Lebar layar Robolectric tidak terpasang", expectedWidthDp, observedWidthDp)
            assertEquals("fontScale tidak terpasang", scale, observedFontScale, 0.001f)
            block()
        }
    }

    private fun awaitText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) { hasText(text) }
    }

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun expectTexts(vararg texts: String) {
        texts.forEach { text ->
            awaitText(text)
            check(hasText(text)) { "Elemen kunci \"$text\" tidak ditemukan" }
        }
    }

    /** Mahasiswa semester 3 yang sudah masuk, dengan beberapa mata kuliah, jadwal, dan tugas. */
    private fun seedStudent() = runBlocking {
        val userId = db.userDao().insertUser(
            UserEntity(
                fullName = "Budi Santoso",
                nim = "2201001",
                studyProgram = "Teknik Informatika",
                faculty = "Fakultas Teknik",
                university = "Universitas Contoh",
                password = "",
                isLoggedIn = true,
                activeSemester = 3,
                email = "budi@example.com",
                authType = AUTH_LOCAL
            )
        )
        db.courseDao().insertCourses(
            listOf(
                CourseEntity(
                    userId = userId, code = "IF2101", name = "Pemrograman Berorientasi Objek dan Pola Rancangan",
                    credits = 3, gradeLetter = "A", gradePoint = 4.0, semester = 2, lecturer = "Dr. Ir. Budi Santoso, M.Kom."
                ),
                CourseEntity(userId = userId, code = "IF2203", name = "Basis Data", credits = 3, gradeLetter = "B+", gradePoint = 3.5, semester = 2),
                CourseEntity(
                    userId = userId, code = "IF3101", name = "Kecerdasan Buatan", credits = 4, gradeLetter = "-", gradePoint = 0.0,
                    semester = 3, lecturer = "Prof. Siti Aminah", classTime = "Kamis 08:00-10:30"
                ),
                CourseEntity(userId = userId, code = "IF3203", name = "Jaringan Komputer", credits = 3, gradeLetter = "-", gradePoint = 0.0, semester = 3)
            )
        )
        db.scheduleDao().insertSchedule(
            ScheduleEntity(
                userId = userId,
                courseName = "Kecerdasan Buatan",
                dayOfWeek = 4,
                startTime = "08:00",
                endTime = "10:30",
                room = "Lab 3",
                lecturer = "Prof. Siti Aminah",
                semester = 3
            )
        )
        val now = System.currentTimeMillis()
        db.taskDao().insertAll(
            listOf(
                TaskEntity(userId = userId, semester = 3, title = "Laporan praktikum basis data terdistribusi", dueAtMillis = now + DAY_MILLIS * 2),
                TaskEntity(userId = userId, semester = 3, title = "Kuis 3", dueAtMillis = now + DAY_MILLIS * 4, priority = "Tinggi")
            )
        )
    }

    private companion object {
        val FONT_SCALES = listOf(1.0f, 2.0f)
        const val DAY_MILLIS = 86_400_000L
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val SHELL_TIMEOUT_MS = 20_000L
    }
}

@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class AdaptiveScreensRender360Test : AdaptiveScreensRenderBase() {
    override val expectedWidthDp = 360
}

@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AdaptiveScreensRender411Test : AdaptiveScreensRenderBase() {
    override val expectedWidthDp = 411
}

@Config(sdk = [36], qualifiers = "w448dp-h960dp-xxhdpi")
class AdaptiveScreensRender448Test : AdaptiveScreensRenderBase() {
    override val expectedWidthDp = 448
}

@Config(sdk = [36], qualifiers = "w800dp-h1280dp-xhdpi")
class AdaptiveScreensRender800Test : AdaptiveScreensRenderBase() {
    override val expectedWidthDp = 800
}
