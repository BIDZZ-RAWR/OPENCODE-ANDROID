package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageRingTest {

    @Test
    fun `a turn's context footprint is every token it charged`() {
        val usage = TokenUsage(
            input = 11_559,
            output = 391,
            reasoning = 645,
            cache = CacheUsage(read = 27_648, write = 0),
        )
        assertEquals(40_243, usage.contextTokens())
    }

    @Test
    fun `missing fields count as zero`() {
        assertEquals(0, TokenUsage().contextTokens())
        assertEquals(500, TokenUsage(input = 500).contextTokens())
    }

    @Test
    fun `the fraction needs a limit`() {
        assertNull(fillFraction(used = 100, limit = null))
        assertNull(fillFraction(used = 100, limit = 0))
        assertNull(fillFraction(used = null, limit = 200_000))
    }

    @Test
    fun `the fraction is used over limit`() {
        assertEquals(0.2f, fillFraction(used = 40_000, limit = 200_000)!!)
    }

    @Test
    fun `an overflow caps at the whole window`() {
        assertEquals(1f, fillFraction(used = 250_000, limit = 200_000)!!)
    }

    @Test
    fun `labels shrink big numbers`() {
        assertEquals("40k/200k", usageLabel(40_243, 200_000))
        assertEquals("900/1.0M", usageLabel(900, 1_000_000))
        assertEquals("1.5M/1.5M", usageLabel(1_500_000, 1_500_000))
    }
}
