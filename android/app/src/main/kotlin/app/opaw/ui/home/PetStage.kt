package app.opaw.ui.home

import app.opaw.core.Temperament
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A rigged pet living on a small stage: it wanders off and comes back close, spins, hops and peeks
 * in from the edge, choosing what to do next by its temperament. Works in stage units: the camera
 * sits at z = 1 looking down -z, the floor is at [FLOOR], and the pet is about one unit long.
 *
 * Each frame, [step] moves the pet on; the view reads [x], [z], [heading], [hop], [squash] and
 * [style] to place it, [moved] and [turned] to step its legs in time with the ground covered, and
 * where it's looking and how its tail wags. Looking about is the head's job: the body only turns
 * to go somewhere, or when the head can't reach round far enough, and it steps as it turns.
 */
internal class PetStage(private val temperament: Temperament, private val random: Random = Random.Default) {
    /** Where the pet stands on the floor. */
    var x = 0f
        private set
    var z = HOME_Z
        private set

    /** Which way its nose points, as a turn from facing the camera (0) towards +x. */
    var heading = 0.35f
        private set

    /** Height above the floor, for hops. */
    var hop = 0f
        private set

    /** Squash (below 0) and stretch (above 0), as a share of its height. */
    var squash = 0f
        private set

    /** What the legs do. */
    var style = PetMotion.Style.IDLE
        private set

    /** Ground covered and turn made in the last step, for the legs. */
    var moved = 0f
        private set
    var turned = 0f
        private set

    /** Where the head points, from straight ahead: turn (positive away from its side), nose up, and tilt. */
    var lookYaw = 0f
        private set
    var lookPitch = 0f
        private set
    var tilt = 0f
        private set

    /** The tail: wag size, wags a second, and how high it's carried. */
    var wag = 0.25f
        private set
    var wagSpeed = 1.5f
        private set
    var tailRaise = 0f
        private set

    private enum class Gaze { CAMERA, GLANCE, SNIFF, AHEAD }
    private var gaze = Gaze.CAMERA
    private var gazeLeft = 2f
    private var glanceYaw = 0f
    private var glancePitch = 0f
    private var wantTilt = 0f

    private val mood = Mood.of(temperament)
    private val queue = ArrayDeque<Step>()
    private var current: Step = Step.Look(1.5f)
    private var clock = 0f

    /** The half-width of floor in view at a depth, given by the view, which knows its shape. */
    var halfWidth: (Float) -> Float = { 1f }

    fun step(dt: Float) {
        val d = dt.coerceIn(0f, 0.05f)
        clock += d
        val x0 = x
        val z0 = z
        val h0 = heading
        if (run(current, d)) next()
        moved = hypot(x - x0, z - z0)
        turned = abs(heading - h0)
        look(d)
        tail(d)
    }

    /** The angle from its nose round to the camera, which sits at x = 0, z = 1. */
    private fun cameraAngle(): Float = wrap(atan2(-x, 1f - z) - heading)

    /** Picks where to look every few seconds, by temperament and by what it's doing, and eases the head there. */
    private fun look(dt: Float) {
        gazeLeft -= dt
        val moving = style != PetMotion.Style.IDLE
        val forced = (current as? Step.Look)?.takeIf { it.toCamera }
        if (forced != null) {
            gaze = Gaze.CAMERA
            if (forced.tilt && wantTilt == 0f) wantTilt = if (random.nextBoolean()) 0.32f else -0.32f
        } else if (gazeLeft <= 0f) {
            gazeLeft = 1.4f + random.nextFloat() * 2.6f
            val roll = random.nextFloat()
            val camera = if (moving) mood.watch * 0.5f else mood.watch
            gaze = when {
                roll < camera -> Gaze.CAMERA
                moving && roll < camera + 0.45f -> Gaze.AHEAD
                roll < camera + 0.15f -> Gaze.SNIFF
                else -> Gaze.GLANCE
            }
            glanceYaw = (random.nextFloat() * 2f - 1f) * 0.9f
            glancePitch = random.nextFloat() * 0.35f - 0.15f
            // A curious little head tilt now and then when it looks at you.
            wantTilt = if (gaze == Gaze.CAMERA && random.nextFloat() < mood.tilting) (if (random.nextBoolean()) 0.3f else -0.3f) else 0f
        }
        var yaw = 0f
        var pitch = 0f
        when (gaze) {
            Gaze.CAMERA -> { yaw = cameraAngle().coerceIn(-1.15f, 1.15f); pitch = 0.12f }
            Gaze.GLANCE -> { yaw = glanceYaw; pitch = glancePitch }
            Gaze.SNIFF -> { yaw = glanceYaw * 0.3f; pitch = -0.5f }
            Gaze.AHEAD -> Unit
        }
        // Eating: nose in the bowl, bobbing as it chews.
        if (current is Step.Eat) {
            yaw = 0f
            pitch = -0.62f + 0.14f * sin(clock * 10f)
        }
        val ease = 1f - kotlin.math.exp(-dt * 5f)
        lookYaw += (yaw - lookYaw) * ease
        lookPitch += (pitch - lookPitch) * ease
        tilt += (wantTilt - tilt) * (1f - kotlin.math.exp(-dt * 3f))
    }

    /** Wags by temperament, harder when it's excited. */
    private fun tail(dt: Float) {
        val excited = current is Step.Spin || current is Step.Hop || style == PetMotion.Style.RUN
        val targetWag = mood.wag * if (excited) 1.4f else 1f
        val ease = 1f - kotlin.math.exp(-dt * 3f)
        wag += (targetWag - wag) * ease
        wagSpeed += (mood.wagSpeed * (if (excited) 1.3f else 1f) - wagSpeed) * ease
        tailRaise += (mood.tailRaise - tailRaise) * ease
    }

    private fun wrap(angle: Float): Float {
        val turn = 2f * PI.toFloat()
        return ((angle + PI.toFloat()) % turn + turn) % turn - PI.toFloat()
    }

    private fun next() {
        clock = 0f
        hop = 0f
        squash = 0f
        if (queue.isEmpty()) plan()
        current = queue.removeFirst()
    }

    /**
     * Drops what it's doing and trots to [x], [z] (stage units), turns to face [face] and eats or
     * laps there for [seconds], head down and bobbing, then looks up at you (16g).
     */
    fun eat(x: Float, z: Float, face: Float, seconds: Float) {
        queue.clear()
        queue += Step.Eat(seconds, face)
        queue += Step.Look(2f, toCamera = true)
        start(Step.Go(Spot(x, z), run = true))
    }

    /** A treat (16f): it hops up for it, then looks at you for more. */
    fun treat() {
        queue.clear()
        queue += Step.Look(2f, toCamera = true)
        start(Step.Hop(2))
    }

    private fun start(step: Step) {
        clock = 0f
        hop = 0f
        squash = 0f
        current = step
    }

    /** Picks the next thing to do, weighted by temperament, and a pause after it. */
    private fun plan() {
        val kinds = Kind.entries.filter { mood.weight(it) > 0 }
        var pick = random.nextFloat() * kinds.sumOf { mood.weight(it).toDouble() }.toFloat()
        val kind = kinds.firstOrNull { pick -= mood.weight(it); pick <= 0f } ?: Kind.LOOK
        when (kind) {
            Kind.WANDER -> queue += Step.Go(spot(), run = false)
            Kind.DASH -> queue += Step.Go(spot(), run = true)
            Kind.SPIN -> queue += Step.Spin(if (random.nextFloat() < mood.double) 2 else 1)
            Kind.HOP -> queue += Step.Hop(1 + random.nextInt(mood.hops))
            Kind.CLOSE -> {
                queue += Step.Go(Spot((random.nextFloat() - 0.5f) * 0.5f, NEAR_Z), run = false)
                queue += Step.Look(2.5f + random.nextFloat() * 2f, toCamera = true)
            }
            Kind.PEEK -> {
                val side = if (x == 0f) (if (random.nextBoolean()) 1f else -1f) else Math.signum(x)
                val peekZ = PEEK_Z + random.nextFloat() * 1.5f
                // Off the edge, out of sight, then the head pokes back in.
                queue += Step.Go(Spot(side * (halfWidth(peekZ) + 0.9f), peekZ), run = false, offStage = true)
                queue += Step.Wait(1f + random.nextFloat() * 1.5f)
                queue += Step.Go(Spot(side * (halfWidth(peekZ) + 0.22f), peekZ), run = false, offStage = true)
                // Nose in towards the middle and the camera, with a curious little sway.
                queue += Step.Look(2.2f + random.nextFloat() * 1.5f, toCamera = true, tilt = true, aim = -side * 0.9f)
                queue += if (random.nextFloat() < mood.retreat) Step.Go(Spot(side * (halfWidth(peekZ) + 0.9f), peekZ), run = false, offStage = true)
                else Step.Go(spot(), run = false)
            }
            Kind.LOOK -> Unit
        }
        queue += Step.Look(mood.pause.start + random.nextFloat() * (mood.pause.endInclusive - mood.pause.start))
    }

    /** A random spot on the floor in view, as far back or as close as the temperament likes. */
    private fun spot(): Spot {
        val sz = mood.near - random.nextFloat() * (mood.near - mood.far)
        val room = (halfWidth(sz) - 0.45f).coerceAtLeast(0.1f)
        return Spot((random.nextFloat() * 2f - 1f) * room, sz)
    }

    /** Advances one step; true when it's finished. */
    private fun run(step: Step, dt: Float): Boolean = when (step) {
        is Step.Look -> {
            style = PetMotion.Style.IDLE
            // The head does the looking. The body only turns, stepping, to face where a peek
            // points it, or when the camera is too far round for the head to reach.
            val aim = step.aim
            if (aim != null) {
                turn(aim, 2.2f * dt)
            } else {
                val toCamera = cameraAngle()
                if (gaze == Gaze.CAMERA && abs(toCamera) > 1.3f) turn(heading + toCamera - Math.signum(toCamera) * 0.6f, 1.6f * dt)
            }
            clock >= step.seconds
        }
        is Step.Wait -> {
            style = PetMotion.Style.IDLE
            clock >= step.seconds
        }
        is Step.Eat -> {
            style = PetMotion.Style.IDLE
            turn(step.face, 2.5f * dt)
            clock >= step.seconds
        }
        is Step.Go -> {
            val dx = step.to.x - x
            val dz = step.to.z - z
            val left = hypot(dx, dz)
            if (left < 0.04f) {
                x = step.to.x
                z = step.to.z
                true
            } else {
                style = if (step.run) PetMotion.Style.RUN else PetMotion.Style.WALK
                val want = atan2(dx, dz)
                val off = turn(want, (if (step.run) 5f else 3.2f) * dt)
                // Turn on the spot first when facing well away from where it's going.
                val speed = (if (step.run) RUN_SPEED else WALK_SPEED) * mood.speed * cos(min(abs(off), PI.toFloat() / 2))
                val move = min(left, speed.coerceAtLeast(0f) * dt)
                x += sin(heading) * move
                z += cos(heading) * move
                if (!step.offStage) {
                    val room = halfWidth(z) + 0.3f
                    x = x.coerceIn(-room, room)
                }
                false
            }
        }
        is Step.Spin -> {
            style = PetMotion.Style.IDLE
            if (step.from.isNaN()) step.from = heading
            val length = 0.95f * step.turns
            val t = (clock / length).coerceIn(0f, 1f)
            // A little crouch, then round it goes with a small hop.
            val crouch = 0.12f / length
            if (t < crouch) {
                squash = -0.08f * (t / crouch)
            } else {
                val u = (t - crouch) / (1f - crouch)
                val eased = u * u * (3f - 2f * u)
                heading = step.from + eased * 2f * PI.toFloat() * step.turns
                hop = 0.12f * sin(PI.toFloat() * u)
                squash = 0.05f * sin(PI.toFloat() * u)
            }
            clock >= length
        }
        is Step.Hop -> {
            style = PetMotion.Style.IDLE
            val each = 0.55f
            val k = (clock / each).toInt()
            val t = (clock - k * each) / each
            // Crouch, spring up stretched, land squashed.
            when {
                t < 0.2f -> { hop = 0f; squash = -0.1f * (t / 0.2f) }
                t < 0.8f -> {
                    val u = (t - 0.2f) / 0.6f
                    hop = mood.hopHeight * 4f * u * (1f - u)
                    squash = 0.08f * (1f - 2f * abs(u - 0.5f)) - 0.02f
                }
                else -> { hop = 0f; squash = -0.08f * (1f - (t - 0.8f) / 0.2f) }
            }
            // Goofy pets wobble mid-air.
            if (mood.wobble) heading += 1.6f * sin(clock * 9f) * dt
            clock >= each * step.count
        }
    }

    /** Turns towards [target] by at most [most]; returns how far off it still is. */
    private fun turn(target: Float, most: Float): Float {
        var off = target - heading
        off = ((off + PI.toFloat()) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat()) - PI.toFloat()
        heading += off.coerceIn(-most, most)
        return off
    }

    private data class Spot(val x: Float, val z: Float)

    private sealed class Step {
        class Look(val seconds: Float, val toCamera: Boolean = false, val tilt: Boolean = false, val aim: Float? = null) : Step()
        class Wait(val seconds: Float) : Step()
        /** Head down in the bowl, facing [face]. */
        class Eat(val seconds: Float, val face: Float) : Step()
        class Go(val to: Spot, val run: Boolean, val offStage: Boolean = false) : Step()
        class Spin(val turns: Int, var from: Float = Float.NaN) : Step()
        class Hop(val count: Int) : Step()
    }

    private enum class Kind { WANDER, DASH, SPIN, HOP, CLOSE, PEEK, LOOK }

    /** How a temperament plays: how often it does each thing, how fast, and where it likes to be. */
    private class Mood(
        val weights: Map<Kind, Float>,
        val speed: Float,
        val pause: ClosedFloatingPointRange<Float>,
        val near: Float,
        val far: Float,
        val hopHeight: Float = 0.16f,
        val hops: Int = 2,
        val double: Float = 0f,
        val wobble: Boolean = false,
        val retreat: Float = 0.3f,
        /** How often it looks at you, and tilts its head when it does. */
        val watch: Float = 0.4f,
        val tilting: Float = 0.3f,
        /** The tail: wag size (radians), wags a second, and how high it's carried. */
        val wag: Float = 0.3f,
        val wagSpeed: Float = 1.8f,
        val tailRaise: Float = 0f,
    ) {
        fun weight(kind: Kind) = weights[kind] ?: 0f

        companion object {
            fun of(temperament: Temperament) = when (temperament) {
                Temperament.PLAYFUL -> Mood(
                    mapOf(Kind.WANDER to 2f, Kind.DASH to 3f, Kind.SPIN to 3f, Kind.HOP to 3f, Kind.PEEK to 1f, Kind.CLOSE to 2f, Kind.LOOK to 1f),
                    speed = 1.15f, pause = 0.6f..1.6f, near = NEAR_Z, far = FAR_Z, hopHeight = 0.2f, hops = 3, double = 0.3f, retreat = 0.1f,
                    watch = 0.5f, tilting = 0.3f, wag = 0.55f, wagSpeed = 3f, tailRaise = 0.15f,
                )
                Temperament.CALM -> Mood(
                    mapOf(Kind.WANDER to 3f, Kind.CLOSE to 1f, Kind.LOOK to 4f),
                    speed = 0.75f, pause = 2.5f..5f, near = -3.0f, far = -6f,
                    watch = 0.4f, tilting = 0.15f, wag = 0.2f, wagSpeed = 1.2f,
                )
                Temperament.CURIOUS -> Mood(
                    mapOf(Kind.WANDER to 3f, Kind.DASH to 1f, Kind.HOP to 1f, Kind.PEEK to 2f, Kind.CLOSE to 3f, Kind.LOOK to 2f),
                    speed = 1f, pause = 1.2f..2.5f, near = NEAR_Z, far = FAR_Z, retreat = 0.15f,
                    watch = 0.6f, tilting = 0.6f, wag = 0.35f, wagSpeed = 2.2f, tailRaise = 0.1f,
                )
                Temperament.SHY -> Mood(
                    mapOf(Kind.WANDER to 2f, Kind.PEEK to 5f, Kind.LOOK to 2f),
                    speed = 0.85f, pause = 1.5f..3f, near = -4.2f, far = FAR_Z, retreat = 0.6f,
                    watch = 0.3f, tilting = 0.4f, wag = 0.12f, wagSpeed = 1f, tailRaise = -0.3f,
                )
                Temperament.SLEEPY -> Mood(
                    mapOf(Kind.WANDER to 1f, Kind.CLOSE to 1f, Kind.LOOK to 5f),
                    speed = 0.6f, pause = 4f..8f, near = -3.2f, far = -5.5f, hopHeight = 0.1f, hops = 1, retreat = 0.2f,
                    watch = 0.3f, tilting = 0.2f, wag = 0.12f, wagSpeed = 0.9f, tailRaise = -0.15f,
                )
                Temperament.GOOFY -> Mood(
                    mapOf(Kind.WANDER to 1f, Kind.DASH to 2f, Kind.SPIN to 4f, Kind.HOP to 3f, Kind.PEEK to 2f, Kind.CLOSE to 1f, Kind.LOOK to 1f),
                    speed = 1.1f, pause = 0.6f..1.4f, near = NEAR_Z, far = FAR_Z, hopHeight = 0.24f, hops = 3, double = 0.5f, wobble = true, retreat = 0.2f,
                    watch = 0.45f, tilting = 0.5f, wag = 0.6f, wagSpeed = 3.4f, tailRaise = 0.1f,
                )
            }
        }
    }

    companion object {
        /** The floor, below the camera so a pet further away stands higher in the picture. */
        const val FLOOR = -0.45f
        const val HOME_Z = -3.8f
        const val NEAR_Z = -2.5f
        const val FAR_Z = -8f
        const val PEEK_Z = -3.6f
        /** Stage units a second, for a pet one unit long. */
        const val WALK_SPEED = 0.55f
        const val RUN_SPEED = 1.5f
    }
}
