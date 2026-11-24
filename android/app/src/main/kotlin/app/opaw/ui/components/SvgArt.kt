package app.opaw.ui.components

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.caverock.androidsvg.SVG

// Art straight from the design artifact, which is SVG. Each drawing is parsed once and drawn with
// AndroidSVG, so the app shows exactly what the design shows.

private val parsed = HashMap<String, SVG>()

/** Parses [text] once and keeps it. */
fun svgOf(text: String): SVG = parsed.getOrPut(text) { SVG.getFromString(text) }

/** Draws an SVG to fill [box], keeping its own proportions. */
fun DrawScope.drawSvg(text: String, box: RectF = RectF(0f, 0f, size.width, size.height)) {
    drawIntoCanvas { canvas -> runCatching { svgOf(text).renderToCanvas(canvas.nativeCanvas, box) } }
}

/** An SVG drawing from the design, filling [modifier]'s box. */
@Composable
fun SvgArt(text: String, modifier: Modifier) {
    Canvas(modifier) { drawSvg(text) }
}

/** Wraps SVG body markup in a root element with the given view box. */
fun svgDoc(viewBox: String, body: String) =
    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox" overflow="visible">$body</svg>"""

/** The design's art used in more than one place. */
object DesignArt {
    /** The O paw, white, in a 200 box: the logo's paw. */
    val PAW_WHITE = svgDoc(
        "0 0 200 200",
        """<circle cx="100" cy="124" r="36" fill="none" stroke="#fff" stroke-width="22"/><g fill="#fff"><ellipse cx="52" cy="80" rx="11" ry="15" transform="rotate(-30 52 80)"/><ellipse cx="80" cy="54" rx="11" ry="15" transform="rotate(-10 80 54)"/><ellipse cx="120" cy="54" rx="11" ry="15" transform="rotate(10 120 54)"/><ellipse cx="148" cy="80" rx="11" ry="15" transform="rotate(30 148 80)"/></g>""",
    )

    /** A Paw, the room's coin: orange with the O paw on it. */
    val PAW_COIN = svgDoc(
        "0 0 200 200",
        """<circle cx="100" cy="100" r="96" fill="#FF7A1A"/><circle cx="100" cy="100" r="80" fill="#FF8F2E"/><g transform="translate(100 104) scale(.62) translate(-100 -110)"><circle cx="100" cy="124" r="36" fill="none" stroke="#fff" stroke-width="22"/><g fill="#fff"><ellipse cx="52" cy="80" rx="11" ry="15" transform="rotate(-30 52 80)"/><ellipse cx="80" cy="54" rx="11" ry="15" transform="rotate(-10 80 54)"/><ellipse cx="120" cy="54" rx="11" ry="15" transform="rotate(10 120 54)"/><ellipse cx="148" cy="80" rx="11" ry="15" transform="rotate(30 148 80)"/></g></g>""",
    )

    /** A four-pointed sparkle, in a 20 box. */
    fun sparkle(color: String) = svgDoc("0 0 20 20", """<path d="M10 0l2.4 7.6L20 10l-7.6 2.4L10 20l-2.4-7.6L0 10l7.6-2.4z" fill="$color"/>""")

    /** The door with the leash, for walks, in a 100 box. */
    val DOOR = svgDoc(
        "0 0 100 100",
        """<path d="M26 96V24a14 14 0 0 1 14-14h20a14 14 0 0 1 14 14v72z" fill="#fff" stroke="#E2E2E6" stroke-width="5"/><circle cx="64" cy="58" r="5" fill="#BBBBBB"/><path d="M86 26c-6 16 6 26 0 44" stroke="#F2600C" stroke-width="6" fill="none" stroke-linecap="round"/>""",
    )

    /** The welcome chest without its lid, and its shadow, in a 100 box. */
    val CHEST_BODY = svgDoc(
        "0 0 100 100",
        """<rect x="14" y="46" width="72" height="9" rx="4" fill="#8F3500"/><rect x="12" y="50" width="76" height="40" rx="9" fill="#FF8F2E" stroke="#D9520A" stroke-width="2.5"/><rect x="25" y="51.3" width="9" height="37.4" fill="#FFD3A8"/><rect x="66" y="51.3" width="9" height="37.4" fill="#FFD3A8"/><path d="M18 84h64" stroke="#FFB070" stroke-width="2.5" stroke-linecap="round"/><circle cx="50" cy="64" r="10" fill="#fff" stroke="#D9520A" stroke-width="2.5"/><g fill="#F2600C"><ellipse cx="50" cy="66.5" rx="4" ry="3.2"/><circle cx="45.2" cy="61.6" r="1.7"/><circle cx="48.4" cy="59.6" r="1.7"/><circle cx="51.6" cy="59.6" r="1.7"/><circle cx="54.8" cy="61.6" r="1.7"/></g>""",
    )
    val CHEST_SHADOW = svgDoc("0 0 100 100", """<ellipse cx="50" cy="93" rx="40" ry="4" fill="#000" fill-opacity=".08"/>""")

    /** The chest's lid, in the same 100 box. */
    val CHEST_LID = svgDoc(
        "0 0 100 100",
        """<path d="M12 54V42a38 16 0 0 1 76 0v12z" fill="#FF7A1F" stroke="#D9520A" stroke-width="2.5" stroke-linejoin="round"/><path d="M25 53V30.8l9-2V53zM66 53V28.8l9 2V53z" fill="#FFD3A8"/><path d="M20 38a32 13 0 0 1 18-8" stroke="#FFC27A" stroke-width="3" fill="none" stroke-linecap="round"/><rect x="44" y="47" width="12" height="9" rx="3" fill="#fff" stroke="#D9520A" stroke-width="2.5"/>""",
    )

    /** The light fanning out of the open chest, in a box from -50 to 50. */
    val CHEST_RAYS = svgDoc(
        "-50 -50 100 100",
        (0 until 12).joinToString("") { """<path d="M0 0L-4 -50H4z" fill="#FFD9A6" fill-opacity=".75" transform="rotate(${it * 30})"/>""" } +
            """<defs><radialGradient id="g"><stop offset="0" stop-color="#FFF3C4"/><stop offset="1" stop-color="#FFF3C4" stop-opacity="0"/></radialGradient></defs><circle r="22" fill="url(#g)"/>""",
    )
}
