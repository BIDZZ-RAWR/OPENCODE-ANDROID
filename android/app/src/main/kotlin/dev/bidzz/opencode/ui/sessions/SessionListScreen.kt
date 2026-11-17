package dev.bidzz.opencode.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Conversation history.
 *
 * The rows sit on one card, like the file listing, so the list reads as one group.
 * Each row has the title the engine generated, and a quiet meta line with how long
 * ago it ran, its directory (only when the list is not already scoped to one project),
 * and its cost when non-zero.
 */
@Composable
fun SessionListScreen(
    state: SessionListUiState,
    onBack: () -> Unit,
    onOpen: (SessionRow) -> Unit,
    onToggleScope: () -> Unit,
    onNewSession: () -> Unit,
    onRename: (SessionRow, String) -> Unit,
    onDelete: (SessionRow) -> Unit,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val colors = OpenCodeTheme.colors

    ScreenScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        actions = { TextAction(stringResource(R.string.history_new), onNewSession) },
    ) {
        // Scope toggle: the two useful views are "this project" and "everything".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ScopeChip(
                label = stringResource(R.string.history_this_project),
                selected = state.filterDirectory != null,
                onClick = onToggleScope,
            )
            ScopeChip(
                label = stringResource(R.string.history_all_projects),
                selected = state.filterDirectory == null,
                onClick = onToggleScope,
            )
        }

        // A rename or delete that failed: said once, above a list that is still usable.
        state.notice?.let { failure ->
            Text(
                text = failure.message(),
                style = OpenCodeTypography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }

        when {
            state.loading -> CenteredMessage(stringResource(R.string.history_loading))
            state.failure != null -> CenteredMessage(state.failure.message(), isError = true)
            state.failureKey != null -> CenteredMessage(stringResource(state.failureKey), isError = true)
            state.rows.isEmpty() -> CenteredMessage(stringResource(R.string.history_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = spacing.xs,
                    bottom = spacing.xxl,
                ),
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    SessionRowItem(
                        row = row,
                        showDirectory = state.filterDirectory == null,
                        isFirst = index == 0,
                        isLast = index == state.rows.lastIndex,
                        onClick = { onOpen(row) },
                        onRename = { title -> onRename(row, title) },
                        onDelete = { onDelete(row) },
                    )
                }
            }
        }
    }
}

/** "5m ago" / "5 mnt lalu", worded in the app's current language. */
@Composable
fun SessionAge.label(): String = when (unit) {
    AgeUnit.NOW -> stringResource(R.string.age_now)
    AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value)
    AgeUnit.HOURS -> stringResource(R.string.age_hours, value)
    AgeUnit.DAYS -> stringResource(R.string.age_days, value)
    AgeUnit.MONTHS -> stringResource(R.string.age_months, value)
}

/** One session. First and last round the card's corners; the rest are square. */
@Composable
private fun SessionRowItem(
    row: SessionRow,
    showDirectory: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val r = OpenCodeTheme.tokens.radius.lg
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp,
    )

    val age = if (row.age != null) row.age.label() else null
    val directory = row.directory
        ?.takeIf { showDirectory }
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() }
    val meta = listOfNotNull(age, directory, row.cost).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            Box {
                val moreLabel = stringResource(R.string.history_more)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = moreLabel }
                        .clickable(role = Role.Button) { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.More, tint = colors.onSurfaceMuted, size = 20.dp)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_rename), style = OpenCodeTypography.body, color = colors.onSurface) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), style = OpenCodeTypography.body, color = colors.danger) },
                        onClick = { menuOpen = false; confirmingDelete = true },
                    )
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            initial = row.title,
            onConfirm = { title -> renaming = false; onRename(title) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmingDelete) {
        LocalizedAlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.history_delete_title), style = OpenCodeTypography.title, color = colors.onSurface) },
            text = { Text(stringResource(R.string.history_delete_body), style = OpenCodeTypography.body, color = colors.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var text by remember { mutableStateOf(initial) }

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_rename_title), style = OpenCodeTypography.title, color = colors.onSurface) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.outline, RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
                    .padding(horizontal = spacing.md, vertical = spacing.md),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.common_save), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
            }
        },
    )
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs + spacing.xxs),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (selected) colors.accent else colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
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
