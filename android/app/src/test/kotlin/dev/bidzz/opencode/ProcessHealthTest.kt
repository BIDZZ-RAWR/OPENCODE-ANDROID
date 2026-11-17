package dev.bidzz.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the process-level instruments answer: why did the system kill us, and
 * how much memory were we holding when it did. The reason-code numbers are
 * documented constants of `ApplicationExitInfo`; locking them here is what
 * keeps a renamed code from turning into a silent "OTHER(3)" on screen.
 */
class ProcessHealthTest {

    @Test
    fun `reason codes map to their documented names`() {
        assertEquals("EXIT_SELF", ProcessHealth.reasonName(1))
        assertEquals("SIGNALED", ProcessHealth.reasonName(2))
        assertEquals("LOW_MEMORY", ProcessHealth.reasonName(3))
        assertEquals("CRASH", ProcessHealth.reasonName(4))
        assertEquals("CRASH_NATIVE", ProcessHealth.reasonName(5))
        assertEquals("ANR", ProcessHealth.reasonName(6))
        assertEquals("USER_STOPPED", ProcessHealth.reasonName(11))
        assertEquals("OTHER(99)", ProcessHealth.reasonName(99))
    }

    @Test
    fun `the concerning reasons are the ones a user would report`() {
        // System kills and failures explain "it stopped on its own"; a clean
        // self-exit or a user-initiated stop does not.
        assertTrue(ProcessHealth.isConcerning(3)) // LOW_MEMORY — the suspected RAM answer
        assertTrue(ProcessHealth.isConcerning(4)) // CRASH
        assertTrue(ProcessHealth.isConcerning(5)) // CRASH_NATIVE
        assertTrue(ProcessHealth.isConcerning(6)) // ANR
        assertTrue(ProcessHealth.isConcerning(7)) // INITIALIZATION_FAILURE
        assertTrue(ProcessHealth.isConcerning(9)) // EXCESSIVE_RESOURCE_USAGE
        assertFalse(ProcessHealth.isConcerning(1)) // EXIT_SELF
        assertFalse(ProcessHealth.isConcerning(2)) // SIGNALED (e.g. our own SIGTERM path)
        assertFalse(ProcessHealth.isConcerning(10)) // USER_REQUESTED
        assertFalse(ProcessHealth.isConcerning(11)) // USER_STOPPED
    }

    @Test
    fun `peak rss is read from the status file`() {
        val status = "Name:\tcom.example\nVmPeak:\t 999999 kB\nVmHWM:\t 123456 kB\nVmRSS:\t 100000 kB\n"
        assertEquals(123456L, ProcessHealth.vmHwmKb(status))
    }

    @Test
    fun `a status file without the line reads as null`() {
        assertNull(ProcessHealth.vmHwmKb("Name:\tcom.example\nVmRSS:\t 100000 kB\n"))
    }

    @Test
    fun `an exit line is readable and marks a concerning reason`() {
        val run = ExitRun("LOW_MEMORY", concerning = true, description = "memory pressure", timestamp = 0)
        val text = ProcessHealth.formatExit(run, 0)
        assertTrue(text.contains("exit#0"))
        assertTrue(text.contains("LOW_MEMORY"))
        assertTrue(text.contains("(!)"))
        assertTrue(text.contains("memory pressure"))
    }

    @Test
    fun `a benign exit is not marked with a warning`() {
        val run = ExitRun("USER_STOPPED", concerning = false, description = "—", timestamp = 0)
        assertFalse(ProcessHealth.formatExit(run, 2).contains("(!)"))
    }

    @Test
    fun `the bug report lists every section in order`() {
        val text = buildBugReport(
            BugReportData(
                version = "0.1.1",
                device = "Test Phone · Android 16 (API 36)",
                engineState = "RUNNING",
                enginePid = 42,
                enginePort = 45147,
                engineBaseUrl = "http://127.0.0.1:45147",
                engineError = null,
                workspace = "/data/workspace",
                paths = listOf("engineLog" to "/data/log"),
                tools = listOf("git: linked -> /data/tools/bin/git"),
                probes = listOf("git --version -> exit 0: git version 2.56.0"),
                process = listOf("memoryClass: 192 MB"),
                engineLog = listOf("starting engine ... --port 45147"),
            ),
        )
        assertTrue(text.contains("version: 0.1.1"))
        assertTrue(text.contains("engine.state: RUNNING"))
        assertTrue(text.contains("[paths]"))
        assertTrue(text.contains("[tools]"))
        assertTrue(text.contains("[tool probes]"))
        assertTrue(text.contains("[process]"))
        assertTrue(text.contains("[engine log, newest last]"))
        // Sections must read in a fixed order, so a bug report is skimmable.
        val order = listOf("[paths]", "[tools]", "[tool probes]", "[process]", "[engine log")
            .map { text.indexOf(it) }
        assertEquals(order, order.sorted())
    }

    @Test
    fun `empty sections are omitted instead of printed empty`() {
        val text = buildBugReport(
            BugReportData(
                version = "0.1.1",
                device = "d",
                engineState = null,
                enginePid = null,
                enginePort = null,
                engineBaseUrl = null,
                engineError = null,
                workspace = "/w",
                paths = emptyList(),
                tools = emptyList(),
                probes = emptyList(),
                process = emptyList(),
                engineLog = emptyList(),
            ),
        )
        assertFalse(text.contains("[paths]"))
        assertFalse(text.contains("[process]"))
        assertFalse(text.contains("[engine log"))
    }
}
