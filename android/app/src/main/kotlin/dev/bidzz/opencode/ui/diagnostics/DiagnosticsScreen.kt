package dev.bidzz.opencode.ui.diagnostics

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.bidzz.opencode.BugReportData
import dev.bidzz.opencode.ExitRun
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.ProcessHealth
import dev.bidzz.opencode.buildBugReport
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.engine.EngineDiagnostics
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runtime diagnostics.
 *
 * This is where the port, password and engine log live now. They used to be the
 * whole app; keeping them available matters because a failed engine start is
 * otherwise unexplainable, but they do not belong on the chat screen.
 *
 * Everything is selectable so a value can be copied into a bug report.
 */
@Composable
fun DiagnosticsScreen(
    app: OpenCodeApplication,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()

    val diagnostics = remember {
        EngineDiagnostics(
            app.engineEnvironment,
            File(app.applicationInfo.nativeLibraryDir),
        )
    }
    var tools by remember { mutableStateOf(diagnostics.tools()) }
    var probes by remember { mutableStateOf<List<EngineDiagnostics.ProbeResult>>(emptyList()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var memoryClass by remember { mutableStateOf(0) }
    var peakRss by remember { mutableStateOf<Long?>(null) }
    var exitRuns by remember { mutableStateOf<List<ExitRun>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val appVersion = remember {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val deviceInfo = remember {
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    val refresh: () -> Unit = {
        if (!running) {
            running = true
            tools = diagnostics.tools()
            memoryClass = ProcessHealth.memoryClassMB(context)
            peakRss = ProcessHealth.peakRssKb()
            exitRuns = ProcessHealth.exitRuns(context)
            scope.launch {
                logLines = diagnostics.engineLogTail()
                probes = listOf(
                    diagnostics.probe("git", listOf("--version")),
                    diagnostics.probe("rg", listOf("--version")),
                )
                running = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    /** Assembles what this screen shows into one text; no password is ever handed to it. */
    val copyReport: () -> Unit = {
        val report = buildBugReport(
            BugReportData(
                version = appVersion,
                device = deviceInfo,
                engineState = status.state.name,
                enginePid = status.pid,
                enginePort = status.port,
                engineBaseUrl = status.credentials?.baseUrl,
                engineError = status.lastError,
                workspace = app.workspaceStore.resolve().absolutePath,
                paths = diagnostics.paths(),
                tools = tools.map { tool ->
                    "${tool.name}: ${if (tool.resolves) "linked" else "MISSING"} -> ${tool.target}"
                },
                probes = probes.map { probe ->
                    "${probe.command.substringAfterLast('/')} -> exit ${probe.exitCode ?: "—"}" +
                        "${if (probe.timedOut) " (timed out)" else ""}: " +
                        probe.output.trim().lineSequence().first().take(200)
                },
                process = buildList {
                    add("memoryClass: $memoryClass MB")
                    peakRss?.let { add("peakRss: $it kB") }
                    exitRuns.forEachIndexed { index, run -> add(ProcessHealth.formatExit(run, index)) }
                },
                engineLog = logLines,
            ),
        )
        clipboard.setText(AnnotatedString(report))
        Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    ScreenScaffold(
        title = stringResource(R.string.settings_diag_title),
        onBack = onBack,
        actions = {
            TextAction(
                stringResource(if (running) R.string.diag_running else R.string.common_refresh),
                refresh,
                enabled = !running,
            )
            TextAction(stringResource(R.string.diag_copy_report), { copyReport() })
        },
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
            ) {
                CardGroup {
                    Line("state", status.state.name)
                    RowDivider(inset = spacing.md)
                    Line("pid", status.pid?.toString() ?: stringResource(R.string.diag_none))
                    RowDivider(inset = spacing.md)
                    Line("port", status.port?.toString() ?: stringResource(R.string.diag_none))
                    RowDivider(inset = spacing.md)
                    Line("baseUrl", status.credentials?.baseUrl ?: stringResource(R.string.diag_none))
                    RowDivider(inset = spacing.md)
                    Box(Modifier.clickable { showPassword = !showPassword }) {
                        Line(
                            "password",
                            status.credentials?.password?.let {
                                if (showPassword) it else stringResource(R.string.diag_password_hidden)
                            } ?: stringResource(R.string.diag_none),
                        )
                    }
                    RowDivider(inset = spacing.md)
                    Line("error", status.lastError ?: stringResource(R.string.diag_none))
                    RowDivider(inset = spacing.md)
                    Line("nativeLibraryDir", app.applicationInfo.nativeLibraryDir)
                    RowDivider(inset = spacing.md)
                    Line("filesDir", app.filesDir.absolutePath)
                    RowDivider(inset = spacing.md)
                    Line("workspace", app.workspaceStore.resolve().absolutePath)
                    RowDivider(inset = spacing.md)
                    Line("all-files", app.workspaceStore.hasAllFilesAccess().toString())
                    app.startupFailure?.let {
                        RowDivider(inset = spacing.md)
                        Line("startupFailure", it)
                    }
                }

                Section(stringResource(R.string.diag_section_tools))
                CardGroup {
                    if (tools.isEmpty()) {
                        Body(stringResource(R.string.diag_no_tools), colors.onSurfaceFaint)
                    } else {
                        tools.forEachIndexed { index, tool ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            Line(
                                tool.name,
                                buildString {
                                    append(
                                        stringResource(
                                            if (tool.resolves) R.string.diag_linked else R.string.diag_missing,
                                        ),
                                    )
                                    append(" -> ")
                                    append(tool.target)
                                    if (!tool.sourcePresent) {
                                        append("  ")
                                        append(stringResource(R.string.diag_no_so))
                                    }
                                },
                                ok = tool.resolves,
                            )
                        }
                    }
                    probes.forEachIndexed { index, probe ->
                        RowDivider(inset = spacing.md)
                        Line(
                            probe.command.substringAfterLast('/').substringBefore(' '),
                            when {
                                probe.timedOut -> stringResource(R.string.diag_timeout, PROBE_TIMEOUT_SECONDS)
                                probe.exitCode == 0 ->
                                    // --version prints a block; the first line is the version, and
                                    // the rest only matters when something is missing.
                                    probe.output.trim().lineSequence().first().let { head ->
                                        val extra = probe.output.trim()
                                            .lines()
                                            .drop(1)
                                            .firstOrNull { it.isNotBlank() }
                                            ?.trim()
                                        if (extra == null) head else "$head  ·  $extra"
                                    }
                                else -> stringResource(
                                    R.string.diag_exit,
                                    probe.exitCode ?: 0,
                                    probe.output.trim().ifEmpty { stringResource(R.string.diag_no_output) }
                                        .replace('\n', ' '),
                                )
                            },
                            ok = probe.ok,
                        )
                    }
                }
                if (probes.isEmpty() && !running) {
                    Body(stringResource(R.string.diag_tap_refresh), colors.onSurfaceFaint)
                }
                Body(
                    text = stringResource(R.string.diag_probe_note),
                    color = colors.onSurfaceFaint,
                )

                Section(stringResource(R.string.diag_section_paths))
                CardGroup {
                    diagnostics.paths().forEachIndexed { index, (label, value) ->
                        // The engine reports a path, not whether the file is there; saying "(missing)"
                        // is the screen's job, in the app's language.
                        if (index > 0) RowDivider(inset = spacing.md)
                        val suffix = if (label == "engineLog" && !diagnostics.engineLogFile.isFile) {
                            " " + stringResource(R.string.diag_missing_suffix)
                        } else {
                            ""
                        }
                        Line(label, value + suffix)
                    }
                }

                Section(stringResource(R.string.diag_section_process))
                CardGroup {
                    Line("memoryClass", "$memoryClass MB")
                    peakRss?.let {
                        RowDivider(inset = spacing.md)
                        Line("peakRss", "$it kB")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && exitRuns.isNotEmpty()) {
                        exitRuns.forEachIndexed { index, run ->
                            RowDivider(inset = spacing.md)
                            Mono(ProcessHealth.formatExit(run, index))
                        }
                    }
                }
                Body(stringResource(R.string.diag_exit_note), colors.onSurfaceFaint)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    Body(stringResource(R.string.diag_exit_unavailable), colors.onSurfaceFaint)
                } else if (exitRuns.isEmpty()) {
                    Body(stringResource(R.string.diag_no_exit), colors.onSurfaceFaint)
                }

                Section(stringResource(R.string.diag_section_output))
                if (status.recentOutput.isEmpty()) {
                    Body(stringResource(R.string.diag_no_output_yet), colors.onSurfaceMuted)
                } else {
                    CardGroup {
                        status.recentOutput.takeLast(40).forEach { line -> Mono(line) }
                    }
                }

                Section(stringResource(R.string.diag_section_log))
                if (logLines.isEmpty()) {
                    Body(stringResource(R.string.diag_nothing_read), colors.onSurfaceMuted)
                } else {
                    Body(stringResource(R.string.diag_lines_newest_last, logLines.size), colors.onSurfaceFaint)
                    CardGroup {
                        logLines.takeLast(120).forEach { line -> Mono(line) }
                    }
                }
            }
        }
    }
}

/** The probe's own timeout, so the message and the run cannot disagree about it. */
private const val PROBE_TIMEOUT_SECONDS = 8

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = OpenCodeTypography.label,
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = Modifier.padding(top = OpenCodeTheme.tokens.spacing.xl, bottom = OpenCodeTheme.tokens.spacing.sm),
    )
}

@Composable
private fun Body(text: String, color: Color) {
    Text(
        text = text,
        style = OpenCodeTypography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = OpenCodeTheme.tokens.spacing.md),
    )
}

@Composable
private fun Mono(text: String) {
    val colors = OpenCodeTheme.colors
    Text(
        text = text,
        style = OpenCodeTypography.code,
        color = colors.onSurfaceMuted,
        modifier = Modifier
            .padding(horizontal = OpenCodeTheme.tokens.spacing.md)
            .horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun Line(label: String, value: String, ok: Boolean? = null) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xxs)) {
        Text(
            text = label,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = value,
            style = OpenCodeTypography.code,
            color = when (ok) {
                true -> colors.success
                false -> colors.danger
                else -> colors.onSurface
            },
        )
    }
}
