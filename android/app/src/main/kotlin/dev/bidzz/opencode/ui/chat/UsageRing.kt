package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.core.design.OpenCodeTheme
import java.util.Locale
import kotlin.math.min

/**
 * The context-usage ring, the shape a "how full is my window" gauge takes here.
 *
 * Ground truth is the last completed assistant message: its `input` plus `cache.read`
 * is the full prompt that turn was charged for (uncached prefix + cached prefix), and
 * `output`/`reasoning` were appended after. The denominator is the model's context
 * limit, which the engine returns per model. No invented numbers: both sides of the
 * ratio come from the engine, verified against a live 2.2.2 server.
 */

/** One turn's footprint on the context window, in tokens. */
internal fun TokenUsage.contextTokens(): Long =
    (input ?: 0) + (output ?: 0) + (reasoning ?: 0) + (cache?.read ?: 0) + (cache?.write ?: 0)

/** Fill of the window as 0..1, or null when the limit is unknown or nonsensical. */
internal fun fillFraction(used: Long?, limit: Long?): Float? {
    if (used == null || limit == null || limit <= 0) return null
    return min(used.toDouble(), limit.toDouble()).toFloat() / limit
}

/** Compactly: 41k/200k, for the ring's description and any label that needs it. */
internal fun usageLabel(used: Long, limit: Long): String =
    String.format(Locale.US, "%s/%s", shortTokens(used), shortTokens(limit))

private fun shortTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%dk", value / 1_000)
    else -> value.toString()
}

/** A small ring that fills clockwise; colour climbs success → warning → danger. */
@Composable
fun UsageRing(
    fraction: Float,
    used: Long,
    limit: Long,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val stroke = 2.dp
    val description = stringResource(
        R.string.ring_context_a11y,
        usageLabel(used, limit),
    )
    Canvas(
        modifier
            .size(14.dp)
            .semantics { contentDescription = description },
    ) {
        val inset = stroke.toPx() / 2
        val diameter = size.width - stroke.toPx()
        val arc = Size(diameter, diameter)
        val level = when {
            fraction >= 0.85f -> colors.danger
            fraction >= 0.60f -> colors.warning
            else -> colors.success
        }
        drawArc(
            color = colors.outline,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = level,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke.toPx(), cap = StrokeCap.Round),
        )
    }
}
