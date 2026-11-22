package app.opaw.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.services.RoomCatalog
import app.opaw.services.RoomSlot
import app.opaw.ui.theme.opaw
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How Home's room is seen (13a): the same camera the 3D pet is drawn with, a wide lens a little
 * above the floor looking straight at the back wall, so the walls, ceiling and floor all run to
 * one point and the pet stands on the drawn floor wherever it goes. World units, camera at z = 1.
 */
internal object RoomCamera {
    const val FOCAL = 30f
    private const val SENSOR = 24f
    val TAN = SENSOR / (2f * FOCAL)
    /** The floor, this far below the camera. */
    const val FLOOR = -1.43f
    const val CEILING = 0.78f
    const val BACK = -9.4f
    /** How long the pet is, in world units. */
    const val PET_LENGTH = 0.72f
    /** How close to a side wall the pet goes. */
    const val WALL_GAP = 0.45f
    /** Where the walls stand, for a view this wide for its height. */
    fun halfWidth(aspect: Float) = 0.82f * (1f - BACK) * TAN * aspect

    /** The view the 3D pet is drawn in, as a share of the room's height: its height and the height of its middle. */
    const val VIEW_HEIGHT = 0.9f
    const val HORIZON = 0.36f
}

/** Projects room points onto the screen, through the 3D pet's view ([left], [top], [width] by [height] pixels). */
internal class RoomProjection(val left: Float, val top: Float, val width: Float, val height: Float) {
    val aspect = width / height
    val half = RoomCamera.halfWidth(aspect)

    fun p(x: Float, y: Float, z: Float): Offset {
        val d = 1f - z
        return Offset(
            left + (x / (d * RoomCamera.TAN * aspect) + 1f) / 2f * width,
            top + (1f - y / (d * RoomCamera.TAN)) / 2f * height,
        )
    }

    /** Pixels per world unit at depth [z], for things standing on the floor. */
    fun unit(z: Float) = height / (2f * (1f - z) * RoomCamera.TAN)

    /** Where something standing at ([x], [z]) touches the floor. */
    fun base(x: Float, z: Float) = p(x, RoomCamera.FLOOR, z)
}

/** The room's colours by day, and by night (13c), which is also its dark-mode look. */
private class RoomPalette(val night: Boolean) {
    val ceiling = if (night) Color(0xFF20222D) else Color(0xFFEDEDF0)
    val side = if (night) Color(0xFF252733) else Color(0xFFF0F0F2)
    val back = if (night) Color(0xFF2B2D3A) else Color(0xFFFAFAFB)
    val floor = if (night) Color(0xFF30323F) else Color(0xFFF6F6F7)
    val line = if (night) Color(0xFF3B3D4B) else Color(0xFFE6E6E9)
    val edge = if (night) Color(0xFF3F4152) else Color(0xFFE2E2E6)
    val rug = if (night) Color(0xFF9A6A48) else Color(0xFFFFB27A)
    val rugRing = if (night) Color(0xFFC79B78) else Color.White
    val door = if (night) Color(0xFF353849) else Color(0xFFF4F4F6)
    val frame = if (night) Color(0xFF4A4D61) else Color(0xFFD7D7DC)
    val sky = if (night) Color(0xFF1F2457) else Color(0xFFE4F1FF)
    val pot = if (night) Color(0xFFB9B9C2) else Color.White
    val leaf = if (night) Color(0xFF5E8E68) else Color(0xFF7DBB86)
    val shade = if (night) Color(0xFFFFE2B0) else Color(0xFFFFE9CC)
    val pole = if (night) Color(0xFFE9E9EE) else Color(0xFFDADADF)
    val bed = if (night) Color(0xFF8E4A1E) else Color(0xFFF26A1B)
    val bedTop = if (night) Color(0xFFB8774C) else Color(0xFFFFD4B3)
    val bowl = if (night) Color(0xFFB4501A) else Color(0xFFE8590C)
    val kibble = Color(0xFF6E3712)
    val water = if (night) Color(0xFF6A8FB0) else Color(0xFFA3D6F7)
    val waterBowl = if (night) Color(0xFF9A9AA6) else Color.White
}

/** Where the room's things are, in world units. */
private object Spots {
    const val BED_X = 0.05f
    const val BED_Z = -7.3f
    const val RUG_X = 0.1f
    const val RUG_Z = -6.5f
    const val LAMP_Z = -6.2f
    const val PLANT_Z = -7.0f
    const val BOWL_X = 0.15f
    const val BOWL_Z = -3.9f
    const val WATER_X = 0.85f
    const val WATER_Z = -3.6f
}

/** What tapping the room's things does. */
class RoomActions(
    val onBowl: () -> Unit,
    val onWater: () -> Unit,
    val onDoor: () -> Unit,
    val onBed: () -> Unit,
)

/**
 * Home's room (13a to 13c): the whole screen is a 3D box seen from the front, and [pet] stands
 * in it. [pet] gets the 3D view's place (the pet's own camera), and where a flat pet stands.
 * The room's things do the care: tap the bowl to log a meal, the water bowl for water, the door
 * with the leash for a walk. [mealDue] puts a bubble over the bowl, such as "Dinner time?".
 */
@Composable
fun PetRoom(
    night: Boolean,
    modifier: Modifier = Modifier,
    mealDue: String? = null,
    /** What's in each spot (16b); the starters when not given. */
    pieces: (RoomSlot) -> String = { RoomCatalog.starter(it).id },
    actions: RoomActions,
    /** Drawn over the room in decorate mode: a marker at each spot. */
    markers: (@Composable (spots: Map<RoomSlot, Offset>) -> Unit)? = null,
    pet: @Composable (view: Modifier, standing: Modifier) -> Unit,
) {
    val palette = remember(night) { RoomPalette(night) }
    BoxWithConstraints(modifier.fillMaxSize().background(palette.back)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val viewHeight = h * RoomCamera.VIEW_HEIGHT
        val viewTop = h * RoomCamera.HORIZON - viewHeight / 2f
        val room = remember(w, h) { RoomProjection(0f, viewTop, w, viewHeight) }

        Canvas(Modifier.fillMaxSize()) { drawRoom(room, palette, pieces) }

        // The 3D pet's view, and where a flat pet stands: in the middle of the rug, two units tall.
        val view = Modifier.place(0f, viewTop, w, viewHeight)
        val stand = room.base(0f, -5.2f)
        val tall = room.unit(-5.2f) * 1.7f
        val standing = Modifier.place(stand.x - tall * 0.5f, stand.y - tall, tall, tall)
        pet(view, standing)

        // The things to tap, in screen pixels.
        val targets = remember(room) { tapTargets(room) }
        Box(
            Modifier.fillMaxSize().pointerInput(targets, actions) {
                detectTapGestures { at ->
                    when {
                        targets.bowl.contains(at) -> actions.onBowl()
                        targets.water.contains(at) -> actions.onWater()
                        targets.door.contains(at) -> actions.onDoor()
                        targets.bed.contains(at) -> actions.onBed()
                    }
                }
            },
        )

        if (markers != null) markers(spotCenters(room))
        if (mealDue != null) {
            val bowl = room.base(Spots.BOWL_X, Spots.BOWL_Z)
            val u = room.unit(Spots.BOWL_Z)
            MealBubble(
                mealDue,
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) {
                        val x = (bowl.x - placeable.width / 2f).coerceIn(12.dp.toPx(), (w - placeable.width - 12.dp.toPx()).coerceAtLeast(12.dp.toPx()))
                        placeable.place(x.roundToInt(), (bowl.y - u * 0.9f - placeable.height).roundToInt())
                    }
                },
                onLog = actions.onBowl,
            )
        }
    }
}

/** Puts something at an exact pixel spot and size inside the room. */
private fun Modifier.place(x: Float, y: Float, width: Float, height: Float): Modifier = layout { measurable, _ ->
    val w = width.roundToInt().coerceAtLeast(1)
    val h = height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(0, 0) { placeable.place(x.roundToInt(), y.roundToInt()) }
}

/** "Dinner time?" with a button to log it, over the bowl (13b). */
@Composable
private fun MealBubble(meal: String, modifier: Modifier, onLog: () -> Unit) {
    Box(modifier) {
        Row(
            Modifier
                .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(Color.White, CircleShape)
                .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${meal.replaceFirstChar(Char::uppercase)} time?", style = opaw(14.sp, FontWeight.ExtraBold), color = Ink)
            Row(
                Modifier.pressable(onClick = onLog).background(Action, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.MEAL, size = 15.dp, tint = Color.White)
                Text("Log $meal", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
            }
        }
    }
}

private class Targets(val bowl: Rect, val water: Rect, val door: Rect, val bed: Rect)

private fun tapTargets(room: RoomProjection): Targets {
    fun around(x: Float, z: Float, wide: Float, tall: Float): Rect {
        val b = room.base(x, z)
        val u = room.unit(z)
        // A little generous, so a quick tap still lands.
        return Rect(b.x - u * wide * 0.7f, b.y - u * tall * 1.2f, b.x + u * wide * 0.7f, b.y + u * 0.2f)
    }
    val half = room.half
    val doorTop = room.p(-0.85f * half, 0.3f, RoomCamera.BACK)
    val doorBottom = room.p(-0.42f * half, RoomCamera.FLOOR, RoomCamera.BACK)
    return Targets(
        bowl = around(Spots.BOWL_X, Spots.BOWL_Z, 0.6f, 0.4f),
        water = around(Spots.WATER_X, Spots.WATER_Z, 0.6f, 0.4f),
        door = Rect(doorTop, doorBottom),
        bed = around(Spots.BED_X, Spots.BED_Z, 1.3f, 0.6f),
    )
}

// MARK: - Drawing

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }, color)
}

/** Where each spot is on screen, for decorate's markers. */
private fun spotCenters(room: RoomProjection): Map<RoomSlot, Offset> {
    val half = room.half
    fun above(x: Float, z: Float, up: Float) = room.base(x, z).let { it.copy(y = it.y - room.unit(z) * up) }
    return mapOf(
        RoomSlot.BED to above(Spots.BED_X, Spots.BED_Z, 0.5f),
        RoomSlot.BOWL to above(Spots.BOWL_X, Spots.BOWL_Z, 0.3f),
        RoomSlot.WALL to room.p(-0.05f * half, 0.25f, RoomCamera.BACK),
        RoomSlot.WINDOW to room.p(0.5f * half, 0.15f, RoomCamera.BACK),
        RoomSlot.RUG to room.base(Spots.RUG_X - 0.9f, Spots.RUG_Z + 0.9f),
        RoomSlot.TOY to above(-0.75f, -4.4f, 0.2f),
        RoomSlot.LAMP to above(-0.78f * half, Spots.LAMP_Z, 1.9f),
        RoomSlot.PLANT to above(0.8f * half, Spots.PLANT_Z, 0.8f),
    )
}

private fun DrawScope.drawRoom(room: RoomProjection, c: RoomPalette, pieces: (RoomSlot) -> String) {
    val half = room.half
    val f = RoomCamera.FLOOR
    val top = RoomCamera.CEILING
    val back = RoomCamera.BACK
    val near = -0.2f
    val bl = room.p(-half, f, back)
    val br = room.p(half, f, back)
    val tl = room.p(-half, top, back)
    val tr = room.p(half, top, back)
    val nbl = room.p(-half, f, near)
    val nbr = room.p(half, f, near)
    val ntl = room.p(-half, top, near)
    val ntr = room.p(half, top, near)
    // Ceiling, side walls, floor, then the back wall.
    quad(ntl, ntr, tr, tl, c.ceiling)
    quad(nbl, bl, tl, ntl, c.side)
    quad(nbr, br, tr, ntr, c.side)
    quad(nbl, nbr, br, bl, c.floor)
    quad(bl, br, tr, tl, c.back)
    // The corners where the walls meet.
    for ((a, b) in listOf(bl to nbl, br to nbr, tl to ntl, tr to ntr)) drawLine(c.edge, a, b, 2.dp.toPx())
    drawRect(c.edge, tl, Size(tr.x - tl.x, bl.y - tl.y), style = Stroke(2.dp.toPx()))
    // Floor tiles.
    val line = 1.2.dp.toPx()
    for (z in listOf(-2.2f, -3.3f, -4.8f, -6.8f)) drawLine(c.line, room.p(-half, f, z), room.p(half, f, z), line)
    for (i in 1..4) {
        val x = -half + i * half * 2f / 5f
        drawLine(c.line, room.p(x, f, back), room.p(x, f, near), line)
    }

    drawWindow(room, c, pieces(RoomSlot.WINDOW))
    drawWallArt(room, c, pieces(RoomSlot.WALL))
    drawDoor(room, c)
    drawRug(room, c, pieces(RoomSlot.RUG))
    if (c.night) {
        // The lamp's warm glow.
        val glow = room.p(-0.78f * half, 0.2f, Spots.LAMP_Z)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFB45A), Color(0x00FFB45A)), glow, size.width * 0.55f), size.width * 0.55f, glow)
    }
    drawLamp(room, c, pieces(RoomSlot.LAMP))
    drawPlant(room, c, pieces(RoomSlot.PLANT))
    drawBed(room, c, pieces(RoomSlot.BED))
    drawToy(room, c, pieces(RoomSlot.TOY))
    drawFoodBowl(room, c, pieces(RoomSlot.BOWL))
    drawWaterBowl(room, c)
}

private fun DrawScope.drawRug(room: RoomProjection, c: RoomPalette, id: String) {
    val half = room.half
    val fill = when (id) {
        "rug.sky" -> if (c.night) Color(0xFF4F6E8C) else Color(0xFF9ED0F5)
        "rug.stripe" -> if (c.night) Color(0xFF8A5A3A) else Color(0xFFFFC48F)
        else -> c.rug
    }
    drawPath(floorEllipse(room, Spots.RUG_X, Spots.RUG_Z, half * 0.72f, 1.6f), fill)
    if (id == "rug.stripe") {
        for (k in listOf(0.6f, 0.42f, 0.24f)) drawPath(floorEllipse(room, Spots.RUG_X, Spots.RUG_Z, half * k, 1.6f * k / 0.72f), if (k == 0.42f) Color.White.copy(alpha = 0.8f) else Color(0xFFF2600C), style = Stroke(5.dp.toPx()))
    } else {
        drawPath(
            floorEllipse(room, Spots.RUG_X, Spots.RUG_Z, half * 0.56f, 1.2f), c.rugRing,
            style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)), cap = StrokeCap.Round),
        )
    }
}

/** A picture on the back wall, between the door and the window. */
private fun DrawScope.drawWallArt(room: RoomProjection, c: RoomPalette, id: String) {
    if (id == "wall.none") return
    val half = room.half
    val a = room.p(-0.2f * half, 0.5f, RoomCamera.BACK)
    val b = room.p(0.1f * half, 0.0f, RoomCamera.BACK)
    val r = Rect(a, b)
    if (id == "wall.sun") {
        val ctr = r.center
        val rad = r.width * 0.42f
        drawCircle(Color(0xFFFFC94A), rad, ctr)
        drawCircle(Color.White, rad * 0.72f, ctr)
        drawLine(Ink, ctr, ctr + Offset(0f, -rad * 0.5f), 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Ink, ctr, ctr + Offset(rad * 0.35f, 0f), 2.5.dp.toPx(), cap = StrokeCap.Round)
        return
    }
    drawRoundRect(Color.White, r.topLeft, r.size, CornerRadius(3.dp.toPx()))
    drawRoundRect(c.frame, r.topLeft, r.size, CornerRadius(3.dp.toPx()), style = Stroke(4.dp.toPx()))
    val p = r.center + Offset(0f, r.height * 0.08f)
    val u = r.width * 0.12f
    drawCircle(Action, u * 1.3f, p)
    for ((dx, dy) in listOf(-1.4f to -1.5f, -0.5f to -2.2f, 0.5f to -2.2f, 1.4f to -1.5f)) drawCircle(Action, u * 0.55f, p + Offset(u * dx, u * dy))
}

private fun DrawScope.drawToy(room: RoomProjection, c: RoomPalette, id: String) {
    val b = room.base(-0.75f, -4.4f)
    val u = room.unit(-4.4f)
    when (id) {
        "toy.ball" -> {
            val ctr = b + Offset(0f, -u * 0.14f)
            drawCircle(Color(0xFFF26A1B), u * 0.14f, ctr)
            drawArc(Color.White, -30f, 150f, false, ctr - Offset(u * 0.1f, u * 0.1f), Size(u * 0.2f, u * 0.2f), style = Stroke(u * 0.03f))
        }
        "toy.rope" -> {
            for (i in 0 until 5) drawCircle(if (i % 2 == 0) Action else Color.White, u * 0.07f, b + Offset(u * (i * 0.09f - 0.18f), -u * 0.07f))
            drawCircle(Color(0xFFE6E6E9), u * 0.06f, b + Offset(-u * 0.27f, -u * 0.07f))
            drawCircle(Color(0xFFE6E6E9), u * 0.06f, b + Offset(u * 0.27f, -u * 0.07f))
        }
        "toy.star" -> {
            val ctr = b + Offset(0f, -u * 0.15f)
            val star = Path().apply {
                for (i in 0 until 10) {
                    val a = -PI / 2 + i * PI / 5
                    val r = if (i % 2 == 0) u * 0.17f else u * 0.08f
                    val pt = ctr + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(star, Color(0xFFFFC94A))
        }
    }
}

private fun floorEllipse(room: RoomProjection, x: Float, z: Float, rx: Float, rz: Float): Path = Path().apply {
    for (i in 0..48) {
        val t = i * 2 * PI / 48
        val p = room.p(x + rx * cos(t).toFloat(), RoomCamera.FLOOR, z + rz * sin(t).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.drawWindow(room: RoomProjection, c: RoomPalette, id: String) {
    val half = room.half
    val wide = id == "window.garden"
    val a = room.p((if (wide) 0.2f else 0.25f) * half, 0.55f, RoomCamera.BACK)
    val b = room.p((if (wide) 0.85f else 0.75f) * half, -0.25f, RoomCamera.BACK)
    val r = Rect(a, b)
    val frame = 5.dp.toPx()
    val corner = if (id == "window.arch") CornerRadius(r.width / 2, r.width / 2) else CornerRadius(4.dp.toPx())
    val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, topLeft = corner, topRight = corner)) }
    val glass = Path().apply {
        val inner = Rect(r.left + frame, r.top + frame, r.right - frame, r.bottom - frame)
        val ic = CornerRadius((corner.x - frame).coerceAtLeast(0f), (corner.y - frame).coerceAtLeast(0f))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(inner, topLeft = ic, topRight = ic))
    }
    drawPath(outline, Color.White.copy(alpha = if (c.night) 0.85f else 1f))
    drawPath(glass, c.sky)
    if (wide) {
        // Green hills in the garden view.
        val hills = Path().apply {
            moveTo(r.left + frame, r.bottom - frame)
            lineTo(r.left + frame, r.bottom - r.height * 0.3f)
            quadraticTo(r.left + r.width * 0.3f, r.bottom - r.height * 0.5f, r.center.x, r.bottom - r.height * 0.32f)
            quadraticTo(r.right - r.width * 0.25f, r.bottom - r.height * 0.18f, r.right - frame, r.bottom - r.height * 0.38f)
            lineTo(r.right - frame, r.bottom - frame)
            close()
        }
        drawPath(hills, if (c.night) Color(0xFF2E4A3A) else Color(0xFF9ED39A))
    }
    drawLine(Color.White, Offset(r.center.x, r.top), Offset(r.center.x, r.bottom), frame * 0.7f)
    if (c.night) {
        // A crescent moon and a few stars.
        val m = Offset(r.right - r.width * 0.28f, r.top + r.height * 0.28f)
        val mr = r.width * 0.1f
        drawCircle(Color(0xFFFFF4C9), mr, m)
        drawCircle(c.sky, mr, m + Offset(mr * 0.5f, -mr * 0.3f))
        for ((sx, sy) in listOf(0.2f to 0.3f, 0.32f to 0.65f, 0.7f to 0.7f, 0.12f to 0.8f)) {
            drawCircle(Color.White, 1.6.dp.toPx(), Offset(r.left + r.width * sx, r.top + r.height * sy))
        }
    } else {
        drawCircle(Color(0xFFFFC94A), r.width * 0.09f, Offset(r.right - r.width * 0.26f, r.top + r.height * 0.26f))
        // A soft cloud.
        val cl = Offset(r.left + r.width * 0.3f, r.top + r.height * 0.62f)
        val cr = r.width * 0.07f
        drawCircle(Color.White, cr, cl)
        drawCircle(Color.White, cr * 1.3f, cl + Offset(cr * 1.2f, -cr * 0.4f))
        drawCircle(Color.White, cr, cl + Offset(cr * 2.4f, 0f))
    }
    // The sill.
    drawRoundRect(c.frame, Offset(r.left - frame, r.bottom - frame * 0.3f), Size(r.width + frame * 2, frame * 1.4f), CornerRadius(frame))
}

private fun DrawScope.drawDoor(room: RoomProjection, c: RoomPalette) {
    val half = room.half
    val a = room.p(-0.85f * half, 0.3f, RoomCamera.BACK)
    val b = room.p(-0.48f * half, RoomCamera.FLOOR, RoomCamera.BACK)
    val r = Rect(a, b)
    val radius = CornerRadius(r.width * 0.25f, r.width * 0.25f)
    val shape = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(r, topLeft = radius, topRight = radius))
    }
    drawPath(shape, c.door)
    drawPath(shape, c.frame, style = Stroke(2.5.dp.toPx()))
    drawCircle(c.frame, r.width * 0.07f, Offset(r.right - r.width * 0.2f, r.top + r.height * 0.55f))
    // The leash on its hook, beside the door.
    val hx = r.right + r.width * 0.45f
    val hook = Offset(hx, r.top + r.height * 0.25f)
    drawCircle(c.frame, 3.dp.toPx(), hook)
    val leash = Path().apply {
        moveTo(hook.x, hook.y)
        cubicTo(hook.x - r.width * 0.12f, hook.y + r.height * 0.12f, hook.x + r.width * 0.12f, hook.y + r.height * 0.22f, hook.x, hook.y + r.height * 0.32f)
    }
    drawPath(leash, Action, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(Action, 4.dp.toPx(), Offset(hook.x, hook.y + r.height * 0.34f))
}

private fun DrawScope.drawLamp(room: RoomProjection, c: RoomPalette, id: String) {
    val x = -0.78f * room.half
    val b = room.base(x, Spots.LAMP_Z)
    val u = room.unit(Spots.LAMP_Z)
    drawOval(c.pole, Offset(b.x - u * 0.2f, b.y - u * 0.05f), Size(u * 0.4f, u * 0.1f))
    drawRect(c.pole, Offset(b.x - u * 0.03f, b.y - u * 1.6f), Size(u * 0.06f, u * 1.6f))
    val top = b.y - u * 1.95f
    if (id == "lamp.mushroom") {
        // A red mushroom cap with white spots.
        val cap = Path().apply {
            moveTo(b.x - u * 0.45f, top + u * 0.38f)
            cubicTo(b.x - u * 0.45f, top - u * 0.15f, b.x + u * 0.45f, top - u * 0.15f, b.x + u * 0.45f, top + u * 0.38f)
            close()
        }
        drawPath(cap, Color(0xFFE5483B))
        for ((dx, dy) in listOf(-0.22f to 0.18f, 0.05f to 0.06f, 0.24f to 0.22f)) drawCircle(Color.White, u * 0.06f, Offset(b.x + u * dx, top + u * dy))
        return
    }
    // The dome shade.
    val shade = Path().apply {
        moveTo(b.x - u * 0.42f, top + u * 0.38f)
        cubicTo(b.x - u * 0.42f, top - u * 0.05f, b.x + u * 0.42f, top - u * 0.05f, b.x + u * 0.42f, top + u * 0.38f)
        close()
    }
    drawPath(shade, c.shade)
    drawLine(Color(0xFFE9C99A), Offset(b.x - u * 0.42f, top + u * 0.38f), Offset(b.x + u * 0.42f, top + u * 0.38f), u * 0.05f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPlant(room: RoomProjection, c: RoomPalette, id: String) {
    val x = 0.8f * room.half
    val b = room.base(x, Spots.PLANT_Z)
    val u = room.unit(Spots.PLANT_Z)
    if (id == "plant.cactus") {
        drawRoundRect(c.leaf, Offset(b.x - u * 0.09f, b.y - u * 1.0f), Size(u * 0.18f, u * 0.7f), CornerRadius(u * 0.09f))
        drawRoundRect(c.leaf, Offset(b.x - u * 0.28f, b.y - u * 0.8f), Size(u * 0.12f, u * 0.32f), CornerRadius(u * 0.06f))
        drawRoundRect(c.leaf, Offset(b.x + u * 0.16f, b.y - u * 0.9f), Size(u * 0.12f, u * 0.3f), CornerRadius(u * 0.06f))
    } else {
        // Leaves fanning up out of the pot.
        for ((angle, len) in listOf(-38f to 0.62f, -14f to 0.78f, 10f to 0.82f, 34f to 0.64f, -2f to 0.55f)) {
            rotate(angle, Offset(b.x, b.y - u * 0.42f)) {
                drawOval(c.leaf, Offset(b.x - u * 0.09f, b.y - u * 0.42f - u * len), Size(u * 0.18f, u * len))
            }
        }
    }
    val pot = Path().apply {
        moveTo(b.x - u * 0.26f, b.y - u * 0.46f); lineTo(b.x + u * 0.26f, b.y - u * 0.46f)
        lineTo(b.x + u * 0.2f, b.y); lineTo(b.x - u * 0.2f, b.y); close()
    }
    drawPath(pot, c.pot)
    drawRect(Action, Offset(b.x - u * 0.245f, b.y - u * 0.34f), Size(u * 0.49f, u * 0.07f))
}

private fun DrawScope.drawBed(room: RoomProjection, c: RoomPalette, id: String) {
    val b = room.base(Spots.BED_X, Spots.BED_Z)
    val u = room.unit(Spots.BED_Z)
    when (id) {
        "bed.cloud" -> {
            val white = if (c.night) Color(0xFFCFCFD8) else Color.White
            val line = if (c.night) Color(0xFF9A9AA6) else Color(0xFFE2E2E6)
            for ((dx, dy, r) in listOf(Triple(-0.42f, -0.28f, 0.28f), Triple(0f, -0.4f, 0.36f), Triple(0.42f, -0.28f, 0.28f))) {
                drawCircle(line, u * r + 2.dp.toPx(), b + Offset(u * dx, u * dy))
            }
            for ((dx, dy, r) in listOf(Triple(-0.42f, -0.28f, 0.28f), Triple(0f, -0.4f, 0.36f), Triple(0.42f, -0.28f, 0.28f))) {
                drawCircle(white, u * r, b + Offset(u * dx, u * dy))
            }
            drawRoundRect(white, Offset(b.x - u * 0.7f, b.y - u * 0.3f), Size(u * 1.4f, u * 0.3f), CornerRadius(u * 0.15f))
            return
        }
        "bed.teepee" -> {
            val tent = Path().apply { moveTo(b.x, b.y - u * 1.3f); lineTo(b.x + u * 0.7f, b.y); lineTo(b.x - u * 0.7f, b.y); close() }
            drawPath(tent, c.bed)
            val door = Path().apply { moveTo(b.x, b.y - u * 0.6f); lineTo(b.x + u * 0.25f, b.y); lineTo(b.x - u * 0.25f, b.y); close() }
            drawPath(door, if (c.night) Color(0xFF3A2416) else Color(0xFF7A3510))
            drawLine(c.bedTop, Offset(b.x - u * 0.45f, b.y - u * 0.45f), Offset(b.x + u * 0.45f, b.y - u * 0.45f), u * 0.06f)
            drawLine(Color(0xFF8A5A3A), Offset(b.x - u * 0.12f, b.y - u * 1.5f), Offset(b.x + u * 0.12f, b.y - u * 1.1f), u * 0.04f)
            drawLine(Color(0xFF8A5A3A), Offset(b.x + u * 0.12f, b.y - u * 1.5f), Offset(b.x - u * 0.12f, b.y - u * 1.1f), u * 0.04f)
            return
        }
        "bed.royal" -> {
            drawRoundRect(Color(0xFF7B4FD1), Offset(b.x - u * 0.7f, b.y - u * 0.4f), Size(u * 1.4f, u * 0.4f), CornerRadius(u * 0.18f))
            drawRoundRect(Color(0xFF9C74EA), Offset(b.x - u * 0.6f, b.y - u * 0.5f), Size(u * 1.2f, u * 0.22f), CornerRadius(u * 0.11f))
            for (dx in listOf(-0.7f, 0.7f)) drawCircle(Color(0xFFFFC94A), u * 0.08f, Offset(b.x + u * dx, b.y - u * 0.4f))
            return
        }
    }
    drawOval(c.bed, Offset(b.x - u * 0.68f, b.y - u * 0.5f), Size(u * 1.36f, u * 0.56f))
    drawOval(c.bed, Offset(b.x - u * 0.68f, b.y - u * 0.62f), Size(u * 1.36f, u * 0.5f))
    drawOval(c.bedTop, Offset(b.x - u * 0.48f, b.y - u * 0.52f), Size(u * 0.96f, u * 0.3f))
}

private fun DrawScope.drawFoodBowl(room: RoomProjection, c: RoomPalette, id: String) {
    val b = room.base(Spots.BOWL_X, Spots.BOWL_Z)
    val u = room.unit(Spots.BOWL_Z)
    val bowl = Path().apply {
        moveTo(b.x - u * 0.34f, b.y - u * 0.24f); lineTo(b.x + u * 0.34f, b.y - u * 0.24f)
        lineTo(b.x + u * 0.25f, b.y); lineTo(b.x - u * 0.25f, b.y); close()
    }
    drawPath(
        bowl,
        when (id) {
            "bowl.steel" -> if (c.night) Color(0xFF7E8290) else Color(0xFFBFC4CC)
            "bowl.blue" -> if (c.night) Color(0xFF2F5C8E) else Color(0xFF4A90D9)
            else -> c.bowl
        },
    )
    drawOval(c.kibble, Offset(b.x - u * 0.3f, b.y - u * 0.31f), Size(u * 0.6f, u * 0.14f))
    for ((dx, dy) in listOf(-0.15f to -0.27f, 0f to -0.29f, 0.14f to -0.26f, -0.06f to -0.24f, 0.08f to -0.3f)) {
        drawCircle(Color(0xFF9A5420), u * 0.035f, Offset(b.x + u * dx, b.y + u * dy))
    }
}

private fun DrawScope.drawWaterBowl(room: RoomProjection, c: RoomPalette) {
    val b = room.base(Spots.WATER_X, Spots.WATER_Z)
    val u = room.unit(Spots.WATER_Z)
    val bowl = Path().apply {
        moveTo(b.x - u * 0.34f, b.y - u * 0.22f); lineTo(b.x + u * 0.34f, b.y - u * 0.22f)
        lineTo(b.x + u * 0.27f, b.y); lineTo(b.x - u * 0.27f, b.y); close()
    }
    drawPath(bowl, c.waterBowl)
    drawRect(Action, Offset(b.x - u * 0.31f, b.y - u * 0.12f), Size(u * 0.62f, u * 0.05f))
    drawOval(c.water, Offset(b.x - u * 0.3f, b.y - u * 0.29f), Size(u * 0.6f, u * 0.13f))
}
