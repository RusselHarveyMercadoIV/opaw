package app.opaw.services

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.opaw.core.AvatarMood
import app.opaw.core.DogSize
import app.opaw.data.Dog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Where a pet's 3D model is: none yet, being built by Meshy, ready on this phone, or failed. */
@Serializable
data class PetModelState(
    val jobId: String? = null,
    /** "none", "modeling", "rigging", "saving", "downloading", "done" or "failed". */
    val stage: String = "none",
    val progress: Int = 0,
    val error: String? = null,
) {
    val isWorking: Boolean get() = stage in setOf("modeling", "rigging", "saving", "downloading")
    val isReady: Boolean get() = stage == "done"

    val label: String
        get() = when (stage) {
            "modeling" -> "Sculpting in 3D"
            "rigging" -> "Adding bones so they can move"
            "saving", "downloading" -> "Almost there"
            else -> ""
        }
}

/**
 * "Bring to life": sends a pet's cutout to the pet-model Edge Function, which has Meshy build a
 * textured 3D model and rig it with a four-legged skeleton, then downloads the rest pose and the
 * walk and run animations as GLB files. Jobs take a few minutes and survive leaving the screen
 * or restarting the app. State lives next to the models in files/models/<dog id>/, so there's no
 * database change.
 */
class PetModelService(private val context: Context, private val pack: PackService, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val states = mutableStateMapOf<String, PetModelState>()
    private val polls = mutableMapOf<String, Job>()

    private fun directory(dogId: String) = File(context.filesDir, "models/$dogId")
    private fun stateFile(dogId: String) = File(directory(dogId), "state.json")

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
    fun file(dogId: String, mood: AvatarMood): File? {
        if (!state(dogId).isReady) return null
        val name = when (mood) {
            AvatarMood.RESTLESS, AvatarMood.PROUD -> "run"
            AvatarMood.HAPPY, AvatarMood.FRESH -> "walk"
            else -> "rest"
        }
        return File(directory(dogId), "$name.glb").takeIf { it.exists() } ?: File(directory(dogId), "rest.glb").takeIf { it.exists() }
    }

    fun start(dog: Dog) {
        if (state(dog.id).isWorking) return
        save(dog.id, PetModelState(stage = "modeling"))
        scope.launch {
            try {
                val height = when (dog.size) {
                    DogSize.SMALL -> 0.3
                    DogSize.MEDIUM -> 0.5
                    DogSize.LARGE -> 0.65
                    DogSize.GIANT -> 0.8
                }
                val job = pack.startPetModel(dog, height)
                save(dog.id, PetModelState(job.id, job.stage, job.progress))
                poll(dog.id)
            } catch (e: Exception) {
                save(dog.id, PetModelState(stage = "failed", error = e.message ?: "Bring to life couldn't start."))
            }
        }
    }

    /** Picks up jobs that were still running when the app was closed. */
    fun resume() {
        File(context.filesDir, "models").listFiles()?.forEach { dir -> if (state(dir.name).isWorking) poll(dir.name) }
    }

    private fun poll(dogId: String) {
        if (polls[dogId]?.isActive == true) return
        polls[dogId] = scope.launch {
            var failures = 0
            while (true) {
                val current = state(dogId)
                val id = current.jobId ?: return@launch save(dogId, current.copy(stage = "failed", error = "The job was lost. Try again."))
                try {
                    val job = pack.petModelStatus(id)
                    failures = 0
                    when (job.stage) {
                        "done" -> {
                            save(dogId, current.copy(stage = "downloading", progress = 96))
                            withContext(Dispatchers.IO) {
                                for ((name, path) in job.outputs) {
                                    val bytes = pack.downloadPetModel(path)
                                    File(directory(dogId), "$name.glb.part").apply { writeBytes(bytes) }.renameTo(File(directory(dogId), "$name.glb"))
                                }
                            }
                            return@launch save(dogId, current.copy(stage = "done", progress = 100, error = null))
                        }
                        "failed" -> return@launch save(dogId, current.copy(stage = "failed", error = job.error ?: "Meshy couldn't bring this photo to life."))
                        else -> save(dogId, current.copy(stage = job.stage, progress = job.progress))
                    }
                } catch (e: Exception) {
                    // Offline or a hiccup: keep trying for a while before giving up.
                    if (++failures >= 20) return@launch save(dogId, current.copy(stage = "failed", error = e.message ?: "Lost touch with the server."))
                }
                delay(12_000)
            }
        }
    }

    fun delete(dogId: String) {
        polls.remove(dogId)?.cancel()
        states.remove(dogId)
        directory(dogId).deleteRecursively()
    }
}
