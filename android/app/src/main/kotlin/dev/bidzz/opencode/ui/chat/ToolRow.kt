package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.inputCommand
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider

/**
 * The whole tool trace for one message: one card, a row per step — the shape Cl0de and
 * Claude Code agree on. The summary row carries the step count plus the files the run
 * touched (bright, so they read at a glance); the chevron turns instead of swapping.
 *
 * A real coding run calls fifteen to thirty tools. Rendering every one as a permanent row
 * buried the answer under a wall of log, so the card is folded to its summary row and opened
 * on demand. Two things stay visible while it is folded: the step currently running, so
 * progress is still legible, and any failure, so a red mark is never hidden behind a tap.
 *
 * It starts open while the message streams and closes itself when the run ends: watch it
 * work, then read what it produced. A step that arrives live slides in; history steps sit
 * in place at once.
 */
@Composable
fun ToolTrace(
    tools: List<ToolCall>,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    if (tools.isEmpty()) return

    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var open by rememberSaveable { mutableStateOf(streaming) }

    val running = tools.filter { it.status == "running" || it.status == "streaming" }
    val failed = tools.count { it.status == "error" }
    val rotation by animateFloatAsState(if (open) 90f else 0f, tween(140), label = "trace")

    LaunchedEffect(streaming) { if (!streaming) open = false }

    CardGroup(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Terminal, tint = colors.onSurfaceMuted, size = 18.dp)
            Text(
                text = buildAnnotatedString {
                    append(
                        stringResource(
                            if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                            tools.size,
                        ),
                    )
                    val files = touchedFiles(tools)
                    if (files.isNotEmpty()) {
                        append("  ·  ")
                        withStyle(SpanStyle(color = colors.onSurface)) {
                            append(files.take(2).joinToString(", ") + if (files.size > 2) ", …" else "")
                        }
                    }
                },
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md),
            )
            if (failed > 0) {
                Text(
                    text = stringResource(R.string.tool_failed, failed),
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                    modifier = Modifier.padding(end = spacing.sm),
                )
            }
            if (running.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tool_working),
                    style = OpenCodeTypography.label,
                    color = colors.warning,
                    modifier = Modifier.padding(end = spacing.sm),
                )
            }
            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 18.dp,
                modifier = Modifier.rotate(rotation),
            )
        }

        val visible = if (open) tools else running + tools.filter { it.status == "error" }
        visible.forEach { call ->
            RowDivider()
            StepIn(live = call.status == "running" || call.status == "streaming") {
                ToolStep(call)
            }
        }
    }
}

/** File tools whose summary is a path; their basenames brighten the summary line. */
private val FILE_TOOLS = setOf("edit", "write", "multiedit", "apply_patch", "patch")

internal fun touchedFiles(tools: List<ToolCall>): List<String> =
    tools.filter { it.name in FILE_TOOLS }
        .map { it.summary.substringAfterLast('/') }
        .filter { it.isNotBlank() }
        .distinct()

/**
 * One step: its icon, what it did, and how it ended (a check, a cross, or a spinner while it
 * runs). Tap toggles the details, which grow rather than snap; a bash step shows its command
 * as a terminal line, and a running one keeps a blinking cursor under its output.
 */
@Composable
fun ToolStep(call: ToolCall, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    val active = call.status == "running" || call.status == "streaming"
    val label = stepLabel(call.name, call.summary, call.status)
    val rotation by animateFloatAsState(if (open) 90f else 0f, tween(140), label = "step")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = spacing.md, vertical = spacing.md)
            .animateContentSize(tween(140, easing = FastOutSlowInEasing)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(label.glyph, tint = if (active) colors.accent else colors.onSurfaceMuted, size = 18.dp)
            Text(
                text = label.text(),
                style = OpenCodeTypography.bodySmall,
                color = if (active) colors.accent else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md, end = spacing.sm),
            )
            when {
                call.status == "completed" -> GlyphIcon(Glyph.Check, tint = colors.success, size = 18.dp)
                call.status == "error" -> GlyphIcon(Glyph.Close, tint = colors.danger, size = 18.dp)
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.accent,
                )
            }
            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 16.dp,
                modifier = Modifier
                    .padding(start = spacing.xs)
                    .rotate(rotation),
            )
        }
        if (open) {
            val command = call.inputCommand()
            if (command != null) {
                CommandBlock(command)
            } else {
                call.inputText?.let { Detail(stringResource(R.string.tool_input).uppercase(), it.take(MAX_INPUT)) }
            }
            call.output?.let {
                Detail(stringResource(R.string.tool_output).uppercase(), it.take(MAX_OUTPUT))
                if (active) BlinkingCursor()
            }
            call.error?.let { Detail(stringResource(R.string.tool_error).uppercase(), it, colors.danger) }
        }
    }
}

/** A bash command the way a terminal would show it, with the prompt dimmed. */
@Composable
private fun CommandBlock(command: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onSurfaceFaint)) { append("$ ") }
            withStyle(SpanStyle(color = colors.onSurface)) { append(command) }
        },
        style = OpenCodeTypography.code,
        modifier = Modifier.padding(top = spacing.sm),
    )
}

/** The terminal cursor at the end of a running command's output. */
@Composable
private fun BlinkingCursor() {
    val colors = OpenCodeTheme.colors
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "blink",
    )
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(width = 7.dp, height = 13.dp)
            .background(colors.onSurface.copy(alpha = alpha)),
    )
}

/** A step that arrived live slides in from below; history steps sit in place at once. */
@Composable
private fun StepIn(live: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (live) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx()
        },
    ) { content() }
}

@Composable
private fun Detail(
    label: String,
    body: String,
    color: androidx.compose.ui.graphics.Color = OpenCodeTheme.colors.onSurface,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = label,
        style = OpenCodeTypography.label,
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = Modifier.padding(top = spacing.sm),
    )
    Text(text = body, style = OpenCodeTypography.code, color = color)
}

private const val MAX_OUTPUT = 4000
private const val MAX_INPUT = 2000
