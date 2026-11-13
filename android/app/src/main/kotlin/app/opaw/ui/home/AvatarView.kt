package app.opaw.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.opaw.core.AvatarMood
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The on-device avatar: the pet's cutout gently breathing, bobbing and reacting to its mood. Tap it to
 * make it hop. Mirrors LivingPhotoView.swift. Motion clips from the avatar worker play in its place
 * once they're ready (AvatarView.swift).
 */
@Composable
fun LivingPhoto(image: Bitmap?, mood: AvatarMood, modifier: Modifier = Modifier, sticker: androidx.compose.ui.unit.Dp = 0.dp, faded: Boolean = false) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var millis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { millis = it } }
    val t = millis / 1000.0
    val breathe = sin(t * 2 * PI / 3.2)
    var scaleY = 1 + 0.018 * breathe
    val scaleX = 1 - 0.008 * breathe
    var tilt = 0.0
    var bob = 0.0
    when (mood) {
        AvatarMood.HAPPY, AvatarMood.PROUD -> {
            tilt = 2.5 * sin(t * 2 * PI / 1.6)
            bob = -3 * max(0.0, sin(t * 2 * PI / 0.8))
        }
        AvatarMood.RESTLESS -> {
            tilt = 3.5 * sin(t * 2 * PI / 0.7)
            bob = -6 * max(0.0, sin(t * 2 * PI / 0.5))
        }
        AvatarMood.HUNGRY, AvatarMood.THIRSTY -> tilt = 6 * sin(t * 2 * PI / 4)
        AvatarMood.SLEEPY -> { scaleY = 1 + 0.03 * sin(t * 2 * PI / 5); tilt = -3.0 }
        AvatarMood.FRESH -> tilt = 4 * sin(t * 2 * PI / 0.25) * max(0.0, sin(t * 2 * PI / 4))
    }
    Box(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
            haptics.tap()
            scope.launch {
                hop.animateTo(28f, tween(180))
                hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.fillMaxSize().graphicsLayer {
            this.scaleX = scaleX.toFloat()
            this.scaleY = scaleY.toFloat()
            rotationZ = tilt.toFloat()
            translationY = (bob.toFloat() - hop.value) * density
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
        if (image != null) {
            val bitmap = remember(image) { image.asImageBitmap() }
            Box(layer.graphicsLayer { alpha = if (faded) 0.6f else 1f }) {
                if (sticker > 0.dp) {
                    // The soft drop shadow and the thick white edge of a printed sticker.
                    val shadow = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f), androidx.compose.ui.graphics.BlendMode.SrcIn)
                    Image(bitmap, null, Modifier.fillMaxSize().offset(y = sticker * 2.5f), contentScale = ContentScale.Fit, colorFilter = shadow)
                    val white = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.BlendMode.SrcIn)
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)) {
                        Image(bitmap, null, Modifier.fillMaxSize().offset(sticker * dx, sticker * dy), contentScale = ContentScale.Fit, colorFilter = white)
                    }
                }
                Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        } else {
            Box(layer.padding(40.dp), contentAlignment = Alignment.Center) { Icon(DesignIcon.PAW, size = 160.dp, tint = Ink.copy(alpha = 0.3f)) }
        }
    }
}
