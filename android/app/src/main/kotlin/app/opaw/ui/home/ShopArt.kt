package app.opaw.ui.home

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG

/**
 * The shop's food and treat art (16e, 16f), made with Higgsfield as flat SVG in the room's style:
 * a can for each food (can-*), that food in the bowl (food-*), each treat (treat-*) and the bags
 * treats come in (bag-*). Kept in assets/shop, and in docs/brand/shop.
 */
internal object ShopArt {
    private val parsed = HashMap<String, SVG?>()

    /** The art by name, such as "can-salmon"; null when there's none. */
    fun svg(context: Context, name: String): SVG? = parsed.getOrPut(name) {
        runCatching { context.assets.open("shop/$name.svg").use { SVG.getFromInputStream(it) } }.getOrNull()
    }

    /** The can a food comes in: "food-salmon" is sold as "can-salmon". */
    fun can(foodId: String): String = "can-" + foodId.removePrefix("food-")
}

/** Draws [svg] fitted into [box], squashed to [fill] of its height from the bottom (food going down). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShop(svg: SVG, box: RectF, fill: Float = 1f) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        if (fill < 1f) native.scale(1f, fill.coerceAtLeast(0.01f), box.centerX(), box.bottom)
        runCatching { svg.renderToCanvas(native, box) }
        native.restore()
    }
}

/** A piece of shop art as a picture, for the shop's cards. */
@Composable
internal fun ShopPicture(name: String, modifier: Modifier) {
    val svg = ShopArt.svg(LocalContext.current, name) ?: return
    Canvas(modifier) { drawShop(svg, RectF(0f, 0f, size.width, size.height)) }
}

/**
 * Tells a pet in its room to do something now: eat at the bowl after a meal is logged (16g),
 * drink after water, or hop for a treat (16f). Home and the Shop read the cue for the pet shown,
 * for the 3D pet and the flat one alike.
 */
class RoomCue(val kind: Kind, val treatId: String? = null) {
    enum class Kind(val seconds: Float) { EAT(6f), DRINK(4f), TREAT(2.4f) }

    val id: Long = System.nanoTime()
    private val startedAt = System.currentTimeMillis()

    /** Whether it's still playing. */
    fun active(now: Long = System.currentTimeMillis()): Boolean = now - startedAt < kind.seconds * 1000
    fun elapsed(now: Long = System.currentTimeMillis()): Float = (now - startedAt) / 1000f
}

object RoomCues {
    private val cues = mutableStateMapOf<String, RoomCue>()

    fun send(dogId: String, cue: RoomCue) {
        cues[dogId] = cue
    }

    /** The pet's latest cue, playing or not; check [RoomCue.active]. */
    fun latest(dogId: String): RoomCue? = cues[dogId]
}
