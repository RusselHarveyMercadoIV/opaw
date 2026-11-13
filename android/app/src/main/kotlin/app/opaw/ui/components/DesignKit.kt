package app.opaw.ui.components

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlin.math.min

// Pieces shared across screens. Mirrors DesignKit.swift and the shared views in Theme.swift.

val Ink = Color(0xFF111111)

/** Settings > Haptics. Set from the settings store. */
object Feedback {
    @Volatile var hapticsOn = true
}

/**
 * One haptic per action, as on iOS (7c). Sounds are added with the Care screens.
 */
class Haptics(private val view: View) {
    private fun fire(constant: Int) {
        if (Feedback.hapticsOn) view.performHapticFeedback(constant)
    }

    /** Log saved, walk saved. */
    fun success() = fire(HapticFeedbackConstants.CONFIRM)
    /** Tap a Care button, flip a switch, pause a walk. */
    fun tap() = fire(HapticFeedbackConstants.VIRTUAL_KEY)
    /** Change day, swipe between pets. */
    fun select() = fire(HapticFeedbackConstants.CLOCK_TICK)
    /** Undo, pull to refresh letting go. */
    fun soft() = fire(HapticFeedbackConstants.KEYBOARD_TAP)
    /** Hold a log until the popup opens, start or resume a walk. */
    fun medium() = fire(HapticFeedbackConstants.LONG_PRESS)
    /** Save failed. */
    fun error() = fire(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** Press (7d): scale to 0.94 in 120 ms, ease-out. The counterpart of `.buttonStyle(.pressable)`. */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** The soft shadow under floating round buttons. */
fun Modifier.floatingShadow(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier =
    shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))

/** A white speech bubble with a sharp bottom-left corner, as the pet talks. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, size: Int = 16) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    Text(
        text,
        style = opaw(size.sp, FontWeight.ExtraBold),
        color = Ink,
        maxLines = 1,
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(Color.White, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The design's small repeating motions. */
enum class LoopMotion(val duration: Double) {
    BOB(1.4), BREATHE(3.2), DROOP(4.0), TROT(0.5), TAG_POP(1.6)
}

/** Repeats a motion back and forth, like `.loopMotion(_:duration:)`. */
fun Modifier.loopMotion(motion: LoopMotion?, duration: Double? = null): Modifier = if (motion == null) this else composed {
    val half = (((duration ?: motion.duration) / 2) * 1000).toInt()
    val t by rememberInfiniteTransition(label = "loop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(half, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "loop",
    )
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    graphicsLayer {
        when (motion) {
            LoopMotion.BOB -> { translationY = lerp(0f, -10f) * density; rotationZ = lerp(-1.5f, 1.5f) }
            LoopMotion.BREATHE -> { val s = lerp(1f, 1.03f); scaleX = s; scaleY = s }
            LoopMotion.DROOP -> { translationY = lerp(8f, 12f) * density; rotationZ = lerp(-3f, -1f) }
            LoopMotion.TROT -> { translationY = lerp(0f, -5f) * density; rotationZ = lerp(0f, 3f) }
            LoopMotion.TAG_POP -> { rotationZ = lerp(-12f, -6f); val s = lerp(1f, 1.12f); scaleX = s; scaleY = s }
        }
    }
}

/** The pet's cut-out photo with the white sticker edge, or a soft paw before there is one. */
@Composable
fun StickerPet(
    image: Bitmap?,
    modifier: Modifier = Modifier,
    edge: Dp = 5.dp,
    motion: LoopMotion? = LoopMotion.BOB,
    duration: Double? = null,
    desaturate: Boolean = false,
) {
    Box(modifier.loopMotion(motion, duration), contentAlignment = Alignment.Center) {
        if (image == null) {
            Icon(DesignIcon.PAW, Modifier.padding(28.dp), size = 120.dp, tint = Ink.copy(alpha = 0.09f))
            return@Box
        }
        val bitmap = remember(image) { image.asImageBitmap() }
        val white = ColorFilter.tint(Color.White, BlendMode.SrcIn)
        val shadow = ColorFilter.tint(Color.Black.copy(alpha = 0.16f), BlendMode.SrcIn)
        val color = if (desaturate) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) }) else null
        // The soft drop shadow, then the white edge drawn in eight directions, then the photo.
        Image(bitmap, null, Modifier.fillMaxSize().offset(y = edge * 2).alpha(0.9f), contentScale = ContentScale.Fit, colorFilter = shadow)
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)) {
            Image(bitmap, null, Modifier.fillMaxSize().offset(edge * dx, edge * dy), contentScale = ContentScale.Fit, colorFilter = white)
        }
        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = color)
    }
}

/** The black full-width pill button from the designs: 64 high in onboarding, 62 in Premium. */
@Composable
fun BlackPillButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 64.dp,
    inverted: Boolean = false,
    fontSize: Int = 17,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.3f)
            .pressable(enabled, onClick = onClick)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (inverted) 0.07f else 0.18f), spotColor = Color.Black.copy(alpha = if (inverted) 0.07f else 0.18f))
            .background(if (inverted) Color.White else Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = opaw(fontSize.sp, FontWeight.ExtraBold), color = if (inverted) Ink else Color.White, maxLines = 1)
    }
}

/** The design's on/off switch: black when on, light gray when off. */
@Composable
fun DesignToggle(isOn: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val fill by animateColorAsState(if (isOn) Ink else Color(0xFFDCDCDF), label = "toggle")
    val knob by animateFloatAsState(if (isOn) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "knob")
    Box(
        modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Switch) { haptics.tap(); onChange(!isOn) },
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .offset(x = 20.dp * knob)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** An icon in a light gray circle, used at the start of list rows. */
@Composable
fun RowIcon(icon: DesignIcon, size: Dp = 36.dp, iconSize: Dp = 17.dp) {
    Box(Modifier.size(size).background(Opaw.colors.chip, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, size = iconSize)
    }
}

/** A white floating circle with an icon, used for top-bar and bottom-bar actions. */
@Composable
fun RoundIcon(icon: DesignIcon, modifier: Modifier = Modifier, size: Dp = 54.dp, tint: Color = Opaw.colors.ink, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .pressable(onClick = onClick)
            .floatingShadow()
            .background(Opaw.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = size * 0.41f, tint = tint)
    }
}

/** A sparkle chip, such as "Detected" or "Avatar ready". */
@Composable
fun SparkleChip(text: String, size: Int = 13, iconSize: Dp = 14.dp) {
    val small = size < 12
    Row(
        Modifier
            .background(Ink, CircleShape)
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 4.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DesignIcon.SPARKLE, size = iconSize, weight = 2.4f, tint = Color.White)
        Text(text, style = opaw(size.sp, FontWeight.ExtraBold), color = Color.White)
    }
}

/** A small round face: a photo, an initial, or an empty circle, on a white rim. */
@Composable
fun MiniFace(image: Bitmap?, size: Dp = 42.dp, rim: Dp = 3.dp, fill: Color = Opaw.colors.chip, initial: String? = null) {
    Box(
        Modifier.size(size).background(Opaw.colors.surface, CircleShape).padding(rim).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Image(remember(image) { image.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            initial != null -> Text(initial, style = opaw((size.value * 0.4f).sp, FontWeight.Black), color = Opaw.colors.muted)
        }
    }
}

/** A thin black progress bar on a light track. */
@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), tween(400), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Opaw.colors.track)) {
        Box(Modifier.fillMaxWidth(shown).height(6.dp).background(Ink, CircleShape))
    }
}

/** Three faint silhouettes (dog, cat, bunny) taking turns to appear, every 9 seconds. */
@Composable
fun GhostCycle(modifier: Modifier = Modifier) {
    val time by rememberInfiniteTransition(label = "ghost").animateFloat(
        0f, 9f, infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "ghost",
    )
    val shapes = remember { (0..2).map(::ghostPath) }
    Canvas(modifier) {
        val s = min(size.width / 200f, size.height / 220f)
        shapes.forEachIndexed { index, path ->
            val (opacity, scale) = ghostState(time - index * 3f)
            if (opacity <= 0f) return@forEachIndexed
            scale(scale * s, pivot = Offset.Zero) {
                drawPath(path, Ink.copy(alpha = 0.09f * opacity))
            }
        }
    }
}

/** The design's ghost-cycle keyframes: fade in by 6%, hold to 28%, fade out by 34%. */
private fun ghostState(time: Float): Pair<Float, Float> {
    val t = (((time % 9f) + 9f) % 9f) / 9f
    return when {
        t < 0.06f -> (t / 0.06f).let { it to 0.94f + 0.06f * it }
        t < 0.28f -> 1f to 1f
        t < 0.34f -> ((t - 0.28f) / 0.06f).let { (1 - it) to 1 + 0.03f * it }
        else -> 0f to 1.03f
    }
}

/** Silhouettes drawn in a 200×220 box: 0 dog, 1 cat, 2 bunny. */
private fun ghostPath(kind: Int): Path {
    val path = Path()
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotate: Float = 0f) {
        val oval = Path().apply { addOval(androidx.compose.ui.geometry.Rect(-rx, -ry, rx, ry)) }
        val m = androidx.compose.ui.graphics.Matrix().apply { translate(cx, cy); rotateZ(rotate) }
        oval.transform(m)
        path.addPath(oval)
    }
    fun pill(x: Float, y: Float, w: Float, h: Float) {
        path.addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, androidx.compose.ui.geometry.CornerRadius(w / 2)))
    }
    when (kind) {
        0 -> {
            ellipse(100f, 165f, 56f, 50f); ellipse(100f, 78f, 40f, 40f)
            ellipse(64f, 70f, 13f, 30f, 22f); ellipse(136f, 70f, 13f, 30f, -22f)
            ellipse(100f, 100f, 20f, 15f); pill(70f, 180f, 20f, 36f); pill(110f, 180f, 20f, 36f)
        }
        1 -> {
            ellipse(100f, 160f, 46f, 56f); ellipse(100f, 82f, 36f, 36f)
            path.moveTo(68f, 66f); path.lineTo(74f, 28f); path.lineTo(96f, 52f); path.close()
            path.moveTo(132f, 66f); path.lineTo(126f, 28f); path.lineTo(104f, 52f); path.close()
            // The tail, as a thick curve approximated by a filled band.
            path.addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M139 199 C176 194 184 156 168 131 L160 137 C172 156 168 186 149 193 Z").toPath())
            pill(76f, 184f, 18f, 32f); pill(106f, 184f, 18f, 32f)
        }
        else -> {
            ellipse(100f, 168f, 50f, 46f); ellipse(100f, 104f, 32f, 32f)
            ellipse(84f, 48f, 11f, 38f, -8f); ellipse(116f, 48f, 11f, 38f, 8f); ellipse(150f, 186f, 14f, 14f)
        }
    }
    return path
}

