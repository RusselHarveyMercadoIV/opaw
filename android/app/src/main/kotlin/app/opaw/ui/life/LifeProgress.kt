package app.opaw.ui.life

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.services.LifeStep
import app.opaw.services.PetModelState
import app.opaw.services.clipTitle
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.ProgressBar
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.SparkleChip
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.home.PetModel3D
import app.opaw.ui.onboarding.OrangeButton
import app.opaw.ui.onboarding.PlainPet
import app.opaw.ui.onboarding.Setup
import app.opaw.ui.onboarding.SetupChip
import app.opaw.ui.onboarding.SetupFoot
import app.opaw.ui.onboarding.SetupHeader
import app.opaw.ui.onboarding.SetupLink
import app.opaw.ui.onboarding.SetupTitle
import app.opaw.ui.onboarding.Tick
import app.opaw.ui.onboarding.breathe
import app.opaw.ui.onboarding.popIn
import app.opaw.ui.onboarding.spinning
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Small versions of the saved reference photos. */
@Composable
private fun rememberThumbs(files: List<File>): List<Bitmap> {
    val thumbs by produceState(emptyList<Bitmap>(), files) {
        value = withContext(Dispatchers.IO) {
            files.mapNotNull { file -> runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() }
        }
    }
    return thumbs
}

/** A ring that fills clockwise from the top. */
@Composable
private fun Ring(progress: Float, stroke: Dp, track: Color, fill: Color, modifier: Modifier) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = Offset(width / 2, width / 2)
        val box = Size(size.width - width, size.height - width)
        drawArc(track, 0f, 360f, false, inset, box, style = Stroke(width))
        drawArc(fill, -90f, 360f * shown, false, inset, box, style = Stroke(width, cap = StrokeCap.Round))
    }
}

/**
 * The generating screen (9a): the pet inside a progress ring with the photos floating around
 * it, then the five checkpoints. Leaving it doesn't stop anything.
 */
@Composable
fun LifeProgressScreen(
    dogId: String,
    name: String,
    image: Bitmap?,
    modifier: Modifier = Modifier,
    /** The button while it works: "Keep setting up while it works" at first run, "Back to Miso" later. */
    leaveTitle: String,
    onLeave: () -> Unit,
    onMeet: () -> Unit,
    /** Retake the photos after a failure; null where that isn't possible. */
    onRetake: (() -> Unit)? = null,
) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val state = app.petModels.state(dogId)
    val thumbs = rememberThumbs(app.petModels.photoFiles(dogId))
    val fraction = state.progress / 100f

    // A tick as each checkpoint is passed, and the milestone thump when the pet is ready.
    var lastStep by remember { mutableStateOf(state.step) }
    var wasReady by remember { mutableStateOf(state.isReady) }
    LaunchedEffect(state.step, state.isReady) {
        if (state.isReady && !wasReady) haptics.milestone() else if (state.step != lastStep && state.isWorking) haptics.select()
        lastStep = state.step
        wasReady = state.isReady
    }

    if (state.isReviewing) return LifeReview(dogId, name, modifier, onLeave, onRetake)

    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            RoundIcon(DesignIcon.BACK, onClick = onLeave)
            Text(
                when {
                    state.isReady -> "Ready"
                    state.isFailed -> "Stopped"
                    else -> "Working on it"
                },
                style = opaw(14.sp, FontWeight.ExtraBold), color = colors.muted, modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 14.dp).size(300.dp, 220.dp), contentAlignment = Alignment.Center) {
                Ring(fraction, 8.dp, colors.track, colors.action, Modifier.size(180.dp))
                // The pet sharpens as the job goes on.
                StickerPet(
                    image,
                    Modifier.size(124.dp, 132.dp).blur(((1f - fraction) * 6f).dp).alpha(0.55f + 0.45f * fraction),
                    edge = 4.dp,
                    motion = if (state.isReady) LoopMotion.BOB else LoopMotion.BREATHE,
                )
                val spots = listOf(Alignment.TopStart to 2.6, Alignment.TopEnd to 3.1, Alignment.BottomStart to 3.4, Alignment.BottomEnd to 2.8)
                thumbs.take(4).forEachIndexed { index, thumb ->
                    val (spot, seconds) = spots[index]
                    Box(Modifier.align(spot).padding(horizontal = 6.dp, vertical = 14.dp).loopMotion(LoopMotion.BOB, seconds)) {
                        Image(
                            remember(thumb) { thumb.asImageBitmap() }, null,
                            Modifier.size(54.dp).shadow(8.dp, RoundedCornerShape(18.dp)).background(Color.White, RoundedCornerShape(18.dp)).padding(3.dp).clip(RoundedCornerShape(15.dp)).background(colors.chip),
                            contentScale = ContentScale.Fit,
                        )
                        if (state.progress >= LifeStep.CUTOUT.from) {
                            Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).background(Action, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(DesignIcon.CHECK, size = 9.dp, tint = Color.White)
                            }
                        }
                    }
                }
            }
            Text(
                when {
                    state.isReady -> "$name is ready"
                    state.isFailed -> "That didn't work"
                    else -> "Bringing $name to life"
                },
                style = opaw(30.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 6.dp),
            )
            Text(
                when {
                    state.isReady -> "Avatar and ${app.petModels.clips(dogId).size} motion clips"
                    state.isFailed -> state.error ?: "Something went wrong."
                    else -> "${state.progress}% · About ${state.minutesLeft} min left"
                },
                style = opaw(15.sp, FontWeight.Medium).copy(lineHeight = 20.sp), color = if (state.isFailed) colors.alert else colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 6.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth().whiteCard().padding(horizontal = 18.dp, vertical = 8.dp)) {
                LifeStep.entries.forEachIndexed { index, step ->
                    Checkpoint(step, state, first = index == 0, last = index == LifeStep.entries.lastIndex)
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                state.isReady -> BlackPillButton("Meet $name", onClick = onMeet)
                state.isFailed -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onRetake != null) BlackPillButton("Retake photos", Modifier.weight(1f), inverted = true, onClick = onRetake)
                    BlackPillButton("Try again", Modifier.weight(1f), enabled = state.send > 0) { app.petModels.retry(dogId) }
                }
                else -> BlackPillButton(leaveTitle, onClick = onLeave)
            }
            if (state.isWorking) Text("You can leave this screen. It keeps going.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
        }
    }
}

/**
 * The quick check (10e): the pet drawn in the app's game-art style, on a round stage that gently
 * breathes, with the three things to check popping in one by one. Saying yes builds it in 3D
 * from this picture; drawing again redraws only the picture, which costs a fraction of a model.
 */
@Composable
private fun LifeReview(dogId: String, name: String, modifier: Modifier, onLeave: () -> Unit, onRetake: (() -> Unit)?) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val state = app.petModels.state(dogId)
    val sheet by produceState<Bitmap?>(null, dogId, state.progress) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(app.petModels.sheetFile(dogId).path) }.getOrNull() }
    }
    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        SetupHeader(4, 4, "Quick check", onBack = onLeave)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(280.dp)) {
                // The drawing comes on white, so it sits on a white stage inside a grey ring.
                Box(Modifier.fillMaxSize().background(Setup.fill, CircleShape).padding(10.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    val image = sheet
                    if (image != null) {
                        Image(
                            remember(image) { image.asImageBitmap() }, "$name, drawn as a game character",
                            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 40.dp).breathe(3000), contentScale = ContentScale.Fit,
                        )
                    } else {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = colors.action, strokeWidth = 3.dp)
                    }
                }
                SetupChip("Our drawing", Modifier.align(Alignment.TopCenter).offset(y = (-18).dp)) {
                    Icon(DesignIcon.SPARKLE, size = 16.dp, weight = 2.4f, tint = Action)
                }
                if (sheet != null) {
                    val checks = listOf(
                        Triple("Ears and tail", Alignment.TopEnd, Offset(36f, 96f)),
                        Triple("Coat and markings", Alignment.BottomStart, Offset(-28f, -10f)),
                        Triple("Body shape", Alignment.BottomEnd, Offset(24f, 30f)),
                    )
                    checks.forEachIndexed { i, (text, spot, shift) ->
                        SetupChip(text, Modifier.align(spot).offset(shift.x.dp, shift.y.dp).popIn(3000, i * 0.083f)) { Tick(20.dp) }
                    }
                }
            }
        }
        SetupTitle(
            "Does this look right?",
            "We drew $name from the photos. A quick yes before we build in 3D, so it comes out the right shape.",
        )
        if (state.error != null) {
            Text(state.error, style = opaw(14.sp, FontWeight.Bold), color = colors.alert, modifier = Modifier.padding(horizontal = 26.dp).padding(top = 10.dp))
        }
        Spacer(Modifier.height(18.dp))
        SetupFoot {
            OrangeButton("Yes, that's my pet", enabled = sheet != null) { haptics.medium(); app.petModels.approve(dogId) }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                if (onRetake != null) SetupLink("New photos", onClick = onRetake)
                SetupLink(
                    if (state.drawsLeft > 0) "Draw again · ${state.drawsLeft} left" else "No tries left",
                    enabled = state.drawsLeft > 0,
                ) { haptics.select(); app.petModels.redraw(dogId) }
            }
        }
    }
}

/** One checkpoint: its dot on the line, its name, and while it's the current one, its own bar. */
@Composable
private fun Checkpoint(step: LifeStep, state: PetModelState, first: Boolean, last: Boolean) {
    val colors = Opaw.colors
    val done = state.isReady || state.progress >= step.to
    val active = !done && state.isWorking && state.step == step
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Row(Modifier.fillMaxWidth().height(if (active) 96.dp else 52.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(14.dp).background(if (first) Color.Transparent else if (done || active) Action else colors.track))
            Box(
                Modifier.size(24.dp).background(if (done) Action else colors.surface, CircleShape).border(2.dp, if (done || active) Action else colors.track, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(DesignIcon.CHECK, size = 11.dp, tint = Color.White)
                if (active) Box(Modifier.size(10.dp).alpha(pulse).background(Action, CircleShape))
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else if (done) Action else colors.track))
        }
        Column(Modifier.weight(1f).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title, style = opaw(16.sp, FontWeight.ExtraBold), color = if (done || active) colors.ink else colors.ghost, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (active) Text("${(state.stepFraction * 100).roundToInt()}%", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.ink)
            }
            if (active) {
                if (step == LifeStep.MOTION) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Bones first, then the clips, so each chip turns black a third of the way along.
                        listOf("rest", "walk", "run").forEachIndexed { index, clip ->
                            val made = state.stepFraction >= (index + 1) / 3f
                            Text(
                                clipTitle(clip), style = opaw(12.sp, FontWeight.ExtraBold), color = if (made) Color.White else colors.ink,
                                modifier = Modifier.background(if (made) Action else colors.chip, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    Text(step.detail, style = opaw(13.sp, FontWeight.Medium), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ProgressBar(state.stepFraction)
            }
        }
    }
}

/**
 * The bar at the top while a pet comes to life (10f, 10h): the brand orange, with a spinner, a
 * bar that fills and the percent while it works, then the pet's face and "Miso is ready in 3D".
 * Tap it for the progress screen, or to meet the pet once it's ready. Swipe it up to put it
 * away, where [onDismiss] is given.
 */
@Composable
fun LifeToast(name: String, state: PetModelState, image: Bitmap?, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null, onTap: () -> Unit) {
    val haptics = rememberHaptics()
    var wasReady by remember { mutableStateOf(state.isReady) }
    LaunchedEffect(state.isReady) {
        if (state.isReady && !wasReady) haptics.milestone()
        wasReady = state.isReady
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(0, min(0f, drag).roundToInt()) }
            .pointerInput(onDismiss) {
                if (onDismiss == null) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { if (drag < -30 * density) onDismiss(); drag = 0f },
                    onVerticalDrag = { _, amount -> drag += amount },
                )
            }
            .pressable(onClick = onTap)
            .shadow(16.dp, shape, ambientColor = Action.copy(alpha = 0.3f), spotColor = Action.copy(alpha = 0.3f))
            .background(Brush.horizontalGradient(listOf(Setup.glowTop, Setup.glowBottom)), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isReady || state.isReviewing) {
            Box(Modifier.size(44.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                PlainPet(image, Modifier.size(32.dp))
            }
        } else {
            Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                if (state.isFailed) {
                    Icon(DesignIcon.ALERT, size = 20.dp, tint = Color.White)
                } else {
                    Box(Modifier.size(24.dp).spinning(1000)) { Ring(0.25f, 3.5.dp, Color.White.copy(alpha = 0.4f), Color.White, Modifier.fillMaxSize()) }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                when {
                    state.isReady -> "$name is ready in 3D"
                    state.isFailed -> "$name couldn't come to life"
                    state.isReviewing -> "Does this look like $name?"
                    else -> "Building $name in 3D"
                },
                style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (state.isWorking) {
                val shown by animateFloatAsState(state.progress / 100f, tween(900), label = "bar")
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(shown.coerceIn(0.04f, 1f)).height(6.dp).background(Color.White, CircleShape))
                }
            } else {
                Text(
                    when {
                        state.isReady -> "Tap to meet $name"
                        state.isFailed -> "Tap to see why"
                        state.isReviewing -> "Tap to check the drawing"
                        else -> state.summary
                    },
                    style = opaw(12.sp, FontWeight.Bold), color = Color(0xFFFFE3D1), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.isWorking) {
            Text("${state.progress}%", style = opaw(14.sp, FontWeight.Black), color = Color.White)
        } else {
            Icon(DesignIcon.CHEVRON_RIGHT, size = 18.dp, weight = 3f, tint = Color.White)
        }
    }
}

/**
 * Meet the pet (10k): the payoff screen goes full brand orange, like the welcome. Rays turn
 * slowly, confetti falls, and the pet stands in 3D on a white disc: drag to spin it, or tap a
 * clip to play it. Opening it counts as meeting the pet, so the "ready" toast goes. Draws its
 * own background edge to edge, so [modifier] should carry no padding.
 */
@Composable
fun LifeRevealScreen(
    dogId: String,
    name: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** Start over with new photos ("Change the look"); null where that isn't offered. */
    onRetake: (() -> Unit)? = null,
    /** How it will play on Home, such as "Playful, so expect zoomies on Home." */
    note: String? = null,
    /** The main button; "Take Miso home" unless given. */
    doneTitle: String? = null,
    onDone: () -> Unit,
) {
    val app = rememberApp()
    val haptics = rememberHaptics()
    val clips = app.petModels.clips(dogId)
    var clip by remember { mutableStateOf("rest") }
    LaunchedEffect(dogId) { app.petModels.markSeen(dogId) }

    Box(modifier.background(Brush.verticalGradient(listOf(Setup.glowTop, Setup.glowBottom)))) {
        Confetti(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Box(
                    Modifier.size(44.dp).pressable(onClick = onBack).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.BACK, size = 20.dp, tint = Color.White) }
                SetupChip("$name is ready in 3D", Modifier.align(Alignment.Center), color = Setup.deep) {
                    Icon(DesignIcon.SPARKLE, size = 16.dp, weight = 2.4f, tint = Action)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Rays(Modifier.size(400.dp).spinning(24000))
                Box(Modifier.size(280.dp).background(Color.White.copy(alpha = 0.95f), CircleShape))
                Box(Modifier.align(Alignment.Center).offset(y = 130.dp).size(180.dp, 30.dp).background(Color(0xFFFFE3CF), CircleShape))
                app.petModels.clip(dogId, clip)?.let { file ->
                    val motion = app.petModels.motion(dogId, clip)
                    PetModel3D(file, Modifier.size(320.dp), interactive = true, breathe = motion == null && file.nameWithoutExtension == "rest", motion = motion)
                }
                SetupChip(
                    "Drag to spin", Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    background = Color.Black.copy(alpha = 0.25f), color = Color.White, flat = true,
                ) { Icon(DesignIcon.REFRESH, size = 16.dp, tint = Color.White) }
            }
            if (clips.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    for (option in clips) {
                        val on = option == clip
                        Text(
                            clipTitle(option),
                            style = opaw(14.sp, FontWeight.ExtraBold), color = if (on) Action else Color.White,
                            modifier = Modifier
                                .pressable { haptics.select(); clip = option }
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            SetupTitle(
                "Meet $name!",
                note ?: "Every walk, meal and cuddle you log, $name feels it. Their room is ready.",
                Modifier.padding(top = 18.dp), center = true, onOrange = true,
            )
            Spacer(Modifier.height(18.dp))
            SetupFoot {
                OrangeButton(doneTitle ?: "Take $name home", white = true, onClick = onDone)
                if (onRetake != null) SetupLink("Change the look", color = Color.White, onClick = onRetake)
            }
        }
    }
}

/** Twelve soft white rays from the middle, for the reveal. */
@Composable
private fun Rays(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        for (i in 0 until 12) {
            rotate(i * 30f, c) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y); lineTo(c.x - r * 0.12f, c.y - r); lineTo(c.x + r * 0.12f, c.y - r); close()
                }
                drawPath(path, Color.White.copy(alpha = 0.12f))
            }
        }
    }
}

private class Piece(val x: Float, val millis: Int, val delay: Float, val size: Float, val round: Boolean, val color: Color)

/** Confetti falling from the top, each piece on its own loop. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        listOf(
            Piece(0.10f, 3200, 0f, 10f, false, Color.White), Piece(0.25f, 2800, 0.28f, 8f, true, Color(0xFFFFE3CF)),
            Piece(0.44f, 3600, 0.08f, 12f, false, Color(0xFFFFD36B)), Piece(0.62f, 3000, 0.46f, 9f, true, Color.White),
            Piece(0.77f, 3400, 0.18f, 11f, false, Color(0xFFFFE3CF)), Piece(0.87f, 2600, 0.73f, 8f, true, Color(0xFFFFD36B)),
            Piece(0.33f, 3800, 0.58f, 10f, false, Color.White), Piece(0.69f, 2900, 0.9f, 9f, false, Color(0xFFFFD36B)),
        )
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(11400, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        for (p in pieces) {
            // Each piece's own clock, from the shared one.
            val local = ((t * 11400f / p.millis) + p.delay) % 1f
            val y = -40.dp.toPx() + local * (size.height * 0.75f)
            val alpha = when {
                local < 0.1f -> local / 0.1f
                else -> 1f - (local - 0.1f) / 0.9f
            }
            val side = p.size.dp.toPx()
            val center = Offset(p.x * size.width, y)
            rotate(320f * local, center) {
                if (p.round) {
                    drawCircle(p.color.copy(alpha = alpha), side / 2, center)
                } else {
                    drawRoundRect(
                        p.color.copy(alpha = alpha), Offset(center.x - side / 2, center.y - side / 2), Size(side, side),
                        androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
    }
}
