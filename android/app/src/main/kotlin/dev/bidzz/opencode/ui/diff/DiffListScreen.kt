package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold

/**
 * Every file the agent changed, in one list.
 *
 * This is the "what did it actually do" screen: it answers the question a diff of
 * a single file cannot, by showing the whole working tree at once.
 */
@Composable
fun DiffListScreen(
    changed: Map<String, VcsFileStatus>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    ScreenScaffold(title = stringResource(R.string.difflist_title, changed.size), onBack = onBack) {
        if (changed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.difflist_empty),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(spacing.xxl),
                )
            }
        } else {
            val sorted = changed.values.sortedBy { it.file }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                // One card with hairline dividers, like the Files list — the app's
                // way of saying "these are rows of the same thing".
                CardGroup {
                    sorted.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider(inset = spacing.md)
                        ChangedFileRow(entry, onClick = { onOpen(entry.file) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedFileRow(entry: VcsFileStatus, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = spacing.md, end = spacing.xs, top = spacing.sm, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.status.first().uppercase(),
            style = OpenCodeTypography.code,
            color = when (entry.status) {
                "added" -> colors.success
                "deleted" -> colors.danger
                else -> colors.warning
            },
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = entry.file,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${entry.additions} −${entry.deletions}",
            style = OpenCodeTypography.label,
            color = colors.onSurfaceFaint,
        )
    }
}
