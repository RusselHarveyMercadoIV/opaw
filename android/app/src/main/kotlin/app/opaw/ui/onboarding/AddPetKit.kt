package app.opaw.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.GhostPuppy
import app.opaw.ui.components.Icon
import app.opaw.ui.components.pressable
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlin.math.PI
import kotlin.math.sin

// The pieces of the v5 "Adding a pet" screens (10c to 10l): clean white screens, one idea per
// screen, light grey stages for the pet, orange for anything you do, and the pet itself moving
// on every step.

/** The colours of the add-a-pet screens, which sit on white rather than the app's grey ground. */
internal object Setup {
    /** The page itself: white, or the dark card colour at night. */
    val ground: Color @Composable get() = Opaw.colors.surface
    /** Light grey stages, tiles and inputs on the white page. */
    val fill: Color @Composable get() = Opaw.colors.cream
    /** A paler grey for tiles that aren't picked. */
    val soft: Color @Composable get() = if (isLight) Color(0xFFF6F6F7) else Opaw.colors.chip
    /** The thin outline that stands in for a card's shadow on white. */
    val line: Color @Composable get() = if (isLight) Color(0xFFEDEDF0) else Opaw.colors.hairline
    /** Pale orange behind picked things and orange icons. */
    val peach: Color @Composable get() = if (isLight) Color(0xFFFFF1E6) else Action.copy(alpha = 0.2f)
    /** Orange words on [peach]. */
    val deep: Color @Composable get() = if (isLight) Color(0xFFB84300) else Color(0xFFFFA36B)
    /** The two oranges of the brand gradient, for the build bar and the reveal. */
    val glowTop = Color(0xFFFF8F2E)
    val glowBottom = Color(0xFFFF6A0F)

    private val isLight: Boolean @Composable get() = Opaw.colors.surface == Color.White
}

/** A white card on the white page: a hairline edge and the faintest shadow. */
@Composable
internal fun Modifier.setupCard(radius: Dp = 26.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
        .background(Setup.ground, shape)
        .border(1.dp, Setup.line, shape)
}

/**
 * The top of each setup screen: a grey round back button, the steps as short bars that fill in
 * orange, and the chapter's name, or [right] in its place.
 */
@Composable
internal fun SetupHeader(filled: Int, of: Int, label: String?, onBack: (() -> Unit)?, right: (@Composable () -> Unit)? = null) {
    val colors = Opaw.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(44.dp).pressable(onClick = onBack).background(Setup.fill, CircleShape), contentAlignment = Alignment.Center) {
                Icon(DesignIcon.BACK, size = 20.dp, tint = colors.ink)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until of) {
                val fill by animateColorAsState(if (i < filled) Action else colors.track, tween(400), label = "segment")
                Box(Modifier.weight(1f).height(6.dp).background(fill, CircleShape))
            }
        }
        when {
            right != null -> right()
            label != null -> Text(label, style = opaw(13.sp, FontWeight.ExtraBold), color = colors.ghost, maxLines = 1)
        }
    }
}

/** A small grey pill in the header, such as "Join with a code". */
@Composable
internal fun HeaderPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = opaw(13.sp, FontWeight.ExtraBold), color = Opaw.colors.ink, maxLines = 1,
        modifier = Modifier.pressable(onClick = onClick).background(Setup.fill, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** A screen's big title and its grey line under it. */
@Composable
internal fun SetupTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, center: Boolean = false, onOrange: Boolean = false) {
    val colors = Opaw.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = opaw(30.sp, FontWeight.Black).copy(lineHeight = 33.sp),
            color = if (onOrange) Color.White else colors.ink,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 22.sp),
                color = if (onOrange) Color(0xFFFFE3D1) else colors.muted,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** The orange pill button, or a white one with orange words on the orange reveal. */
@Composable
internal fun OrangeButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    white: Boolean = false,
    icon: DesignIcon? = null,
    /** A small tag at the end, such as "3D · Premium". */
    badge: String? = null,
    onClick: () -> Unit,
) {
    val glow = if (white) Color(0xFF782800) else Action
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pressable(enabled, onClick = onClick)
            .shadow(14.dp, CircleShape, ambientColor = glow.copy(alpha = 0.3f), spotColor = glow.copy(alpha = 0.3f))
            .background(if (white) Color.White else Action, CircleShape)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (white) Action else Color.White
        if (icon != null) Icon(icon, size = 21.dp, weight = 2.4f, tint = ink)
        Text(title, style = opaw(17.sp, FontWeight.ExtraBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (badge != null) {
            Text(
                badge, style = opaw(12.sp, FontWeight.ExtraBold), color = ink, maxLines = 1,
                modifier = Modifier.background(ink.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** A quiet text button under the main one. */
@Composable
internal fun SetupLink(text: String, modifier: Modifier = Modifier, color: Color = Opaw.colors.ghost, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = opaw(15.sp, FontWeight.ExtraBold), color = color, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f).pressable(enabled, onClick = onClick).padding(8.dp),
    )
}

/** The buttons pinned to the bottom of a setup screen. */
@Composable
internal fun SetupFoot(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** The round orange tick on anything done or picked. */
@Composable
internal fun Tick(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Action, CircleShape), contentAlignment = Alignment.Center) {
        Icon(DesignIcon.CHECK, size = size * 0.55f, weight = 3.2f, tint = Color.White)
    }
}

/** A white chip with a soft shadow, such as a check that popped in or "Our drawing". */
@Composable
internal fun SetupChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Setup.ground,
    color: Color = Opaw.colors.ink,
    flat: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = CircleShape
    Row(
        modifier
            .height(36.dp)
            .then(if (flat) Modifier else Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f)))
            .background(background, shape)
            .padding(start = if (leading != null) 10.dp else 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, style = opaw(13.sp, FontWeight.ExtraBold), color = color, maxLines = 1)
    }
}

/** The pet as it is, no sticker edge: the cut-out on white, or a faint puppy before there is one. */
@Composable
internal fun PlainPet(image: Bitmap?, modifier: Modifier = Modifier, flip: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            GhostPuppy(Modifier.fillMaxSize().padding(8.dp))
        } else {
            Image(
                remember(image) { image.asImageBitmap() }, null,
                Modifier.fillMaxSize().graphicsLayer { if (flip) scaleX = -1f },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** The pet in a round grey stage, cropped to its face, for avatars and rows. */
@Composable
internal fun PetDisc(image: Bitmap?, size: Dp, modifier: Modifier = Modifier, fill: Color = Setup.fill) {
    Box(modifier.size(size).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        PlainPet(image, Modifier.size(size * 0.76f))
    }
}

// MARK: - Motion

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))
private fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x

/** A 0 to 1 clock that loops every [millis]. */
@Composable
private fun loop(millis: Int): Float {
    val t by rememberInfiniteTransition(label = "loop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    return t
}

/** A hop with a squash on landing: up and a little stretched, then squashed as it lands. */
internal fun Modifier.hop(millis: Int = 2400, height: Dp = 18.dp): Modifier = composed {
    val t = loop(millis)
    graphicsLayer {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        when {
            t < 0.4f -> { val x = ease(t / 0.4f); translationY = -height.toPx() * x; scaleX = lerp(1f, 0.97f, x); scaleY = lerp(1f, 1.03f, x) }
            t < 0.55f -> { val x = ease((t - 0.4f) / 0.15f); translationY = -height.toPx() * (1 - x); scaleX = lerp(0.97f, 1.04f, x); scaleY = lerp(1.03f, 0.96f, x) }
            else -> { val x = ease((t - 0.55f) / 0.45f); scaleX = lerp(1.04f, 1f, x); scaleY = lerp(0.96f, 1f, x) }
        }
    }
}

/** A slow breath, wider than tall. */
internal fun Modifier.breathe(millis: Int = 3000): Modifier = composed {
    val t = loop(millis)
    graphicsLayer {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        val wave = (sin(2 * PI * t).toFloat() + 1) / 2
        scaleX = lerp(1f, 1.035f, wave)
        scaleY = lerp(1f, 0.98f, wave)
    }
}

/** Floats up and down, for cards that hover over the pet. */
internal fun Modifier.drift(millis: Int = 3000, height: Dp = 8.dp): Modifier = composed {
    val t = loop(millis)
    graphicsLayer { translationY = -height.toPx() * (1 - kotlin.math.cos(2 * PI * t).toFloat()) / 2 }
}

/** Pops in after [delay] of each [millis] loop, then stays, as each check appears in turn. */
internal fun Modifier.popIn(millis: Int, delay: Float): Modifier = composed {
    val t = loop(millis)
    graphicsLayer {
        val local = ((t - delay + 1f) % 1f)
        val s = when {
            local < 0.3f -> 0f
            local < 0.45f -> lerp(0f, 1.1f, (local - 0.3f) / 0.15f)
            local < 0.55f -> lerp(1.1f, 1f, (local - 0.45f) / 0.1f)
            else -> 1f
        }
        scaleX = s; scaleY = s; alpha = s.coerceAtMost(1f)
    }
}

/** A soft orange ring that keeps growing out from something to tap. */
internal fun Modifier.pulse(millis: Int = 1600): Modifier = composed {
    val t = loop(millis)
    val spread = (t / 0.7f).coerceAtMost(1f)
    drawBehind {
        drawCircle(Action.copy(alpha = 0.45f * (1 - spread)), radius = size.minDimension / 2 + 14.dp.toPx() * spread)
    }
}

/** Turns all the way round, slowly, for rays and orbits; [reverse] spins the other way. */
internal fun Modifier.spinning(millis: Int, reverse: Boolean = false): Modifier = composed {
    val t = loop(millis)
    graphicsLayer { rotationZ = 360f * t * if (reverse) -1 else 1 }
}
