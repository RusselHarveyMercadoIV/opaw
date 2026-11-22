package app.opaw.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.OpawApplication
import app.opaw.data.points
import app.opaw.data.reaction
import app.opaw.services.CareRecorder
import app.opaw.services.CareToast
import app.opaw.services.Notice
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Shows the app's toasts at the bottom of this box, [bottom] above its bottom edge. Mirrors `ToastHost`. */
@Composable
fun BoxScope.ToastHost(bottom: Dp) {
    val context = LocalContext.current
    val app = context.applicationContext as OpawApplication
    val center = app.toasts
    val haptics = rememberHaptics()
    SideEffect { center.haptics = haptics }
    val item = center.item

    AnimatedContent(
        targetState = item,
        transitionSpec = {
            (slideInVertically(tween(320)) { 24 } + fadeIn(tween(320))) togetherWith (slideOutVertically(tween(200)) { 10 } + fadeOut(tween(200)))
        },
        contentKey = { it?.id },
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp).padding(bottom = bottom),
        label = "toast",
    ) { shown ->
        shown ?: return@AnimatedContent
        var drag by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(shown.id) {
            val seconds = shown.seconds ?: return@LaunchedEffect
            delay((seconds * 1000).toLong())
            center.dismiss(shown.id)
        }
        Box(
            Modifier
                .offset { IntOffset(0, max(0f, drag).roundToInt()) }
                .clickable(MutableInteractionSource(), indication = null) { center.dismiss(shown.id) }
                .pointerInput(shown.id) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > 30 * density) center.dismiss(shown.id); drag = 0f },
                        onVerticalDrag = { _, amount -> drag += amount },
                    )
                },
        ) {
            when (shown) {
                is CareToast -> CareToastView(shown, undoAction(shown))
                is Notice -> NoticeView(shown) {
                    center.dismiss(shown.id)
                    shown.action?.invoke()
                }
            }
        }
    }
}

@Composable
private fun undoAction(toast: CareToast): (() -> Unit)? {
    val context = LocalContext.current
    val app = context.applicationContext as OpawApplication
    return when (toast.style) {
        CareToast.Style.LOGGED -> toast.eventId?.let { id ->
            { app.scope.launch { app.toasts.show(CareRecorder.remove(context, id, CareToast.Style.UNDONE)) } }
        }
        CareToast.Style.DELETED -> toast.restore?.let { event ->
            {
                app.scope.launch {
                    CareRecorder.restore(context, event)
                    app.toasts.dismiss(toast.id)
                }
            }
        }
        CareToast.Style.UNDONE -> null
    }
}

/**
 * The toast pill (7b): black with the pet bobbing inside a countdown ring after a log, white for
 * deletes and undos. Tap it or swipe it down to dismiss.
 */
@Composable
fun CareToastView(toast: CareToast, onUndo: (() -> Unit)?) {
    val dark = toast.style == CareToast.Style.LOGGED
    val ink = if (dark) Color.White else Ink
    val headline = when (toast.style) {
        CareToast.Style.LOGGED -> "“${toast.kind.reaction}”"
        CareToast.Style.DELETED -> "${toast.name} log deleted"
        CareToast.Style.UNDONE -> "${toast.name} log removed"
    }
    val detail = when (toast.style) {
        CareToast.Style.LOGGED -> "${toast.name} logged · score ${toast.before} → ${toast.after}"
        else -> "Score back to ${toast.after}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (dark) 0.25f else 0.12f), spotColor = Color.Black.copy(alpha = if (dark) 0.25f else 0.12f))
            .background(if (dark) Ink else Color.White, CircleShape)
            .defaultMinSize(minHeight = if (dark) 0.dp else 60.dp)
            .padding(start = if (dark) 8.dp else 22.dp, end = if (onUndo == null) 22.dp else 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dark) ToastPet(toast)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(headline, style = opaw(16.sp, FontWeight.ExtraBold), color = ink, maxLines = 1)
            Text(detail, style = opaw(13.sp, FontWeight.Medium), color = if (dark) Color(0xFFAAAAAA) else Color(0xFF666666), maxLines = 1)
        }
        if (dark && toast.paws > 0) {
            // The Paws it earned, on an orange coin pill.
            Row(
                Modifier.background(Action, CircleShape).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Icon(DesignIcon.PAW, size = 12.dp, tint = Action) }
                Text("+${toast.paws}", style = opaw(16.sp, FontWeight.Black), color = Color.White)
            }
        } else if (dark) {
            Text("+${toast.kind.points}", style = opaw(19.sp, FontWeight.Black), color = Color.White)
        }
        if (onUndo != null) {
            Row(
                Modifier
                    .height(44.dp)
                    .pressable(onClick = onUndo)
                    .background(if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFFF1F1F2), CircleShape)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.UNDO, size = 16.dp, tint = ink)
                Text("Undo", style = opaw(14.sp, FontWeight.ExtraBold), color = ink)
            }
        }
    }
}

/** The pet in a white circle, with a ring that counts down until the toast goes. */
@Composable
private fun ToastPet(toast: CareToast) {
    val image = rememberPhoto(toast.imagePath, 300)
    val ring = remember(toast.id) { Animatable(1f) }
    LaunchedEffect(toast.id) { ring.animateTo(0f, tween((toast.seconds * 1000).toInt(), easing = LinearEasing)) }
    Box(Modifier.padding(3.dp).size(56.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(remember(image) { image.asImageBitmap() }, null, Modifier.fillMaxSize().padding(6.dp).loopMotion(LoopMotion.BOB, 0.9), contentScale = ContentScale.Fit)
            } else {
                Icon(DesignIcon.PAW, size = 26.dp, tint = Ink)
            }
        }
        Canvas(Modifier.size(64.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(Color.White, -90f, 360f * ring.value, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** A notice pill: an icon in a circle, two lines, and an optional action. */
@Composable
fun NoticeView(notice: Notice, onAction: () -> Unit) {
    val dark = notice.kind == Notice.Kind.MILESTONE
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (dark) 0.25f else 0.14f), spotColor = Color.Black.copy(alpha = if (dark) 0.25f else 0.14f))
            .background(if (dark) Ink else Color.White, CircleShape)
            .defaultMinSize(minHeight = 78.dp)
            .padding(8.dp)
            .padding(end = if (notice.actionTitle == null) 14.dp else 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val background = when (notice.kind) {
            Notice.Kind.DUE -> Opaw.colors.alert
            Notice.Kind.FAMILY -> Color(0xFFE6E6E8)
            Notice.Kind.MILESTONE -> Color.White
            else -> Color(0xFFF4F4F5)
        }
        val tint = if (notice.kind == Notice.Kind.DUE) Color.White else Ink
        Box(Modifier.size(62.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            when (notice.kind) {
                Notice.Kind.FAMILY -> {
                    val image = rememberPhoto(notice.imagePath, 300)
                    if (image != null) Image(remember(image) { image.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(DesignIcon.USERS, size = 24.dp, tint = tint)
                }
                Notice.Kind.DUE -> Icon(DesignIcon.MEDS, size = 24.dp, weight = 2.4f, tint = tint)
                Notice.Kind.OFFLINE -> Icon(DesignIcon.CLOUD_OFF, size = 24.dp, tint = tint)
                Notice.Kind.FAILED -> Icon(DesignIcon.ALERT, size = 24.dp, tint = tint)
                Notice.Kind.MILESTONE -> Text(notice.medal ?: "★", style = opaw(22.sp, FontWeight.Black), color = Ink, maxLines = 1, modifier = Modifier.padding(6.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(notice.title, style = opaw(16.sp, FontWeight.ExtraBold), color = if (dark) Color.White else Ink, maxLines = 1)
            Text(notice.detail, style = opaw(13.sp, FontWeight.Medium), color = if (dark) Color(0xFFAAAAAA) else Color(0xFF666666), maxLines = 1)
        }
        if (notice.actionTitle != null && notice.action != null) {
            Box(
                Modifier.height(46.dp).pressable(onClick = onAction).background(if (dark) Color.White.copy(alpha = 0.16f) else Action, CircleShape).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(notice.actionTitle, style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White) }
        }
    }
}

