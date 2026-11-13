package app.opaw.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.opaw.services.CutoutService
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.Executor

/** 4b: a live camera with viewfinder corners, the library, the shutter and flip. */
@Composable
fun CameraStep(onBack: () -> Unit, onPhoto: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { CutoutService.load(context, uri)?.let(onPhoto) }
    }
    fun openLibrary() = library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    DisposableEffect(granted) {
        if (granted) controller.bindToLifecycle(lifecycle)
        onDispose { controller.unbind() }
    }
    var isCapturing by remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(if (isCapturing) 0.92f else 1f, label = "shutter")

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            AndroidView(
                factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFE6E6E8)))
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) { StepHeader(1, onBack = onBack) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Brackets(Modifier.padding(horizontal = 36.dp).padding(top = 52.dp).fillMaxWidth().height(400.dp))
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .shadow(10.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DesignIcon.SPARKLE, size = 16.dp, weight = 2.4f, tint = Ink)
                    Text(
                        if (granted) "Fit your whole pet in the frame" else "Pick a photo of your whole pet",
                        style = app.opaw.ui.theme.opaw(15.sp, FontWeight.ExtraBold),
                        color = Ink,
                    )
                }
            }
            // Controls on a white sheet with rounded top corners.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .navigationBarsPadding()
                    .height(150.dp)
                    .padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .pressable { openLibrary() }
                        .border(1.dp, Color(0xFFE6E6E8), RoundedCornerShape(19.dp))
                        .padding(3.dp)
                        .background(Color(0xFFE6E6E8), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.PHOTO, size = 22.dp, tint = Color(0xFF999999), cutoutColor = Color(0xFFE6E6E8)) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .scale(shutterScale)
                        .size(102.dp)
                        .pressable {
                            if (!granted) return@pressable openLibrary()
                            if (isCapturing) return@pressable
                            isCapturing = true
                            haptics.tap()
                            scope.launch {
                                capture(controller, ContextCompat.getMainExecutor(context))?.let(onPhoto)
                                isCapturing = false
                            }
                        }
                        .background(Ink, CircleShape)
                        .padding(4.dp)
                        .background(Color.White, CircleShape)
                        .padding(5.dp)
                        .background(Ink, CircleShape),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(54.dp)
                        .pressable(enabled = granted) {
                            controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                        }
                        .background(Color(0xFFF1F1F2), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.REFRESH, size = 22.dp, tint = Ink.copy(alpha = if (granted) 1f else 0.4f)) }
            }
        }
    }
}

private suspend fun capture(controller: LifecycleCameraController, executor: Executor): Bitmap? = suspendCoroutine { cont ->
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = runCatching { CutoutService.prepared(image.toBitmap(), image.imageInfo.rotationDegrees) }.getOrNull()
                image.close()
                cont.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) = cont.resume(null)
        },
    )
}

/** White viewfinder corners. */
@Composable
private fun Brackets(modifier: Modifier) {
    Canvas(modifier) {
        val arm = 44.dp.toPx()
        val r = 30.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, arm); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); lineTo(arm, 0f)
            moveTo(w - arm, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, arm)
            moveTo(0f, h - arm); lineTo(0f, h - r); quadraticTo(0f, h, r, h); lineTo(arm, h)
            moveTo(w - arm, h); lineTo(w - r, h); quadraticTo(w, h, w, h - r); lineTo(w, h - arm)
        }
        drawPath(path, Color.White, style = Stroke(5.dp.toPx()))
    }
}

