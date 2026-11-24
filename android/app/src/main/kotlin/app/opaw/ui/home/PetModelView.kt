package app.opaw.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.ColorGrading
import com.google.android.filament.IndirectLight
import com.google.android.filament.ToneMapper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 3D pet from "Bring to life": a GLB drawn with Filament on a transparent background, playing
 * its own animation on a loop if it has one. A pet Tripo rigged comes without clips, and
 * [motion] ("walk", "run" or "idle") is played on its skeleton by the app instead. A still model
 * breathes. With [interactive], one finger spins the pet around.
 */
@Composable
fun PetModel3D(
    file: File,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    breathe: Boolean = false,
    motion: String? = null,
    /** A rigged pet with a temperament roams its stage instead of playing one motion in place. */
    temperament: app.opaw.core.Temperament? = null,
    /** On Home: the stage is the pet's room, seen through [RoomCamera], and walled in. */
    room: Boolean = false,
    /** Something to do now in the room: eat, drink or hop for a treat. */
    cue: RoomCue? = null,
) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    AndroidView(
        factory = { PetModelLayout(it) },
        update = { it.show(file, interactive, motion, temperament, room, cue) },
        modifier = modifier.graphicsLayer {
            if (breathe) {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = 1f + 0.025f * breath
                scaleX = 1f - 0.008f * breath
            }
        },
    )
}

/**
 * Holds a fresh TextureView and ModelViewer for each time it's on screen. ModelViewer destroys
 * its engine when its view leaves the window, so a reused view would draw with a dead engine.
 */
@SuppressLint("ViewConstructor")
private class PetModelLayout(context: Context) : FrameLayout(context), Choreographer.FrameCallback {
    private var surface: TextureView? = null
    private var viewer: ModelViewer? = null
    private var loaded: File? = null
    private var file: File? = null
    private var interactive = false
    private var startNanos = 0L
    private var fit: PetModelFit? = null
    private var fill: IndirectLight? = null
    private var framed = false
    private var lampsOn = true
    private var player: PetMotion? = null
    private var style: PetMotion.Style? = null
    private var temperament: app.opaw.core.Temperament? = null
    private var room = false
    private var stage: PetStage? = null
    private var aspect = 1f
    private var lastNanos = 0L
    private val motion = PetMotion.State()
    private val shadow = StageShadow(context)
    private var cue: RoomCue? = null
    private var playedCue = 0L

    fun show(file: File, interactive: Boolean, motion: String? = null, temperament: app.opaw.core.Temperament? = null, room: Boolean = false, cue: RoomCue? = null) {
        this.file = file
        if (cue != null) this.cue = cue
        if (this.room != room) {
            this.room = room
            framed = false
        }
        if (this.temperament != temperament) {
            this.temperament = temperament
            stage = null
            framed = false
        }
        style = when (motion) {
            "walk" -> PetMotion.Style.WALK
            "run" -> PetMotion.Style.RUN
            "idle" -> PetMotion.Style.IDLE
            else -> null
        }
        if (this.interactive != interactive) {
            this.interactive = interactive
            framed = false
            surface?.let { applyTouch(it) }
        }
        load()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        framed = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureFilament()
        val texture = TextureView(context).apply { isOpaque = false }
        val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
        val modelViewer = ModelViewer(texture, uiHelper = helper).apply {
            view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
            renderer.clearOptions = renderer.clearOptions.apply {
                clear = true
                clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            }
            // Show the texture's own colours instead of a film-style curve that dulls them.
            view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
        }
        // A soft, even fill so the fur isn't lit only from above.
        fill = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1f, 1f, 1f))
            .intensity(30_000f)
            .build(modelViewer.engine)
        modelViewer.scene.indirectLight = fill
        lampsOn = true
        surface = texture
        viewer = modelViewer
        loaded = null
        fit = null
        framed = false
        applyTouch(texture)
        addView(shadow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        load()
        startNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        // Removing the TextureView makes ModelViewer destroy itself.
        viewer = null
        fill = null
        surface?.let { removeView(it) }
        surface = null
        removeView(shadow)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applyTouch(texture: TextureView) {
        val modelViewer = viewer
        if (interactive && modelViewer != null) texture.setOnTouchListener(modelViewer) else texture.setOnTouchListener(null)
    }

    private fun load() {
        val modelViewer = viewer ?: return
        val target = file ?: return
        if (target == loaded) return
        val bytes = runCatching { target.readBytes() }.getOrNull() ?: return
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.rewind()
        runCatching {
            modelViewer.loadModelGlb(buffer)
            // Until the view has a size to frame against; also the fallback if measuring fails.
            modelViewer.transformToUnitCube()
        }
        fit = PetModelMeasure.measure(bytes)
        player = modelViewer.asset?.let { PetMotion.create(context, bytes, it) }
        stage = null
        framed = false
        runCatching {
            lamps(modelViewer, on = fit?.selfLit != true)
            // The stored box ignores the skeleton, so Filament would hide the pet whenever that box left the view.
            val renderables = modelViewer.engine.renderableManager
            modelViewer.asset?.renderableEntities?.forEach { entity ->
                val instance = renderables.getInstance(entity)
                if (instance != 0) renderables.setCulling(instance, false)
            }
        }
        loaded = target
        startNanos = 0L
    }

    /** Meshy's texture glows with its own colours; lamps on top of that wash it out. */
    private fun lamps(modelViewer: ModelViewer, on: Boolean) {
        if (on == lampsOn) return
        lampsOn = on
        if (on) {
            modelViewer.scene.addEntity(modelViewer.light)
            modelViewer.scene.indirectLight = fill
        } else {
            modelViewer.scene.removeEntity(modelViewer.light)
            modelViewer.scene.indirectLight = null
        }
    }

    /**
     * Sizes and centres the pet in front of the camera from its measured box. Waits for the view
     * to have a size, as a tall narrow view fits less across than a wide one.
     */
    private fun frame(modelViewer: ModelViewer) {
        val measured = fit ?: return
        val asset = modelViewer.asset ?: return
        val viewport = modelViewer.view.viewport
        if (width <= 0 || height <= 0 || viewport.width <= 0 || viewport.height <= 0) return
        framed = true
        val (wide, tall, deep) = measured.size
        if (max(wide, max(tall, deep)) <= 0f) return
        if (roams()) {
            val focal = if (room) RoomCamera.FOCAL else FOCAL_LENGTH
            modelViewer.cameraFocalLength = focal
            aspect = viewport.width.toFloat() / viewport.height
            val tan = SENSOR_HEIGHT / (2f * focal)
            if (stage == null) stage = temperament?.let { PetStage(it) }
            val walls = RoomCamera.HALF - RoomCamera.WALL_GAP
            stage?.halfWidth = if (room) ({ z -> min((1f - RoomCamera.worldZ(z)) * tan * aspect, walls) }) else ({ z -> (1f - z) * tan * aspect })
            return
        }
        // Turn the pet so its head points a little to one side of the camera: a three-quarter
        // view shows both the face and the body's length.
        val turn = TURN - measured.facing
        val c = cos(turn)
        val s = sin(turn)
        val turnedWide = abs(c) * wide + abs(s) * deep
        val turnedDeep = abs(s) * wide + abs(c) * deep
        // A long lens keeps the nose from ballooning toward the camera.
        modelViewer.cameraFocalLength = FOCAL_LENGTH
        val seenTall = CAMERA_DISTANCE * SENSOR_HEIGHT / FOCAL_LENGTH
        val seenWide = seenTall * viewport.width / viewport.height
        // Spinning the pet swings its length across the view, so leave room for that too.
        val across = if (interactive) max(turnedWide, turnedDeep) else turnedWide
        // The near end looks a little bigger than it is, so give depth some weight too.
        val scale = FILL * min(seenTall / max(tall, NEAR * turnedDeep), seenWide / max(across, 1e-9f))
        // Scale, then turn around the pet's middle, then place it in front of the camera.
        val (cx, cy, cz) = measured.center
        val tx = c * cx + s * cz
        val tz = -s * cx + c * cz
        val transforms = modelViewer.engine.transformManager
        transforms.setTransform(
            transforms.getInstance(asset.root),
            floatArrayOf(
                scale * c, 0f, -scale * s, 0f,
                0f, scale, 0f, 0f,
                scale * s, 0f, scale * c, 0f,
                -scale * tx, -scale * cy, TARGET_Z - scale * tz, 1f,
            ),
        )
    }

    override fun doFrame(frameTimeNanos: Long) {
        val modelViewer = viewer ?: return
        Choreographer.getInstance().postFrameCallback(this)
        if (startNanos == 0L) startNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startNanos) / 1_000_000_000f
        if (!framed) runCatching { frame(modelViewer) }
        val dt = if (lastNanos == 0L) 0f else (frameTimeNanos - lastNanos) / 1_000_000_000f
        lastNanos = frameTimeNanos
        val roaming = stage
        if (roaming != null && framed) {
            runCatching { roam(modelViewer, roaming, dt) }
            modelViewer.render(frameTimeNanos)
            return
        }
        shadow.hide()
        modelViewer.animator?.let { animator ->
            val moving = style
            val skeleton = player
            if (animator.animationCount > 0) {
                val duration = animator.getAnimationDuration(0).coerceAtLeast(0.01f)
                animator.applyAnimation(0, seconds % duration)
                animator.updateBoneMatrices()
            } else if (moving != null && skeleton != null) {
                runCatching {
                    skeleton.apply(seconds, moving, modelViewer.engine.transformManager)
                    animator.updateBoneMatrices()
                }
            }
        }
        modelViewer.render(frameTimeNanos)
    }

    /** A rigged pet with a temperament, on Home: it moves about its stage. */
    private fun roams() = temperament != null && !interactive && style != null && player != null

    /** Moves the pet on, places it on the stage, and plays its legs. */
    private fun roam(modelViewer: ModelViewer, stage: PetStage, dt: Float) {
        val measured = fit ?: return
        val asset = modelViewer.asset ?: return
        val skeleton = player ?: return
        // A new cue: off to the bowl, or a hop for a treat.
        cue?.takeIf { it.id != playedCue && room && it.active() }?.let { next ->
            playedCue = next.id
            when (next.kind) {
                RoomCue.Kind.EAT, RoomCue.Kind.DRINK -> {
                    val (ax, ay) = if (next.kind == RoomCue.Kind.EAT) RoomCamera.EAT_AT else RoomCamera.DRINK_AT
                    val (sx, sz) = RoomCamera.stageSpot(ax, ay)
                    stage.eat(sx, sz, face = (PI / 2).toFloat(), seconds = next.kind.seconds)
                }
                RoomCue.Kind.TREAT -> stage.treat()
            }
        }
        stage.step(dt)
        val (wide, tall, deep) = measured.size
        val k = (if (room) RoomCamera.PET_LENGTH else 1f) / max(max(wide, deep), 1e-6f)
        val floor = if (room) RoomCamera.FLOOR else PetStage.FLOOR
        // In the room the stage is placed deeper, and kept between the walls even when it peeks.
        val wx = if (room) stage.x.coerceIn(-(RoomCamera.HALF - 0.3f), RoomCamera.HALF - 0.3f) else stage.x
        val wz = if (room) RoomCamera.worldZ(stage.z) else stage.z

        // Legs: ease between standing, walking and trotting, and step in time with the ground
        // covered so the paws don't slide. Turning on the spot steps too.
        val ease = 1f - exp(-dt * 6f)
        val gait = when (stage.style) {
            PetMotion.Style.RUN -> 1.3f
            PetMotion.Style.WALK -> 0f
            PetMotion.Style.IDLE -> motion.gait
        }
        motion.gait += (gait - motion.gait) * ease
        val stepping = stage.style != PetMotion.Style.IDLE || stage.turned > 0.002f
        motion.stride += ((if (stepping) 1f else 0f) - motion.stride) * (1f - exp(-dt * 8f))
        val leg = (skeleton.legLength * k).coerceAtLeast(0.05f)
        motion.phase += stage.moved / (skeleton.cycleLength(motion.gait) * leg) + stage.turned * 0.35f
        motion.time += dt
        motion.lookYaw = stage.lookYaw
        motion.lookPitch = stage.lookPitch
        motion.tilt = stage.tilt
        motion.wag = stage.wag
        motion.wagSpeed = stage.wagSpeed
        motion.tailRaise = stage.tailRaise
        skeleton.apply(motion, modelViewer.engine.transformManager)
        modelViewer.animator?.updateBoneMatrices()
        val bounce = skeleton.bob(motion) * leg

        // About one stage unit long, standing on the floor, turned the way it's heading.
        val turn = stage.heading - measured.facing
        val c = cos(turn)
        val s = sin(turn)
        val across = k * (1f - 0.5f * stage.squash)
        val up = k * (1f + stage.squash)
        val (cx, cy, cz) = measured.center
        val bottom = cy - tall / 2f
        val transforms = modelViewer.engine.transformManager
        transforms.setTransform(
            transforms.getInstance(asset.root),
            floatArrayOf(
                across * c, 0f, -across * s, 0f,
                0f, up, 0f, 0f,
                across * s, 0f, across * c, 0f,
                wx - across * (c * cx + s * cz), floor + stage.hop + bounce - up * bottom, wz - across * (-s * cx + c * cz), 1f,
            ),
        )

        // Its shadow on the floor, smaller and lighter while it's in the air.
        val tan = SENSOR_HEIGHT / (2f * (if (room) RoomCamera.FOCAL else FOCAL_LENGTH))
        val depth = (1f - wz).coerceAtLeast(0.1f)
        val px = (wx / (depth * tan * aspect) + 1f) / 2f * width
        val py = (1f - floor / (depth * tan)) / 2f * height
        val reach = (0.22f + 0.24f * abs(sin(stage.heading))) * (if (room) RoomCamera.PET_LENGTH else 1f) / (depth * tan * aspect) * width / 2f
        val lift = (1f - 2.5f * stage.hop).coerceIn(0.4f, 1f)
        shadow.show(px, py, reach * (0.8f + 0.2f * lift), reach * 0.3f * (0.8f + 0.2f * lift), 0.22f * lift)
    }

    companion object {
        // ModelViewer's camera sits at z = 1 and looks at z = -4.
        private const val TARGET_Z = -4f
        private const val CAMERA_DISTANCE = 5f
        private const val SENSOR_HEIGHT = 24f
        private const val FOCAL_LENGTH = 70f
        private const val FILL = 0.88f
        /** Where the head ends up: 35° from facing the camera, towards the viewer's right. */
        private const val TURN = 0.61f
        private const val NEAR = 0.6f

        private var initialized = false

        fun ensureFilament() {
            if (!initialized) {
                Utils.init()
                initialized = true
            }
        }
    }
}

/** A soft round shadow under the roaming pet, drawn behind the 3D view. */
private class StageShadow(context: Context) : android.view.View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var cx = 0f
    private var cy = 0f
    private var rx = 0f
    private var ry = 0f
    private var strength = 0f

    fun show(x: Float, y: Float, radiusX: Float, radiusY: Float, strength: Float) {
        cx = x; cy = y; rx = radiusX; ry = radiusY; this.strength = strength
        invalidate()
    }

    fun hide() {
        if (this.strength == 0f) return
        this.strength = 0f
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        if (strength <= 0f || rx <= 1f || ry <= 0.5f) return
        val ink = (strength * 255).toInt().coerceIn(0, 255)
        paint.shader = android.graphics.RadialGradient(
            0f, 0f, rx,
            intArrayOf(android.graphics.Color.argb(ink, 0, 0, 0), android.graphics.Color.argb(ink / 2, 0, 0, 0), android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(1f, ry / rx)
        canvas.drawCircle(0f, 0f, rx, paint)
        canvas.restore()
    }
}
