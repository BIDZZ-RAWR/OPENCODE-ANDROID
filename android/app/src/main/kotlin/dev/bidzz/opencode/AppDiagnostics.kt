package dev.bidzz.opencode

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The instrumentation a released install needs, because `run-as` refuses a
 * non-debuggable build: when a user reports "it force closed", neither of us has
 * anything else to read. Three instruments, one file:
 *
 *  - [CrashLog] catches a Java/Kotlin crash and leaves its stacktrace on disk
 *    before the process dies.
 *  - [ProcessHealth] answers "did the system kill us, and why" through
 *    `getHistoricalProcessExitReasons` (API 30+), and reports the memory budget
 *    so a phone that worked can be compared with one that did not.
 *  - [buildBugReport] assembles everything the Diagnostics screen shows into one
 *    copyable text. It deliberately receives no password: what goes into a bug
 *    report is decided here, not by whoever assembles it.
 */
object CrashLog {

    const val MAX_FILES = 10

    fun fileName(atMs: Long): String = "crash-$atMs.txt"

    fun report(threadName: String, throwable: Throwable, atMs: Long): String = buildString {
        appendLine("OpenCode Android crash report")
        appendLine("at: ${formatTimestamp(atMs)}")
        appendLine("thread: $threadName")
        append(throwable.printStackTraceString())
    }

    /**
     * Installs the writer. The previous handler is still run: the system's own
     * handler is what shows the "app has stopped" dialog and logs to logcat, and
     * a report that swallowed those would be worse than no report.
     */
    fun install(crashDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler && previous.dir == crashDir) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(crashDir, previous))
    }

    /** Files to delete so at most [max] remain; name order is time order, oldest first. */
    fun prune(files: List<File>, max: Int): List<File> =
        files.sortedBy { it.name }.dropLast(max.coerceAtLeast(0))

    private class Handler(val dir: File, private val previous: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            // A failing report must not replace the crash with a different one:
            // swallow, then hand over whatever happened.
            runCatching { write(dir, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(dir: File, thread: Thread, throwable: Throwable) {
        dir.mkdirs()
        val now = System.currentTimeMillis()
        File(dir, fileName(now)).writeText(report(thread.name, throwable, now))
        prune(
            dir.listFiles { file -> file.extension == "txt" }?.toList().orEmpty(),
            MAX_FILES,
        ).forEach { it.delete() }
    }
}

private fun Throwable.printStackTraceString(): String =
    StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()

internal fun formatTimestamp(atMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(atMs))

/** One recorded death of this app process. */
data class ExitRun(val reason: String, val concerning: Boolean, val description: String, val timestamp: Long)

object ProcessHealth {

    /**
     * Reason codes to their documented names (`ApplicationExitInfo`, API 30).
     * The ints are locked by the test; the runtime reads them from [exitRuns],
     * never from literals.
     */
    fun reasonName(code: Int): String = when (code) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "UID_IDLE"
        else -> "OTHER($code)"
    }

    /** The reasons that answer "it stopped by itself": system kills and failures. */
    fun isConcerning(code: Int): Boolean = code in setOf(3, 4, 5, 6, 7, 9)

    fun vmHwmKb(statusText: String): Long? =
        statusText.lineSequence()
            .firstOrNull { it.startsWith("VmHWM:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()

    /** Peak resident set of this process, from `/proc/self/status` — null when unreadable. */
    fun peakRssKb(): Long? = runCatching { vmHwmKb(File("/proc/self/status").readText()) }.getOrNull()

    fun memoryClassMB(context: Context): Int =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass

    /**
     * The last [max] exits of this app process. Empty on Android 10, where the
     * API does not exist yet — the screen says so rather than implying "none".
     */
    fun exitRuns(context: Context, max: Int = 5): List<ExitRun> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, max).map { info ->
                ExitRun(
                    reason = reasonName(info.reason),
                    concerning = isConcerning(info.reason),
                    description = info.description ?: "—",
                    timestamp = info.timestamp,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun formatExit(run: ExitRun, index: Int): String =
        "exit#$index ${run.reason}${if (run.concerning) " (!)" else ""} · ${run.description} · ${formatTimestamp(run.timestamp)}"
}

/** Everything [buildBugReport] needs; the report's content is exactly this. */
data class BugReportData(
    val version: String,
    val device: String,
    val engineState: String?,
    val enginePid: Int?,
    val enginePort: Int?,
    val engineBaseUrl: String?,
    val engineError: String?,
    val workspace: String,
    val paths: List<Pair<String, String>>,
    val tools: List<String>,
    val probes: List<String>,
    val process: List<String>,
    val engineLog: List<String>,
)

fun buildBugReport(d: BugReportData): String = buildString {
    appendLine("OpenCode Android bug report")
    appendLine("version: ${d.version}")
    appendLine("device: ${d.device}")
    appendLine("engine.state: ${d.engineState ?: "—"}")
    appendLine("engine.pid: ${d.enginePid ?: "—"}")
    appendLine("engine.port: ${d.enginePort ?: "—"}")
    appendLine("engine.baseUrl: ${d.engineBaseUrl ?: "—"}")
    appendLine("engine.error: ${d.engineError ?: "—"}")
    appendLine("workspace: ${d.workspace}")
    if (d.paths.isNotEmpty()) {
        appendLine()
        appendLine("[paths]")
        d.paths.forEach { (label, value) -> appendLine("$label: $value") }
    }
    if (d.tools.isNotEmpty()) {
        appendLine()
        appendLine("[tools]")
        d.tools.forEach(::appendLine)
    }
    if (d.probes.isNotEmpty()) {
        appendLine()
        appendLine("[tool probes]")
        d.probes.forEach(::appendLine)
    }
    if (d.process.isNotEmpty()) {
        appendLine()
        appendLine("[process]")
        d.process.forEach(::appendLine)
    }
    if (d.engineLog.isNotEmpty()) {
        appendLine()
        appendLine("[engine log, newest last]")
        d.engineLog.takeLast(120).forEach(::appendLine)
    }
}
