package dev.bidzz.opencode.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * Message input.
 *
 * One bordered box in two lines. On top, the text. Underneath, mode and model as plain text on
 * the left (see `ChatControls`), and dictation and send at the right end. Mode and model are
 * decided right before typing, so they sit with the input.
 *
 * The field grows with its content up to a cap, then scrolls, so a long prompt does not
 * push the conversation off screen. Send becomes stop while the agent is working, in the
 * same position, because stopping is what the user wants at that moment.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions

    val canSend = enabled && value.isNotBlank() && !busy
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.outline, shape)
            .padding(start = spacing.lg, end = spacing.sm, top = spacing.md, bottom = spacing.sm),
    ) {
        // `decorationBox` rather than a sibling Text: the field itself must own the
        // placeholder and the layout, otherwise it collapses to zero height and the IME
        // connection never attaches to it.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = dims.composerMaxHeight)
                .padding(end = spacing.sm, bottom = spacing.xs),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        // While the agent runs the button is Stop and send() refuses, so the
                        // field only accumulates a draft for after the run.
                        Text(
                            text = stringResource(
                                when {
                                    !enabled -> R.string.composer_not_ready
                                    busy -> R.string.composer_busy
                                    else -> R.string.composer_hint
                                },
                            ),
                            style = OpenCodeTypography.input,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    inner()
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { controls() }

            // Dictation is offered only while there is nothing typed to send.
            if (enabled && !busy && value.isEmpty()) {
                DictationButton(onText = { spoken -> onValueChange((value + " " + spoken).trim()) })
            }
            ActionButton(
                busy = busy,
                canSend = canSend,
                onClick = { if (busy) onStop() else onSend() },
            )
        }
    }
}

/** A rounded-square button in the composer's second line. */
@Composable
private fun SquareButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: androidx.compose.ui.graphics.Color = OpenCodeTheme.colors.surfaceVariant,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(start = OpenCodeTheme.tokens.spacing.sm)
            .size(width = 46.dp, height = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun DictationButton(onText: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val context = LocalContext.current
    val voicePrompt = stringResource(R.string.composer_voice_prompt)
    val voiceUnavailable = stringResource(R.string.composer_voice_unavailable)
    val voiceLabel = stringResource(R.string.composer_voice)
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) onText(spoken)
    }

    SquareButton(
        description = voiceLabel,
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
            // No resolveActivity check: package visibility would hide a recognizer that exists.
            try {
                dictation.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        GlyphIcon(Glyph.Mic, tint = colors.onSurfaceMuted, size = 22.dp)
    }
}

@Composable
private fun ActionButton(busy: Boolean, canSend: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val active = busy || canSend

    SquareButton(
        description = stringResource(if (busy) R.string.composer_stop else R.string.composer_send),
        onClick = onClick,
        enabled = active,
        // Sending is the one primary action on this screen, so it carries the brand accent.
        fill = if (active) colors.accent else colors.surfaceVariant,
    ) {
        if (busy) {
            // A filled square reads as "stop" without needing an icon.
            Box(
                Modifier
                    .size(12.dp)
                    .background(colors.onAccent, RoundedCornerShape(2.dp)),
            )
        } else {
            GlyphIcon(
                glyph = Glyph.ArrowUp,
                tint = if (canSend) colors.onAccent else colors.onSurfaceFaint,
                size = 22.dp,
            )
        }
    }
}
