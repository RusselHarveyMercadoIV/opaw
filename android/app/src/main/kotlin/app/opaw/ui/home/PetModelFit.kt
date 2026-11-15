package app.opaw.ui.home

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where a 3D pet really is and how big, measured with its skeleton applied.
 * [selfLit] means the texture is the model's own glow (how Meshy exports), so it needs no lamps.
 */
internal class PetModelFit(val center: FloatArray, val size: FloatArray, val selfLit: Boolean)

/**
 * Measures a GLB the way it's drawn. Filament sizes a model from its raw vertex box and ignores
 * the skeleton; Meshy's rigged pets have a skeleton scaled 100× against that box, so Filament's
 * own fit shows the pet a hundred times too big. This poses the skeleton through the whole first
 * animation and returns the box that holds the pet in every frame.
 */
internal object PetModelMeasure {
    private const val SAMPLES = 8

    fun measure(bytes: ByteArray): PetModelFit? = runCatching { Glb(bytes).fit() }.getOrNull()

    private class Channel(val node: Int, val path: String, val times: FloatArray, val values: FloatArray, val width: Int, val stride: Int, val offset: Int, val step: Boolean)

    private class Glb(bytes: ByteArray) {
        private val data: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private val json: JSONObject
        private val binStart: Int

        init {
            require(data.getInt(0) == 0x46546C67) { "Not a GLB." }
            val jsonLength = data.getInt(12)
            json = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
            binStart = 20 + jsonLength + 8
        }

        private val nodes: JSONArray = json.getJSONArray("nodes")
        private val accessors: JSONArray = json.getJSONArray("accessors")
        private val views: JSONArray = json.getJSONArray("bufferViews")

        private val kept = HashMap<Int, Pair<FloatArray, Int>>()

        /** An accessor's numbers as floats, and how many there are per element. Kept, as every sampled moment reuses them. */
        private fun read(index: Int): Pair<FloatArray, Int> = kept.getOrPut(index) { load(index) }

        private fun load(index: Int): Pair<FloatArray, Int> {
            val accessor = accessors.getJSONObject(index)
            val view = views.getJSONObject(accessor.getInt("bufferView"))
            val type = accessor.getInt("componentType")
            val width = when (accessor.getString("type")) {
                "SCALAR" -> 1
                "VEC2" -> 2
                "VEC3" -> 3
                "VEC4" -> 4
                "MAT4" -> 16
                else -> error("Unsupported accessor.")
            }
            val bytesEach = when (type) {
                5120, 5121 -> 1
                5122, 5123 -> 2
                5125, 5126 -> 4
                else -> error("Unsupported number type.")
            }
            val count = accessor.getInt("count")
            val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: (bytesEach * width)
            val start = binStart + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val normalized = accessor.optBoolean("normalized", false)
            val out = FloatArray(count * width)
            for (i in 0 until count) {
                for (c in 0 until width) {
                    val at = start + i * stride + c * bytesEach
                    out[i * width + c] = when (type) {
                        5126 -> data.getFloat(at)
                        5121 -> (data.get(at).toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                        5123 -> (data.getShort(at).toInt() and 0xFFFF).let { if (normalized) it / 65535f else it.toFloat() }
                        5125 -> data.getInt(at).toFloat()
                        5120 -> data.get(at).toInt().let { if (normalized) max(it / 127f, -1f) else it.toFloat() }
                        else -> data.getShort(at).toInt().let { if (normalized) max(it / 32767f, -1f) else it.toFloat() }
                    }
                }
            }
            return out to width
        }

        private fun floats(array: JSONArray?, fallback: FloatArray): FloatArray =
            if (array == null) fallback.copyOf() else FloatArray(array.length()) { array.getDouble(it).toFloat() }

        // Each node's resting move, turn and size.
        private val restMove = Array(nodes.length()) { floats(nodes.getJSONObject(it).optJSONArray("translation"), floatArrayOf(0f, 0f, 0f)) }
        private val restTurn = Array(nodes.length()) { floats(nodes.getJSONObject(it).optJSONArray("rotation"), floatArrayOf(0f, 0f, 0f, 1f)) }
        private val restSize = Array(nodes.length()) { floats(nodes.getJSONObject(it).optJSONArray("scale"), floatArrayOf(1f, 1f, 1f)) }

        private fun readChannels(): Pair<List<Channel>, Float> {
            val animation = json.optJSONArray("animations")?.optJSONObject(0) ?: return emptyList<Channel>() to 0f
            val samplers = animation.getJSONArray("samplers")
            val list = animation.getJSONArray("channels")
            val out = ArrayList<Channel>()
            var duration = 0f
            for (i in 0 until list.length()) {
                val channel = list.getJSONObject(i)
                val target = channel.getJSONObject("target")
                val path = target.optString("path")
                val node = target.optInt("node", -1)
                if (node < 0 || path !in setOf("translation", "rotation", "scale")) continue
                val sampler = samplers.getJSONObject(channel.getInt("sampler"))
                val times = read(sampler.getInt("input")).first
                val (values, width) = read(sampler.getInt("output"))
                if (times.isEmpty()) continue
                // A cubic spline stores a tangent, the value, then another tangent for each key.
                val spline = sampler.optString("interpolation") == "CUBICSPLINE"
                out += Channel(node, path, times, values, width, if (spline) 3 else 1, if (spline) 1 else 0, sampler.optString("interpolation") == "STEP")
                duration = max(duration, times.last())
            }
            return out to duration
        }

        /** Every node's place in the model at one moment of the animation. */
        private fun pose(channels: List<Channel>, time: Float): Array<FloatArray> {
            val move = Array(nodes.length()) { restMove[it] }
            val turn = Array(nodes.length()) { restTurn[it] }
            val size = Array(nodes.length()) { restSize[it] }
            val animated = BooleanArray(nodes.length())
            for (channel in channels) {
                val times = channel.times
                var key = 0
                while (key < times.size - 1 && times[key + 1] <= time) key++
                val next = min(key + 1, times.size - 1)
                val span = times[next] - times[key]
                val blend = if (channel.step || span <= 0f) 0f else ((time - times[key]) / span).coerceIn(0f, 1f)
                val a = (key * channel.stride + channel.offset) * channel.width
                val b = (next * channel.stride + channel.offset) * channel.width
                val value = FloatArray(channel.width)
                if (channel.path == "rotation") {
                    // Turn the short way round.
                    var dot = 0f
                    for (c in 0 until 4) dot += channel.values[a + c] * channel.values[b + c]
                    val sign = if (dot < 0f) -1f else 1f
                    var length = 0f
                    for (c in 0 until 4) {
                        value[c] = channel.values[a + c] * (1f - blend) + channel.values[b + c] * blend * sign
                        length += value[c] * value[c]
                    }
                    length = sqrt(length).takeIf { it > 0f } ?: 1f
                    for (c in 0 until 4) value[c] /= length
                    turn[channel.node] = value
                } else {
                    for (c in 0 until channel.width) value[c] = channel.values[a + c] * (1f - blend) + channel.values[b + c] * blend
                    if (channel.path == "translation") move[channel.node] = value else size[channel.node] = value
                }
                animated[channel.node] = true
            }

            val world = Array(nodes.length()) { IDENTITY }
            val scenes = json.optJSONArray("scenes")
            val roots = scenes?.optJSONObject(json.optInt("scene", 0))?.optJSONArray("nodes")
            val stack = ArrayDeque<Pair<Int, FloatArray>>()
            if (roots != null) for (i in 0 until roots.length()) stack.addLast(roots.getInt(i) to IDENTITY)
            var visited = 0
            while (stack.isNotEmpty() && visited++ <= nodes.length()) {
                val (index, parent) = stack.removeLast()
                val node = nodes.getJSONObject(index)
                val matrix = node.optJSONArray("matrix")
                val local = if (matrix != null && !animated[index]) FloatArray(16) { matrix.getDouble(it).toFloat() } else compose(move[index], turn[index], size[index])
                world[index] = multiply(parent, local)
                val children = node.optJSONArray("children") ?: continue
                for (c in 0 until children.length()) stack.addLast(children.getInt(c) to world[index])
            }
            return world
        }

        fun fit(): PetModelFit {
            val (channels, duration) = readChannels()
            val low = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val high = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            val meshes = json.getJSONArray("meshes")
            val skins = json.optJSONArray("skins")
            val moments = if (channels.isEmpty() || duration <= 0f) 1 else SAMPLES
            for (moment in 0 until moments) {
                val world = pose(channels, duration * moment / moments)
                for (index in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(index)
                    if (!node.has("mesh")) continue
                    // A skinned mesh is placed by its joints alone; its own node is ignored.
                    val skin = if (node.has("skin")) skins?.optJSONObject(node.getInt("skin")) else null
                    val bones = skin?.let { jointMatrices(it, world) }
                    val primitives = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")
                    for (p in 0 until primitives.length()) {
                        val attributes = primitives.getJSONObject(p).getJSONObject("attributes")
                        val positions = read(attributes.getInt("POSITION")).first
                        val skinned = bones != null && attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")
                        val joints = if (skinned) read(attributes.getInt("JOINTS_0")).first else null
                        val weights = if (skinned) read(attributes.getInt("WEIGHTS_0")).first else null
                        for (v in 0 until positions.size / 3) {
                            val x = positions[v * 3]
                            val y = positions[v * 3 + 1]
                            val z = positions[v * 3 + 2]
                            var px = 0f
                            var py = 0f
                            var pz = 0f
                            var total = 0f
                            if (joints != null && weights != null && bones != null) {
                                for (c in 0 until 4) {
                                    val weight = weights[v * 4 + c]
                                    if (weight <= 0f) continue
                                    val m = bones.getOrNull(joints[v * 4 + c].toInt()) ?: continue
                                    px += weight * (m[0] * x + m[4] * y + m[8] * z + m[12])
                                    py += weight * (m[1] * x + m[5] * y + m[9] * z + m[13])
                                    pz += weight * (m[2] * x + m[6] * y + m[10] * z + m[14])
                                    total += weight
                                }
                            }
                            if (total > 0f) {
                                px /= total
                                py /= total
                                pz /= total
                            } else {
                                val m = world[index]
                                px = m[0] * x + m[4] * y + m[8] * z + m[12]
                                py = m[1] * x + m[5] * y + m[9] * z + m[13]
                                pz = m[2] * x + m[6] * y + m[10] * z + m[14]
                            }
                            low[0] = min(low[0], px); high[0] = max(high[0], px)
                            low[1] = min(low[1], py); high[1] = max(high[1], py)
                            low[2] = min(low[2], pz); high[2] = max(high[2], pz)
                        }
                    }
                }
            }
            require(high[0] >= low[0]) { "No vertices." }
            return PetModelFit(
                center = FloatArray(3) { (low[it] + high[it]) / 2f },
                size = FloatArray(3) { high[it] - low[it] },
                selfLit = selfLit(),
            )
        }

        /** One matrix per joint: where the joint is now, times where it was when the skin was bound. */
        private fun jointMatrices(skin: JSONObject, world: Array<FloatArray>): Array<FloatArray> {
            val joints = skin.getJSONArray("joints")
            val bind = if (skin.has("inverseBindMatrices")) read(skin.getInt("inverseBindMatrices")).first else null
            return Array(joints.length()) { k ->
                val joint = world[joints.getInt(k)]
                if (bind == null) joint else multiply(joint, bind.copyOfRange(k * 16, k * 16 + 16))
            }
        }

        private fun selfLit(): Boolean {
            val materials = json.optJSONArray("materials") ?: return false
            if (materials.length() == 0) return false
            for (i in 0 until materials.length()) {
                val material = materials.getJSONObject(i)
                val glow = material.optJSONArray("emissiveFactor")
                val bright = glow != null && (0 until glow.length()).any { glow.getDouble(it) > 0.5 }
                if (!material.has("emissiveTexture") || !bright) return false
            }
            return true
        }
    }

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Column-major, like glTF and Filament. */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[column * 4 + k]
                out[column * 4 + row] = sum
            }
        }
        return out
    }

    private fun compose(move: FloatArray, turn: FloatArray, size: FloatArray): FloatArray {
        val x = turn[0]
        val y = turn[1]
        val z = turn[2]
        val w = turn[3]
        return floatArrayOf(
            (1f - 2f * (y * y + z * z)) * size[0], (2f * (x * y + z * w)) * size[0], (2f * (x * z - y * w)) * size[0], 0f,
            (2f * (x * y - z * w)) * size[1], (1f - 2f * (x * x + z * z)) * size[1], (2f * (y * z + x * w)) * size[1], 0f,
            (2f * (x * z + y * w)) * size[2], (2f * (y * z - x * w)) * size[2], (1f - 2f * (x * x + y * y)) * size[2], 0f,
            move[0], move[1], move[2], 1f,
        )
    }
}
