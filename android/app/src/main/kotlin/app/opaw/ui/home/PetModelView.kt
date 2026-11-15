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
import kotlin.math.max
import kotlin.math.min

/**
 * The 3D pet from "Bring to life": a Meshy GLB drawn with Filament on a transparent background,
 * playing its animation on a loop. The rest pose has no animation, so it breathes instead.
 * With [interactive], one finger spins the pet around.
 */
@Composable
fun PetModel3D(file: File, modifier: Modifier = Modifier, interactive: Boolean = false, breathe: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    AndroidView(
        factory = { PetModelLayout(it) },
        update = { it.show(file, interactive) },
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

    fun show(file: File, interactive: Boolean) {
        this.file = file
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
        // A long lens keeps the nose from ballooning toward the camera.
        modelViewer.cameraFocalLength = FOCAL_LENGTH
        val seenTall = CAMERA_DISTANCE * SENSOR_HEIGHT / FOCAL_LENGTH
        val seenWide = seenTall * viewport.width / viewport.height
        // Spinning the pet swings its length across the view, so leave room for that too.
        val across = if (interactive) max(wide, deep) else wide
        val scale = FILL * min(seenTall / max(tall, deep), seenWide / max(across, 1e-9f))
        val transforms = modelViewer.engine.transformManager
        transforms.setTransform(
            transforms.getInstance(asset.root),
            floatArrayOf(
                scale, 0f, 0f, 0f,
                0f, scale, 0f, 0f,
                0f, 0f, scale, 0f,
                -scale * measured.center[0], -scale * measured.center[1], TARGET_Z - scale * measured.center[2], 1f,
            ),
        )
    }

    override fun doFrame(frameTimeNanos: Long) {
        val modelViewer = viewer ?: return
        Choreographer.getInstance().postFrameCallback(this)
        if (startNanos == 0L) startNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startNanos) / 1_000_000_000f
        if (!framed) runCatching { frame(modelViewer) }
        modelViewer.animator?.let { animator ->
            if (animator.animationCount > 0) {
                val duration = animator.getAnimationDuration(0).coerceAtLeast(0.01f)
                animator.applyAnimation(0, seconds % duration)
                animator.updateBoneMatrices()
            }
        }
        modelViewer.render(frameTimeNanos)
    }

    companion object {
        // ModelViewer's camera sits at z = 1 and looks at z = -4.
        private const val TARGET_Z = -4f
        private const val CAMERA_DISTANCE = 5f
        private const val SENSOR_HEIGHT = 24f
        private const val FOCAL_LENGTH = 70f
        private const val FILL = 0.88f

        private var initialized = false

        fun ensureFilament() {
            if (!initialized) {
                Utils.init()
                initialized = true
            }
        }
    }
}
