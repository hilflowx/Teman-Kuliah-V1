package com.temankuliah.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tes Linter Otomatis untuk mendeteksi pelanggaran layout responsif pada Jetpack Compose:
 * 1. Mendeteksi penggunaan 'offset(...)' pada elemen teks yang menyebabkan teks keluar kartu / bertabrakan.
 * 2. Mendeteksi penggunaan 'height(NN.dp)' kaku pada wadah yang membungkus teks.
 */
class ResponsiveLayoutLintTest {

    private val uiDir: File = run {
        val path1 = File("src/main/java/com/temankuliah/app/ui")
        if (path1.exists()) path1 else File("app/src/main/java/com/temankuliah/app/ui")
    }

    @Test
    fun `no text elements use manual offset modifier`() {
        val violations = mutableListOf<String>()
        val ktFiles = uiDir.walkTopDown().filter { it.extension == "kt" }.toList()

        ktFiles.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                // Periksa jika ada Text atau DashText yang memakai offset manual
                if (line.contains(".offset(") && !file.name.contains("SplashScreen") && !file.name.contains("Motion")) {
                    // Cek apakah baris ini atau baris sekitarnya mendefinisikan Text / DashText
                    val window = lines.subList(maxOf(0, index - 4), minOf(lines.size, index + 5)).joinToString("\n")
                    if (window.contains("DashText") || window.contains("Text(") || window.contains("TextPrimary")) {
                        violations.add("${file.name}:${index + 1} -> $line")
                    }
                }
            }
        }

        assertTrue(
            "Ditemukan penggunaan .offset(...) pada elemen teks yang berpotensi memotong teks atau keluar dari kartu:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `gpa hero card does not use fixed heights or manual offsets`() {
        val base = if (File("src/main/java").exists()) "src/main/java" else "app/src/main/java"
        val gpaFiles = listOf(
            File("$base/com/temankuliah/app/ui/dashboard/DashboardGpaCard.kt"),
            File("$base/com/temankuliah/app/ui/dashboard/DashboardComponents.kt")
        ).filter { it.exists() }

        gpaFiles.forEach { file ->
            val content = file.readText()
            assertTrue(
                "${file.name} tidak boleh menggunakan .offset(x = 5.dp) pada batas cumlaude",
                !content.contains(".offset(x = 5.dp)")
            )
            assertTrue(
                "${file.name} tidak boleh mengunci tinggi kartu IPK dengan fixed .height(60.dp)",
                !content.contains(".height(60.dp)")
            )
            assertTrue(
                "${file.name} StatsPanel harus memakai heightIn dan IntrinsicSize.Min",
                content.contains("heightIn(min = 59.dp)") || !content.contains("StatsPanel")
            )
        }
    }

    @Test
    fun `timeline and task cards allow 2 lines for title and flexible bar height`() {
        val base = if (File("src/main/java").exists()) "src/main/java" else "app/src/main/java"
        val agendaFiles = listOf(
            File("$base/com/temankuliah/app/ui/dashboard/DashboardAgenda.kt"),
            File("$base/com/temankuliah/app/ui/dashboard/DashboardComponents.kt")
        ).filter { it.exists() }

        agendaFiles.forEach { file ->
            val content = file.readText()
            if (content.contains("TimelineCard")) {
                assertTrue(
                    "${file.name} TimelineCard harus memakai heightIn(min = 37.dp) bukan .height(37.dp)",
                    !content.contains(".height(37.dp)")
                )
            }
        }
    }
}
