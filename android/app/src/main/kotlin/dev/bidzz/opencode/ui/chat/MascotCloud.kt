package dev.bidzz.opencode.ui.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * The pixel cloud mascot, in the spirit of Cl0de's (MIT): a flat Canvas pet whose mood
 * follows the engine, reacts to taps, pets, shakes and charging, dresses for the season,
 * and says short lines in the app's language. All art is rectangles on a pixel grid.
 */

enum class Mood { IDLE, WORKING, ERROR, SLEEPY, CELEBRATE, PET, DIZZY, CHARGED }

/** Preferences: the mascot lives in the same `app_settings` file as language and intro. */
object Mascot {
    private const val PREFS = "app_settings"
    private const val KEY_ON = "mascot_enabled"
    private const val KEY_OUTFIT = "mascot_outfit"

    /** Order the Settings row cycles through when tapped. */
    val OUTFIT_CHOICES = listOf("auto", "off", "santa", "party", "witch", "shades")

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, value).apply()
    }

    fun outfit(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OUTFIT, "auto") ?: "auto"

    fun setOutfit(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OUTFIT, value).apply()
    }
}

/** One line in both languages; the app's locale picks the half. */
private data class Line(val id: String, val en: String)

/** Short reactive lines, written for this app (the idea is Cl0de's, the words are not). */
private object Talk {
    private val tap = listOf(
        Line("Boop.", "Boop."),
        Line("Hehe.", "Hehe."),
        Line("Itu nggelitik.", "That tickles."),
        Line("Lagi?", "Again?"),
        Line("*memantul*", "*bounces*"),
    )
    private val pet = listOf(
        Line("Purrr…", "Purrr…"),
        Line("Empuk sekali.", "Softest pets."),
        Line("Lagi, dong.", "More, please."),
        Line("Aku suka kamu.", "I like you."),
    )
    private val dizzy = listOf(
        Line("Aduh… langitnya berputar.", "Whoa… the sky spins."),
        Line("Jangan digoyang!", "Not the shaking!"),
    )
    private val charged = listOf(
        Line("Penuh tenaga.", "Fully charged."),
        Line("Listrik datang.", "Power's here."),
    )
    private val celebrate = listOf(
        Line("Berhasil!", "Shipped!"),
        Line("Selesai, rapi.", "Done and dusted."),
        Line("Toplima awan!", "High cloud five!"),
    )
    private val error = listOf(
        Line("Awan badai…", "Stormy skies…"),
        Line("Aku baik saja; tugasmu yang tidak.", "I'm fine; your task isn't."),
    )
    private val sleep = listOf(
        Line("Zzz…", "Zzz…"),
        Line("Memimpikan langit biru.", "Dreaming of blue skies."),
    )

    fun of(kind: String, indonesian: Boolean): String? {
        val pool = when (kind) {
            "tap" -> tap; "pet" -> pet; "dizzy" -> dizzy; "charged" -> charged
            "celebrate" -> celebrate; "error" -> error; "sleep" -> sleep
            else -> return null
        }
        val line = pool.random()
        return if (indonesian) line.id else line.en
    }
}

/**
 * The season, resolved once per composition: which outfit the cloud wears and what drifts
 * around it. Pure so the calendar can be tested without a device.
 */
internal fun seasonFor(month: Int, day: Int): Pair<String?, String?> = when {
    (month == 12 && day == 31) || (month == 1 && day <= 2) -> "party" to "confetti"
    month == 12 -> "santa" to "snow"
    month == 10 && day >= 25 -> "witch" to null
    month in 6..8 -> "shades" to null
    month in 9..11 -> null to "leaf"
    month == 2 && day == 14 -> null to "heart"
    else -> null to null
}

/** Threshold helper for the accelerometer: a sharp jolt, not two, and not while cooling down. */
internal fun isShake(delta: Float, nowMs: Long, lastShakeMs: Long): Boolean =
    delta > SHAKE_THRESHOLD && nowMs - lastShakeMs > 1500

internal const val SHAKE_THRESHOLD = 22f

// The cloud itself: one grid, 'w' body. Eyes and mouth are drawn over it, per mood.
private val CLOUD = listOf(
    "....wwwww....",
    "..wwwwwwwww..",
    ".wwwwwwwwwww.",
    "wwwwwwwwwwwww",
    "wwwwwwwwwwwww",
    ".wwwwwwwwwww.",
)
private const val CELL = 10f // dp per pixel cell

// Outfits: rows drawn above (or over) the cloud, each letter one coloured cell.
private val SANTA = listOf(
    "....w...",
    "...rrr..",
    "..rrrr..",
    ".rrrrr..",
    "rrrrrrr.",
    "wwwwwwww",
)
private val PARTY = listOf(
    "...p...",
    "...pp..",
    "..ppp..",
    "..pppp.",
    ".ppppp.",
    ".y.y.y.",
)
private val WITCH = listOf(
    "...k...",
    "...kk..",
    "..kkk..",
    ".kkkk..",
    "kkkkkkk",
)
private val SHADES = listOf("kkk.kkk")

private val OUTFIT_ART: Map<String, Pair<List<String>, Map<Char, Color>>> = mapOf(
    "santa" to (
        SANTA to mapOf(
            'r' to Color(0xFFD9575B),
            'w' to Color(0xFFF4F4F6),
        )
        ),
    "party" to (
        PARTY to mapOf(
            'p' to Color(0xFF9F9DF2),
            'y' to Color(0xFFF2C94C),
        )
        ),
    "witch" to (WITCH to mapOf('k' to Color(0xFF3A3150))),
    "shades" to (SHADES to mapOf('k' to Color(0xFF18181A))),
)

/** Mascot with speech, gestures, sensors and the season. Placed in the empty chat. */
@Composable
fun MascotCloud(
    engineFailed: Boolean,
    busy: Boolean,
    stopped: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val indonesian = remember { context.resources.configuration.locales[0].language == "id" }

    var speech by remember { mutableStateOf<String?>(null) }
    var moodOverride by remember { mutableStateOf<Mood?>(null) }
    var charging by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    fun setMood(mood: Mood, ms: Long, line: String? = null) {
        moodOverride = mood
        speech = line
        scope.launch {
            delay(ms)
            moodOverride = null
            speech = null
        }
    }

    fun talk(kind: String, mood: Mood, ms: Long = 1600) {
        Talk.of(kind, indonesian)?.let { setMood(mood, ms, it) }
    }

    // A run that just ended gets a small celebration.
    var wasBusy by remember { mutableStateOf(busy) }
    LaunchedEffect(busy) {
        if (wasBusy && !busy && !engineFailed) {
            celebrate = true
            speech = Talk.of("celebrate", indonesian)
            delay(2200)
            celebrate = false
            speech = null
        }
        wasBusy = busy
    }

    // Charging state rides the standard battery broadcast.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (charging) talk("charged", Mood.CHARGED)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // A sharp jolt of the accelerometer makes the cloud dizzy.
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            private val last = FloatArray(3)
            private var lastShakeAt = 0L
            override fun onSensorChanged(event: SensorEvent) {
                val delta = abs(event.values[0] - last[0]) + abs(event.values[1] - last[1]) + abs(event.values[2] - last[2])
                last[0] = event.values[0]; last[1] = event.values[1]; last[2] = event.values[2]
                val now = System.currentTimeMillis()
                if (isShake(delta, now, lastShakeAt)) {
                    lastShakeAt = now
                    talk("dizzy", Mood.DIZZY, 1800)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager.unregisterListener(listener) }
    }

    val outfitChoice = remember { Mascot.outfit(context) }
    val calendar = remember { java.util.Calendar.getInstance() }
    val (autoOutfit, particles) = remember {
        seasonFor(calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH))
    }
    val outfitId = when (outfitChoice) {
        "auto" -> autoOutfit
        "off" -> null
        else -> outfitChoice
    }

    val mood = moodOverride ?: when {
        engineFailed -> Mood.ERROR
        celebrate -> Mood.CELEBRATE
        busy -> Mood.WORKING
        charging -> Mood.CHARGED
        stopped -> Mood.SLEEPY
        else -> Mood.IDLE
    }

    val bob = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "bob",
    )
    val fall = rememberInfiniteTransition(label = "fall").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "fall",
    )
    val bounce = remember { Animatable(1f) }
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        if (mood == Mood.DIZZY) {
            wobble.snapTo(1f)
            wobble.animateTo(0f, tween(1600, easing = LinearEasing))
        }
    }

    val colors = OpenCodeTheme.colors

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(width = 190.dp, height = 150.dp)
                .pointerInput(Unit) {
                    detectTapGestures { _ ->
                        scope.launch {
                            bounce.snapTo(0.85f)
                            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
                        }
                        talk("tap", Mood.PET)
                    }
                }
                .pointerInput(Unit) {
                    var petted = 0f
                    detectDragGestures(
                        onDragStart = { petted = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            petted += abs(amount.x) + abs(amount.y)
                            if (petted > 90f) {
                                petted = -999f
                                talk("pet", Mood.PET)
                            }
                        },
                    )
                }
                .graphicsLayerMirror(bounce.value, wobble.value),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(Modifier.size(width = 190.dp, height = 150.dp)) {
                translate(0f, sin(bob.value) * 2.dp.toPx()) {
                    drawOutfit(outfitId, cell = CELL.dp.toPx(), width = size.width)
                    drawCloud(colors.onSurface, colors.background, colors.accent, colors.danger, mood)
                }
                drawParticles(particles, fall.value)
            }
        }
        AnimatedVisibility(
            visible = speech != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
        ) {
            val colors = OpenCodeTheme.colors
            val spacing = OpenCodeTheme.tokens.spacing
            Text(
                text = speech.orEmpty(),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(colors.surfaceRaised, RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
                    .border(0.5.dp, colors.outline, RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
                    .padding(horizontal = spacing.md, vertical = spacing.xxs),
            )
        }
    }
}

/** graphicsLayer with the bounce scale and the dizzy wobble, kept out of the way. */
private fun Modifier.graphicsLayerMirror(scale: Float, wobble: Float): Modifier =
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = sin(wobble * 6f) * 8f * wobble
    }

private fun DrawScope.drawCloud(
    body: Color,
    eye: Color,
    accent: Color,
    danger: Color,
    mood: Mood,
) {
    val cell = CELL.dp.toPx()
    val width = CLOUD.maxOf { it.length } * cell
    val left = (size.width - width) / 2
    val top = size.height - CLOUD.size * cell

    CLOUD.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            if (ch == 'w') drawRect(body, Offset(left + col * cell, top + row * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }

    // Eyes: two two-cell windows; they look toward the work while the agent runs.
    val look = when (mood) {
        Mood.WORKING -> 1
        Mood.SLEEPY -> 0
        else -> 0
    }
    val eyeRow = 2
    val eyeCols = listOf(3, 4, 8, 9)
    val closed = mood == Mood.SLEEPY
    eyeCols.forEach { col ->
        val shifted = col + look
        if (closed) {
            drawRect(eye, Offset(left + shifted * cell, top + (eyeRow + 1) * cell), Size(cell + 0.5f, cell * 0.4f))
        } else {
            drawRect(eye, Offset(left + shifted * cell, top + eyeRow * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }

    // Mouth: quiet by default; a small o while working, a smile when happy, a zigzag in a storm.
    val mouthRow = 4f
    fun mouthCell(col: Int, color: Color) =
        drawRect(color, Offset(left + col * cell, top + mouthRow * cell), Size(cell + 0.5f, cell + 0.5f))
    when (mood) {
        Mood.WORKING -> mouthCell(6, eye)
        Mood.PET, Mood.CELEBRATE, Mood.CHARGED -> {
            mouthCell(5, accent); mouthCell(6, accent); mouthCell(7, accent)
        }
        Mood.ERROR -> {
            mouthCell(4, danger); mouthCell(6, danger); mouthCell(8, danger)
        }
        else -> Unit
    }
}

/** The outfit, drawn above the cloud; shades go over the eyes instead. */
private fun DrawScope.drawOutfit(outfitId: String?, cell: Float, width: Float) {
    if (outfitId == null) return
    val (grid, colors) = OUTFIT_ART[outfitId] ?: return
    val overEyes = outfitId == "shades"
    val gridWidth = grid.maxOf { it.length } * cell
    val left = (width - gridWidth) / 2
    val cloudTop = size.height - CLOUD.size * CELL.dp.toPx()
    // Shades sit exactly on the eye row (row 2 of the cloud grid); hats float above.
    val top = if (overEyes) cloudTop + 2 * CELL.dp.toPx() else cloudTop - grid.size * cell + 2f
    grid.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            colors[ch]?.let { drawRect(it, Offset(left + col * cell, top + row * cell), Size(cell + 0.5f, cell + 0.5f)) }
        }
    }
}

/** Ambient season particles drifting down through the mascot's sky. */
private fun DrawScope.drawParticles(kind: String?, fall: Float) {
    if (kind == null) return
    val palette = when (kind) {
        "snow" -> listOf(Color(0xFFB8C4D4))
        "confetti" -> listOf(Color(0xFFF2C94C), Color(0xFF9F9DF2), Color(0xFF5CB885), Color(0xFFD9575B))
        "leaf" -> listOf(Color(0xFFD0794A), Color(0xFFB98A3C))
        "heart" -> listOf(Color(0xFFD9575B))
        else -> return
    }
    repeat(10) { i ->
        val seed = (i * 91.7f) % 1f
        val x = seed * size.width
        val speed = 0.6f + seed * 0.8f
        val y = ((fall * speed + seed) % 1f) * size.height
        val color = palette[i % palette.size]
        drawRect(color.copy(alpha = 0.7f), Offset(x, y), Size(4.dp.toPx(), 4.dp.toPx()))
    }
}

/**
 * A quiet backdrop: a few dim pixel-cloud clusters along the top of the screen, the way
 * Cl0de keeps its screens soft. Applied behind the empty chat.
 */
@Composable
fun Modifier.cloudBackdrop(enabled: Boolean): Modifier {
    if (!enabled) return this
    val colors = OpenCodeTheme.colors
    return drawBehind {
        val alpha = 0.05f
        val clusters = listOf(
            Triple(0.08f, 0.02f, 1.0f),
            Triple(0.42f, 0.05f, 0.8f),
            Triple(0.74f, 0.01f, 1.2f),
        )
        val cell = 9.dp.toPx()
        clusters.forEach { (fx, fy, scale) ->
            val c = (cell * scale).coerceAtLeast(4f)
            val left = fx * size.width
            val top = fy * size.height
            BACKDROP_CLOUD.forEachIndexed { row, line ->
                line.forEachIndexed { col, ch ->
                    if (ch == 'w') {
                        drawRect(
                            colors.onSurface.copy(alpha = alpha),
                            Offset(left + col * c, top + row * c),
                            Size(c + 0.5f, c + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

private val BACKDROP_CLOUD = listOf(
    "...wwww....",
    ".wwwwwwww..",
    "wwwwwwwwwww",
)
