package com.temankuliah.app

import com.temankuliah.app.ui.splash.SplashGate
import com.temankuliah.app.ui.splash.SplashMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashMotionTest {

    @Test
    fun visibleWindow_isBetween1800And3000Ms() {
        assertEquals(1_800L, SplashMotion.MIN_VISIBLE_MS)
        assertEquals(3_000L, SplashMotion.MAX_VISIBLE_MS)
        assertTrue(SplashMotion.MIN_VISIBLE_MS < SplashMotion.MAX_VISIBLE_MS)
    }

    @Test
    fun entranceTimeline_followsSpecOrderAndFitsAbout1200Ms() {
        val m = SplashMotion
        assertTrue(m.ICON_DELAY_MS < m.HALO_INNER_DELAY_MS)
        assertTrue(m.HALO_INNER_DELAY_MS < m.TITLE_DELAY_MS)
        assertTrue(m.TITLE_DELAY_MS < m.FOOTER_DELAY_MS)
        assertEquals(120L, m.HALO_OUTER_DELAY_MS - m.HALO_INNER_DELAY_MS)

        val entranceEnd = maxOf(
            m.BG_FADE_DELAY_MS + m.BG_FADE_MS,
            m.ICON_DELAY_MS + 600,
            m.HALO_OUTER_DELAY_MS + m.HALO_EXPAND_MS,
            m.TITLE_DELAY_MS + m.SUBTITLE_STAGGER_MS + m.TITLE_MS,
            m.FOOTER_DELAY_MS + m.FOOTER_FADE_MS
        )
        assertTrue("Masuk harus selesai sekitar 1,2 detik, bukan $entranceEnd ms", entranceEnd in 1_000L..1_250L)
    }

    @Test
    fun exitTransition_isAbout350Ms() {
        assertEquals(350L, SplashMotion.EXIT_TOTAL_MS)
        assertTrue(SplashMotion.EXIT_TEXT_MS <= SplashMotion.EXIT_TOTAL_MS)
        assertTrue(SplashMotion.EXIT_ICON_MS <= SplashMotion.EXIT_TOTAL_MS)
    }

    @Test
    fun barFill_finishesAndExitsBeforeMaxDuration() {
        val barEnd = SplashMotion.BAR_FILL_DELAY_MS + SplashMotion.BAR_FILL_MS
        assertTrue(barEnd >= SplashMotion.MIN_VISIBLE_MS)
        assertTrue(barEnd + SplashMotion.EXIT_TOTAL_MS <= SplashMotion.MAX_VISIBLE_MS)
    }

    @Test
    fun pulse_isOneToOnePointZeroFourOverTwoSeconds() {
        assertEquals(2_000, SplashMotion.HALO_PULSE_CYCLE_MS)
        assertEquals(1.04f, SplashMotion.HALO_PULSE_SCALE, 0f)
    }

    @Test
    fun gate_remainingMinimum_neverNegative() {
        assertEquals(1_800L, SplashGate.remainingMinimumMs(0))
        assertEquals(300L, SplashGate.remainingMinimumMs(1_500))
        assertEquals(0L, SplashGate.remainingMinimumMs(2_200))
        assertEquals(0L, SplashGate.remainingMaximumMs(5_000))
        assertEquals(1_000L, SplashGate.remainingMaximumMs(2_000))
    }

    @Test
    fun gate_holdsBarAtNinetyPercentOnlyWhileDataNotReady() {
        assertTrue(SplashGate.shouldHoldBar(0.9f, dataReady = false))
        assertTrue(SplashGate.shouldHoldBar(0.95f, dataReady = false))
        assertFalse(SplashGate.shouldHoldBar(0.5f, dataReady = false))
        assertFalse(SplashGate.shouldHoldBar(0.9f, dataReady = true))
    }

    @Test
    fun easeInOut_hasFixedEndpointsAndSymmetricMidpoint() {
        val e = SplashMotion.EaseInOutStandard
        assertEquals(0f, e.transform(0f), 1e-4f)
        assertEquals(1f, e.transform(1f), 1e-4f)
        assertEquals(0.5f, e.transform(0.5f), 1e-2f)
    }
}
