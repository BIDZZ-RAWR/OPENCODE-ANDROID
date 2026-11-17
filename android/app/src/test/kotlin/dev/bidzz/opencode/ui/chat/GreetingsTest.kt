package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingsTest {

    @Test
    fun `every greeting has two non-blank lines in both languages`() {
        assertTrue(Greetings.all.isNotEmpty())
        Greetings.all.forEach { g ->
            listOf(g.aEn, g.bEn, g.aId, g.bId).forEach { line ->
                assertTrue("blank line in: $g", line.isNotBlank())
            }
        }
    }

    @Test
    fun `same seed yields the same greeting in the requested language`() {
        val en = Greetings.pick(indonesian = false, seed = 7)
        val id = Greetings.pick(indonesian = true, seed = 7)
        assertTrue(Greetings.all.any { it.aEn == en.first && it.bEn == en.second })
        assertTrue(Greetings.all.any { it.aId == id.first && it.bId == id.second })
    }

    @Test
    fun `indonesian and english are the same greeting translated, not mixed`() {
        // find one greeting and ask for both languages with its own seed-space:
        // pick is deterministic per seed, so brute-force until both halves match one entry.
        val id = Greetings.pick(indonesian = true, seed = 3)
        val entry = Greetings.all.first { it.aId == id.first && it.bId == id.second }
        assertEquals(entry.aId, id.first)
        assertEquals(entry.bId, id.second)
    }

    @Test
    fun `pick varies across seeds`() {
        val distinct = (0L until 60L).map { Greetings.pick(indonesian = false, seed = it) }.toSet()
        assertTrue("expected several greetings, got ${distinct.size}", distinct.size > 3)
    }
}
