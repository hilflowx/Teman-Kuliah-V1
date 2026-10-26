package com.temankuliah.app

import androidx.compose.ui.graphics.Color
import com.temankuliah.app.ui.calendar.AgendaKind
import com.temankuliah.app.ui.calendar.agendaBadgeLabel
import com.temankuliah.app.ui.calendar.agendaCountLabel
import com.temankuliah.app.ui.calendar.agendaKindOf
import com.temankuliah.app.ui.calendar.weekRangeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class CalendarModelsTest {
    @Test
    fun `rentang minggu dalam satu bulan`() {
        assertEquals("19 – 25 Okt 2026", weekRangeLabel(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun `rentang minggu melewati batas bulan menyebut kedua bulan`() {
        assertEquals("28 Sep – 4 Okt 2026", weekRangeLabel(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `rentang minggu melewati batas tahun menyebut kedua tahun`() {
        assertEquals("28 Des 2026 – 3 Jan 2027", weekRangeLabel(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `kegiatan akademik dikelompokkan sebagai ujian tetapi lencananya tetap Kegiatan`() {
        assertEquals(AgendaKind.Kuliah, agendaKindOf("Kuliah"))
        assertEquals(AgendaKind.Tugas, agendaKindOf("Tugas"))
        assertEquals(AgendaKind.Ujian, agendaKindOf("Ujian"))
        assertEquals(AgendaKind.Ujian, agendaKindOf("Kegiatan Akademik"))
        assertEquals("Kegiatan", agendaBadgeLabel("Kegiatan Akademik"))
        assertEquals("Ujian", agendaBadgeLabel("Ujian"))
    }

    @Test
    fun `tiga jenis agenda punya warna titik berbeda`() {
        val accents: List<Color> = AgendaKind.entries.map { it.accent }
        assertEquals(accents.size, accents.toSet().size)
        assertNotEquals(AgendaKind.Kuliah.accent, AgendaKind.Tugas.accent)
    }

    @Test
    fun `keterangan jumlah agenda menyebut hari ini hanya untuk tanggal hari ini`() {
        assertEquals("0 agenda hari ini", agendaCountLabel(0, isToday = true))
        assertEquals("2 agenda", agendaCountLabel(2, isToday = false))
    }
}
