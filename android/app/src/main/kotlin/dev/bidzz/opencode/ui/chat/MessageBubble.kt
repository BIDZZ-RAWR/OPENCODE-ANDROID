package dev.bidzz.opencode.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * One chat message.
 *
 * Layout is asymmetric on purpose, following how reading actually works here:
 *
 * - The user's own message is a compact bubble on the right. It is short, already
 *   known, and does not need width.
 * - The assistant's message is full-width with no bubble. Model output contains
 *   code and tables, and a bubble would steal horizontal space from exactly the
 *   content that needs it.
 *
 * That asymmetry is the single biggest reason a chat reads as a coding tool rather
 * than a messaging app.
 */
@Composable
fun MessageBubble(
    role: Role,
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    tools: List<dev.bidzz.opencode.core.api.dto.ToolCall> = emptyList(),
    /** Mode and model that produced this reply, when known. Empty when absent. */
    provenance: String? = null,
    /** Copy and share under the reply, and the reminder to check it. Only the newest reply gets them. */
    showActions: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    when (role) {
        Role.USER -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dims.userBubbleMaxWidthFraction)
                    .background(colors.userBubble, RoundedCornerShape(radius.lg))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                // User text is plain: markdown the user typed is not rendered as
                // formatting, so what they see is what they sent.
                Text(
                    text = markdown,
                    style = OpenCodeTypography.body,
                    color = colors.onUserBubble,
                )
            }
        }

        Role.ASSISTANT -> Column(modifier = modifier.fillMaxWidth()) {
            // Which mode and model answered, small and quiet: it is reference, not content.
            // Nothing at all when there is nothing to say, instead of a label on every reply.
            // With steps, the label sits under their card, the way a step is attributed to its model;
            // without any, it stays above the text.
            val topLabel = provenance?.takeIf { tools.isEmpty() }
            if (!topLabel.isNullOrBlank() || streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!topLabel.isNullOrBlank()) {
                        Text(
                            text = topLabel,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    if (streaming) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = if (topLabel.isNullOrBlank()) 0.dp else spacing.sm)
                                .size(dims.statusDotSize),
                            strokeWidth = 1.5.dp,
                            color = colors.accent,
                        )
                    }
                }
            }
            // The steps come first, then what the agent concluded from them.
            ToolTrace(
                tools = tools,
                streaming = streaming,
                modifier = Modifier.padding(top = spacing.xs),
            )
            if (tools.isNotEmpty()) {
                Text(
                    text = provenance.orEmpty(),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(top = spacing.xs, bottom = spacing.sm),
                )
            }
            Box(Modifier.padding(top = spacing.xs)) {
                if (markdown.isEmpty() && streaming) {
                    // Nothing yet: say so instead of showing an empty gap that looks
                    // like a broken message.
                    Text(
                        text = stringResource(R.string.bubble_thinking),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                    )
                } else {
                    MarkdownText(markdown = markdown)
                }
            }
            if (showActions && markdown.isNotEmpty() && !streaming) ReplyActions(markdown)
        }

        Role.ERROR -> Row(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dims.userBubbleMaxWidthFraction)
                    .background(colors.surfaceSunken, RoundedCornerShape(radius.sm))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Column {
                    Text(
                        text = markdown,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                    )
                    provenance?.let {
                        Text(
                            text = it,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** Copy and share for a finished reply, and the one-line reminder under them. */
@Composable
private fun ReplyActions(markdown: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.chat_copy)
    val shareLabel = stringResource(R.string.chat_share)

    Column(Modifier.padding(top = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReplyAction(Glyph.Copy, copyLabel) { clipboard.setText(AnnotatedString(markdown)) }
            ReplyAction(Glyph.Share, shareLabel) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, markdown)
                // The app's language context is not an Activity, so the new task flag is required.
                val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(chooser)
                } catch (_: ActivityNotFoundException) {
                    // Nothing on the device can take text; there is nothing useful to say about it.
                }
            }
        }
        Text(
            text = stringResource(R.string.chat_disclaimer),
            style = OpenCodeTypography.label,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

@Composable
private fun ReplyAction(glyph: Glyph, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = OpenCodeTheme.colors.onSurfaceMuted, size = 20.dp)
    }
}

enum class Role { USER, ASSISTANT, ERROR }

