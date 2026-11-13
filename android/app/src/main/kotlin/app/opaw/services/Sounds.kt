package app.opaw.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * The short sound that goes with each haptic (7c). Every tone is synthesized from the design's table
 * of [frequency, length, loudness, start, wave], so nothing ships as an audio file. Mirrors Sounds.swift.
 * Sounds play as UI sonification, so they follow the phone's media volume and mix with other audio.
 */
object Sounds {
    @Volatile var soundsOn = true

    enum class Wave { SINE, TRIANGLE }

    /** One tone: pitch in Hz, length and start in seconds, loudness 0–1. */
    data class Note(val hz: Double, val length: Double, val loudness: Double, val start: Double = 0.0, val wave: Wave = Wave.SINE)

    sealed class Cue {
        data object Light : Cue()
        data object Medium : Cue()
        data object Heavy : Cue()
        data object Soft : Cue()
        data object Rigid : Cue()
        data object Selection : Cue()
        data object Success : Cue()
        data object Warning : Cue()
        data object Error : Cue()
        data object Milestone : Cue()
        /** One tick of the hold-to-finish ring; [step] 0…1 raises the pitch as the hold fills. */
        data class Tick(val step: Double) : Cue()

        val notes: List<Note>
            get() = when (this) {
                Light -> listOf(Note(1400.0, 0.04, 0.12))
                Medium -> listOf(Note(880.0, 0.06, 0.18))
                Heavy -> listOf(Note(170.0, 0.12, 0.4), Note(85.0, 0.16, 0.3))
                Soft -> listOf(Note(520.0, 0.1, 0.12))
                Rigid -> listOf(Note(2300.0, 0.025, 0.12))
                Selection -> listOf(Note(1900.0, 0.02, 0.07))
                Success -> listOf(Note(784.0, 0.09, 0.16), Note(1175.0, 0.18, 0.18, start = 0.09))
                Warning -> listOf(Note(622.0, 0.1, 0.16), Note(466.0, 0.18, 0.16, start = 0.12))
                Error -> listOf(
                    Note(220.0, 0.07, 0.22, wave = Wave.TRIANGLE),
                    Note(220.0, 0.07, 0.22, start = 0.1, wave = Wave.TRIANGLE),
                    Note(196.0, 0.16, 0.24, start = 0.2, wave = Wave.TRIANGLE),
                )
                Milestone -> listOf(Note(170.0, 0.12, 0.4), Note(784.0, 0.09, 0.16, start = 0.16), Note(1175.0, 0.22, 0.18, start = 0.25))
                is Tick -> {
                    // The design's three ticks rise 1700 → 2000 → 2400 Hz and get a little louder.
                    val s = step.coerceIn(0.0, 1.0)
                    listOf(Note(1700 + 700 * s, 0.025, 0.08 + 0.04 * s))
                }
            }
    }

    private const val RATE = 44_100
    private val cache = HashMap<Cue, ShortArray>()
    private val worker = Executors.newSingleThreadExecutor()

    fun play(cue: Cue) {
        if (!soundsOn) return
        worker.execute {
            runCatching {
                val samples = if (cue is Cue.Tick) render(cue.notes) else synchronized(cache) { cache.getOrPut(cue) { render(cue.notes) } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep((samples.size * 1000L / RATE) + 50)
                track.release()
            }
        }
    }

    /**
     * Mixes the notes into one buffer: each starts near silence, rises to its loudness in 5 ms and
     * falls away exponentially by the end of its length, like the prototype's Web Audio envelope.
     */
    private fun render(notes: List<Note>): ShortArray {
        val end = notes.maxOfOrNull { it.start + it.length + 0.03 } ?: 0.0
        val frames = ceil(end * RATE).toInt()
        val mix = DoubleArray(frames)
        val floorGain = 0.0001
        val attack = 0.005
        for (note in notes) {
            val first = (note.start * RATE).toInt()
            val count = ((note.length + 0.03) * RATE).toInt()
            for (n in 0 until count) {
                if (first + n >= frames) break
                val t = n.toDouble() / RATE
                val gain = when {
                    t < attack -> floorGain * (note.loudness / floorGain).pow(t / attack)
                    t < note.length -> note.loudness * (floorGain / note.loudness).pow((t - attack) / (note.length - attack))
                    else -> 0.0
                }
                val phase = note.hz * t
                val wave = when (note.wave) {
                    Wave.SINE -> sin(2 * Math.PI * phase)
                    Wave.TRIANGLE -> 4 * abs(phase - floor(phase) - 0.5) - 1
                }
                mix[first + n] += wave * gain
            }
        }
        return ShortArray(frames) { (mix[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }
}
