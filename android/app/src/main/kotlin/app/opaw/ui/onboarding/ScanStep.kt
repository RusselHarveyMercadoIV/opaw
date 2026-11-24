package app.opaw.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.services.PhotoIssue
import app.opaw.services.Segmentation
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.SparkleChip
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

internal enum class ScanPhase { SCANNING, HIGHLIGHT, LIFTED }

/** Scanning while the photo is processed, then the white highlight for a moment, then lifted. */
@Composable
internal fun rememberScanPhase(capture: CaptureResult, isProcessing: Boolean): ScanPhase {
    val haptics = rememberHaptics()
    var phase by remember(capture.original) { mutableStateOf(ScanPhase.SCANNING) }
    LaunchedEffect(capture.original, isProcessing, capture.segmentation) {
        if (isProcessing || capture.original == null) return@LaunchedEffect
        if (capture.segmentation != null) {
            phase = ScanPhase.HIGHLIGHT
            haptics.soft()
            delay(1100)
        }
        phase = ScanPhase.LIFTED
        haptics.success()
    }
    return phase
}

/** The photo with the scan line, then the white outline, then the lifted sticker. */
@Composable
internal fun ScanViewfinder(photo: Bitmap?, segmentation: Segmentation?, cutout: Bitmap?, phase: ScanPhase, modifier: Modifier) {
    val colors = Opaw.colors
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
        if (photo == null) return@Box
        val lifted = phase == ScanPhase.LIFTED && cutout != null
        val photoAlpha by animateFloatAsState(if (lifted) 0f else 1f, tween(450), label = "photo")
        val dim by animateFloatAsState(if (phase == ScanPhase.HIGHLIGHT) 0.5f else 0f, tween(400), label = "dim")
        val outline by animateFloatAsState(if (phase == ScanPhase.SCANNING) 0f else 7f, spring(dampingRatio = 0.55f, stiffness = 260f), label = "outline")

        if (photoAlpha > 0.01f) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Fit the photo inside the card so the outline lines up with it exactly.
                val ratio = photo.width.toFloat() / photo.height
                val boxRatio = maxWidth / maxHeight
                val w = if (ratio > boxRatio) maxWidth else maxHeight * ratio
                val h = if (ratio > boxRatio) maxWidth / ratio else maxHeight
                Box(Modifier.size(w, h).graphicsLayer { alpha = photoAlpha; val s = 1f + 0.04f * (1f - photoAlpha); scaleX = s; scaleY = s }) {
                    val photoBitmap = remember(photo) { photo.asImageBitmap() }
                    Image(photoBitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                    if (segmentation != null && phase != ScanPhase.SCANNING) {
                        val fg = remember(segmentation) { segmentation.foreground.asImageBitmap() }
                        val white = ColorFilter.tint(Color.White, BlendMode.SrcIn)
                        // A thick white edge: the pet drawn in white in 16 directions around itself.
                        for (i in 0 until 16) {
                            val angle = i * Math.PI / 8
                            Image(
                                fg, null,
                                Modifier.fillMaxSize().offset((outline * cos(angle)).dp, (outline * sin(angle)).dp),
                                contentScale = ContentScale.FillBounds, colorFilter = white,
                            )
                        }
                        Image(fg, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    if (phase == ScanPhase.SCANNING) ScanSweep(Modifier.fillMaxSize())
                }
            }
        }
        if (lifted) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            val scale by animateFloatAsState(if (shown) 1f else 0.85f, spring(dampingRatio = 0.6f, stiffness = 220f), label = "lift")
            StickerPet(
                cutout,
                Modifier.padding(36.dp).fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; alpha = 1f - photoAlpha },
                edge = 6.dp,
                motion = LoopMotion.BOB,
                duration = 3.2,
            )
            Box(Modifier.align(Alignment.TopStart).padding(16.dp).alpha(1f - photoAlpha)) { SparkleChip("Lifted out") }
        }
    }
}

/** A bright line sweeping down and up the photo, with a soft glow trailing behind it. */
@Composable
private fun ScanSweep(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val pulse by transition.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        drawRect(Color.Black.copy(alpha = 0.18f))
        // A faint dot grid, so the scan reads as "looking".
        val step = 22.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.22f * pulse), 1.4.dp.toPx(), Offset(x, y))
                x += step
            }
            y += step
        }
        val lineY = t * size.height
        val trail = 90.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.38f)), startY = lineY - trail, endY = lineY),
            topLeft = Offset(0f, lineY - trail), size = Size(size.width, trail),
        )
        drawRect(Color.White, topLeft = Offset(0f, lineY - 1.5.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
    }
}

/**
 * Offers to turn the pet into a 3D model with Meshy. Shows why when the photo isn't clear enough,
 * and a lock for free users (tapping it opens Premium).
 */
@Composable
fun BringToLifeCard(
    name: String,
    issues: List<PhotoIssue>?,
    isPremium: Boolean,
    selected: Boolean,
    selectedText: String,
    onBringToLife: () -> Unit,
    onRetake: (() -> Unit)?,
    modifier: Modifier = Modifier,
    working: Boolean = false,
) {
    val colors = Opaw.colors
    Column(modifier.fillMaxWidth().whiteCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Action, CircleShape), contentAlignment = Alignment.Center) {
                Icon(DesignIcon.SPARKLE, size = 20.dp, weight = 2.4f, tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bring $name to life", style = opaw(17.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (!isPremium) {
                        Row(
                            Modifier.background(colors.chip, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(DesignIcon.LOCK, size = 11.dp, tint = colors.ink)
                            Text("Premium", style = opaw(11.sp, FontWeight.ExtraBold), color = colors.ink)
                        }
                    }
                }
                Text("A 3D $name that walks, runs and spins around.", style = opaw(14.sp, FontWeight.Medium), color = colors.muted)
            }
        }
        when {
            issues == null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = colors.ink, strokeWidth = 2.dp)
                Text("Checking the photo…", style = opaw(14.sp, FontWeight.SemiBold), color = colors.muted)
            }
            issues.isNotEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().background(colors.chip, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Needs a clearer photo", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.ink)
                    issues.take(3).forEach { issue ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(DesignIcon.ALERT, Modifier.padding(top = 2.dp), size = 14.dp, tint = colors.alert)
                            Column {
                                Text(issue.title, style = opaw(14.sp, FontWeight.Bold), color = colors.ink)
                                Text(issue.tip, style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                            }
                        }
                    }
                }
                if (onRetake != null) BlackPillButton("Retake photo", height = 52.dp, inverted = true, fontSize = 16, onClick = onRetake)
            }
            selected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DesignIcon.CHECK, size = 18.dp, tint = colors.good)
                Text(selectedText, style = opaw(15.sp, FontWeight.Bold), color = colors.good)
            }
            else -> BlackPillButton(
                if (isPremium) "Bring to life" else "Unlock with Premium",
                height = 52.dp, fontSize = 16, enabled = !working, onClick = onBringToLife,
            )
        }
    }
}
