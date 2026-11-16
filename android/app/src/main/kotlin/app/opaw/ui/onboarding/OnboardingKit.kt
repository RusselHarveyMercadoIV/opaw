package app.opaw.ui.onboarding

import android.graphics.Bitmap
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
import app.opaw.data.Dog
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.GhostCycle
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlin.math.PI
import kotlin.math.cos

// The pieces Turn 10 of the design adds to onboarding: the chapter rail, the two ways through the
// photos, the pet acting out each temperament, the care icons on the welcome screen, joining with
// a code, and welcoming back a returning owner.

/** Where the rail's pet face was last, so the next step's rail slides on from there. */
private var railLast = 0f

/**
 * The chapter rail (10c to 10j): a back button, one line that fills as setup goes on with the
 * pet's face riding along it, and the chapter's name, or Join and Skip on the first screen.
 */
@Composable
internal fun ChapterRail(
    index: Int,
    chapter: String,
    face: Bitmap?,
    inset: Int = 20,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)? = null,
    onJoin: (() -> Unit)? = null,
) {
    val colors = Opaw.colors
    val target = ((index - 1) * 17 + 8) / 100f
    val fraction = remember { Animatable(railLast) }
    LaunchedEffect(target) {
        fraction.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 120f))
        railLast = target
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = inset.dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) RoundIcon(DesignIcon.BACK, onClick = onBack)
        BoxWithConstraints(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.CenterStart) {
            val width = maxWidth
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.track)) {
                Box(Modifier.fillMaxWidth(fraction.value).height(6.dp).background(Ink, CircleShape))
            }
            Box(
                Modifier
                    .offset(x = width * fraction.value - 17.dp)
                    .size(34.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
                    .background(Color.White, CircleShape)
                    .border(2.5.dp, Ink, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (face != null) {
                    Image(remember(face) { face.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(DesignIcon.PAW, size = 15.dp, tint = Ink)
                }
            }
        }
        if (onJoin != null || onSkip != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onJoin != null) {
                    Row(
                        Modifier
                            .height(38.dp)
                            .pressable(onClick = onJoin)
                            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                            .background(colors.surface, CircleShape)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DesignIcon.USERS, size = 15.dp, tint = colors.ink)
                        Text("Join", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.ink)
                    }
                }
                if (onSkip != null) {
                    Text(
                        "Skip", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.muted,
                        modifier = Modifier.pressable(onClick = onSkip).padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        } else {
            Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.End) {
                Text(chapter, style = opaw(13.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                Text("$index of 6", style = opaw(11.sp, FontWeight.Bold), color = colors.muted)
            }
        }
    }
}

/**
 * The two ways through the photos (10c), together so the choice is plain: the 3D pet, which is
 * Premium, or one photo for the free sticker avatar.
 */
@Composable
internal fun PathActions(
    name: String,
    isPremium: Boolean,
    enabled: Boolean,
    onLife: () -> Unit,
    /** Null until the front photo has been looked at. */
    onFree: (() -> Unit)?,
) {
    val colors = Opaw.colors
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .alpha(if (enabled) 1f else 0.3f)
                .pressable(enabled, onClick = onLife)
                .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .background(Ink, CircleShape)
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Bring $name to life", style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(
                Modifier.height(28.dp).background(Color.White.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.SPARKLE, size = 12.dp, weight = 2.4f, tint = Color.White)
                Text(if (isPremium) "3D" else "3D · Premium", style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White)
            }
        }
        if (onFree != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressable(onClick = onFree)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                    .background(colors.surface, CircleShape)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Continue with one photo", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
                Text("Free sticker", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
            }
        }
    }
}

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
 * Join with a code (10l), from the first screen: for a walker or partner who was sent a code.
 * The code goes to [HouseholdJoinHost], which joins as soon as it can and says how it went.
 */
@Composable
internal fun JoinCodeScreen(app: OpawApplication, modifier: Modifier, onDone: () -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val focus = remember { FocusRequester() }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val boxes = 8
    Column(modifier) {
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) { RoundIcon(DesignIcon.BACK, onClick = onDone) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Join a household", style = opaw(30.sp, FontWeight.Black), color = colors.ink)
            Text("Enter the code from your invite to help look after their pet.", style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp), color = colors.muted)
        }
        // One hidden field drives the boxes, so pasting a whole code works.
        Box(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
            BasicTextField(
                code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(boxes) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().height(58.dp).alpha(0f).focusRequester(focus),
            )
            Row(Modifier.fillMaxWidth().pressable { runCatching { focus.requestFocus() } }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until boxes) {
                    val current = i == code.length.coerceAtMost(boxes - 1)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .whiteCard(16.dp)
                            .then(if (current) Modifier.border(2.5.dp, Ink, RoundedCornerShape(16.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Text(code.getOrNull(i)?.toString().orEmpty(), style = opaw(24.sp, FontWeight.Black), color = colors.ink) }
                }
            }
        }
        Text(
            "You'll skip the photos and land on Home with their pet.",
            style = opaw(14.sp, FontWeight.Medium), color = colors.muted,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 14.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BlackPillButton("Join household", enabled = code.length >= 6) {
                haptics.medium()
                app.household.offer(code)
                onDone()
            }
            Text("Not now", style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.pressable(onClick = onDone).padding(4.dp))
        }
    }
}

/** While the account's pets are looked for after signing in on a new phone. */
@Composable
internal fun LookingForPets(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GhostCycle(Modifier.size(170.dp, 190.dp).loopMotion(LoopMotion.BOB, 3.2))
        Text("Looking for your pets…", style = opaw(18.sp, FontWeight.Black), color = Opaw.colors.ink, modifier = Modifier.padding(top = 18.dp))
        Text("Checking your Google account", style = opaw(14.sp, FontWeight.Medium), color = Opaw.colors.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Welcome back (10b): signed in on a new phone or after a reinstall, and the account already
 * had pets, so setup is skipped and they're shown coming back.
 */
@Composable
internal fun WelcomeBackScreen(app: OpawApplication, dogs: List<Dog>, modifier: Modifier, onDone: () -> Unit) {
    val colors = Opaw.colors
    Column(modifier) {
        Column(Modifier.weight(1f).padding(top = 26.dp)) {
            Row(
                Modifier.padding(horizontal = 20.dp).background(Ink, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.CLOUD, size = 14.dp, tint = Color.White)
                Text("Found on your account", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome back", style = opaw(34.sp, FontWeight.Black), color = colors.ink)
                Text(
                    "Your pets were saved to your Google account, so nothing was lost: logs, photos and their 3D selves.",
                    style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp), color = colors.muted,
                )
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (dog in dogs.take(4)) {
                    val image = rememberPetImage(dog, 300)
                    val has3D = app.petModels.clips(dog.id).isNotEmpty()
                    Row(
                        Modifier.fillMaxWidth().whiteCard(26.dp).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(colors.chip), contentAlignment = Alignment.Center) {
                            StickerPet(image, Modifier.fillMaxSize().padding(6.dp), edge = 2.dp, motion = LoopMotion.BREATHE)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(dog.name.ifEmpty { "Your pet" }, style = opaw(18.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(dog.breed.ifEmpty { null }, dog.birthDate?.let { ageText(Dog.age(it)) }).joinToString(" · ").ifEmpty { "Restored" },
                                style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (has3D) {
                            Row(
                                Modifier.height(30.dp).background(Ink, CircleShape).padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(DesignIcon.CHECK, size = 11.dp, tint = Color.White)
                                Text("3D", style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White)
                            }
                        }
                    }
                }
                if (dogs.size > 4) Text("and ${dogs.size - 4} more", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
            BlackPillButton(if (dogs.size == 1) "Go to ${dogs.first().name.ifEmpty { "your pet" }}" else "Go to your pets", onClick = onDone)
        }
    }
}

private fun ageText(years: Double): String {
    val whole = years.toInt()
    return when {
        whole < 1 -> "puppy"
        whole == 1 -> "1 yr"
        else -> "$whole yrs"
    }
}

/** How a temperament shows on Home, for the reveal: "Playful, so expect zoomies on Home." */
internal fun Temperament.homeNote(): String = when (this) {
    Temperament.PLAYFUL -> "Playful, so expect zoomies on Home."
    Temperament.CALM -> "Calm, so expect slow strolls on Home."
    Temperament.CURIOUS -> "Curious, so expect them to come up close on Home."
    Temperament.SHY -> "Shy, so expect them to peek at you on Home."
    Temperament.GOOFY -> "Goofy, so expect silly spins on Home."
}
