package app.opaw.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.OpawApplication
import app.opaw.core.Temperament
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlin.math.PI
import kotlin.math.cos

// More pieces of adding a pet: the pet acting out each temperament, the care icons on the welcome
// screen, and joining with a code.

/**
 * The pet acting out a temperament, as it will on Home (10f, 10g): Playful spins and hops, Calm
 * strolls back and forth, Curious leans in, Shy peeks in from the side, Goofy wobbles.
 * [amplitude] scales the movement to the size it's drawn at.
 */
internal fun Modifier.temperamentMotion(temperament: Temperament, amplitude: Float = 1f): Modifier = composed {
    val period = when (temperament) {
        Temperament.PLAYFUL -> 2200
        Temperament.CALM -> 6000
        Temperament.CURIOUS -> 2800
        Temperament.SHY -> 3400
        Temperament.GOOFY -> 1600
        Temperament.SLEEPY -> 3400
    }
    val t by rememberInfiniteTransition(label = "temperament").animateFloat(
        0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))
    fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x
    graphicsLayer {
        val unit = density * amplitude
        when (temperament) {
            Temperament.PLAYFUL -> when {
                t < 0.35f -> { val x = ease(t / 0.35f); translationY = -14f * x * unit; rotationZ = 180f * x }
                t < 0.7f -> { val x = ease((t - 0.35f) / 0.35f); translationY = -14f * (1 - x) * unit; rotationZ = 180f + 180f * x }
                else -> { translationY = 0f; rotationZ = 0f }
            }
            Temperament.CALM -> when {
                t < 0.45f -> { translationX = lerp(-10f, 10f, ease(t / 0.45f)) * unit; scaleX = 1f }
                t < 0.5f -> { translationX = 10f * unit; scaleX = -1f }
                t < 0.95f -> { translationX = lerp(10f, -10f, ease((t - 0.5f) / 0.45f)) * unit; scaleX = -1f }
                else -> { translationX = -10f * unit; scaleX = 1f }
            }
            Temperament.CURIOUS -> {
                val wave = ((1 - cos(2 * PI * t)) / 2).toFloat()
                val s = lerp(0.86f, 1.08f, wave)
                scaleX = s; scaleY = s
                translationY = lerp(4f, -2f, wave) * unit
            }
            Temperament.SHY -> translationX = when {
                t < 0.4f -> lerp(26f, 4f, ease(t / 0.4f))
                t < 0.6f -> 4f
                else -> lerp(4f, 26f, ease((t - 0.6f) / 0.4f))
            } * unit
            Temperament.GOOFY -> {
                val rotations = floatArrayOf(-10f, 8f, -6f, 12f, -10f)
                val lifts = floatArrayOf(0f, -8f, 0f, -5f, 0f)
                val i = (t * 4).toInt().coerceAtMost(3)
                val x = ease(t * 4 - i)
                rotationZ = lerp(rotations[i], rotations[i + 1], x)
                translationY = lerp(lifts[i], lifts[i + 1], x) * unit
            }
            Temperament.SLEEPY -> {
                val wave = ((1 - cos(2 * PI * t)) / 2).toFloat()
                rotationZ = lerp(-4f, -6f, wave)
                translationY = lerp(0f, 3f, wave) * unit
            }
        }
    }
}

/** A small rounded tile with the pet acting out [temperament]. */
@Composable
internal fun TemperamentTile(image: Bitmap?, temperament: Temperament, size: Dp, dark: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.31f)).background(if (dark) Color(0xFF2A2A2C) else Opaw.colors.chip),
        contentAlignment = Alignment.Center,
    ) {
        StickerPet(
            image,
            Modifier.size(size * 0.7f, size * 0.76f).temperamentMotion(temperament, amplitude = size.value / 58f),
            edge = if (size < 40.dp) 1.5.dp else 2.5.dp,
            motion = null,
        )
    }
}

private class Orb(val icon: DesignIcon, val alignment: Alignment, val x: Dp, val y: Dp, val tilt: Float, val phase: Float)

/** The care icons drifting around the pet on the welcome screen (10a), like a small world. */
@Composable
internal fun CareOrbs(modifier: Modifier = Modifier) {
    val colors = Opaw.colors
    val t by rememberInfiniteTransition(label = "orbs").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(modifier) {
        // A soft white stage behind the pet.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp)
                .size(320.dp)
                .background(Brush.radialGradient(0f to Color.White, 0.62f to Color.White, 1f to Color.White.copy(alpha = 0f)), CircleShape),
        )
        val orbs = listOf(
            Orb(DesignIcon.MEAL, Alignment.TopStart, 26.dp, 118.dp, -8f, 0f),
            Orb(DesignIcon.PAW, Alignment.TopEnd, (-30).dp, 92.dp, 10f, 0.35f),
            Orb(DesignIcon.BALL, Alignment.TopStart, 44.dp, 290.dp, 6f, 0.6f),
            Orb(DesignIcon.WATER, Alignment.TopEnd, (-44).dp, 300.dp, -6f, 0.18f),
        )
        for (orb in orbs) {
            val wave = ((1 - cos(2 * PI * (t + orb.phase))) / 2).toFloat()
            Box(
                Modifier
                    .align(orb.alignment)
                    .offset(orb.x, orb.y)
                    .graphicsLayer { translationY = -8f * wave * density; rotationZ = orb.tilt }
                    .size(54.dp)
                    .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(orb.icon, size = 22.dp, tint = colors.ink) }
        }
    }
}

/**
 * Join with a code (10l), from the photos: for a walker or partner who was sent a code. No photos,
 * no setup. The code goes to [HouseholdJoinHost], which joins as soon as it can, brings their pet
 * and lands on its room. "I have a link instead" reads an invite link from the clipboard.
 */
@Composable
internal fun JoinCodeScreen(app: OpawApplication, modifier: Modifier, onDone: () -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val boxes = 8
    fun join(value: String) {
        haptics.medium()
        app.household.offer(value)
        onDone()
    }
    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        SetupHeader(1, 1, "Join", onBack = onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(160.dp).background(Setup.fill, CircleShape), contentAlignment = Alignment.Center) {
                    HouseArt(Modifier.size(96.dp))
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 6.dp).drift(2400)
                            .size(48.dp).shadow(8.dp, CircleShape).background(Action, CircleShape).border(4.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", style = opaw(20.sp, FontWeight.Black), color = Color.White) }
                }
            }
            SetupTitle("Join a household", "Enter the code from your invite to help look after their pet.", Modifier.padding(top = 26.dp))
            // One hidden field drives the boxes, so pasting a whole code works.
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                BasicTextField(
                    code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(boxes); note = null },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().height(56.dp).alpha(0f).focusRequester(focus),
                )
                Row(Modifier.fillMaxWidth().pressable { runCatching { focus.requestFocus() } }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until boxes) {
                        val current = i == code.length.coerceAtMost(boxes - 1)
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .then(
                                    if (current) Modifier.background(colors.surface, shape).border(3.dp, Action, shape)
                                    else Modifier.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f)).background(colors.surface, shape),
                                ),
                            contentAlignment = Alignment.Center,
                        ) { Text(code.getOrNull(i)?.toString().orEmpty(), style = opaw(24.sp, FontWeight.Black), color = colors.ink) }
                    }
                }
            }
            Text(
                note ?: "You'll skip the photos and land in their pet's room.",
                style = opaw(14.sp, FontWeight.SemiBold), color = if (note != null) colors.alert else colors.muted,
                modifier = Modifier.padding(horizontal = 26.dp).padding(top = 14.dp),
            )
        }
        SetupFoot {
            OrangeButton("Join household", enabled = code.length >= 6) { join(code) }
            SetupLink("I have a link instead") {
                // An invite link ends in ?c=CODE.
                val text = clipboard.getText()?.text.orEmpty()
                val found = Regex("[?&]c=([A-Za-z0-9]{6,32})").find(text)?.groupValues?.get(1)
                    ?: text.trim().takeIf { it.length in 6..32 && it.all(Char::isLetterOrDigit) }
                if (found != null) join(found) else note = "Copy the invite link first, then tap here."
            }
        }
    }
}

/** A flat house in the brand orange, for joining a household. */
@Composable
private fun HouseArt(modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 100f
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(14 * u, 46 * u); lineTo(50 * u, 16 * u); lineTo(86 * u, 46 * u); lineTo(86 * u, 86 * u)
            quadraticTo(86 * u, 92 * u, 80 * u, 92 * u); lineTo(20 * u, 92 * u); quadraticTo(14 * u, 92 * u, 14 * u, 86 * u); close()
        }
        drawPath(body, Color(0xFFFFE3CF))
        drawPath(body, Action, style = androidx.compose.ui.graphics.drawscope.Stroke(5 * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val door = androidx.compose.ui.graphics.Path().apply {
            moveTo(40 * u, 92 * u); lineTo(40 * u, 66 * u); quadraticTo(40 * u, 56 * u, 50 * u, 56 * u); quadraticTo(60 * u, 56 * u, 60 * u, 66 * u); lineTo(60 * u, 92 * u)
        }
        drawPath(door, Color.White)
        drawPath(door, Action, style = androidx.compose.ui.graphics.drawscope.Stroke(5 * u))
    }
}

/** How a temperament shows on Home, for the reveal: "Playful, so expect zoomies on Home." */
internal fun Temperament.homeNote(): String = when (this) {
    Temperament.PLAYFUL -> "Playful, so expect zoomies on Home."
    Temperament.CALM -> "Calm, so expect slow strolls on Home."
    Temperament.CURIOUS -> "Curious, so expect them to come up close on Home."
    Temperament.SHY -> "Shy, so expect them to peek at you on Home."
    Temperament.GOOFY -> "Goofy, so expect silly spins on Home."
    Temperament.SLEEPY -> "Sleepy, so expect lots of naps on Home."
}
