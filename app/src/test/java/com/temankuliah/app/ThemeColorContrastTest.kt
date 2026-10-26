package com.temankuliah.app

import androidx.compose.ui.graphics.Color
import com.temankuliah.app.ui.theme.BackgroundLight
import com.temankuliah.app.ui.theme.BorderLight
import com.temankuliah.app.ui.theme.TemanKuliahBlue
import com.temankuliah.app.ui.theme.TemanKuliahBlueDark
import com.temankuliah.app.ui.theme.TemanKuliahBlueLight
import com.temankuliah.app.ui.theme.TemanKuliahGreenDark
import com.temankuliah.app.ui.theme.TemanKuliahGreenLight
import com.temankuliah.app.ui.theme.TemanKuliahOrangeLight
import com.temankuliah.app.ui.theme.TemanKuliahRedDark
import com.temankuliah.app.ui.theme.TemanKuliahRedLight
import com.temankuliah.app.ui.theme.TextSecondary
import com.temankuliah.app.ui.theme.TkInk
import com.temankuliah.app.ui.theme.TemanKuliahOrangeDark
import com.temankuliah.app.ui.theme.TextMuted
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContrastTest {
    @Test
    fun primaryBlueMeetsNormalTextContrastAgainstWhite() {
        assertTrue(contrastRatio(TemanKuliahBlue, Color.White) >= 4.5)
    }

    @Test
    fun filledOrangeControlsMeetNormalTextContrastAgainstWhite() {
        assertTrue(contrastRatio(TemanKuliahOrangeDark, Color.White) >= 4.5)
    }

    @Test
    fun mutedTextMeetsNormalTextContrastAgainstWhite() {
        assertTrue(contrastRatio(TextMuted, Color.White) >= 4.5)
    }

    @Test
    fun timeBadgeTextMeetsContrastAgainstInk() {
        assertTrue(contrastRatio(Color.White, TkInk) >= 4.5)
    }

    @Test
    fun blueTextMeetsContrastOnLightSurfaces() {
        assertTrue(contrastRatio(TemanKuliahBlueDark, TemanKuliahBlueLight) >= 4.5)
        assertTrue(contrastRatio(TemanKuliahBlue, BackgroundLight) >= 4.5)
        assertTrue(contrastRatio(Color.White, TemanKuliahBlueDark) >= 4.5)
    }

    @Test
    fun statusPairsMeetContrast() {
        assertTrue(contrastRatio(TemanKuliahGreenDark, TemanKuliahGreenLight) >= 4.5)
        assertTrue(contrastRatio(TemanKuliahRedDark, TemanKuliahRedLight) >= 4.5)
        assertTrue(contrastRatio(TemanKuliahOrangeDark, TemanKuliahOrangeLight) >= 4.5)
        assertTrue(contrastRatio(Color.White, TemanKuliahRedDark) >= 4.5)
    }

    @Test
    fun secondaryTextMeetsContrastOnPageBackground() {
        assertTrue(contrastRatio(TextSecondary, BackgroundLight) >= 4.5)
        assertTrue(contrastRatio(TextMuted, BackgroundLight) >= 4.5)
    }

    @Test
    fun fieldAndChipOutlineMeetsNonTextContrastAgainstWhite() {
        assertTrue(contrastRatio(BorderLight, Color.White) >= 3.0)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }

        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }
}
