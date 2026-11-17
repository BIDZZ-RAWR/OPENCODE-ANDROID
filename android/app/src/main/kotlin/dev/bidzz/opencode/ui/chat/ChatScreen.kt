package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.bidzz.opencode.R
import dev.bidzz.opencode.ui.sessions.label
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.sessions.SessionRow
import kotlinx.coroutines.launch

/**
 * The main screen: a conversation with the agent.
 *
 * Structure is a plain column — header, scrolling transcript, composer — inside a
 * side drawer that holds navigation and recent sessions. Nothing here is decorative:
 * there are no cards, gradients or shadows, per the anti-slop rules in `plan.md` §12.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    sessions: List<SessionRow>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPermissionReply: (String) -> Unit,
    onAgent: (String) -> Unit,
    onModel: (dev.bidzz.opencode.core.api.dto.ModelRef) -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDrawerOpen: () -> Unit,
    onSessionsClick: () -> Unit,
    onProjectClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Follow the conversation as it grows, but only while the reader is already at the bottom,
    // so scrolling up during a stream is not yanked back down.
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty() && nearBottom) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    /** Closes the drawer, then runs [action]; the drawer must not stay open over the next screen. */
    fun closeThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                projectTitle = headerTitle(state.workspacePath),
                currentSessionID = state.sessionID,
                sessions = sessions,
                onNewSession = { closeThen(onNewSession) },
                onOpenSession = { id -> closeThen { onOpenSession(id) } },
                onAllSessions = { closeThen(onSessionsClick) },
                onFiles = { closeThen(onFilesClick) },
                onProject = { closeThen(onProjectClick) },
                onSettings = { closeThen(onSettingsClick) },
            )
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            ChatHeader(
                title = headerTitle(state.workspacePath),
                engineState = state.engineState,
                busy = state.busy,
                onMenu = {
                    onDrawerOpen()
                    scope.launch { drawerState.open() }
                },
                onStatusClick = onStatusClick,
                onNewSession = onNewSession,
            )

            Box(Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyChat(
                        engineReady = state.workspaceReady,
                        engineFailed = state.failure != null,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.lg,
                            end = spacing.lg,
                            top = spacing.md,
                            bottom = spacing.lg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            // Computed here, not when the repository emitted, so the label
                            // upgrades to human names as soon as the catalog loads.
                            MessageBubble(
                                modifier = Modifier.animateItem(),
                                role = message.role,
                                markdown = message.text,
                                streaming = message.streaming,
                                tools = message.tools,
                                provenance = provenanceLabel(
                                    message.agent,
                                    message.model,
                                    state.agents,
                                    state.models,
                                ),
                                showActions = !state.busy && message.id == state.messages.lastOrNull()?.id,
                            )
                        }
                        // Until the first reply token arrives, the only sign of life is this line
                        // under the user's own bubble; once the reply streams it has its spinner.
                        if (state.busy && state.messages.lastOrNull()?.role == Role.USER) {
                            item(key = "working") { WorkingLine() }
                        }
                        if (state.failure != null || state.failureKey != null) {
                            item(key = "error") {
                                MessageBubble(
                                    role = Role.ERROR,
                                    markdown = state.failure?.message()
                                        ?: stringResource(state.failureKey!!),
                                )
                            }
                        }
                    }
                    // Following stops when the reader scrolls up, so give them the way back.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !nearBottom,
                        enter = scaleIn(tween(140)) + fadeIn(tween(140)),
                        exit = scaleOut(tween(120)) + fadeOut(tween(120)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = spacing.lg, bottom = spacing.md),
                    ) {
                        JumpToLatest(
                            onClick = { scope.launch { listState.animateScrollToItem(state.messages.lastIndex) } },
                        )
                    }
                }
            }

            state.pendingPermission?.let { request ->
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = shown,
                    enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(140)) + fadeOut(tween(140)),
                ) {
                    PermissionPrompt(
                        request = request,
                        onReply = onPermissionReply,
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
            }

            AnimatedVisibility(
                visible = state.draft.startsWith("/") && state.commands.isNotEmpty(),
                enter = expandVertically(tween(160)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(140)) + fadeOut(tween(140)),
            ) {
                CommandSuggestions(
                    commands = state.commands,
                    draft = state.draft,
                    onPick = { name -> onDraftChange("/$name ") },
                )
            }

            // Only on a blank page with nothing typed: once there is a draft or a chat, they are noise.
            if (state.messages.isEmpty() && state.workspaceReady && state.draft.isEmpty()) {
                SuggestionStrip(onSuggestion = onDraftChange)
            }

            Composer(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
                busy = state.busy,
                enabled = state.workspaceReady,
                modifier = Modifier.navigationBarsPadding(),
                controls = {
                    // The newest completed turn decides how full the context window is,
                    // and its own model provides the limit; the ring rides on the chip.
                    val lastTurn = state.messages.lastOrNull { it.tokens != null }
                    val contextUsed = lastTurn?.tokens?.contextTokens()?.takeIf { it > 0 }
                    ChatControls(
                        agents = state.agents,
                        activeAgent = state.activeAgent,
                        models = state.models,
                        activeModel = state.activeModel,
                        onAgent = onAgent,
                        onModel = onModel,
                        contextUsed = contextUsed,
                        contextModel = lastTurn?.model,
                    )
                },
            )
        }
    }
}

/**
 * Menu on the left, the project and the engine's state centred, new session on the right.
 *
 * The status sits under the title as coloured text rather than a pill: it is read at a
 * glance, and tapping it still opens diagnostics.
 */
@Composable
private fun ChatHeader(
    title: String,
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    onMenu: () -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val status = engineState.toStatus(busy)

    // Two bare icons, and the conversation's name small and centred beneath them. The dot
    // carries the engine's state; tapping the name opens diagnostics.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Glyph.Menu, stringResource(R.string.chat_menu), onMenu)
            IconAction(Glyph.NewChat, stringResource(R.string.chat_new_session), onNewSession)
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
                .clickable(onClick = onStatusClick)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor by animateColorAsState(colors.statusColor(status), tween(240), label = "dot")
            Box(
                Modifier
                    .size(dims.statusDotSize)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = title,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = spacing.sm)
                    .widthIn(max = 240.dp),
            )
            GlyphIcon(
                Glyph.ChevronDown,
                tint = colors.onSurfaceFaint,
                size = 16.dp,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
    }
}

/** A round, hairline-edged button: the header's and the drawer's way of saying "tap". */
@Composable
private fun RoundButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = OpenCodeTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.outline, CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = colors.onSurface, size = 20.dp)
    }
}

/**
 * Navigation and recent sessions.
 *
 * What used to be a dropdown behind one glyph: the same destinations, plus the
 * conversations themselves, so switching sessions is one tap instead of a screen.
 */
@Composable
private fun ChatDrawer(
    projectTitle: String,
    currentSessionID: String?,
    sessions: List<SessionRow>,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onAllSessions: () -> Unit,
    onFiles: () -> Unit,
    onProject: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerContentColor = colors.onSurface,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(top = spacing.lg)) {
            Text(
                text = "OpenCode",
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.xl),
            )
            Text(
                text = projectTitle,
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
            )

            DrawerItem(stringResource(R.string.drawer_files), Glyph.File, onFiles)
            DrawerItem(stringResource(R.string.drawer_project), Glyph.Folder, onProject)
            DrawerItem(stringResource(R.string.settings_title), Glyph.Sliders, onSettings)

            Box(
                Modifier
                    .padding(vertical = spacing.sm)
                    .fillMaxWidth()
                    .size(0.5.dp)
                    .background(colors.outline),
            )

            Text(
                text = stringResource(R.string.drawer_recent).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
            )
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_no_sessions),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
                )
            }
        }

        // Only the list scrolls; the destinations above stay put.
        LazyColumn(Modifier.weight(1f)) {
            items(sessions.take(DRAWER_SESSIONS), key = { it.id }) { row ->
                val selected = row.id == currentSessionID
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) colors.surfaceSunken else colors.surface)
                        .clickable { onOpenSession(row.id) }
                        .padding(horizontal = spacing.xl, vertical = spacing.sm),
                ) {
                    Text(
                        text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    row.age?.let { age ->
                        Text(
                            text = age.label(),
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                        )
                    }
                }
            }
        }

        // Pinned at the bottom, within thumb reach: all sessions, and a new one.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised)
                    .border(0.5.dp, colors.outline, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAllSessions)
                    .padding(horizontal = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 20.dp)
                Text(
                    text = stringResource(R.string.drawer_all_sessions),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = spacing.md),
                )
            }
            RoundButton(
                glyph = Glyph.NewChat,
                description = stringResource(R.string.chat_new_session),
                onClick = onNewSession,
                size = 48.dp,
            )
        }
    }
}

@Composable
private fun DrawerItem(label: String, glyph: Glyph, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val tint = colors.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, tint = tint, size = 20.dp)
        Text(
            text = label,
            style = OpenCodeTypography.body,
            color = tint,
            modifier = Modifier.padding(start = spacing.lg),
        )
    }
}

private const val DRAWER_SESSIONS = 20

/** "Working." with a calm pulse: the same words, alive while nothing else has arrived. */
@Composable
private fun WorkingLine() {
    val colors = OpenCodeTheme.colors
    val alpha by rememberInfiniteTransition(label = "working").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Text(
        text = stringResource(R.string.chat_working),
        style = OpenCodeTypography.label,
        color = colors.onSurfaceMuted,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    )
}

/** A small round button that returns to the newest message. */
@Composable
private fun JumpToLatest(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val label = stringResource(R.string.chat_jump_latest)

    Box(
        modifier = modifier
            .size(40.dp)
            .background(colors.surface, CircleShape)
            .border(0.5.dp, colors.outline, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.ArrowDown, tint = colors.onSurface, size = 20.dp)
    }
}

/**
 * Shown before the first message: the brand lockup, and nothing else to read.
 *
 * The starting points are a strip of pills just above the composer (see [SuggestionStrip]), so
 * the middle of the screen stays quiet. While the engine starts, it says so.
 */
@Composable
private fun EmptyChat(engineReady: Boolean, engineFailed: Boolean, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val context = LocalContext.current
    val mascotOn = remember { Mascot.enabled(context) }

    Column(
        // Full width, so the children centre on the screen rather than on their own widest line.
        modifier = modifier
            .cloudBackdrop(mascotOn)
            .fillMaxWidth()
            .widthIn(max = dims.contentMaxWidth)
            .padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.brand_mark),
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(21.dp)),
        )
        // The wordmark from the logo sheet: the name large, "ANDROID" small and spaced, in lime.
        Text(
            text = "OpenCode",
            style = OpenCodeTypography.title.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            modifier = Modifier.padding(top = spacing.xl),
        )
        Text(
            text = "ANDROID",
            style = OpenCodeTypography.label.copy(fontSize = 13.sp, letterSpacing = 6.sp),
            color = colors.accent,
            modifier = Modifier.padding(top = spacing.xs),
        )
        // The pixel cloud pet, below the wordmark: it bobs here, reacts to a tap or a
        // stroke, and mirrors the engine while it warms up.
        if (mascotOn) {
            MascotCloud(
                engineFailed = engineFailed,
                busy = false,
                stopped = !engineReady,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
        // A fresh greeting on every visit to the empty state, in the app's language.
        val greeting = remember { Greetings.from(context) }
        Text(
            text = greeting.first,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xl),
        )
        Text(
            text = greeting.second,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xxs),
        )
        Text(
            text = stringResource(if (engineReady) R.string.empty_title_ready else R.string.empty_title_starting),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xl),
        )
        if (!engineReady) {
            Text(
                text = stringResource(R.string.empty_starting_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

/**
 * Starting points as a row of pills that scrolls sideways, above the composer.
 *
 * Tapping one fills the composer rather than sending, so the user can change it first.
 */
@Composable
private fun SuggestionStrip(onSuggestion: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SUGGESTIONS.forEach { suggestionRes ->
            val suggestion = stringResource(suggestionRes)
            Text(
                text = suggestion,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceRaised)
                    .border(0.5.dp, colors.outline, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSuggestion(suggestion) }
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            )
        }
    }
}

private val SUGGESTIONS = listOf(
    R.string.suggestion_structure,
    R.string.suggestion_bug,
    R.string.suggestion_review,
    R.string.suggestion_tests,
)

private fun dev.bidzz.opencode.core.engine.EngineState.toStatus(busy: Boolean): Status = when (this) {
    dev.bidzz.opencode.core.engine.EngineState.RUNNING -> if (busy) Status.WORKING else Status.READY
    dev.bidzz.opencode.core.engine.EngineState.STARTING,
    dev.bidzz.opencode.core.engine.EngineState.INITIALIZING,
    dev.bidzz.opencode.core.engine.EngineState.STOPPING -> Status.WORKING
    dev.bidzz.opencode.core.engine.EngineState.FAILED -> Status.ERROR
    dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> Status.ERROR
    dev.bidzz.opencode.core.engine.EngineState.STOPPED -> Status.IDLE
}

@Composable
private fun dev.bidzz.opencode.core.engine.EngineState.statusLabel(busy: Boolean): String = stringResource(
    when (this) {
        dev.bidzz.opencode.core.engine.EngineState.RUNNING ->
            if (busy) R.string.chat_status_working else R.string.chat_status_ready
        dev.bidzz.opencode.core.engine.EngineState.INITIALIZING -> R.string.chat_status_preparing
        dev.bidzz.opencode.core.engine.EngineState.STARTING -> R.string.chat_status_starting
        dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> R.string.chat_status_unhealthy
        dev.bidzz.opencode.core.engine.EngineState.STOPPING -> R.string.chat_status_stopping
        dev.bidzz.opencode.core.engine.EngineState.FAILED -> R.string.chat_status_failed
        dev.bidzz.opencode.core.engine.EngineState.STOPPED -> R.string.chat_status_stopped
    },
)
