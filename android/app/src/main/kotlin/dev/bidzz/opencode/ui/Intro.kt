package dev.bidzz.opencode.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * The cold-start intro, the way Cl0de (MIT) does it: a flat, hand-timed overlay that plays
 * on top of the app while the engine starts — once per process, skipped with a tap,
 * toggleable and replayable from Settings. Drawn with Canvas only, no glow, no images.
 *
 * Our variant traces the OpenCode mark (a rounded frame with a window), fills it, lets the
 * lower block of the window rise, and brings the name up letter by letter.
 */
object Intro {
    private const val PREFS = "app_settings"
    private const val KEY = "intro_enabled"

    /** Already shown in this process. */
    var played = false
        private set

    /** Called by [IntroHost] the moment the intro starts (or would have). */
    internal fun markPlayed() {
        played = true
    }

    /** Bumped from Settings to replay the intro on top of the current screen. */
    val replay = mutableIntStateOf(0)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, value)
            .apply()
    }
}

/** [a, b] of the timeline → 0..1 with a smooth start and end. */
private fun seg(t: Float, a: Float, b: Float): Float {
    val k = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/** 0..1 with a little overshoot, for things that pop into place. */
private fun pop(t: Float, a: Float, b: Float): Float {
    val k = ((t - a) / (b - a)).coerceIn(0f, 1f)
    if (k == 0f) return 0f
    val s = 1.70158f * 1.4f
    val x = k - 1
    return x * x * ((s + 1) * x + s) + 1
}

@Composable
fun IntroHost() {
    val context = LocalContext.current
    val replay by Intro.replay
    var show by remember {
        mutableStateOf(!Intro.played && Intro.enabled(context) && !animationsOff(context))
    }
    LaunchedEffect(replay) { if (replay > 0) show = true }
    if (!show) return

    val t = remember(replay) { Animatable(0f) }
    val fade = remember(replay) { Animatable(1f) }
    LaunchedEffect(replay) {
        Intro.markPlayed()
        if (animationsOff(context)) {
            // Respect "remove animations": no overlay at all on a replay either.
            show = false
            return@LaunchedEffect
        }
        t.snapTo(0f)
        fade.snapTo(1f)
        t.animateTo(1f, tween(2400, easing = LinearEasing))
        fade.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        show = false
    }

    val colors = OpenCodeTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { show = false },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 96.dp, height = 120.dp)) {
                val p = t.value
                val w = size.width
                val h = size.height
                val stroke = w * 0.05f

                // 1. the frame is traced, then filled
                val frame = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(w * 0.22f)))
                }
                val measure = PathMeasure().apply { setPath(frame, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * seg(p, 0f, 0.3f), drawn, true)
                drawPath(drawn, colors.accent, style = Stroke(stroke, cap = StrokeCap.Round))

                val fill = seg(p, 0.26f, 0.44f)
                if (fill > 0f) {
                    drawRoundRect(
                        colors.accent,
                        Offset.Zero,
                        Size(w, h),
                        CornerRadius(w * 0.22f),
                        alpha = fill,
                    )
                }

                // 2. the window is cut out of the frame
                val window = pop(p, 0.4f, 0.56f)
                if (window > 0f) {
                    val ww = w * 0.5f * window
                    val wh = h * 0.6f * window
                    drawRoundRect(
                        colors.background,
                        Offset(w * 0.5f - ww / 2, h * 0.5f - wh / 2),
                        Size(ww, wh),
                        CornerRadius(ww * 0.14f * window),
                    )
                }

                // 3. the lower block inside the window rises
                val rise = seg(p, 0.48f, 0.62f)
                if (rise > 0f) {
                    val bh = h * 0.4f * rise
                    drawRoundRect(
                        colors.accent.copy(alpha = 0.45f),
                        Offset(w * 0.25f, h * 0.8f - bh),
                        Size(w * 0.5f, bh),
                        CornerRadius(w * 0.04f),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            RiseText("OpenCode", t.value, from = 0.55f, to = 0.78f)
            Spacer(Modifier.height(4.dp))
            val tag = seg(t.value, 0.74f, 0.88f)
            Text(
                text = "ANDROID",
                style = OpenCodeTypography.label.copy(fontSize = 13.sp, letterSpacing = 6.sp),
                color = colors.accent,
                modifier = Modifier.graphicsLayer {
                    alpha = tag
                    translationY = (1 - tag) * 8.dp.toPx()
                },
            )
        }
    }
}

/** A word letter by letter: each one rises into place a little after the previous. */
@Composable
private fun RiseText(text: String, t: Float, from: Float, to: Float) {
    val colors = OpenCodeTheme.colors
    Row {
        text.forEachIndexed { i, ch ->
            val start = from + (to - from) * 0.6f * i / text.length
            val end = from + (to - from) * (0.4f + 0.6f * (i + 1) / text.length)
            val k = seg(t, start, end)
            Text(
                text = ch.toString(),
                style = OpenCodeTypography.title.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = k
                    translationY = (1 - k) * 18.dp.toPx()
                },
            )
        }
    }
}
