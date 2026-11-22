package app.opaw.ui.home

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.services.RoomCatalog
import app.opaw.services.RoomSlot
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.ui.theme.opaw
import com.caverock.androidsvg.SVG
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * How Home's room is seen, matched to the design (13a): the room art is drawn on a 390 by 844
 * phone, and the 3D pet's camera puts the floor where the art has it. The vanishing point is at
 * (195, 258.4); the back wall's floor line is 199.7 below it and its sides 128.4 either side.
 * The 3D pet's view is centred on the vanishing point, the full width, and reaches the bottom.
 * World units, camera at z = 1 looking down -z.
 */
internal object RoomCamera {
    const val FOCAL = 30f
    private const val SENSOR = 24f
    val TAN = SENSOR / (2f * FOCAL)
    /** The floor, this far below the camera. */
    const val FLOOR = -1.43f
    /** The room's side walls, either side of the middle. */
    const val HALF = 0.919f
    /** How long the pet is, in world units. */
    const val PET_LENGTH = 0.72f
    /** How close to a side wall the pet goes. */
    const val WALL_GAP = 0.38f

    /** The design's phone, and where its vanishing point is. */
    const val DESIGN_WIDTH = 390f
    const val DESIGN_HEIGHT = 844f
    const val HORIZON = 258.4f
    /** The 3D view's height in design units: from as far above the horizon as the bottom is below. */
    const val VIEW_HEIGHT = 2f * (DESIGN_HEIGHT - HORIZON)

    /**
     * The pet's stage runs from 3.5 to 9 units away; the room is a little deeper, from where the
     * design's pet stands up front (about 5 away) to just in front of the back wall.
     */
    fun worldZ(stageZ: Float): Float {
        val d = 1f - stageZ
        return 1f - (4.9f + (d - 3.5f) * 0.855f)
    }
}

/** What tapping the room's things does. */
class RoomActions(
    val onBowl: () -> Unit,
    val onWater: () -> Unit,
    val onDoor: () -> Unit,
    val onBed: () -> Unit,
)

/** What a flat pet is doing in the room (13a to 13c). */
enum class RoomPose { WALK, WAIT, SLEEP, STILL }

/** Where each spot's piece sits on the design's phone: left, top and size of its 100 by 100 box. */
private class Spot(val left: Float, val top: Float, val size: Float)

private val spots = mapOf(
    RoomSlot.RUG to Spot(22f, 386f, 333f),
    RoomSlot.PLANT to Spot(255f, 366f, 119f),
    RoomSlot.BED to Spot(138f, 389f, 159f),
    RoomSlot.WALL to Spot(162f, 205f, 69f),
    RoomSlot.TOY to Spot(34f, 588f, 96f),
    RoomSlot.BOWL to Spot(181f, 554f, 134f),
)
private val lampSpot = Spot(-32f, 369f, 202f)
private val waterSpot = Spot(260f, 580f, 128f)

/** Parsed once: parsing SVG is slow enough to notice on every frame. */
private val parsed = HashMap<String, SVG>()

private fun svg(text: String): SVG = parsed.getOrPut(text) { SVG.getFromString(text) }

/** The night's dimming of the room's pieces, as in 13c: darker and a little less colourful. */
private val nightPaint by lazy {
    val m = ColorMatrix().apply { setSaturation(0.85f) }
    m.postConcat(ColorMatrix().apply { setScale(0.55f, 0.55f, 0.55f, 1f) })
    Paint().apply { colorFilter = ColorMatrixColorFilter(m) }
}

/** Draws one piece of room art in a box of the screen. */
private fun DrawScope.drawArt(text: String, box: RectF, dim: Boolean) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        if (dim) native.saveLayer(RectF(box.left - box.width(), box.top - box.height(), box.right + box.width(), box.bottom + box.height()), nightPaint)
        runCatching { svg(text).renderToCanvas(native, box) }
        if (dim) native.restore()
    }
}

/** A piece of room art by id, as a small picture (for the shop's cards). */
@Composable
internal fun PieceArt(id: String, modifier: Modifier) {
    val text = RoomArt.pieces[id] ?: return
    Canvas(modifier) { drawArt(text, RectF(0f, 0f, size.width, size.height), dim = false) }
}

/**
 * Home's room (13a to 13c), drawn from the design's own art: the room by day or by night, and
 * the pieces in their spots, with [pet] standing in it. [pet] gets the 3D view's place (the pet's
 * own camera) and the flat pet's place, which already walks the design's path round the room.
 * The toy and bowls stand in front of the pet. Tap the bowl to log a meal, the water for water,
 * the door with the leash for a walk, the bed for the day's list.
 */
@Composable
fun PetRoom(
    night: Boolean,
    modifier: Modifier = Modifier,
    mealDue: String? = null,
    pose: RoomPose = RoomPose.WALK,
    /** What's in each spot (16b); the starters when not given. */
    pieces: (RoomSlot) -> String = { RoomCatalog.starter(it).id },
    actions: RoomActions,
    /** Drawn over the room in decorate mode: a marker at each spot, given in pixels. */
    markers: (@Composable (spots: Map<RoomSlot, Offset>) -> Unit)? = null,
    pet: @Composable (view: Modifier, standing: Modifier) -> Unit,
) {
    val floorEnd = if (night) Color(0xFF353847) else Color.White
    val ceiling = if (night) Color(0xFF1C1D26) else Color(0xFFF3F3F5)
    BoxWithConstraints(modifier.fillMaxSize().background(floorEnd)) {
        val w = constraints.maxWidth.toFloat()
        val s = w / RoomCamera.DESIGN_WIDTH
        fun box(spot: Spot) = RectF(spot.left * s, spot.top * s, (spot.left + spot.size) * s, (spot.top + spot.size) * s)
        fun art(slot: RoomSlot) = RoomArt.pieces[pieces(slot)] ?: RoomArt.pieces[RoomCatalog.starter(slot).id]

        // The room and everything behind the pet.
        Canvas(Modifier.fillMaxSize()) {
            // The room art is 410 by 864, fitted to the design's 390 wide phone, 11 from the top.
            val fit = RoomCamera.DESIGN_WIDTH / 410f
            val top = (RoomCamera.DESIGN_HEIGHT - 864f * fit) / 2f * s
            drawRect(ceiling, size = Size(size.width, top + 2f))
            drawArt(if (night) RoomArt.ROOM_NIGHT else RoomArt.ROOM_DAY, RectF(0f, top, size.width, top + 864f * fit * s), dim = false)
            for (slot in listOf(RoomSlot.WALL, RoomSlot.RUG, RoomSlot.PLANT)) art(slot)?.let { drawArt(it, box(spots.getValue(slot)), night) }
            RoomArt.pieces["lamp-floor"]?.let { drawArt(it, box(lampSpot), false) }
            art(RoomSlot.BED)?.let { drawArt(it, box(spots.getValue(RoomSlot.BED)), night) }
        }
        if (mealDue != null) MealPulse(Modifier.place(208f * s, 636f * s, 80f * s, 30f * s))

        // The 3D pet's view, and the flat pet's place on the design (200 by 210, feet at 660).
        val viewTop = (RoomCamera.HORIZON - RoomCamera.VIEW_HEIGHT / 2f) * s
        val view = Modifier.place(0f, viewTop, w, RoomCamera.VIEW_HEIGHT * s)
        val standing = Modifier.place(95f * s, 450f * s, 200f * s, 210f * s).roomPose(pose, s)
        pet(view, standing)

        // In front of the pet: the toy and the bowls.
        Canvas(Modifier.fillMaxSize()) {
            art(RoomSlot.TOY)?.let { drawArt(it, box(spots.getValue(RoomSlot.TOY)), night) }
            art(RoomSlot.BOWL)?.let { drawArt(it, box(spots.getValue(RoomSlot.BOWL)), night) }
            if (pieces(RoomSlot.BOWL) != "bowl-bone") RoomArt.pieces["bowl-water"]?.let { drawArt(it, box(waterSpot), night) }
        }
        if (pose == RoomPose.SLEEP) {
            Text(
                "z z", style = opaw(22.sp, FontWeight.Black), color = Color(0xFFFFB27A),
                modifier = Modifier.place(286f * s, 412f * s, null, null).graphicsLayer { rotationZ = -8f },
            )
        }

        // The things to tap: only these spots take a touch, so swiping between pets still works.
        TapSpot(Rect(198f, 612f, 298f, 680f), s, actions.onBowl)
        TapSpot(Rect(276f, 636f, 372f, 698f), s, actions.onWater)
        TapSpot(Rect(88f, 296f, 162f, 458f), s, actions.onDoor)
        TapSpot(Rect(150f, 440f, 286f, 524f), s, actions.onBed)

        if (markers != null) {
            markers(spots.mapValues { (_, spot) -> Offset((spot.left + spot.size / 2f) * s, (spot.top + spot.size * 0.55f) * s) })
        }
        if (mealDue != null) MealBubble(mealDue, Modifier.place(118f * s, 392f * s, null, null), actions.onBowl)
    }
}

/** A tappable area of the room, in design units. */
@Composable
private fun TapSpot(area: Rect, s: Float, onTap: () -> Unit) {
    Box(Modifier.place(area.left * s, area.top * s, area.width * s, area.height * s).pressable(onClick = onTap))
}

/** The orange ring that pulses out round the bowl while a meal is due (13b). */
@Composable
private fun MealPulse(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(
        modifier
            .graphicsLayer { val k = 0.8f + 0.7f * t; scaleX = k; scaleY = k; alpha = 0.9f * (1f - t) }
            .border(3.dp, Action, CircleShape),
    )
}

/**
 * The flat pet's motion in the room. Walking follows the design's path (16 s): from the front
 * left to the back right, small with distance, turning round, then the middle left and back to
 * the front, bobbing as it goes. Waiting stands by the bowl; sleeping curls up in the bed.
 */
private fun Modifier.roomPose(pose: RoomPose, s: Float): Modifier = composed {
    val loop = rememberInfiniteTransition(label = "pose")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "walk")
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        val at = when (pose) {
            RoomPose.WAIT -> floatArrayOf(-74f, 0f, 0.94f, 1f)
            RoomPose.SLEEP -> floatArrayOf(12f, -150f, 0.46f, 1f)
            RoomPose.STILL -> floatArrayOf(0f, 0f, 1f, 1f)
            RoomPose.WALK -> walkAt(t)
        }
        val wave = (1 - cos(bob * 2 * PI).toFloat()) / 2f
        translationX = at[0] * s
        val lift = when (pose) {
            RoomPose.SLEEP -> 0.3f
            RoomPose.STILL -> 0f
            else -> 1f
        }
        translationY = at[1] * s - 10f * s * wave * lift
        scaleX = at[2] * at[3]
        scaleY = at[2]
        rotationZ = (-1.5f + 3f * wave) * lift
    }
}

/** The design's walk keyframes: time, then x, y, size and which way it faces. */
private val walkKeys = listOf(
    floatArrayOf(0f, -60f, 0f, 1f, 1f),
    floatArrayOf(0.24f, 64f, -150f, 0.6f, 1f),
    floatArrayOf(0.30f, 64f, -150f, 0.6f, -1f),
    floatArrayOf(0.56f, -92f, -78f, 0.8f, -1f),
    floatArrayOf(0.64f, -92f, -78f, 0.8f, 1f),
    floatArrayOf(0.88f, -60f, 0f, 1f, 1f),
    floatArrayOf(1f, -60f, 0f, 1f, 1f),
)

/** Where the walking pet is at [t] (0 to 1): x, y, size and facing, eased between keyframes. */
private fun walkAt(t: Float): FloatArray {
    val i = walkKeys.indexOfLast { it[0] <= t }.coerceIn(0, walkKeys.size - 2)
    val a = walkKeys[i]
    val b = walkKeys[i + 1]
    val span = (b[0] - a[0]).coerceAtLeast(1e-4f)
    val p = ((t - a[0]) / span).coerceIn(0f, 1f).let { (1 - cos(it * PI).toFloat()) / 2f }
    // Turning round is a flip, not a squash through nothing.
    val face = if (p < 0.5f) a[4] else b[4]
    return floatArrayOf(a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p, a[3] + (b[3] - a[3]) * p, face)
}

/** Puts something at an exact pixel spot and size inside the room; a null size wraps it. */
private fun Modifier.place(x: Float, y: Float, width: Float?, height: Float?): Modifier = layout { measurable, constraints ->
    val placeable = if (width != null && height != null) {
        measurable.measure(Constraints.fixed(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)))
    } else {
        measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    }
    layout(0, 0) { placeable.place(x.roundToInt(), y.roundToInt()) }
}

/** "Dinner time?" with a button to log it, over the bed (13b). */
@Composable
private fun MealBubble(meal: String, modifier: Modifier, onLog: () -> Unit) {
    val shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 8.dp)
    Box(modifier) {
        Row(
            Modifier
                .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(Color.White, shape)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${meal.replaceFirstChar(Char::uppercase)} time?", style = opaw(14.sp, FontWeight.ExtraBold), color = Ink)
            Row(
                Modifier.pressable(onClick = onLog).background(Action, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.MEAL, size = 15.dp, tint = Color.White)
                Text("Log $meal", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White)
            }
        }
    }
}
