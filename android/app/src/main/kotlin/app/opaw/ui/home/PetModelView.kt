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
import com.google.android.filament.IndirectLight
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    fun show(file: File, interactive: Boolean) {
        this.file = file
        if (this.interactive != interactive) {
            this.interactive = interactive
            surface?.let { applyTouch(it) }
        }
        load()
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
            // A soft, even fill so the fur isn't lit only from above.
            scene.indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1f, 1f, 1f))
                .intensity(30_000f)
                .build(engine)
        }
        surface = texture
        viewer = modelViewer
        loaded = null
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
            modelViewer.transformToUnitCube()
        }
        loaded = target
        startNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        val modelViewer = viewer ?: return
        Choreographer.getInstance().postFrameCallback(this)
        if (startNanos == 0L) startNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startNanos) / 1_000_000_000f
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
        private var initialized = false

        fun ensureFilament() {
            if (!initialized) {
                Utils.init()
                initialized = true
            }
        }
    }
}
