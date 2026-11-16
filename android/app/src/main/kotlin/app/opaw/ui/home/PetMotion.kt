package app.opaw.ui.home

import android.content.Context
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.TransformManager
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moves a pet Tripo rigged, built in code instead of bought per pet: legs that walk (one paw at a
 * time) or trot (diagonal pairs), a back that sways and breathes, a head that turns to look and
 * tilts, and a tail that wags. Every Tripo pet has the same named bones (only the tail's are
 * unnamed, and found by walking up from its tip), but its own rest pose and facing; each turn here
 * is in the dog's own axes (forward, up, to its side), moved into this pet's and applied on top of
 * its rest pose. Bones a pet lacks are skipped, and their children follow their parents.
 *
 * The gaits were worked out on a real Tripo husky and checked frame by frame: hips swing the leg,
 * and in the air the front paw folds back at the wrist and the hind paw tucks at the hock.
 */
internal class PetMotion private constructor(
    private val skeleton: Skeleton,
    private val entities: Map<Int, Int>,
    private val tail: List<Int>,
) {
    /** What the stage asks for: IDLE stands, WALK walks, RUN trots fast. */
    enum class Style { WALK, RUN, IDLE }

    /** Everything that moves, set by the stage each frame. */
    class State {
        /** Walk (0) to trot (1); above 1, a fast trot. */
        var gait = 0f
        /** How much the legs move, 0 (standing) to 1. */
        var stride = 0f
        /** Where in the step cycle, in cycles. */
        var phase = 0f
        /** Head turn from straight ahead (positive turns away from its side), nose up, and tilt, in radians. */
        var lookYaw = 0f
        var lookPitch = 0f
        var tilt = 0f
        /** Tail wag size (radians), speed (wags a second), and how high it's carried. */
        var wag = 0.25f
        var wagSpeed = 1.5f
        var tailRaise = 0f
        /** Seconds, for breathing and wagging. */
        var time = 0f
    }

    /** One leg's length, in the model's units: for matching the step to the ground covered. */
    val legLength: Float = skeleton.legLength

    /** How far the body moves per step cycle, in leg lengths, for this gait. */
    fun cycleLength(gait: Float): Float {
        val g = gait.coerceIn(0f, 1f)
        val swing = lerp(0.31f, if (gait > 1f) 0.47f else 0.40f, g)
        val duty = lerp(0.62f, if (gait > 1f) 0.38f else 0.42f, g)
        return 2f * sin(swing) / duty
    }

    /** A little bounce of the body with the trot, in leg lengths. */
    fun bob(state: State): Float = 0.03f * state.stride * state.gait.coerceIn(0f, 1f) * cos(4f * PI_F * state.phase)

    /** Plays a style in place, for previews: [seconds] since it started. */
    fun apply(seconds: Float, style: Style, transforms: TransformManager) {
        val state = State().apply {
            time = seconds
            gait = if (style == Style.RUN) 1.3f else 0f
            stride = if (style == Style.IDLE) 0f else 1f
            phase = seconds * if (style == Style.RUN) 2.1f else 1.1f
            lookYaw = 0.35f * sin(seconds * 0.5f)
            tilt = 0.12f * sin(seconds * 0.37f)
            wag = if (style == Style.IDLE) 0.3f else 0.4f
            wagSpeed = 2f
        }
        apply(state, transforms)
    }

    fun apply(state: State, transforms: TransformManager) {
        val turns = HashMap<Int, FloatArray>()
        fun set(name: String, q: FloatArray) {
            skeleton.index[name]?.let { turns[it] = q }
        }

        // Legs.
        val g = state.gait.coerceIn(0f, 1f)
        val fast = state.gait > 1f
        val duty = lerp(0.62f, if (fast) 0.38f else 0.42f, g)
        val lift = lerp(1.0f, 1.15f, g) * state.stride
        val swingFront = lerp(0.32f, if (fast) 0.5f else 0.42f, g) * state.stride
        val swingHind = lerp(0.30f, if (fast) 0.45f else 0.38f, g) * state.stride
        for (leg in LEGS) {
            val offset = lerp(leg.walk, leg.trot, g)
            val q = ((state.phase + offset) % 1f + 1f) % 1f
            val swing = if (leg.front) swingFront else swingHind
            val hip: Float
            val up: Float
            if (q < duty) {
                // On the ground: the leg sweeps back under the body.
                hip = swing * (1f - 2f * q / duty)
                up = 0f
            } else {
                // In the air: it swings forward again, lifted.
                val v = (q - duty) / (1f - duty)
                hip = swing * (-1f + 2f * v * v * (3f - 2f * v))
                up = sin(PI_F * v) * lift
            }
            val a0: Float
            val a1: Float
            val a2: Float
            if (leg.front) {
                a0 = hip
                a1 = a0 - 1.0f * up
                a2 = a1 - 0.6f * up
            } else {
                a0 = hip + 0.05f * up
                a1 = a0 - 0.55f * up
                a2 = a1 + 0.75f * up
            }
            set("${leg.prefix}0", pitch(a0))
            set("${leg.prefix}1", pitch(a1))
            set("${leg.prefix}2", pitch(a2))
            set("${leg.prefix}3", pitch(a2))
        }

        // The back: breathing at rest, a sway with each stride.
        val breath = 0.018f * sin(state.time * 2.3f)
        val sway = 0.05f * state.stride * sin(2f * PI_F * state.phase)
        set("tripo::Spine_1", euler(sway, 0f, 0f))
        set("tripo::Spine_2", euler(0.6f * sway, breath, 0f))
        set("tripo::Spine_3", euler(0.2f * sway, breath * 0.6f, 0f))
        set("tripo::Spine_4", euler(-0.4f * sway, 0f, 0f))

        // Neck and head: looking, with a nod in time with the steps.
        val nod = 0.05f * state.stride * sin(4f * PI_F * state.phase)
        set("tripo::Spine_5", euler(0.4f * state.lookYaw - 0.3f * sway, 0.45f * state.lookPitch + 0.5f * nod, 0.3f * state.tilt))
        set("tripo::Head_0", euler(state.lookYaw, state.lookPitch + nod, state.tilt))

        // Tail: each bone wags a little later and further than the one before, like a whip.
        val n = tail.size
        for ((k, bone) in tail.withIndex()) {
            val share = (k + 1f) / n
            val wag = state.wag * share * sin(2f * PI_F * state.wagSpeed * state.time - 0.7f * k)
            turns[bone] = euler(wag, state.tailRaise * share, 0f)
        }

        val world = arrayOfNulls<FloatArray>(skeleton.count)
        for (i in skeleton.order) {
            val parent = skeleton.parent[i]
            val parentWorld = if (parent >= 0) world[parent]!! else IDENTITY
            val turn = turns[i]
            if (turn == null) {
                world[i] = mul(parentWorld, skeleton.restLocal[i])
                continue
            }
            // From the dog's own axes into this pet's, on top of its rest pose.
            val now = mul(mul(mul(skeleton.body, turn), inverse(skeleton.body)), skeleton.restWorld[i])
            world[i] = now
            val local = mul(inverse(parentWorld), now)
            val entity = entities[i] ?: continue
            val instance = transforms.getInstance(entity)
            if (instance != 0) transforms.setTransform(instance, matrix(skeleton.translation[i], local, skeleton.scale[i]))
        }
    }

    private class Leg(val prefix: String, val front: Boolean, val walk: Float, val trot: Float)

    /** One rigged pet's bones: their parents, rest pose, and the pet's own axes. */
    private class Skeleton(
        val count: Int,
        val names: Array<String?>,
        val parent: IntArray,
        val order: List<Int>,
        val translation: Array<FloatArray>,
        val restLocal: Array<FloatArray>,
        val scale: Array<FloatArray>,
        val restWorld: Array<FloatArray>,
        val body: FloatArray,
        val legLength: Float,
    ) {
        val index: Map<String, Int> = names.withIndex().mapNotNull { (i, n) -> n?.let { it to i } }.toMap()
    }

    companion object {
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)

        /** When each leg steps: walking goes hind, then fore on the same side, then the other side; trotting moves diagonal pairs. */
        private val LEGS = listOf(
            Leg("tripo::0_Left_Limb_", front = true, walk = 0.25f, trot = 0f),
            Leg("tripo::0_Right_Limb_", front = true, walk = 0.75f, trot = 0.5f),
            Leg("tripo::1_Left_Limb_", front = false, walk = 0f, trot = 0.5f),
            Leg("tripo::1_Right_Limb_", front = false, walk = 0.5f, trot = 0f),
        )

        /** A player for this GLB, or null when it isn't a pet Tripo rigged. */
        @Suppress("UNUSED_PARAMETER")
        fun create(context: Context, glb: ByteArray, asset: FilamentAsset): PetMotion? = runCatching {
            val skeleton = readSkeleton(glb) ?: return null
            val tail = tailChain(skeleton)
            val named = skeleton.names.withIndex()
                .filter { (_, n) -> n != null && n.startsWith("tripo::") && (n.contains("Limb") || n.contains("Spine") || n.contains("Head")) }
                .map { it.index }
            val entities = HashMap<Int, Int>()
            for (i in named + tail) {
                val name = skeleton.names[i] ?: continue
                val entity = asset.getFirstEntityByName(name)
                if (entity != 0) entities[i] = entity
            }
            if (entities.isEmpty()) return null
            PetMotion(skeleton, entities, tail)
        }.getOrNull()

        /** The tail's bones from its root to its tip: up from the bone named Tail to the spine, then on down its children. */
        private fun tailChain(skeleton: Skeleton): List<Int> {
            val named = skeleton.names.indexOfFirst { it?.contains("Tail") == true }
            if (named < 0) return emptyList()
            val up = ArrayList<Int>()
            var i = named
            while (i >= 0 && skeleton.names[i]?.let { it.contains("Spine") || it.contains("Root") } != true) {
                up += i
                i = skeleton.parent[i]
            }
            val chain = up.reversed().toMutableList()
            var last = named
            while (chain.size < 8) {
                val child = (0 until skeleton.count).firstOrNull { skeleton.parent[it] == last } ?: break
                chain += child
                last = child
            }
            return chain
        }

        private fun readSkeleton(bytes: ByteArray): Skeleton? {
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (data.getInt(0) != 0x46546C67) return null
            val json = JSONObject(String(bytes, 20, data.getInt(12), Charsets.UTF_8))
            if (!json.has("skins")) return null
            val nodes = json.getJSONArray("nodes")
            val count = nodes.length()
            val parent = IntArray(count) { -1 }
            for (i in 0 until count) {
                nodes.getJSONObject(i).optJSONArray("children")?.let { c -> for (k in 0 until c.length()) parent[c.getInt(k)] = i }
            }
            val order = ArrayList<Int>()
            val seen = BooleanArray(count)
            fun visit(i: Int) {
                if (seen[i]) return
                if (parent[i] >= 0) visit(parent[i])
                seen[i] = true
                order += i
            }
            for (i in 0 until count) visit(i)
            val names = Array(count) { nodes.getJSONObject(it).optString("name").takeIf { n -> n.isNotEmpty() } }
            val translation = Array(count) { vec(nodes.getJSONObject(it).optJSONArray("translation"), 0f) }
            val restLocal = Array(count) { nodes.getJSONObject(it).optJSONArray("rotation")?.let(::quat) ?: IDENTITY }
            val scale = Array(count) { vec(nodes.getJSONObject(it).optJSONArray("scale"), 1f) }
            val restWorld = arrayOfNulls<FloatArray>(count)
            val position = arrayOfNulls<FloatArray>(count)
            for (i in order) {
                val p = parent[i]
                if (p < 0) {
                    restWorld[i] = restLocal[i]
                    position[i] = translation[i]
                } else {
                    restWorld[i] = mul(restWorld[p]!!, restLocal[i])
                    val moved = rotate(restWorld[p]!!, floatArrayOf(translation[i][0] * scale[p][0], translation[i][1] * scale[p][1], translation[i][2] * scale[p][2]))
                    position[i] = floatArrayOf(position[p]!![0] + moved[0], position[p]!![1] + moved[1], position[p]!![2] + moved[2])
                }
            }
            fun at(name: String) = names.indexOf(name).takeIf { it >= 0 }?.let { position[it] }
            val head = at("tripo::Head_0") ?: return null
            val hipL = at("tripo::1_Left_Limb_0") ?: return null
            val hipR = at("tripo::1_Right_Limb_0") ?: return null
            // From shoulder to paw, along the front left leg.
            val chain = (0..3).mapNotNull { at("tripo::0_Left_Limb_$it") }
            var leg = 0f
            for (k in 1 until chain.size) {
                val a = chain[k - 1]
                val b = chain[k]
                leg += sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))
            }
            return Skeleton(count, names, parent, order, translation, restLocal, scale, Array(count) { restWorld[it]!! }, bodyFrame(head, hipL, hipR), leg)
        }

        /** The dog's own axes as a rotation: forward from its hips to its head, up, and to its side. */
        private fun bodyFrame(head: FloatArray, hipL: FloatArray, hipR: FloatArray): FloatArray {
            val fwd = floatArrayOf(head[0] - (hipL[0] + hipR[0]) / 2, 0f, head[2] - (hipL[2] + hipR[2]) / 2)
            val length = sqrt(fwd[0] * fwd[0] + fwd[2] * fwd[2]).coerceAtLeast(1e-6f)
            fwd[0] /= length; fwd[2] /= length
            // side = forward × up, with up = (0, 1, 0)
            val side = floatArrayOf(-fwd[2], 0f, fwd[0])
            // Columns: forward, up, side.
            return fromMatrix(
                fwd[0], 0f, side[0],
                fwd[1], 1f, side[1],
                fwd[2], 0f, side[2],
            )
        }

        private const val PI_F = PI.toFloat()

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** A turn about the dog's side axis: positive swings a leg forward, or lifts the nose. */
        private fun pitch(angle: Float) = floatArrayOf(0f, 0f, sin(angle / 2), cos(angle / 2))

        /** Yaw about up, then pitch about the side, then roll about forward, all in the dog's own axes. */
        private fun euler(yaw: Float, pitch: Float, roll: Float): FloatArray {
            val y = floatArrayOf(0f, sin(yaw / 2), 0f, cos(yaw / 2))
            val p = floatArrayOf(0f, 0f, sin(pitch / 2), cos(pitch / 2))
            val r = floatArrayOf(sin(roll / 2), 0f, 0f, cos(roll / 2))
            return mul(mul(y, p), r)
        }

        private fun vec(a: JSONArray?, default: Float) = FloatArray(3) { a?.optDouble(it, default.toDouble())?.toFloat() ?: default }
        private fun quat(a: JSONArray) = FloatArray(4) { a.getDouble(it).toFloat() }

        // Quaternions as x, y, z, w.
        private fun mul(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
        )

        private fun inverse(q: FloatArray): FloatArray {
            val n = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]
            return floatArrayOf(-q[0] / n, -q[1] / n, -q[2] / n, q[3] / n)
        }

        private fun rotate(q: FloatArray, v: FloatArray): FloatArray {
            val p = mul(mul(q, floatArrayOf(v[0], v[1], v[2], 0f)), inverse(q))
            return floatArrayOf(p[0], p[1], p[2])
        }

        private fun slerp(a: FloatArray, b0: FloatArray, t: Float): FloatArray {
            var b = b0
            var dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
            if (dot < 0) { b = floatArrayOf(-b[0], -b[1], -b[2], -b[3]); dot = -dot }
            if (dot > 0.9995f) return normalize(FloatArray(4) { a[it] + (b[it] - a[it]) * t })
            val theta = acos(dot.coerceIn(-1f, 1f))
            val s = sin(theta)
            val wa = sin((1 - t) * theta) / s
            val wb = sin(t * theta) / s
            return FloatArray(4) { a[it] * wa + b[it] * wb }
        }

        /** The same turn, its angle times [amount]. */
        private fun scale(q0: FloatArray, amount: Float): FloatArray {
            if (amount == 1f) return q0
            val q = if (q0[3] < 0) FloatArray(4) { -q0[it] } else q0
            val angle = 2 * acos(q[3].coerceIn(-1f, 1f))
            val s = sqrt(max(1 - q[3] * q[3], 1e-12f))
            if (s < 1e-6f) return IDENTITY
            val half = angle * amount / 2
            val k = sin(half) / s
            return floatArrayOf(q[0] * k, q[1] * k, q[2] * k, cos(half))
        }

        private fun normalize(q: FloatArray): FloatArray {
            val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            return FloatArray(4) { q[it] / n }
        }

        /** A rotation matrix (row-major m00…m22) as a quaternion. */
        private fun fromMatrix(m00: Float, m01: Float, m02: Float, m10: Float, m11: Float, m12: Float, m20: Float, m21: Float, m22: Float): FloatArray {
            val trace = m00 + m11 + m22
            return when {
                trace > 0 -> { val s = sqrt(trace + 1) * 2; floatArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s) }
                m00 > m11 && m00 > m22 -> { val s = sqrt(1 + m00 - m11 - m22) * 2; floatArrayOf(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s) }
                m11 > m22 -> { val s = sqrt(1 + m11 - m00 - m22) * 2; floatArrayOf((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s) }
                else -> { val s = sqrt(1 + m22 - m00 - m11) * 2; floatArrayOf((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s) }
            }
        }

        /** Translation, rotation and scale as Filament's column-major 4×4. */
        private fun matrix(t: FloatArray, q: FloatArray, s: FloatArray): FloatArray {
            val (x, y, z, w) = normalize(q).let { listOf(it[0], it[1], it[2], it[3]) }
            return floatArrayOf(
                (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0f,
                (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0f,
                (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0f,
                t[0], t[1], t[2], 1f,
            )
        }
    }
}
