package dev.bidzz.opencode

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-disk crash report: what a released install leaves behind after a
 * Java/Kotlin crash, since nothing else (no adb, no logcat of a dead process)
 * can tell the user or us why the app died.
 */
class CrashLogTest {

    @Test
    fun `the file name carries the timestamp`() {
        assertEquals("crash-1700000000000.txt", CrashLog.fileName(1_700_000_000_000))
    }

    @Test
    fun `a report names the thread and the throwable`() {
        val text = CrashLog.report("main", RuntimeException("boom"), 0)
        assertTrue(text.contains("OpenCode Android crash report"))
        assertTrue(text.contains("thread: main"))
        assertTrue(text.contains("RuntimeException"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("\tat ")) // a real stack frame, not just the header
    }

    @Test
    fun `prune keeps the newest files and drops the oldest`() {
        val files = listOf("crash-3.txt", "crash-1.txt", "crash-4.txt", "crash-2.txt").map(::File)
        val dropped = CrashLog.prune(files, 2).map { it.name }.toSet()
        assertEquals(setOf("crash-1.txt", "crash-2.txt"), dropped) // sorted by name, drop the first two
    }

    @Test
    fun `prune with room to spare drops nothing`() {
        val files = listOf("crash-1.txt", "crash-2.txt").map(::File)
        assertTrue(CrashLog.prune(files, 10).isEmpty())
    }

    @Test
    fun `an invoked handler leaves a readable report on disk`() {
        val dir = Files.createTempDirectory("crash-test").toFile()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashLog.install(dir)
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            val files = dir.listFiles().orEmpty()
            assertEquals(1, files.size)
            assertTrue(files.single().readText().contains("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `the previous handler still runs after ours`() {
        // The system's handler shows the crash dialog and logs to logcat; a report
        // that swallowed it would be worse than no report.
        val dir = Files.createTempDirectory("crash-test-delegate").toFile()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            var handed = false
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> handed = true }
            CrashLog.install(dir)
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertTrue(handed)
            assertEquals(1, dir.listFiles()!!.size) // ours ran too, not instead
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
