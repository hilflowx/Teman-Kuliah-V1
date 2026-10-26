package com.temankuliah.app

import com.temankuliah.app.ui.AcademicSection
import com.temankuliah.app.ui.BackAction
import com.temankuliah.app.ui.DashboardSection
import com.temankuliah.app.ui.ProfileSection
import com.temankuliah.app.ui.TAB_ACADEMIC
import com.temankuliah.app.ui.TAB_HOME
import com.temankuliah.app.ui.TAB_PROFILE
import com.temankuliah.app.ui.resolveBackAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Aturan tombol back sistem: urutan dan syarat harus sama dengan perilaku sebelum rute bertipe. */
class NavigationTest {
    private val tabCalendar = 1
    private val tabTasks = 2

    private fun back(
        tab: Int = TAB_HOME,
        dashboard: DashboardSection = DashboardSection.ROOT,
        academic: AcademicSection = AcademicSection.DASHBOARD,
        profile: ProfileSection = ProfileSection.ROOT,
        showingProjects: Boolean = false
    ) = resolveBackAction(tab, dashboard, academic, profile, showingProjects)

    @Test
    fun `beranda di halaman utama tidak ditangani aplikasi`() {
        assertNull(back())
    }

    @Test
    fun `layar notifikasi di beranda ditutup dulu`() {
        assertEquals(BackAction.CLOSE_DASHBOARD_SECTION, back(dashboard = DashboardSection.NOTIFICATIONS))
    }

    @Test
    fun `halaman turunan akademik ditutup dulu`() {
        for (section in AcademicSection.entries.filter { it != AcademicSection.DASHBOARD }) {
            assertEquals(BackAction.CLOSE_ACADEMIC_SECTION, back(tab = TAB_ACADEMIC, academic = section))
        }
    }

    @Test
    fun `halaman turunan profil ditutup dulu`() {
        for (section in ProfileSection.entries.filter { it != ProfileSection.ROOT }) {
            assertEquals(BackAction.CLOSE_PROFILE_SECTION, back(tab = TAB_PROFILE, profile = section))
        }
    }

    @Test
    fun `daftar proyek ditutup sebelum kembali ke beranda`() {
        assertEquals(BackAction.CLOSE_PROJECTS, back(tab = tabTasks, showingProjects = true))
        assertEquals(BackAction.CLOSE_PROJECTS, back(showingProjects = true))
    }

    @Test
    fun `tab lain di halaman utama kembali ke beranda`() {
        assertEquals(BackAction.GO_HOME, back(tab = tabCalendar))
        assertEquals(BackAction.GO_HOME, back(tab = tabTasks))
        assertEquals(BackAction.GO_HOME, back(tab = TAB_ACADEMIC))
        assertEquals(BackAction.GO_HOME, back(tab = TAB_PROFILE))
    }

    @Test
    fun `halaman turunan tab lain tidak dihitung`() {
        assertEquals(BackAction.GO_HOME, back(tab = tabCalendar, academic = AcademicSection.KRS, profile = ProfileSection.ABOUT))
        assertNull(back(tab = TAB_HOME, academic = AcademicSection.KRS, profile = ProfileSection.ABOUT))
    }

    @Test
    fun `halaman turunan didahulukan daripada daftar proyek`() {
        assertEquals(
            BackAction.CLOSE_ACADEMIC_SECTION,
            back(tab = TAB_ACADEMIC, academic = AcademicSection.KHS, showingProjects = true)
        )
    }

    @Test
    fun `nilai awal setiap bagian adalah halaman utama`() {
        assertEquals(DashboardSection.ROOT, DashboardSection.entries.first())
        assertEquals(AcademicSection.DASHBOARD, AcademicSection.entries.first())
        assertEquals(ProfileSection.ROOT, ProfileSection.entries.first())
    }
}
