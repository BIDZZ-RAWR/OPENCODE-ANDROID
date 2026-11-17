package dev.bidzz.opencode.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.McpServer
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * MCP servers: which are connected, and a button to connect or disconnect each.
 *
 * Adding a server is left to the engine config for now: it takes a command or a URL plus
 * headers, and a form for that is worth building only once someone needs it from the phone.
 */
@Composable
fun McpScreen(
    state: McpUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (McpServer) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    ScreenScaffold(
        title = stringResource(R.string.mcp_title),
        onBack = onBack,
        actions = { TextAction(stringResource(R.string.common_refresh), onRefresh) },
    ) {
        when {
            state.loading && state.servers.isEmpty() -> Centered(stringResource(R.string.mcp_loading))
            state.failureKey != null -> Centered(stringResource(state.failureKey), isError = true)
            state.failure != null && state.servers.isEmpty() -> Centered(state.failure.message(), isError = true)
            state.servers.isEmpty() -> Centered(stringResource(R.string.mcp_empty))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                state.failure?.let {
                    Text(
                        text = it.message(),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(bottom = spacing.sm),
                    )
                }
                CardGroup {
                    state.servers.forEachIndexed { index, server ->
                        if (index > 0) RowDivider(inset = spacing.md + 36.dp + spacing.md)
                        ServerRow(server, busy = server.name in state.busy, onToggle = { onToggle(server) })
                    }
                }
                Text(
                    text = stringResource(R.string.mcp_note),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(start = spacing.xs, top = spacing.md, bottom = spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun ServerRow(server: McpServer, busy: Boolean, onToggle: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val connected = server.status.status == "connected"
    val statusColor = when (server.status.status) {
        "connected" -> colors.success
        "pending" -> colors.warning
        "failed", "needs_auth" -> colors.danger
        else -> colors.onSurfaceFaint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = spacing.md, top = spacing.sm, bottom = spacing.sm, end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconBadge(Glyph.Plug, tint = if (connected) colors.accent else colors.onSurfaceMuted)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(
                text = server.title?.takeIf { it.isNotBlank() } ?: server.name,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLabel(server.status.status),
                style = OpenCodeTypography.bodySmall,
                color = statusColor,
            )
            // The engine's own reason, for a failure or a sign-in request, kept short.
            server.status.error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextAction(
            label = stringResource(if (connected) R.string.mcp_disconnect else R.string.mcp_connect),
            onClick = onToggle,
            enabled = !busy,
        )
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "connected" -> R.string.mcp_status_connected
        "pending" -> R.string.mcp_status_pending
        "failed" -> R.string.mcp_status_failed
        "needs_auth" -> R.string.mcp_status_needs_auth
        else -> R.string.mcp_status_disabled
    },
)

@Composable
private fun Centered(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
