package app.opaw.services

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Why a photo isn't clear enough to bring a pet to life in 3D. */
enum class PhotoIssue(val title: String, val tip: String) {
    NO_PET("We couldn't find a pet", "Try a photo where your pet stands out from the background."),
    NOT_A_PET("That might not be a pet", "Bring to life works best with a dog, cat or bunny."),
    CROWDED("There's more than one subject", "Use a photo with just your pet in it."),
    CUT_OFF("Part of your pet is cut off", "Fit their whole body in the frame, paws to ears."),
    TOO_SMALL("Your pet is too far away", "Move closer so they fill more of the frame."),
    BLURRY("The photo is blurry", "Hold still, or wait until your pet does."),
    TOO_DARK("It's too dark", "Find some brighter light."),
    TOO_BRIGHT("It's too bright", "Step out of direct sunlight."),
    SAME_WAY("Both sides face the same way", "One was taken from the wrong side. Their left side has their nose on your left."),
}

/**
 * Checks that a photo shows one whole, sharp, well-lit pet, which Meshy needs to build a model
 * that can be rigged and animated. Runs on device in a few milliseconds.
 */
object PhotoCheck {
    suspend fun check(photo: Bitmap, segmentation: Segmentation?, looksLikePet: Boolean): List<PhotoIssue> =
        withContext(Dispatchers.Default) {
            segmentation ?: return@withContext listOf(PhotoIssue.NO_PET)
            val issues = mutableListOf<PhotoIssue>()
            if (!looksLikePet) issues += PhotoIssue.NOT_A_PET
            if (segmentation.subjects > 1) issues += PhotoIssue.CROWDED

            val b = segmentation.bounds
            val margin = max(2, (max(photo.width, photo.height) * 0.006).roundToInt())
            if (b.left <= margin || b.top <= margin || b.right >= photo.width - margin || b.bottom >= photo.height - margin) {
                issues += PhotoIssue.CUT_OFF
            }
            val boxShare = b.width().toFloat() * b.height() / (photo.width.toFloat() * photo.height)
            if (boxShare < 0.08f || segmentation.coverage < 0.04f) issues += PhotoIssue.TOO_SMALL

            val (sharpness, brightness) = measure(photo, segmentation)
            if (sharpness < BLUR_THRESHOLD) issues += PhotoIssue.BLURRY
            if (brightness < 45) issues += PhotoIssue.TOO_DARK
            if (brightness > 228) issues += PhotoIssue.TOO_BRIGHT
            issues
        }

    /** Laplacian variance (sharpness) and mean luminance over the pet's pixels only. */
    private fun measure(photo: Bitmap, segmentation: Segmentation): Pair<Double, Double> {
        val b = segmentation.bounds
        val scale = minOf(1f, 360f / max(b.width(), b.height()))
        val w = max(3, (b.width() * scale).roundToInt())
        val h = max(3, (b.height() * scale).roundToInt())
        val crop = Bitmap.createScaledBitmap(Bitmap.createBitmap(photo, b.left, b.top, b.width(), b.height()), w, h, true)
        val mask = Bitmap.createScaledBitmap(Bitmap.createBitmap(segmentation.foreground, b.left, b.top, b.width(), b.height()), w, h, true)
        val pixels = IntArray(w * h).also { crop.getPixels(it, 0, w, 0, 0, w, h) }
        val alpha = IntArray(w * h).also { mask.getPixels(it, 0, w, 0, 0, w, h) }
        val gray = DoubleArray(w * h) { i ->
            val c = pixels[i]
            0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        }
        var lumSum = 0.0; var lumCount = 0
        var lapSum = 0.0; var lapSq = 0.0; var lapCount = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((alpha[i] ushr 24) < 160) continue
            lumSum += gray[i]; lumCount++
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) continue
            // Skip the outline itself: the pet's edge against the background is always sharp.
            if ((alpha[i - 1] ushr 24) < 160 || (alpha[i + 1] ushr 24) < 160 || (alpha[i - w] ushr 24) < 160 || (alpha[i + w] ushr 24) < 160) continue
            val lap = gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4 * gray[i]
            lapSum += lap; lapSq += lap * lap; lapCount++
        }
        val brightness = if (lumCount == 0) 128.0 else lumSum / lumCount
        val sharpness = if (lapCount < 50) Double.MAX_VALUE else lapSq / lapCount - (lapSum / lapCount).let { it * it }
        return sharpness to brightness
    }

    /** Below this Laplacian variance, at 360 px, fur reads as smeared. Kept lenient on purpose. */
    private const val BLUR_THRESHOLD = 28.0
}
