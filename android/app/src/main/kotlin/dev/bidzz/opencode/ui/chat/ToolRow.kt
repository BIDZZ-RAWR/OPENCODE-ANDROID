package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider

/**
 * The whole tool trace for one message: one card, a row per step.
 *
 * A real coding run calls fifteen to thirty tools. Rendering every one as a
 * permanent row buried the answer under a wall of log, so the card is folded to
 * its summary row and opened on demand. Two things stay visible while it is folded:
 * the step currently running, so progress is still legible, and any failure, so a
 * red mark is never hidden behind a tap.
 *
 * It starts open while the message streams and closes itself when the run ends:
 * watch it work, then read what it produced.
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
                text = stringResource(
                    if (tools.size == 1) R.string.tool_steps_one else R.string.tool_steps_other,
                    tools.size,
                ),
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
                if (open) Glyph.ChevronDown else Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 18.dp,
            )
        }

        val visible = if (open) tools else running + tools.filter { it.status == "error" }
        visible.forEach { call ->
            RowDivider()
            ToolStep(call)
        }
    }
}

/**
 * One step: its icon, what it did, and how it ended (a check, a cross, or a spinner while it
 * runs). Tap toggles the input and output.
 */
@Composable
fun ToolStep(call: ToolCall, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    val active = call.status == "running" || call.status == "streaming"
    val label = stepLabel(call.name, call.summary, call.status)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = spacing.md, vertical = spacing.md),
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
        }
        if (open) {
            call.inputText?.let { Detail(stringResource(R.string.tool_input).uppercase(), it) }
            call.output?.let { Detail(stringResource(R.string.tool_output).uppercase(), it.take(MAX_OUTPUT)) }
            call.error?.let { Detail(stringResource(R.string.tool_error).uppercase(), it, colors.danger) }
        }
    }
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
