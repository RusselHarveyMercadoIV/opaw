package app.opaw.services

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Splits the reference sheet ("Bring to life") into its two views: the pet from the front on the
 * left, and from its left side on the right, both on white. Finds the two widest runs of columns
 * with something in them, skipping any thin divider line the drawing put between them, then trims
 * each view to the pet with a little white margin.
 */
object SheetSplit {
    /** The front view and the side view, or null when the sheet doesn't hold two pets side by side. */
    fun split(sheet: Bitmap): Pair<Bitmap, Bitmap>? {
        // Look at a small copy: fast, and plenty to find where the pets are.
        val w = 768
        val h = (sheet.height * w.toFloat() / sheet.width).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(sheet, w, h, true)
        val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        // Anything clearly not white.
        val ink = BooleanArray(w * h) {
            val p = pixels[it]
            765 - ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) > 60
        }
        val column = FloatArray(w) { x -> (0 until h).count { y -> ink[y * w + x] } / h.toFloat() }
        // A divider is filled almost all the way down; a pet never is.
        val filled = BooleanArray(w) { column[it] > 0.01f && column[it] <= 0.9f }
        val runs = ArrayList<IntRange>()
        var start = -1
        for (x in 0..w) {
            val on = x < w && filled[x]
            if (on && start < 0) start = x
            if (!on && start >= 0) { runs += start until x; start = -1 }
        }
        val two = runs.sortedByDescending { it.last - it.first }.take(2).sortedBy { it.first }
        if (two.size < 2 || two.any { it.last - it.first < w / 12 }) return null
        val scale = sheet.width.toFloat() / w
        val views = two.map { run ->
            val rows = (0 until h).filter { y -> (run.first..run.last).count { x -> ink[y * w + x] } > (run.last - run.first + 1) * 0.003f }
            if (rows.isEmpty()) return null
            val pad = (0.06f * max(run.last - run.first, rows.last() - rows.first())).roundToInt()
            val left = (max(run.first - pad, 0) * scale).roundToInt()
            val top = (max(rows.first() - pad, 0) * scale).roundToInt()
            val right = (min(run.last + 1 + pad, w) * scale).roundToInt().coerceAtMost(sheet.width)
            val bottom = (min(rows.last() + 1 + pad, h) * scale).roundToInt().coerceAtMost(sheet.height)
            Bitmap.createBitmap(sheet, left, top, right - left, bottom - top)
        }
        return views[0] to views[1]
    }
}
