package app.opaw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opaw.core.CareKind
import app.opaw.ui.theme.Opaw

/** One shape of an icon, in its 24×24 box. */
sealed interface IconElement {
    data class P(val d: String) : IconElement
    data class Circle(val x: Float, val y: Float, val r: Float) : IconElement
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : IconElement
    /** Drawn in the background color on top of a filled icon (the photo icon's sun and hill). */
    data class Cutout(val d: String) : IconElement
    data class CutoutCircle(val x: Float, val y: Float, val r: Float) : IconElement
    /** An outline inside a filled icon (the activity line, the share links). */
    data class Stroked(val d: String, val width: Float) : IconElement
}

private fun p(d: String) = IconElement.P(d)
private fun c(x: Number, y: Number, r: Number) = IconElement.Circle(x.toFloat(), y.toFloat(), r.toFloat())
private fun r(x: Number, y: Number, w: Number, h: Number, rad: Number) = IconElement.Rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), rad.toFloat())

/** The icons from the Pet Avatar App v2 design, drawn from their original 24×24 SVG paths. Mirrors Shared/DesignIcon.swift. */
enum class DesignIcon(val elements: List<IconElement>) {
    BACK(listOf(p("m15 18-6-6 6-6"))),
    CHEVRON_RIGHT(listOf(p("m9 18 6-6-6-6"))),
    PLUS(listOf(p("M5 12h14M12 5v14"))),
    CHECK(listOf(p("m5 12 5 5 9-10"))),
    HOME(listOf(p("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"))),
    PAW(listOf(c(5, 10, 2.3), c(9, 5.5, 2.3), c(15, 5.5, 2.3), c(19, 10, 2.3), p("M12 11c-3 0-6 4.5-6 7 0 2 1.5 3 3 3 1.2 0 2-.6 3-.6s1.8.6 3 .6c1.5 0 3-1 3-3 0-2.5-3-7-6-7Z"))),
    ACTIVITY(listOf(IconElement.Stroked("M22 12h-4l-3 9L9 3l-3 9H2", 2.6f))),
    PHOTO(listOf(r(3, 3, 18, 18, 5), IconElement.CutoutCircle(9f, 9f, 2f), IconElement.Cutout("m21 15-4-4-10 10h11a3 3 0 0 0 3-3z"))),
    HEART(listOf(p("M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"))),
    SHARE(listOf(c(18, 5, 2.5), c(6, 12, 2.5), c(18, 19, 2.5), IconElement.Stroked("m8.6 13.5 6.8 4M15.4 6.5l-6.8 4", 2.4f))),
    UPLOAD(listOf(p("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13"))),
    BELL(listOf(p("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"), p("M10.3 21a1.94 1.94 0 0 0 3.4 0"))),
    CALENDAR(listOf(r(3, 4, 18, 18, 4), p("M16 2v4M8 2v4M3 10h18"))),
    TROPHY(listOf(p("M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z"))),
    CAMERA(listOf(p("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"), c(12, 13, 3))),
    SPARKLE(listOf(p("M12 3l1.9 5.8L20 10l-6.1 1.2L12 17l-1.9-5.8L4 10l6.1-1.2z"))),
    DOCUMENT(listOf(p("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"), p("M14 2v6h6M8 13h8M8 17h5"))),
    PAUSE(listOf(r(6, 5, 4, 14, 1.5), r(14, 5, 4, 14, 1.5))),
    MEAL(listOf(p("M3 11h18a9 9 0 0 1-18 0Z"), p("M7 21h10M9 7c0-2 2-2 2-4M14 7c0-2 2-2 2-4"))),
    WATER(listOf(p("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"))),
    PEE(listOf(
        p("M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z"),
        p("M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97"),
    )),
    POOP(listOf(p("M5 20h14a3 3 0 0 0 .6-5.94A3 3 0 0 0 16 10h-.5A3 3 0 0 0 13 4c0 1.5-1.5 2.5-3 2.5A3 3 0 0 0 8 11.8 3 3 0 0 0 5 20Z"))),
    MEDS(listOf(p("m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z"), p("m8.5 8.5 7 7"))),
    BATH(listOf(p("M9 6 6.5 3.5a1.5 1.5 0 0 0-1-.5C4.68 3 4 3.68 4 4.5V17a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5"), p("M2 12h20M7 19v2M17 19v2"))),
    BRUSH(listOf(p("M3 7h18v4H3z"), p("M5 11v7M9 11v7M13 11v7M17 11v7"))),
    NAILS(listOf(c(6, 6, 3), c(6, 18, 3), p("M20 4 8.12 15.88M14.47 14.48 20 20M8.12 8.12 12 12"))),
    WEIGHT(listOf(c(12, 5, 3), p("M6.5 8a2 2 0 0 0-1.9 1.46L2.1 18.5A2 2 0 0 0 4 21h16a2 2 0 0 0 1.93-2.54L19.4 9.5A2 2 0 0 0 17.48 8Z"))),
    VET(listOf(p("M11 2v2M5 2v2M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1"), p("M8 15a6 6 0 0 0 12 0v-3"), c(20, 10, 2))),
    CLOSE(listOf(p("M18 6 6 18M6 6l12 12"))),
    CHEVRON_DOWN(listOf(p("m6 9 6 6 6-6"))),
    REFRESH(listOf(p("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"), p("M21 3v5h-5"), p("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"), p("M8 16H3v5"))),
    LINK(listOf(p("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"), p("M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"))),
    LOCATION(listOf(p("M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"), c(12, 10, 3))),
    LOCK(listOf(r(4, 11, 16, 10, 3), p("M8 11V7a4 4 0 0 1 8 0v4"))),
    CHART(listOf(p("M3 3v18h18"), p("M8 16v-4M13 16V8M18 16v-7"))),
    USERS(listOf(p("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"), c(9, 7, 4), p("M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"))),
    CLOUD(listOf(p("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"))),
    HELP(listOf(c(12, 12, 9), p("M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01"))),
    GRID(listOf(r(3, 3, 7, 7, 2), r(14, 3, 7, 7, 2), r(14, 14, 7, 7, 2), r(3, 14, 7, 7, 2))),
    RULER(listOf(p("M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.4 2.4 0 0 1 0-3.4l2.6-2.6a2.4 2.4 0 0 1 3.4 0Z"), p("m7.5 10.5 2-2M10.5 13.5l2-2M13.5 16.5l2-2"))),
    CLOCK(listOf(c(12, 12, 9), p("M12 7v5l3 2"))),
    PETS(listOf(c(5, 10, 2), c(9, 5.5, 2), c(15, 5.5, 2), c(19, 10, 2), p("M12 11c-3 0-6 4.5-6 7 0 2 1.5 3 3 3 1.2 0 2-.6 3-.6s1.8.6 3 .6c1.5 0 3-1 3-3 0-2.5-3-7-6-7Z"))),
    SETTINGS(listOf(p("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"), c(12, 12, 3))),
    WAVE(listOf(p("M18 11V6a2 2 0 0 0-4 0M14 10V4a2 2 0 0 0-4 0v2M10 10.5V6a2 2 0 0 0-4 0v8"), p("M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"))),
    PENCIL(listOf(p("M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z"), p("m15 5 4 4"))),
    SCHEDULE(listOf(p("M21 7.5V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.5"), p("M16 2v4M8 2v4M3 10h5"), c(16, 16, 6), p("M16 14v2.2l1.6 1"))),
    UNDO(listOf(p("M9 14 4 9l5-5"), p("M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11"))),
    TRASH(listOf(p("M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"))),
    CLOUD_OFF(listOf(p("m3 3 18 18"), p("M6.5 6.6A6.5 6.5 0 0 0 8 19.5h9.5c.5 0 1-.1 1.4-.2"), p("M21.3 16.5A4.5 4.5 0 0 0 17.5 10.5h-1.3A6.5 6.5 0 0 0 10.2 6.2"))),
    ALERT(listOf(c(12, 12, 9), p("M12 7.5v5.5M12 16.5v.01"))),
    SPEAKER(listOf(p("M11 5 6 9H2v6h4l5 4V5z"), p("M15.54 8.46a5 5 0 0 1 0 7.07"), p("M19.07 4.93a10 10 0 0 1 0 14.14"))),
    VIBRATE(listOf(p("m2 8 2 2-2 2 2 2-2 2"), p("m22 8-2 2 2 2-2 2 2 2"), r(8, 5, 8, 14, 1.5))),
    BONE(listOf(p("M17 10c.7-.7 1.69 0 2.5 0a2.5 2.5 0 1 0 0-5 .5.5 0 0 1-.5-.5 2.5 2.5 0 1 0-5 0c0 .81.7 1.8 0 2.5l-7 7c-.7.7-1.69 0-2.5 0a2.5 2.5 0 0 0 0 5c.28 0 .5.22.5.5a2.5 2.5 0 1 0 5 0c0-.81-.7-1.8 0-2.5Z"))),
    HEART_LINE(listOf(p("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"))),
    STAR(listOf(p("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"))),
    MOON(listOf(p("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"))),
    SUN(listOf(c(12, 12, 4), p("M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"))),
    BOOK(listOf(p("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"), p("M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"))),
    BALL(listOf(c(12, 12, 9), p("M12 3a9 9 0 0 0 0 18M3 12h18")));

    /** The raw value used in saved data, matching iOS (e.g. "chevronDown", "heartLine"). */
    val raw: String = name.lowercase().split("_").let { parts -> parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) } }

    val isFilled: Boolean get() = this in setOf(HOME, PAW, ACTIVITY, PHOTO, HEART, SHARE, PAUSE)

    /** The design's stroke width for this icon, in its 24-point box. */
    val defaultWeight: Float
        get() = when (this) {
            CHECK -> 4f
            CHEVRON_RIGHT -> 3f
            MEAL, WATER, PEE, POOP, MEDS, BATH, BRUSH, NAILS, WEIGHT, VET -> 2.3f
            CALENDAR, CAMERA, DOCUMENT -> 2.4f
            REFRESH, LINK, LOCATION, CHART, USERS, CLOUD, HELP, GRID, RULER, PETS, SETTINGS, WAVE -> 2.4f
            SPARKLE -> 2.2f
            PENCIL, BONE, HEART_LINE, STAR, MOON, SUN, BOOK, BALL -> 2.3f
            TRASH, CLOUD_OFF, SPEAKER, VIBRATE -> 2.4f
            else -> 2.6f
        }

    companion object {
        fun fromRaw(raw: String?): DesignIcon? = entries.firstOrNull { it.raw == raw }

        fun forKind(kind: CareKind): DesignIcon? = when (kind) {
            CareKind.MEAL -> MEAL
            CareKind.WATER -> WATER
            CareKind.PEE -> PEE
            CareKind.POOP -> POOP
            CareKind.MEDS -> MEDS
            CareKind.BATH -> BATH
            CareKind.BRUSH -> BRUSH
            CareKind.NAILS -> NAILS
            CareKind.WEIGHT -> WEIGHT
            CareKind.VET -> VET
            CareKind.PLAY -> BALL
            CareKind.CUSTOM -> SPARKLE
        }
    }
}

private val pathCache = HashMap<String, Path>()

private fun parse(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun DrawScope.shape(element: IconElement, color: Color, style: Stroke?) {
    val drawStyle = style ?: androidx.compose.ui.graphics.drawscope.Fill
    when (element) {
        is IconElement.P -> drawPath(parse(element.d), color, style = drawStyle)
        is IconElement.Circle -> drawCircle(color, element.r, Offset(element.x, element.y), style = drawStyle)
        is IconElement.Rect -> drawRoundRect(color, Offset(element.x, element.y), Size(element.w, element.h), CornerRadius(element.r), style = drawStyle)
        else -> Unit
    }
}

/** Draws a design icon at any size, in [tint]. The counterpart of `Icon(_:size:weight:)`. */
@Composable
fun Icon(
    icon: DesignIcon,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    weight: Float? = null,
    tint: Color = Opaw.colors.ink,
    /**
     * Fill for the cut-out details of solid icons (the photo icon). Left unspecified, the details
     * are punched through so whatever is behind shows, in light and dark alike.
     */
    cutoutColor: Color = Color.Unspecified,
) {
    val line = weight ?: icon.defaultWeight
    val stroke = remember(line) { Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val punch = cutoutColor == Color.Unspecified
    val layer = if (punch && icon.isFilled) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
    Canvas(modifier.size(size).then(layer)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            if (!icon.isFilled) {
                icon.elements.forEach { shape(it, tint, stroke) }
            } else {
                icon.elements.forEach { shape(it, tint, null) }
                icon.elements.filterIsInstance<IconElement.Stroked>().forEach {
                    drawPath(parse(it.d), tint, style = Stroke(it.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                icon.elements.forEach {
                    when (it) {
                        is IconElement.Cutout -> drawPath(parse(it.d), if (punch) Color.Black else cutoutColor, blendMode = if (punch) BlendMode.Clear else BlendMode.SrcOver)
                        is IconElement.CutoutCircle -> drawCircle(if (punch) Color.Black else cutoutColor, it.r, Offset(it.x, it.y), blendMode = if (punch) BlendMode.Clear else BlendMode.SrcOver)
                        else -> Unit
                    }
                }
            }
        }
    }
}
