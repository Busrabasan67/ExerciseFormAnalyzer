package com.example.exerciseformanalyzer.util

import com.example.exerciseformanalyzer.model.Landmark
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * AngleUtils için birim testler — mock landmark verisi ile doğrulama.
 */
class AngleUtilsTest {

    private val DELTA = 0.5f  // Kabul edilebilir açı toleransı (derece)

    // ─── calculateAngle testleri ─────────────────────────────────────────────

    @Test
    fun `duz hat - 180 derece`() {
        // A–B–C aynı çizgide olduğunda açı 180° olmalı
        val a = Landmark(0f, 0f, 0f)
        val b = Landmark(1f, 0f, 0f)
        val c = Landmark(2f, 0f, 0f)
        val angle = AngleUtils.calculateAngle(a, b, c)
        assertEquals(180f, angle, DELTA)
    }

    @Test
    fun `dik aci - 90 derece`() {
        // L şeklinde 3 nokta — 90° açı vermeli
        val a = Landmark(0f, 1f, 0f)   // üstte
        val b = Landmark(0f, 0f, 0f)   // köşe
        val c = Landmark(1f, 0f, 0f)   // sağda
        val angle = AngleUtils.calculateAngle(a, b, c)
        assertEquals(90f, angle, DELTA)
    }

    @Test
    fun `squat alt pozisyonu - 90 derece diz`() {
        // Kalça (0, 0), diz (0, 1), ayak bileği (1, 1) → 90° diz açısı
        val hip = Landmark(0f, 0f, 0f)
        val knee = Landmark(0f, 1f, 0f)
        val ankle = Landmark(1f, 1f, 0f)
        val angle = AngleUtils.calculateAngle(hip, knee, ankle)
        assertEquals(90f, angle, DELTA)
    }

    @Test
    fun `tam ayakta pozisyon - 170 derece diz`() {
        // Ayakta duruş — diz açısı ~170-180°
        val hip = Landmark(0.5f, 0.3f, 0f)
        val knee = Landmark(0.5f, 0.6f, 0f)
        val ankle = Landmark(0.5f, 0.9f, 0f)
        val angle = AngleUtils.calculateAngle(hip, knee, ankle)
        assertEquals(180f, angle, DELTA)   // Tam dikey hatta tam dik beklenir
    }

    // ─── EMA smoothing testi ─────────────────────────────────────────────────

    @Test
    fun `ema smoothing gecmise agir baser`() {
        val current = 100f
        val previous = 50f
        val alpha = 0.3f
        val smoothed = AngleUtils.smoothAngle(current, previous, alpha)
        // 0.3 * 100 + 0.7 * 50 = 30 + 35 = 65
        assertEquals(65f, smoothed, DELTA)
    }

    @Test
    fun `ema alpha 1 anlık degeri verir`() {
        val smoothed = AngleUtils.smoothAngle(current = 120f, previous = 80f, alpha = 1.0f)
        assertEquals(120f, smoothed, DELTA)
    }

    @Test
    fun `ema alpha 0 onceki degeri verir`() {
        val smoothed = AngleUtils.smoothAngle(current = 120f, previous = 80f, alpha = 0.0f)
        assertEquals(80f, smoothed, DELTA)
    }

    // ─── calculateTorsoInclination testleri ───────────────────────────────────

    @Test
    fun `dik gövde - 0 derece egim`() {
        // Omuz ve kalça aynı x'te, dikey hatta → 0° eğim
        val lShoulder = Landmark(0.4f, 0.2f, 0f)
        val rShoulder = Landmark(0.6f, 0.2f, 0f)
        val lHip = Landmark(0.4f, 0.6f, 0f)
        val rHip = Landmark(0.6f, 0.6f, 0f)
        val inclination = AngleUtils.calculateTorsoInclination(lShoulder, rShoulder, lHip, rHip)
        assertEquals(0f, inclination, DELTA)
    }

    @Test
    fun `yatay gövde - 90 derece egim`() {
        // Omuz ve kalça aynı y'de, yatay hatta → 90° eğim
        val lShoulder = Landmark(0.2f, 0.5f, 0f)
        val rShoulder = Landmark(0.4f, 0.5f, 0f)
        val lHip = Landmark(0.6f, 0.5f, 0f)
        val rHip = Landmark(0.8f, 0.5f, 0f)
        val inclination = AngleUtils.calculateTorsoInclination(lShoulder, rShoulder, lHip, rHip)
        assertEquals(90f, inclination, DELTA)
    }
}
