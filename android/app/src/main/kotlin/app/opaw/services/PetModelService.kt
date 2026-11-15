package app.opaw.services

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import app.opaw.core.AvatarMood
import app.opaw.core.DogSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** The checkpoints shown while a pet comes to life (9a), each owning a slice of the 0 to 100 bar. */
enum class LifeStep(val title: String, val detail: String, val from: Int, val to: Int) {
    PHOTOS("Photos checked", "All of the same pet, sharp and well lit", 0, 8),
    CUTOUT("Cut out from the background", "One clean cut-out per photo", 8, 20),
    SHAPE("Shape from every angle", "Building a 3D model from your photos", 20, 45),
    MOTION("Motion clips", "Adding bones, then walking and running", 45, 88),
    FINISH("Final touches", "Saving everything to this phone", 88, 100),
}

/** One reference photo for "Bring to life". The face close-up is kept for show but not sent. */
/** [angle] is "front", "side", "back" or "face", so the server can tell the 3D service which view each photo is. */
class LifePhoto(val image: Bitmap, val send: Boolean, val angle: String = "")

/** Where a pet's 3D model is: none yet, being built on the server, ready on this phone, or failed. */
@Serializable
data class PetModelState(
    val jobId: String? = null,
    /** "none", "checking", "uploading", "modeling", "rigging", "saving", "downloading", "done" or "failed". */
    val stage: String = "none",
    /** The whole job, 0 to 100, across the five checkpoints. */
    val progress: Int = 0,
    val error: String? = null,
    /** The reference photos, as file names in the pet's refs folder. */
    val photos: List<String> = emptyList(),
    /** How many of [photos], from the first, are sent to be modelled. */
    val send: Int = 0,
    /** The angle of each photo sent ("front", "side", "back"), in the same order. */
    val angles: List<String> = emptyList(),
    val heightMeters: Double = 0.5,
    /** The motion clips that came back: "rest", "walk", "run". */
    val clips: List<String> = emptyList(),
    /** False from the moment the pet is ready until their owner has met them. */
    val seen: Boolean = true,
    val startedAt: Long = 0,
) {
    val isWorking: Boolean get() = stage in setOf("checking", "uploading", "modeling", "rigging", "saving", "downloading")
    val isReady: Boolean get() = stage == "done"
    val isFailed: Boolean get() = stage == "failed"
    /** Started at some point, so the toast and the progress screen have something to show. */
    val isStarted: Boolean get() = stage != "none"

    /** The checkpoint the job is on. */
    val step: LifeStep get() = LifeStep.entries.lastOrNull { progress >= it.from } ?: LifeStep.PHOTOS

    /** How far through the current checkpoint, 0 to 1. */
    val stepFraction: Float get() = step.let { ((progress - it.from).toFloat() / (it.to - it.from)).coerceIn(0f, 1f) }

    /** A rough guess: the whole job usually takes about six minutes. */
    val minutesLeft: Int get() = max(1, ((100 - progress) / 100f * 6).roundToInt())

    /** "64% · Motion clips", for the toast. */
    val summary: String get() = "$progress% · ${step.title}"
}

/** The names shown for Meshy's clips. */
fun clipTitle(clip: String): String = when (clip) {
    "rest" -> "Idle"
    "walk" -> "Walk"
    "run" -> "Run"
    else -> clip.replaceFirstChar(Char::uppercase)
}

/**
 * "Bring to life": sends a pet's photos from several angles to the pet-model Edge Function, which
 * has Meshy build a textured 3D model and rig it with a four-legged skeleton, then downloads the
 * rest pose and the walk and run clips as GLB files. Jobs take a few minutes and survive leaving
 * the screen or restarting the app. State lives next to the models in files/models/<dog id>/, so
 * there's no database change, and a job can start before the pet itself is saved.
 */
class PetModelService(private val context: Context, private val pack: PackService, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val states = mutableStateMapOf<String, PetModelState>()
    private val jobs = mutableMapOf<String, Job>()
    /** Toasts swiped away, by pet: "working" or "ready". In memory only, so they return next launch. */
    private val hidden = mutableStateMapOf<String, String>()

    private fun directory(dogId: String) = File(context.filesDir, "models/$dogId")
    private fun stateFile(dogId: String) = File(directory(dogId), "state.json")
    private fun refs(dogId: String) = File(directory(dogId), "refs")

    /** Observable from Compose: reading it recomposes when the job moves on. */
    fun state(dogId: String): PetModelState = states.getOrPut(dogId) {
        runCatching { json.decodeFromString(PetModelState.serializer(), stateFile(dogId).readText()) }.getOrDefault(PetModelState())
    }

    private fun save(dogId: String, state: PetModelState) {
        states[dogId] = state
        runCatching {
            directory(dogId).mkdirs()
            stateFile(dogId).writeText(json.encodeToString(PetModelState.serializer(), state))
        }
    }

    /**
     * The GLB to show for a mood once the model is ready: running when they're restless or proud,
     * walking when happy or fresh, and the rest pose (which the view gently breathes) otherwise.
     */
    fun file(dogId: String, mood: AvatarMood): File? = clip(
        dogId,
        when (mood) {
            AvatarMood.RESTLESS, AvatarMood.PROUD -> "run"
            AvatarMood.HAPPY, AvatarMood.FRESH -> "walk"
            else -> "rest"
        },
    )

    /**
     * One motion clip by name, falling back to the nearest one this pet has: a run falls back to the
     * walk (four-legged pets from Tripo only get a walk), and anything else to the rest pose.
     */
    fun clip(dogId: String, name: String): File? {
        if (!state(dogId).isReady) return null
        val order = if (name == "run") listOf("run", "walk", "rest") else listOf(name, "rest")
        return order.map { File(directory(dogId), "$it.glb") }.firstOrNull { it.exists() }
    }

    /** The clips on this phone, in play order. */
    fun clips(dogId: String): List<String> =
        if (state(dogId).isReady) listOf("rest", "walk", "run").filter { File(directory(dogId), "$it.glb").exists() } else emptyList()

    /** The saved reference photos, for the thumbnails on the progress screen. */
    fun photoFiles(dogId: String): List<File> = state(dogId).photos.map { File(refs(dogId), it) }

    /** True when the toast for this pet should show: working, or ready and not met yet. */
    fun showsToast(dogId: String): Boolean {
        val state = state(dogId)
        return when {
            state.isWorking -> hidden[dogId] != "working"
            state.isReady && !state.seen -> hidden[dogId] != "ready"
            else -> false
        }
    }

    fun hideToast(dogId: String) {
        hidden[dogId] = if (state(dogId).isReady) "ready" else "working"
    }

    /** The owner has met their 3D pet, so the "ready" toast can go. */
    fun markSeen(dogId: String) {
        val state = state(dogId)
        if (!state.seen) save(dogId, state.copy(seen = true))
    }

    /**
     * Starts a job from photos of the same pet. Those marked [LifePhoto.send] go to Meshy, in the
     * order given (front first). Does nothing while a job for this pet is already running.
     */
    fun start(dogId: String, photos: List<LifePhoto>, size: DogSize) {
        if (state(dogId).isWorking || photos.none { it.send }) return
        val height = when (size) {
            DogSize.SMALL -> 0.3
            DogSize.MEDIUM -> 0.5
            DogSize.LARGE -> 0.65
            DogSize.GIANT -> 0.8
        }
        hidden.remove(dogId)
        save(dogId, PetModelState(stage = "checking", progress = 2, heightMeters = height, seen = false, startedAt = System.currentTimeMillis()))
        launch(dogId) {
            // Photos to send come first, so `send` is a simple count.
            val ordered = photos.sortedByDescending { it.send }
            val names = withContext(Dispatchers.IO) {
                refs(dogId).deleteRecursively()
                refs(dogId).mkdirs()
                ordered.mapIndexed { index, photo ->
                    "$index.png".also { name -> File(refs(dogId), name).outputStream().use { fitted(photo.image).compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
            }
            save(dogId, state(dogId).copy(stage = "uploading", progress = LifeStep.CUTOUT.from, photos = names, send = ordered.count { it.send }, angles = ordered.filter { it.send }.map { it.angle }))
            run(dogId)
        }
    }

    /** Runs one pet's job, replacing any earlier one, and records why if it can't start. */
    private fun launch(dogId: String, block: suspend () -> Unit) {
        jobs.remove(dogId)?.cancel()
        jobs[dogId] = scope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                fail(dogId, e.message ?: "Bring to life couldn't start.")
            }
        }
    }

    /** Tries a failed job again with the photos it already has. */
    fun retry(dogId: String) {
        val state = state(dogId)
        if (!state.isFailed || state.send == 0) return
        hidden.remove(dogId)
        val previous = state.jobId
        save(dogId, state.copy(jobId = null, stage = "uploading", progress = LifeStep.CUTOUT.from, error = null, seen = false, startedAt = System.currentTimeMillis()))
        launch(dogId) {
            // If the model was built and only the motion step failed, the server reuses it.
            val revived = previous?.let { runCatching { pack.petModelRetry(it) }.getOrNull() }
            if (revived == null) return@launch run(dogId)
            save(dogId, state(dogId).copy(jobId = revived.id, stage = revived.stage, progress = overall(revived.stage, revived.progress)))
            follow(dogId)
        }
    }

    /** Uploads the saved photos, starts the 3D job and follows it to the end. */
    private suspend fun run(dogId: String) {
        val state = state(dogId)
        val files = photoFiles(dogId).take(state.send)
        val span = LifeStep.CUTOUT.to - LifeStep.CUTOUT.from
        val job = pack.startPetModel(dogId, files, state.heightMeters, state.angles.take(files.size)) { done ->
            save(dogId, state(dogId).copy(progress = LifeStep.CUTOUT.from + span * done / files.size))
        }
        save(dogId, state(dogId).copy(jobId = job.id, stage = job.stage, progress = overall(job.stage, job.progress)))
        follow(dogId)
    }

    private fun fail(dogId: String, message: String) = save(dogId, state(dogId).copy(stage = "failed", error = message))

    /**
     * The server counts modeling as 0 to 60 and rigging as 60 to 90. Here modeling fills "Shape
     * from every angle" and rigging fills "Motion clips".
     */
    private fun overall(stage: String, server: Int): Int = when (stage) {
        "modeling" -> LifeStep.SHAPE.from + (server.coerceIn(0, 60) * (LifeStep.SHAPE.to - LifeStep.SHAPE.from) / 60f).roundToInt()
        "rigging" -> LifeStep.MOTION.from + ((server - 60).coerceIn(0, 30) * (LifeStep.MOTION.to - LifeStep.MOTION.from) / 30f).roundToInt()
        "saving" -> LifeStep.FINISH.from + 2
        "done" -> 100
        else -> 0
    }

    /** Picks up jobs that were still running when the app was closed. */
    fun resume() {
        File(context.filesDir, "models").listFiles()?.forEach { dir ->
            val state = state(dir.name)
            when {
                !state.isWorking -> Unit
                state.jobId != null -> launch(dir.name) { follow(dir.name) }
                // Closed before the photos were up: start over from the saved photos.
                state.send > 0 -> { fail(dir.name, "Bring to life was interrupted."); retry(dir.name) }
                else -> fail(dir.name, "Bring to life was interrupted. Try again.")
            }
        }
    }

    private suspend fun follow(dogId: String) {
        var failures = 0
        while (true) {
            val current = state(dogId)
            val id = current.jobId ?: return fail(dogId, "The job was lost. Try again.")
            try {
                val job = pack.petModelStatus(id)
                failures = 0
                when (job.stage) {
                    "done" -> {
                        val outputs = job.outputs.entries.toList()
                        outputs.forEachIndexed { index, (name, path) ->
                            save(dogId, state(dogId).copy(stage = "downloading", progress = LifeStep.FINISH.from + 3 + 8 * index / outputs.size))
                            withContext(Dispatchers.IO) {
                                val bytes = pack.downloadPetModel(path)
                                File(directory(dogId), "$name.glb.part").apply { writeBytes(bytes) }.renameTo(File(directory(dogId), "$name.glb"))
                            }
                        }
                        return save(dogId, state(dogId).copy(stage = "done", progress = 100, error = null, clips = listOf("rest", "walk", "run").filter { it in job.outputs }))
                    }
                    "failed" -> return fail(dogId, job.error ?: "We couldn't bring these photos to life.")
                    // Never let the bar slide back when the server reports a little less.
                    else -> save(dogId, current.copy(stage = job.stage, progress = max(current.progress, overall(job.stage, job.progress))))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Offline or a hiccup: keep trying for a while before giving up.
                if (++failures >= 20) return fail(dogId, e.message ?: "Lost touch with the server.")
            }
            delay(8_000)
        }
    }

    fun delete(dogId: String) {
        jobs.remove(dogId)?.cancel()
        states.remove(dogId)
        hidden.remove(dogId)
        directory(dogId).deleteRecursively()
    }

    /** Meshy doesn't need more than this, and smaller files are kinder to a phone's data. */
    private fun fitted(image: Bitmap, maxDimension: Int = 1280): Bitmap {
        val longest = max(image.width, image.height)
        if (longest <= maxDimension) return image
        val scale = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(image, (image.width * scale).roundToInt(), (image.height * scale).roundToInt(), true)
    }
}
