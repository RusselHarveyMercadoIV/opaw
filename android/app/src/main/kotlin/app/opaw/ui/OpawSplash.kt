package app.opaw.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four toes of the "O" paw, on the icon's 200-unit grid: centre x, centre y, tilt in degrees. */
private val Toes = listOf(Triple(52f, 80f, -30f), Triple(80f, 54f, -10f), Triple(120f, 54f, 10f), Triple(148f, 80f, 30f))

/** The system launch screen's flat orange (`@color/opaw_orange`), where the opening starts. */
private val LaunchOrange = Color(0xFFFF7A1A)
private val IconTop = Color(0xFFFF8F2E)
private val IconBottom = Color(0xFFFF6A0F)

private val TileSize = 120.dp
/** The tile sits this far above the middle of the screen, leaving room for the word below. */
private val TileLift = 40.dp

/**
 * The animated opening (design 11a). The screen starts all orange, the same flat orange as the
 * system launch screen, so there's no white flash. The orange pulls in from the edges into the
 * app icon tile, the white o draws round, the four toes pop in one by one, then the paw squishes
 * once as "Opaw" rises in. About 1.5 s, then [onFinished]. It stays on the finished icon
 * afterwards, so it can also stand in while the account's pets are brought back. With animations
 * turned off in the system settings it shows the finished icon straight away.
 */
@Composable
fun OpawSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    val pull = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val toes = remember { List(Toes.size) { Animatable(0f) } }
    val squish = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val motionOff = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (motionOff) {
            (listOf(pull, ring, word) + toes).forEach { it.snapTo(1f) }
            delay(400)
            finished()
            return@LaunchedEffect
        }
        // A beat of plain orange, matching the system launch screen it follows.
        delay(150)
        // Springy, so the tile overshoots a little smaller and settles: it feels pulled in.
        launch { pull.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f)) }
        delay(470)
        launch { ring.animateTo(1f, tween(440, easing = FastOutSlowInEasing)) }
        delay(360)
        toes.forEachIndexed { index, toe ->
            launch {
                delay(index * 70L)
                toe.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 600f))
            }
        }
        delay(Toes.size * 70L + 120)
        launch { word.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        squish.animateTo(1f, tween(130))
        squish.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
        delay(150)
        finished()
    }

    Box(Modifier.fillMaxSize().background(Opaw.colors.cream), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Opaw" }) {
            val tile = TileSize.toPx()
            val target = Offset((size.width - tile) / 2f, size.height / 2f - TileLift.toPx() - tile / 2f)
            val p = pull.value
            fun mix(from: Float, to: Float) = from + (to - from) * p
            val left = mix(0f, target.x)
            val top = mix(0f, target.y)
            val right = mix(size.width, target.x + tile)
            val bottom = mix(size.height, target.y + tile)
            val shade = p.coerceIn(0f, 1f)
            // Squish around the bottom middle of the tile, like a paw pressing down.
            scale(1f + 0.05f * squish.value, 1f - 0.08f * squish.value, pivot = Offset(size.width / 2f, target.y + tile)) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(lerp(LaunchOrange, IconTop, shade), lerp(LaunchOrange, IconBottom, shade)), startY = top, endY = bottom),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(tile * 0.2237f * shade),
                )
                val u = tile / 200f
                translate(target.x, target.y) {
                    if (ring.value > 0f) {
                        drawArc(
                            Color.White, -90f, 360f * ring.value, useCenter = false,
                            topLeft = Offset(64f * u, 88f * u), size = Size(72f * u, 72f * u),
                            style = Stroke(22f * u, cap = StrokeCap.Round),
                        )
                    }
                    Toes.forEachIndexed { index, (x, y, tilt) ->
                        val pop = toes[index].value
                        if (pop > 0f) {
                            translate(x * u, y * u) {
                                rotate(tilt, pivot = Offset.Zero) {
                                    scale(pop, pop, pivot = Offset.Zero) {
                                        drawOval(Color.White, topLeft = Offset(-11f * u, -15f * u), size = Size(22f * u, 30f * u))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Opaw",
            style = opaw(30.sp, FontWeight.Black),
            color = Opaw.colors.ink,
            // Centred 18 dp under the tile: lift, half the tile, the gap, half the line.
            modifier = Modifier.offset(y = -TileLift + TileSize / 2 + 18.dp + 20.dp).graphicsLayer {
                alpha = word.value
                translationY = (1f - word.value) * 10.dp.toPx()
            },
        )
    }
}
