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
import androidx.compose.ui.graphics.Color
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
                Ring(fraction, 8.dp, colors.track, colors.ink, Modifier.size(180.dp))
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
                            Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).background(Ink, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
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
 * The pet drawn in the app's game-art style, for the owner to check before the 3D model is made.
 * Saying yes builds it in 3D from this picture; trying again redraws only the picture, which costs
 * a fraction of a whole model.
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
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            RoundIcon(DesignIcon.BACK, onClick = onLeave)
            Text("Quick check", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.muted, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Does this look like $name?",
                style = opaw(30.sp, FontWeight.Black).copy(lineHeight = 34.sp), color = colors.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 18.dp),
            )
            Text(
                "Here's $name as a game character. The 3D $name is built from this picture, so check the face, coat, markings and shape.",
                style = opaw(15.sp, FontWeight.Medium).copy(lineHeight = 20.sp), color = colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp).padding(top = 8.dp),
            )
            Box(
                Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth().height(340.dp).whiteCard(26.dp).clip(RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val image = sheet
                if (image != null) {
                    Image(remember(image) { image.asImageBitmap() }, "$name, drawn as a game character", Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
                } else {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = colors.ink, strokeWidth = 3.dp)
                }
            }
            if (state.error != null) {
                Text(state.error, style = opaw(14.sp, FontWeight.Bold), color = colors.alert, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp).padding(top = 12.dp))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BlackPillButton("Yes, that's $name", enabled = sheet != null) { haptics.medium(); app.petModels.approve(dogId) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetake != null) BlackPillButton("New photos", Modifier.weight(1f), inverted = true, onClick = onRetake)
                BlackPillButton(
                    if (state.drawsLeft > 0) "Draw again" else "No tries left",
                    Modifier.weight(1f), inverted = true, enabled = state.drawsLeft > 0,
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
            Box(Modifier.width(2.dp).height(14.dp).background(if (first) Color.Transparent else if (done || active) Ink else colors.track))
            Box(
                Modifier.size(24.dp).background(if (done) Ink else colors.surface, CircleShape).border(2.dp, if (done || active) Ink else colors.track, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(DesignIcon.CHECK, size = 11.dp, tint = Color.White)
                if (active) Box(Modifier.size(10.dp).alpha(pulse).background(Ink, CircleShape))
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else if (done) Ink else colors.track))
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
                                modifier = Modifier.background(if (made) Ink else colors.chip, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
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
 * The toast at the top while a pet comes to life (9a): black, with the pet inside a ring that
 * fills as the job goes on. Tap it for the progress screen, or to meet the pet once it's ready.
 * Swipe it up to put it away, where [onDismiss] is given.
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
            .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(Ink, CircleShape)
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(remember(image) { image.asImageBitmap() }, null, Modifier.fillMaxSize().padding(5.dp).loopMotion(LoopMotion.BOB, 0.9), contentScale = ContentScale.Fit)
                } else {
                    Icon(DesignIcon.PAW, size = 20.dp, tint = Ink)
                }
            }
            Ring(if (state.isReady) 1f else state.progress / 100f, 3.dp, Color.White.copy(alpha = 0.2f), Color.White, Modifier.fillMaxSize())
            if (state.isReady) {
                Box(Modifier.align(Alignment.BottomEnd).size(18.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(DesignIcon.CHECK, size = 9.dp, tint = Ink)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                when {
                    state.isReady -> "$name is ready"
                    state.isFailed -> "$name couldn't come to life"
                    state.isReviewing -> "Does this look like $name?"
                    else -> "Bringing $name to life"
                },
                style = opaw(16.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    state.isReady -> "Avatar and motion clips · tap to meet"
                    state.isFailed -> "Tap to see why"
                    else -> state.summary
                },
                style = opaw(13.sp, FontWeight.Medium), color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(DesignIcon.CHEVRON_RIGHT, size = 16.dp, tint = Color.White)
    }
}

/**
 * The reveal (9a): the pet in 3D, spinnable, with a button for each motion clip that came back.
 * Opening it counts as meeting the pet, so the "ready" toast goes.
 */
@Composable
fun LifeRevealScreen(
    dogId: String,
    name: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** Start over with new photos; null where that isn't offered. */
    onRetake: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val clips = app.petModels.clips(dogId)
    val photos = app.petModels.state(dogId).photos.size
    var clip by remember { mutableStateOf("rest") }
    LaunchedEffect(dogId) { app.petModels.markSeen(dogId) }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            RoundIcon(DesignIcon.BACK, onClick = onBack)
            Box(Modifier.align(Alignment.Center)) { SparkleChip(if (clips.size > 1) "Avatar and ${clips.size} motion clips ready" else "Avatar ready") }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            app.petModels.clip(dogId, clip)?.let { file ->
                val motion = app.petModels.motion(dogId, clip)
                PetModel3D(file, Modifier.fillMaxSize(), interactive = true, breathe = motion == null && file.nameWithoutExtension == "rest", motion = motion)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Say hi to $name", style = opaw(30.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (photos > 0) "Made from your $photos photos. " else "") + "Drag to spin, tap a clip to play it.",
                style = opaw(15.sp, FontWeight.Medium), color = colors.muted, textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (option in clips) {
                val on = option == clip
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .pressable { haptics.select(); clip = option }
                        .then(if (on) Modifier.background(Ink, RoundedCornerShape(20.dp)) else Modifier.whiteCard(20.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(clipTitle(option), style = opaw(16.sp, FontWeight.ExtraBold), color = if (on) Color.White else colors.ink) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onRetake != null) {
                Box(
                    Modifier.size(64.dp).pressable(onClick = onRetake).shadow(10.dp, CircleShape).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.REFRESH, size = 22.dp, tint = Ink) }
            }
            BlackPillButton("Looks like $name", Modifier.weight(1f), onClick = onDone)
        }
    }
}
