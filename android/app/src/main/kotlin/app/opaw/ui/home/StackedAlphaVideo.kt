package app.opaw.ui.home

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.opaw.core.AvatarMood
import app.opaw.core.Temperament
import app.opaw.data.AvatarStatus
import app.opaw.data.Dog
import app.opaw.ui.rememberApp
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Stores a pet's generated motion clips on disk. Mirrors AvatarClipStore.swift.
 *
 * Clips are "stacked alpha" MP4 files: the top half is the colour video and the bottom half is the
 * transparency mask. The avatar worker makes them and [StackedAlphaVideo] plays them.
 */
object AvatarClipStore {
    /** Clip names made by the avatar worker. Keep in sync with `avatar-worker/opaw_worker/prompts.py`. */
    val clipNames = listOf("idle", "wag", "excited", "hungry", "sleepy", "shake", "celebrate", "walk")

    fun directory(context: Context, dogId: String): File = File(context.filesDir, "clips/$dogId")

    /** The clip file if it exists, falling back to "idle". */
    fun playableFile(context: Context, dogId: String, preferred: String): File? =
        listOf(preferred, "idle").map { File(directory(context, dogId), "$it.mp4") }.firstOrNull { it.isFile && it.length() > 0 }

    fun deleteAll(context: Context, dogId: String) {
        directory(context, dogId).deleteRecursively()
    }
}

/** Plays a looping stacked-alpha clip with real transparency, so the pet sits on any background. */
@Composable
fun StackedAlphaVideo(file: File, modifier: Modifier = Modifier) {
    key(file.path) {
        AndroidView(factory = { StackedAlphaVideoView(it, file) }, modifier = modifier)
    }
}

/**
 * A transparent TextureView that decodes the clip into an OpenGL texture and, for each frame, takes the
 * colour from the top half and the alpha from the bottom half. Mirrors StackedAlphaPlayerView in
 * StackedAlphaVideoView.swift, using a GPU shader where iOS uses Core Image.
 */
class StackedAlphaVideoView(context: Context, private val file: File) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderer: Renderer? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer = Renderer(surface, file, width, height).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderer?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer?.stop()
        renderer = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private class Renderer(private val output: SurfaceTexture, private val file: File, @Volatile private var width: Int, @Volatile private var height: Int) {
        private val thread = HandlerThread("StackedAlpha").apply { start() }
        private val handler = Handler(thread.looper)
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var textureId = 0
        private var video: SurfaceTexture? = null
        private var videoSurface: Surface? = null
        private var player: MediaPlayer? = null
        private var videoWidth = 0
        private var videoHeight = 0
        private val transform = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0) }

        fun start() = handler.post { runCatching { setUp() }.onFailure { tearDown() } }

        fun resize(w: Int, h: Int) {
            width = w
            height = h
            handler.post { draw(update = false) }
        }

        /** Releases everything on the render thread before the TextureView frees its surface. */
        fun stop() {
            val done = CountDownLatch(1)
            handler.post { tearDown(); done.countDown() }
            done.await(500, TimeUnit.MILLISECONDS)
            thread.quitSafely()
        }

        private fun setUp() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1))
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], output, intArrayOf(EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))

            program = buildProgram()
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val frames = SurfaceTexture(textureId).apply { setOnFrameAvailableListener({ draw(update = true) }, handler) }
            video = frames
            videoSurface = Surface(frames)
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setSurface(videoSurface)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h -> handler.post { this@Renderer.videoWidth = w; this@Renderer.videoHeight = h } }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
            draw(update = false)
        }

        private fun draw(update: Boolean) {
            val frames = video ?: return
            if (eglSurface == EGL14.EGL_NO_SURFACE) return
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
            if (update) {
                frames.updateTexImage()
                frames.getTransformMatrix(transform)
            }
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (videoWidth > 0 && videoHeight > 1) {
                // Fit the top half of the video (the colour) inside the view, like resizeAspect on iOS.
                val clipAspect = videoWidth.toFloat() / (videoHeight / 2)
                val viewAspect = width.toFloat() / height
                val (w, h) = if (clipAspect > viewAspect) width to (width / clipAspect).toInt() else (height * clipAspect).toInt() to height
                GLES20.glViewport((width - w) / 2, (height - h) / 2, w, h)
                GLES20.glUseProgram(program)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFrame"), 0)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTransform"), 1, false, transform, 0)
                val position = GLES20.glGetAttribLocation(program, "aPosition")
                GLES20.glEnableVertexAttribArray(position)
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GLES20.glDisableVertexAttribArray(position)
            }
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        private fun tearDown() {
            player?.run { runCatching { stop() }; release() }
            player = null
            videoSurface?.release()
            videoSurface = null
            video?.release()
            video = null
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            eglSurface = EGL14.EGL_NO_SURFACE
            context = EGL14.EGL_NO_CONTEXT
            display = EGL14.EGL_NO_DISPLAY
        }

        private fun buildProgram(): Int {
            fun shader(type: Int, source: String) = GLES20.glCreateShader(type).also {
                GLES20.glShaderSource(it, source)
                GLES20.glCompileShader(it)
            }
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
                GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
                GLES20.glLinkProgram(it)
            }
        }
    }

    private companion object {
        val VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        // Texture y runs bottom to top, so the video's top half (the colour) is y 0.5–1 and the mask is y 0–0.5.
        val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uFrame;
            uniform mat4 uTransform;
            varying vec2 vUv;
            void main() {
                vec2 colorUv = (uTransform * vec4(vUv.x, 0.5 + vUv.y * 0.5, 0.0, 1.0)).xy;
                vec2 maskUv = (uTransform * vec4(vUv.x, vUv.y * 0.5, 0.0, 1.0)).xy;
                vec3 color = texture2D(uFrame, colorUv).rgb;
                float alpha = texture2D(uFrame, maskUv).r;
                gl_FragColor = vec4(color * alpha, alpha);
            }
        """.trimIndent()
    }
}

/**
 * The best avatar available for a pet: its generated motion clip for the mood, else the living photo.
 * Mirrors AvatarView.swift.
 */
@Composable
fun PetAvatar(
    dog: Dog,
    image: android.graphics.Bitmap?,
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    sticker: androidx.compose.ui.unit.Dp = 0.dp,
    faded: Boolean = false,
    /** Lets one finger spin the 3D pet. Off on Home, where a swipe changes pets. */
    interactive: Boolean = false,
    /** On Home: the 3D pet roams its room (see [PetRoom]). */
    room: Boolean = false,
    /** In the room: eat, drink or hop for a treat now. */
    cue: RoomCue? = null,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = rememberApp()
    // A 3D pet from "Bring to life" comes first, then motion clips, then the living photo.
    val model = app.petModels.file(dog.id, mood)
    val motion = app.petModels.motion(dog.id, app.petModels.clipName(mood))
    val clip = if (model == null && dog.avatarStatus == AvatarStatus.READY) AvatarClipStore.playableFile(context, dog.id, mood.clipName) else null
    val shown: File? = model ?: clip
    Crossfade(shown, modifier, label = "avatar") { file ->
        val tap = if (onTap != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap) else Modifier
        val fade = Modifier.graphicsLayer { alpha = if (faded) 0.6f else 1f }
        when {
            file != null && file.extension == "glb" ->
                PetModel3D(
                    file, Modifier.fillMaxSize().then(fade).then(tap), interactive = interactive,
                    breathe = motion == null && file.nameWithoutExtension == "rest", motion = motion,
                    // On Home it roams, playing as its temperament; a sleepy pet takes it easy.
                    temperament = if (interactive) null else if (mood == AvatarMood.SLEEPY) Temperament.SLEEPY else dog.temperament,
                    room = room,
                    cue = cue,
                )
            file != null -> StackedAlphaVideo(file, Modifier.fillMaxSize().then(fade).then(tap))
            else -> LivingPhoto(image, mood, Modifier.fillMaxSize(), sticker = sticker, faded = faded, onTap = onTap)
        }
    }
}
