package app.opaw.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import app.opaw.core.BreedCatalog
import app.opaw.core.BreedGuess
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Finds the pet in a photo and lifts it off the background, entirely on device, with ML Kit.
 * Mirrors Services/CutoutService.swift.
 */
object CutoutService {
    /**
     * Everything the app learns from one photo: the pet's outline, a breed guess, and whether it's
     * clear enough for 3D. Never throws; a photo it can't read comes back with nothing found.
     * The models run one after another, which keeps memory low on small phones.
     */
    suspend fun scan(image: Bitmap): PetScan = try {
        val labels = labels(image)
        val segmentation = segment(image)
        val guess = BreedCatalog.bestGuess(labels)
        val hasDog = labels.any { (label, confidence) -> label.equals("dog", ignoreCase = true) && confidence > 0.3 }
        val looksLikePet = hasDog || guess != null || labels.any { (label, confidence) -> label.lowercase() in petLabels && confidence > 0.3 }
        PetScan(segmentation, guess, hasDog, PhotoCheck.check(image, segmentation, looksLikePet))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.e("Opaw", "Scanning the photo failed", error)
        PetScan(null, null, false, listOf(PhotoIssue.NO_PET))
    }

    /** Animals ML Kit's labeler can name, for the 3D photo check. */
    private val petLabels = setOf("dog", "cat", "pet", "rabbit", "bird", "horse", "hamster", "animal", "mammal", "puppy", "kitten")

    /**
     * ML Kit's on-device labels with their confidence. It knows "Dog" but not breeds, so a breed
     * guess only works when a label happens to match the catalog.
     */
    private suspend fun labels(image: Bitmap): List<Pair<String, Double>> = runCatching {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            labeler.process(InputImage.fromBitmap(image, 0)).await().map { it.text to it.confidence.toDouble() }
        } finally {
            labeler.close()
        }
    }.getOrDefault(emptyList())

    /**
     * Lifts the pet off the background. The result keeps the full-size foreground, which lines up
     * with the photo pixel for pixel, so the scan can draw its white outline right on the photo.
     * Everything is copied out before the segmenter closes, so nothing depends on it afterwards.
     */
    private suspend fun segment(image: Bitmap): Segmentation? = runCatching {
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            val result = segmenter.process(InputImage.fromBitmap(image, 0)).await()
            val found = result.foregroundBitmap ?: return@runCatching null
            withContext(Dispatchers.Default) {
                val w = found.width
                val h = found.height
                val pixels = IntArray(w * h)
                found.getPixels(pixels, 0, w, 0, 0, w, h)
                val (bounds, coverage) = contentBounds(pixels, w, h) ?: return@withContext null
                val foreground = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                val cutout = Bitmap.createBitmap(foreground, bounds.left, bounds.top, bounds.width(), bounds.height())
                Segmentation(foreground, cutout, bounds, coverage, countSubjects(pixels, w, h))
            }
        } finally {
            segmenter.close()
        }
    }.onFailure { Log.w("Opaw", "Lifting the pet out failed", it) }.getOrNull()

    /** The box around the non-transparent pixels and the share of the image they cover. */
    private fun contentBounds(pixels: IntArray, w: Int, h: Int): Pair<Rect, Float>? {
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        var filled = 0
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 8) {
                filled++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        return Rect(minX, minY, maxX + 1, maxY + 1) to filled.toFloat() / (w * h)
    }

    /**
     * How many separate things were lifted out: the connected blobs in the foreground, on a
     * coarse grid. Blobs under 2% of the photo are specks and don't count.
     */
    private fun countSubjects(pixels: IntArray, w: Int, h: Int): Int {
        val step = max(1, max(w, h) / 160)
        val gw = w / step
        val gh = h / step
        if (gw == 0 || gh == 0) return 1
        val open = BooleanArray(gw * gh) { i -> (pixels[(i / gw) * step * w + (i % gw) * step] ushr 24) > 128 }
        val minCells = gw * gh * 0.02
        val stack = IntArray(gw * gh)
        var subjects = 0
        for (start in open.indices) {
            if (!open[start]) continue
            var top = 0
            var cells = 0
            stack[top++] = start
            open[start] = false
            while (top > 0) {
                val i = stack[--top]
                cells++
                val x = i % gw
                if (x > 0 && open[i - 1]) { open[i - 1] = false; stack[top++] = i - 1 }
                if (x < gw - 1 && open[i + 1]) { open[i + 1] = false; stack[top++] = i + 1 }
                if (i >= gw && open[i - gw]) { open[i - gw] = false; stack[top++] = i - gw }
                if (i < gw * (gh - 1) && open[i + gw]) { open[i + gw] = false; stack[top++] = i + gw }
            }
            if (cells >= minCells) subjects++
        }
        return max(1, subjects)
    }

    /** Reads a photo upright and no larger than [maxDimension] pixels on its longest side. */
    suspend fun load(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            prepared(decoded, rotation, maxDimension)
        }.getOrNull()
    }

    /** Rotates upright and scales down so the longest side is at most [maxDimension]. */
    fun prepared(bitmap: Bitmap, rotationDegrees: Int, maxDimension: Int = 1600): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val scale = if (longest > maxDimension) maxDimension.toFloat() / longest else 1f
        if (scale == 1f && rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/** Where pet photos live: files/pets/<name>. Entities keep the relative path. */
object PhotoStore {
    private fun dir(context: Context) = File(context.filesDir, "pets").apply { mkdirs() }

    fun save(context: Context, bitmap: Bitmap, name: String, png: Boolean): String {
        val file = File(dir(context), name)
        FileOutputStream(file).use {
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 85, it)
        }
        return "pets/$name"
    }

    fun load(context: Context, path: String?, maxDimension: Int = 1200): Bitmap? {
        path ?: return null
        val file = File(context.filesDir, path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun delete(context: Context, path: String?) {
        path?.let { File(context.filesDir, it).delete() }
    }
}

/**
 * The pet found in a photo. [foreground] is the photo's size and transparent outside the pet;
 * [cutout] is the same pet cropped to its [bounds], for the sticker avatar.
 */
class Segmentation(val foreground: Bitmap, val cutout: Bitmap, val bounds: Rect, val coverage: Float, val subjects: Int)

/** What [CutoutService.scan] found. [issues] says why the photo can't become a 3D pet; empty when it can. */
class PetScan(val segmentation: Segmentation?, val guess: BreedGuess?, val hasDog: Boolean, val issues: List<PhotoIssue>)
