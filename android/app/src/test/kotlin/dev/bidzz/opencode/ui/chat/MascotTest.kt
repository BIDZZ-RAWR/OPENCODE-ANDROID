package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotTest {

    @Test
    fun `christmas season dresses santa and snows`() {
        val (outfit, particles) = seasonFor(12, 5)
        assertEquals("santa", outfit)
        assertEquals("snow", particles)
    }

    @Test
    fun `new year eve turns the party up`() {
        val (outfit, particles) = seasonFor(12, 31)
        assertEquals("party", outfit)
        assertEquals("confetti", particles)
    }

    @Test
    fun `halloween week is witchy`() {
        val (outfit, _) = seasonFor(10, 27)
        assertEquals("witch", outfit)
    }

    @Test
    fun `summer wears shades`() {
        val (outfit, _) = seasonFor(7, 14)
        assertEquals("shades", outfit)
    }

    @Test
    fun `ordinary days are plain`() {
        val (outfit, particles) = seasonFor(4, 9)
        assertNull(outfit)
        assertNull(particles)
    }

    @Test
    fun `valentine drifts hearts`() {
        val (_, particles) = seasonFor(2, 14)
        assertEquals("heart", particles)
    }

    @Test
    fun `a sharp jolt after quiet is a shake`() {
        assertTrue(isShake(delta = 30f, nowMs = 10_000, lastShakeMs = 0))
    }

    @Test
    fun `gentle movement is not a shake`() {
        assertFalse(isShake(delta = 5f, nowMs = 10_000, lastShakeMs = 0))
    }

    @Test
    fun `shakes need cooldown between them`() {
        assertFalse(isShake(delta = 30f, nowMs = 11_000, lastShakeMs = 10_500))
        assertTrue(isShake(delta = 30f, nowMs = 12_001, lastShakeMs = 10_500))
    }
}
