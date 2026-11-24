package app.opaw.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Earning Paws (16a): coins pop out of the toast's "+5" (or the middle of the screen when there's
 * no toast), scatter, then fly in an arc to the Paws count on Home, which ticks up as each lands.
 * Positions are in root coordinates; [PawsBurstLayer] draws the coins above everything.
 */
object PawsFx {
    /** The centre of each pet's Paws coin on Home, while it's on screen. */
    val targets = mutableStateMapOf<String, Offset>()
    /** The centre of the "+5" coin pill on the toast showing now. */
    var source by mutableStateOf<Offset?>(null)
    /** Where the next coins pop out of, when a log came from something on screen (the bowl), and when. */
    private var origin: Offset? = null
    private var originAt = 0L

    /** The coins for the log about to happen come out of [at], such as the bowl that was tapped. */
    fun from(at: Offset) {
        origin = at
        originAt = System.currentTimeMillis()
    }

    /** The count Home last showed for each pet, so Paws earned elsewhere fly in on the way back. */
    val shown = HashMap<String, Int>()
    internal val flights = mutableStateListOf<Flight>()
    /** The size of the layer the coins are drawn in, for the fallback source. */
    internal var layerSize by mutableStateOf(Offset.Zero)
    internal var layerOrigin by mutableStateOf(Offset.Zero)

    internal class Flight(val start: Offset, val scatter: Offset, val bend: Offset, val target: Offset) {
        val progress = Animatable(0f)
    }

    /**
     * Flies [gained] Paws from the toast to [dogId]'s count; [onLand] gets each coin's share as it
     * lands. Suspends until every coin is in.
     */
    suspend fun burst(dogId: String, gained: Int, density: Float, onLand: (Int) -> Unit) {
        val target = targets[dogId]
        if (gained <= 0 || target == null) { onLand(gained); return }
        // Out of what was tapped (16g), else the toast's "+5", else the middle of the screen.
        val tapped = origin?.takeIf { System.currentTimeMillis() - originAt < 3000 }
        origin = null
        val start = tapped ?: source ?: (layerOrigin + Offset(layerSize.x / 2f, layerSize.y * 0.6f))
        val count = gained.coerceIn(3, 8)
        // Split the Paws so the shares add up exactly; the last coins carry the remainder.
        val shares = List(count) { i -> gained / count + if (i >= count - gained % count) 1 else 0 }
        val random = Random(System.nanoTime())
        coroutineScope {
            shares.forEachIndexed { i, share ->
                val angle = -PI / 2 + (random.nextDouble() - 0.5) * PI * 1.5
                val reach = (46 + random.nextInt(42)) * density
                val scatter = start + Offset((cos(angle) * reach).toFloat(), (sin(angle) * reach).toFloat())
                // Bow the path out sideways so the coins don't fly in one line.
                val mid = (scatter + target) / 2f
                val bend = mid + Offset((random.nextFloat() - 0.5f) * 160 * density, (random.nextFloat() * 40 + 30) * density)
                val flight = Flight(start, scatter, bend, target)
                launch {
                    delay(i * 55L)
                    flights.add(flight)
                    try {
                        flight.progress.animateTo(1f, tween(900, easing = LinearEasing))
                        onLand(share)
                    } finally {
                        flights.remove(flight)
                    }
                }
            }
        }
    }
}

private const val POP = 0.32f

private fun easeOutBack(t: Float): Float {
    val c = 1.9f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

private fun easeInOut(t: Float): Float = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

/** Draws the coins in flight. Put it last in the app's root box so it sits above toasts and bars. */
@Composable
fun BoxScope.PawsBurstLayer() {
    val size = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        Modifier.matchParentSize().onGloballyPositioned {
            PawsFx.layerOrigin = it.positionInRoot()
            PawsFx.layerSize = Offset(it.size.width.toFloat(), it.size.height.toFloat())
        },
    ) {
        val origin = PawsFx.layerOrigin
        for (flight in PawsFx.flights) {
            Box(
                Modifier.size(24.dp).graphicsLayer {
                    val p = flight.progress.value
                    val at: Offset
                    val scale: Float
                    if (p < POP) {
                        // Pop: out of the pill and grow, overshooting a little.
                        val t = p / POP
                        val e = easeOutBack(t)
                        at = flight.start + (flight.scatter - flight.start) * e
                        scale = 0.35f + 0.85f * e
                    } else {
                        // Fly: a curve from where it landed to the count, shrinking into it.
                        val t = easeInOut((p - POP) / (1f - POP))
                        val u = 1 - t
                        at = flight.scatter * (u * u) + flight.bend * (2 * u * t) + flight.target * (t * t)
                        scale = 1.2f - 0.45f * t
                    }
                    translationX = at.x - origin.x - size / 2
                    translationY = at.y - origin.y - size / 2
                    scaleX = scale
                    scaleY = scale
                    rotationY = p * 540f
                },
            ) { SvgArt(DesignArt.PAW_COIN, Modifier.fillMaxSize()) }
        }
    }
}
