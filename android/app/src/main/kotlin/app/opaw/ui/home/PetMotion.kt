package app.opaw.ui.home

import android.content.Context
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.TransformManager
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Walk, run and idle for any pet Tripo rigged, played by the app instead of bought per pet.
 *
 * assets/motion/walk.json holds Tripo's four-legged walk once: for each named bone ("tripo::Spine_1",
 * "tripo::0_Left_Limb_0"…), how it turns at each moment, in the dog's own axes (forward, up, to
 * its side) and relative to its rest pose. Every pet's skeleton has the same bone names but its own
 * rest pose and facing, so each turn is moved into this pet's axes and applied on top of its own
 * rest pose: the bone turns in the world as the original dog's did. Bones a pet lacks are skipped,
 * and their children simply follow their parents. Run is the walk faster and bigger; idle is the
 * walk slowed right down and small, a gentle shift of weight.
 */
internal class PetMotion private constructor(
    private val skeleton: Skeleton,
    private val clip: Clip,
    private val entities: Map<Int, Int>,
) {
    enum class Style(val speed: Float, val amount: Float) {
        WALK(1f, 1f),
        RUN(1.75f, 1.3f),
        IDLE(0.3f, 0.14f),
    }

    /** Poses the pet for [seconds] into the motion. Call animator.updateBoneMatrices() after. */
    fun apply(seconds: Float, style: Style, transforms: TransformManager) {
        val t = (seconds * style.speed) % clip.duration
        // Between two keyframes: blend them, so the motion is smooth at any frame rate.
        var k = clip.times.indexOfLast { it <= t }.coerceAtLeast(0)
        if (k >= clip.times.size - 1) k = clip.times.size - 2
        val span = (clip.times[k + 1] - clip.times[k]).coerceAtLeast(1e-6f)
        val f = ((t - clip.times[k]) / span).coerceIn(0f, 1f)

        val world = arrayOfNulls<FloatArray>(skeleton.count)
        for (i in skeleton.order) {
            val parent = skeleton.parent[i]
            val parentWorld = if (parent >= 0) world[parent]!! else IDENTITY
            val track = clip.bones[skeleton.names[i]]
            if (track == null) {
                world[i] = mul(parentWorld, skeleton.restLocal[i])
                continue
            }
            val turn = scale(slerp(track[k], track[k + 1], f), style.amount)
            // From the dog's own axes into this pet's world, on top of its rest pose.
            val inWorld = mul(mul(skeleton.body, turn), inverse(skeleton.body))
            val now = mul(inWorld, skeleton.restWorld[i])
            world[i] = now
            val local = mul(inverse(parentWorld), now)
            val entity = entities[i] ?: continue
            val instance = transforms.getInstance(entity)
            if (instance != 0) transforms.setTransform(instance, matrix(skeleton.translation[i], local, skeleton.scale[i]))
        }
    }

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
    )

    private class Clip(val duration: Float, val times: FloatArray, val bones: Map<String, Array<FloatArray>>)

    companion object {
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)
        private var walk: Clip? = null

        /** A player for this GLB, or null when it isn't a pet Tripo rigged. */
        fun create(context: Context, glb: ByteArray, asset: FilamentAsset): PetMotion? = runCatching {
            val clip = walk ?: loadClip(context).also { walk = it }
            val skeleton = readSkeleton(glb) ?: return null
            if (skeleton.names.none { it != null && it in clip.bones }) return null
            val entities = HashMap<Int, Int>()
            for (i in 0 until skeleton.count) {
                val name = skeleton.names[i] ?: continue
                if (name !in clip.bones) continue
                val entity = asset.getFirstEntityByName(name)
                if (entity != 0) entities[i] = entity
            }
            PetMotion(skeleton, clip, entities)
        }.getOrNull()

        private fun loadClip(context: Context): Clip {
            val json = JSONObject(context.assets.open("motion/walk.json").bufferedReader().use { it.readText() })
            val times = json.getJSONArray("times").let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val bones = HashMap<String, Array<FloatArray>>()
            val all = json.getJSONObject("bones")
            for (name in all.keys()) {
                val frames = all.getJSONArray(name)
                bones[name] = Array(frames.length()) { quat(frames.getJSONArray(it)) }
            }
            return Clip(json.getDouble("duration").toFloat(), times, bones)
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
            return Skeleton(count, names, parent, order, translation, restLocal, scale, Array(count) { restWorld[it]!! }, bodyFrame(head, hipL, hipR))
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
