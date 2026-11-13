package app.opaw.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.opaw.core.BreedCatalog
import app.opaw.core.BreedGuess
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Finds the pet in a photo and lifts it off the background, entirely on device, with ML Kit.
 * Mirrors Services/CutoutService.swift.
 *
 * ML Kit's on-device labeler knows "Dog" but not breeds, so [guessBreed] only works when a
 * label happens to match the breed catalog; the person can always pick the breed.
 */
object CutoutService {
    /** Whether ML Kit recognizes a dog in the photo. */
    suspend fun containsDog(image: Bitmap): Boolean = labels(image).any { (label, confidence) ->
        label.equals("dog", ignoreCase = true) && confidence > 0.3
    }

    suspend fun guessBreed(image: Bitmap): BreedGuess? = BreedCatalog.bestGuess(labels(image))

    private suspend fun labels(image: Bitmap): List<Pair<String, Double>> = runCatching {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            labeler.process(InputImage.fromBitmap(image, 0)).await().map { it.text to it.confidence.toDouble() }
        } finally {
            labeler.close()
        }
    }.getOrDefault(emptyList())

    /** The foreground subject on a transparent background, cropped to the subject, or null if none was found. */
    suspend fun cutout(image: Bitmap): Bitmap? = runCatching {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = segmenter.process(InputImage.fromBitmap(image, 0)).await()
            result.foregroundBitmap?.let { withContext(Dispatchers.Default) { cropToContent(it) } }
        } finally {
            segmenter.close()
        }
    }.getOrNull()

    /** Trims fully transparent edges. */
    private fun cropToContent(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 8) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return bitmap
        return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
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
