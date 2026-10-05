package it.palsoftware.pastiera.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import it.palsoftware.pastiera.R
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Numen, the app's mascot: a keycap drawn as pixel art.
 *
 * Sprite rows, one character per pixel: O outline, W key top, S key side, E eye,
 * A amber light, C cheek, M mouth, P tongue/heart, '.' transparent.
 */
private val BODY = listOf(
    "...OOOOOOOOOOOO...",
    "..OWWWWWWWWWWWWO..",
    ".OWWWWWWWWWWWAAWO.",
    "OWWWWWWWWWWWWAAWWO",
    "OWWWWWWWWWWWWWWWWO",
    "OWWWEEWWWWWWEEWWWO",
    "OWWWEEWWWWWWEEWWWO",
    "OWWWEEWWWWWWEEWWWO",
    "OWCCWWWMWWMWWWCCWO",
    "OWWWWWWWMMWWWWWWWO",
    "OWWWWWWWWWWWWWWWWO",
    "OSSSSSSSSSSSSSSSSO",
    "OSSSSSSSSSSSSSSSSO",
    ".OSSSSSSSSSSSSSSO.",
    "..OOOOOOOOOOOOOO..",
)

/** Eyes closed, for blinking. */
private val BLINK = BODY.toMutableList().apply {
    this[5] = "OWWWWWWWWWWWWWWWWO"
    this[6] = "OWWWEEWWWWWWEEWWWO"
    this[7] = "OWWWWWWWWWWWWWWWWO"
}

/** Happy: eyes squeezed shut and an open grin. */
private val HAPPY = BLINK.toMutableList().apply {
    this[8] = "OWCCWWWMWWMWWWCCWO"
    this[9] = "OWWWWWWMPPMWWWWWWO"
    this[10] = "OWWWWWWWMMWWWWWWWO"
}

private val LEGS_APART = listOf(
    "....OO......OO....",
    "...OO........OO...",
)
private val LEGS_TOGETHER = listOf(
    ".....OO....OO.....",
    ".....OO....OO.....",
)

private val HEART = listOf(
    "PP.PP",
    "PPPPP",
    ".PPP.",
    "..P..",
)

private const val SPRITE_WIDTH = 18
private const val SPRITE_HEIGHT = 17
/** Centre of the amber light (in sprite pixels), for its glow. */
private const val LIGHT_X = 14f
private const val LIGHT_Y = 3f

private val INDIGO = Color(0xFF2B2F6B)
private val AMBER = Color(0xFFFFB84D)
private val PALETTE = mapOf(
    'O' to INDIGO,
    'W' to Color(0xFFF5F6FC),
    'S' to Color(0xFFB4B9E0),
    'E' to INDIGO,
    'A' to AMBER,
    'C' to Color(0xFFF4A7B9),
    'M' to INDIGO,
    'P' to Color(0xFFEC7896),
)

enum class NumenFace { Open, Blink, Happy }

/** One frame of the numen, [pixel] dp per sprite pixel, optionally facing left. */
@Composable
fun NumenSprite(
    face: NumenFace,
    legsApart: Boolean,
    facingLeft: Boolean,
    glow: Float,
    pixel: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(pixel * SPRITE_WIDTH, pixel * SPRITE_HEIGHT)) {
        drawNumen(face, legsApart, facingLeft, glow, pixel.toPx())
    }
}

private fun DrawScope.drawNumen(face: NumenFace, legsApart: Boolean, facingLeft: Boolean, glow: Float, p: Float) {
    val body = when (face) {
        NumenFace.Open -> BODY
        NumenFace.Blink -> BLINK
        NumenFace.Happy -> HAPPY
    }
    val rows = body + (if (legsApart) LEGS_APART else LEGS_TOGETHER)
    fun column(x: Float) = if (facingLeft) SPRITE_WIDTH - 1 - x else x
    // Pulsing glow behind the amber light.
    drawCircle(
        color = AMBER.copy(alpha = 0.18f + 0.22f * glow),
        radius = p * (1.9f + 0.5f * glow),
        center = Offset((column(LIGHT_X - 0.5f) + 0.5f) * p, LIGHT_Y * p)
    )
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, key ->
            val color = PALETTE[key] ?: return@forEachIndexed
            // Slight overlap hides hairline gaps between pixels.
            drawRect(color, topLeft = Offset(column(x.toFloat()) * p, y * p), size = Size(p + 0.5f, p + 0.5f))
        }
    }
}

/** The numen's pose at one moment of its routine. */
private data class NumenPose(
    val x: Float,
    val facingLeft: Boolean,
    val face: NumenFace,
    val legsApart: Boolean,
    /** 0 on the ground, 1 at the top of a hop. */
    val hop: Float,
    /** Vertical squash: below 1 squashes (wider), above 1 stretches. */
    val squash: Float,
    val bubble: Float,
    /** 0..1 while the heart floats up; negative when there is none. */
    val heart: Float,
    val glow: Float
)

private const val START = 0.04f
private const val MIDDLE = 0.5f
private const val END = 0.96f
private const val WALK_IN_MS = 3400f // START -> MIDDLE, and MIDDLE -> END
private const val PAUSE_MS = 3600f // the stop in the middle
private const val TURN_MS = 450f
private const val WALK_BACK_MS = 6800f // END -> START
private const val CYCLE_MS = TURN_MS + WALK_IN_MS + PAUSE_MS + WALK_IN_MS + TURN_MS + WALK_BACK_MS
private const val STILL_MS = TURN_MS + WALK_IN_MS + 100f // standing in the middle, facing you
private const val STEP_MS = 190f

private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** The numen's pose [t] ms into its routine: walk to the middle, greet, hop, walk on and back. */
private fun numenPose(t: Float): NumenPose {
    val glow = 0.5f + 0.5f * sin(t / 650f)
    val periodicBlink = (t % 3900f) > 3760f
    val stepping = (t / STEP_MS).toInt() % 2 == 0
    fun walking(x: Float, facingLeft: Boolean) = NumenPose(
        x = x, facingLeft = facingLeft, face = if (periodicBlink) NumenFace.Blink else NumenFace.Open,
        legsApart = stepping, hop = 0f, squash = if (stepping) 1f else 0.95f, bubble = 0f, heart = -1f, glow = glow
    )

    var time = t
    // 1. Turn to face right.
    if (time < TURN_MS) return walking(START, facingLeft = time < TURN_MS / 2).copy(legsApart = false, squash = 1f)
    time -= TURN_MS
    // 2. Walk to the middle.
    if (time < WALK_IN_MS) return walking(START + (MIDDLE - START) * time / WALK_IN_MS, facingLeft = false)
    time -= WALK_IN_MS
    // 3. Pause: blink, say hi, then hop with a heart.
    if (time < PAUSE_MS) {
        val hopStart = 2550f
        val hopEnd = 3100f
        val hop = if (time in hopStart..hopEnd) sin(PI.toFloat() * ramp(time, hopStart, hopEnd)) else 0f
        val squash = when {
            time in (hopStart - 140f)..hopStart -> 0.86f // crouch before the jump
            time in hopStart..(hopStart + 120f) -> 1.08f // stretch on take-off
            time in hopEnd..(hopEnd + 150f) -> 0.84f + 0.16f * ramp(time, hopEnd, hopEnd + 150f) // land
            else -> 1f
        }
        val face = when {
            time in 350f..500f -> NumenFace.Blink
            time >= hopStart - 140f -> NumenFace.Happy
            else -> NumenFace.Open
        }
        val bubble = minOf(ramp(time, 600f, 850f), 1f - ramp(time, 2200f, 2450f))
        val heart = if (time >= hopStart) ramp(time, hopStart, PAUSE_MS) else -1f
        return NumenPose(MIDDLE, false, face, legsApart = false, hop = hop, squash = squash, bubble = bubble, heart = heart, glow = glow)
    }
    time -= PAUSE_MS
    // 4. Walk on to the edge.
    if (time < WALK_IN_MS) return walking(MIDDLE + (END - MIDDLE) * time / WALK_IN_MS, facingLeft = false)
    time -= WALK_IN_MS
    // 5. Turn around.
    if (time < TURN_MS) return walking(END, facingLeft = time >= TURN_MS / 2).copy(legsApart = false, squash = 1f)
    time -= TURN_MS
    // 6. Walk all the way back.
    return walking(END + (START - END) * time / WALK_BACK_MS, facingLeft = true)
}

/** The numen going about its routine across the available width. */
@Composable
fun NumenWalking(modifier: Modifier = Modifier, pixel: Dp = 6.dp) {
    val context = LocalContext.current
    // With animations turned off in Android settings, the numen stands still in the middle.
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val time: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "numen").animateFloat(
            initialValue = 0f,
            targetValue = CYCLE_MS,
            animationSpec = infiniteRepeatable(tween(CYCLE_MS.toInt(), easing = LinearEasing)),
            label = "numenTime"
        )
    } else {
        remember { mutableFloatStateOf(STILL_MS) }
    }
    // Poses are read only while drawing and placing, so frames repaint without recomposing.
    val showBubble by remember { derivedStateOf { numenPose(time.value).bubble > 0f } }
    val headroom = 14 // sprite pixels above it for the hop, the heart and the bubble
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(pixel * (SPRITE_HEIGHT + headroom + 2))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pose = numenPose(time.value)
            val p = pixel.toPx()
            val spriteWidth = p * SPRITE_WIDTH
            val left = (size.width - spriteWidth) * pose.x
            val ground = size.height - p * 2
            val top = ground - p * SPRITE_HEIGHT - p * 7 * pose.hop

            // Soft shadow on the ground, smaller while it is in the air.
            val shadowWidth = spriteWidth * 0.72f * (1f - 0.35f * pose.hop)
            drawOval(
                color = INDIGO.copy(alpha = 0.14f * (1f - 0.4f * pose.hop)),
                topLeft = Offset(left + (spriteWidth - shadowWidth) / 2f, ground - p * 0.6f),
                size = Size(shadowWidth, p * 1.4f)
            )
            withTransform({
                translate(left, top)
                scale(2f - pose.squash, pose.squash, pivot = Offset(spriteWidth / 2f, p * SPRITE_HEIGHT))
            }) {
                drawNumen(pose.face, pose.legsApart, pose.facingLeft, pose.glow, p)
            }
            if (pose.heart >= 0f) {
                translate(left + spriteWidth / 2f - p * 2.5f, top - p * 5 - p * 6 * pose.heart) {
                    HEART.forEachIndexed { y, row ->
                        row.forEachIndexed { x, key ->
                            PALETTE[key]?.let {
                                drawRect(it, Offset(x * p, y * p), Size(p + 0.5f, p + 0.5f), alpha = 1f - pose.heart)
                            }
                        }
                    }
                }
            }
        }

        if (showBubble) {
            val areaWidth = constraints.maxWidth
            val areaHeight = constraints.maxHeight
            Surface(
                modifier = Modifier
                    .offset {
                        val pose = numenPose(time.value)
                        val p = pixel.toPx()
                        val spriteWidth = p * SPRITE_WIDTH
                        val left = (areaWidth - spriteWidth) * pose.x
                        val top = areaHeight - p * 2 - p * SPRITE_HEIGHT - p * 7 * pose.hop
                        IntOffset((left + spriteWidth * 0.62f).roundToInt(), (top - p * 6).roundToInt())
                    }
                    .graphicsLayer { alpha = numenPose(time.value).bubble }
                    .border(2.dp, INDIGO, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                color = Color.White
            ) {
                Text(
                    text = stringResource(R.string.numen_mascot_greeting),
                    maxLines = 1,
                    softWrap = false,
                    color = INDIGO,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/* "Numen" in pixel letters with the amber light top right. X letter, A amber. */
private val WORDMARK = listOf(
    "XX...XX.................................AA",
    "XXX..XX.................................AA",
    "XXX..XX.XX..XX.XXXXXXXXX...XXXX..XXXXX....",
    "XX.X.XX.XX..XX.XX..XX..XX.XX..XX.XX..XX...",
    "XX.X.XX.XX..XX.XX..XX..XX.XXXXXX.XX..XX...",
    "XX..XXX.XX..XX.XX..XX..XX.XX.....XX..XX...",
    "XX..XXX.XX..XX.XX..XX..XX.XX.....XX..XX...",
    "XX...XX..XXXXX.XX..XX..XX..XXXXX.XX..XX...",
)

/** The app's name as a pixel-art wordmark, [pixel] dp per pixel, with a one-pixel drop shadow. */
@Composable
fun NumenWordmark(name: String, modifier: Modifier = Modifier, pixel: Dp = 4.dp) {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val letters = if (light) INDIGO else PALETTE.getValue('W')
    val shadow = if (light) PALETTE.getValue('S') else INDIGO
    Canvas(
        modifier = modifier
            .size(pixel * WORDMARK[0].length, pixel * (WORDMARK.size + 1))
            .semantics { contentDescription = name }
    ) {
        val p = pixel.toPx()
        for ((dy, color) in listOf(1 to shadow, 0 to null)) {
            WORDMARK.forEachIndexed { y, row ->
                row.forEachIndexed { x, key ->
                    val fill = when (key) {
                        'X' -> color ?: letters
                        'A' -> if (color == null) AMBER else return@forEachIndexed
                        else -> return@forEachIndexed
                    }
                    drawRect(fill, topLeft = Offset(x * p, (y + dy) * p), size = Size(p + 0.5f, p + 0.5f))
                }
            }
        }
    }
}
