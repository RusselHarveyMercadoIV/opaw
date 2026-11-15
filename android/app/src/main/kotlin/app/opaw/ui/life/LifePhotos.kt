package app.opaw.ui.life

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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.opaw.services.CutoutService
import app.opaw.services.LifePhoto
import app.opaw.services.PetModelState
import app.opaw.services.PetScan
import app.opaw.services.PhotoIssue
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** The four reference photos (9a). Three body shots teach Meshy the shape; the face keeps the eyes sharp. */
enum class LifeAngle(val label: String, val tip: String, val hint: String) {
    FRONT("Front", "Face the camera, whole body in frame", "Facing you"),
    SIDE("Side", "Standing side on, nose to tail", "Nose to tail"),
    BACK("Back", "From behind, tail in frame", "Tail in frame"),
    FACE("Face", "Close up, eyes in focus", "Close up"),
}

/** One photo in a slot. [scan] is null while the photo is still being looked at. */
class LifeShot(val photo: Bitmap, val scan: PetScan?) {
    val cutout: Bitmap? get() = scan?.segmentation?.cutout
}

/** The photos taken so far, and whether there are enough good ones to bring a pet to life. */
class LifePhotos(private val scope: CoroutineScope) {
    val shots = mutableStateMapOf<LifeAngle, LifeShot>()

    /** Puts a new photo in a slot and scans it for the pet. */
    fun add(angle: LifeAngle, photo: Bitmap) {
        shots[angle] = LifeShot(photo, null)
        scope.launch {
            val scan = CutoutService.scan(photo)
            // Retaken while scanning: the newer photo wins.
            if (shots[angle]?.photo === photo) shots[angle] = LifeShot(photo, scan)
        }
    }

    /**
     * Why a slot's photo can't be used, or null when it's fine. The front has to pass every check.
     * From the side or behind a pet often isn't recognised as one, and a face close-up is meant to
     * crop the body, so those slots skip the checks that would always fail.
     */
    fun problem(angle: LifeAngle): PhotoIssue? {
        val issues = shots[angle]?.scan?.issues ?: return null
        val ignored = when (angle) {
            LifeAngle.FRONT -> emptySet<PhotoIssue>()
            LifeAngle.SIDE, LifeAngle.BACK -> setOf(PhotoIssue.NOT_A_PET)
            LifeAngle.FACE -> setOf(PhotoIssue.NOT_A_PET, PhotoIssue.NO_PET, PhotoIssue.CUT_OFF, PhotoIssue.TOO_SMALL, PhotoIssue.CROWDED)
        }
        return issues.firstOrNull { it !in ignored }
    }

    fun isScanning(angle: LifeAngle): Boolean = shots[angle]?.let { it.scan == null } == true
    fun isGood(angle: LifeAngle): Boolean = shots[angle]?.scan != null && problem(angle) == null

    val good: Int get() = LifeAngle.entries.count(::isGood)
    val scanning: Boolean get() = LifeAngle.entries.any(::isScanning)

    /** The next empty slot, in the order the design asks for them. */
    val next: LifeAngle? get() = LifeAngle.entries.firstOrNull { it !in shots }

    /** At least three good photos, the front among them. */
    val isReady: Boolean get() = good >= 3 && isGood(LifeAngle.FRONT) && !scanning

    /** What to hand to the 3D job: the body shots as cut-outs to send, the face to keep for show. */
    fun photos(): List<LifePhoto> = LifeAngle.entries.filter(::isGood).mapNotNull { angle ->
        val shot = shots[angle] ?: return@mapNotNull null
        LifePhoto(shot.cutout ?: shot.photo, send = angle != LifeAngle.FACE)
    }

    /** "2 of 4 · 1 more to go" */
    fun countText(name: String, state: PetModelState): String = when {
        state.isWorking -> "Photos locked while $name comes to life"
        state.isReady -> "$name is ready"
        scanning -> "Taking a good look…"
        LifeAngle.entries.any { problem(it) != null } -> "Retake the marked photo"
        shots.isNotEmpty() && !isGood(LifeAngle.FRONT) -> "Start with the front"
        good >= 4 -> "All 4 angles · best result"
        good == 3 -> "3 of 4 · a 4th angle makes motion smoother"
        else -> "$good of 4 · ${3 - good} more to go"
    }
}

/**
 * The photos screen (9a): a two by two grid of angles, the tips, the count, and the button that
 * starts it. [header] and [actions] differ between first run and the Avatar screen.
 */
@Composable
fun LifePhotosGrid(
    photos: LifePhotos,
    name: String,
    state: PetModelState,
    modifier: Modifier = Modifier,
    onOpen: (LifeAngle) -> Unit,
    header: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val colors = Opaw.colors
    val locked = state.isWorking || state.isReady
    Column(modifier) {
        header()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photos from 4 angles", style = opaw(30.sp, FontWeight.Black), color = colors.ink)
                Text(
                    "Motion clips are made from these. Take at least 3, all of the same pet.",
                    style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp), color = colors.muted,
                )
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in LifeAngle.entries.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (angle in row) {
                            LifeSlot(
                                angle, photos.shots[angle], photos.problem(angle),
                                isNext = !locked && photos.next == angle,
                                locked = locked,
                                modifier = Modifier.weight(1f),
                            ) { onOpen(angle) }
                        }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).fillMaxWidth().background(colors.chip, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.SPARKLE, size = 14.dp, weight = 2.4f, tint = colors.ink)
                Text("Good light · Standing up · Same pet each time", style = opaw(13.sp, FontWeight.Bold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                photos.countText(name, state),
                style = opaw(14.sp, FontWeight.Bold), color = colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp),
            )
        }
        actions()
    }
}

/** One angle's slot: empty with its hint, the next one to take in black, or the photo with a check. */
@Composable
private fun LifeSlot(angle: LifeAngle, shot: LifeShot?, problem: PhotoIssue?, isNext: Boolean, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Opaw.colors
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .pressable(enabled = !locked, onClick = onClick)
            .whiteCard(26.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (shot == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(48.dp).background(if (isNext) Ink else colors.chip, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(DesignIcon.CAMERA, size = 22.dp, tint = if (isNext) Color.White else colors.ghost)
                }
                Text(angle.label, style = opaw(17.sp, FontWeight.Black), color = if (isNext) colors.ink else colors.muted)
                Text(if (isNext) "Tap to take" else angle.hint, style = opaw(13.sp, if (isNext) FontWeight.ExtraBold else FontWeight.Medium), color = if (isNext) colors.ink else colors.ghost)
            }
            return@Box
        }
        val cutout = shot.cutout
        // The sticker pops in once the pet is lifted off the photo, as on the old scan step.
        val lifted by animateFloatAsState(if (cutout != null) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 220f), label = "lift")
        if (cutout == null || problem != null) {
            Image(remember(shot.photo) { shot.photo.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (shot.scan == null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 3.dp)
                }
            }
        } else {
            StickerPet(
                cutout,
                Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 30.dp).graphicsLayer { val s = 0.85f + 0.15f * lifted; scaleX = s; scaleY = s },
                edge = 3.dp, motion = null,
            )
        }
        Text(
            angle.label,
            style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Ink, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        if (shot.scan != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).background(if (problem == null) Ink else colors.alert, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(if (problem == null) DesignIcon.CHECK else DesignIcon.REFRESH, size = 13.dp, tint = Color.White) }
        }
        if (problem != null) {
            Text(
                problem.title,
                style = opaw(12.sp, FontWeight.ExtraBold), color = Ink, maxLines = 2, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * The angle camera (9a): the live view with white corners and the tip for this angle, then a
 * white panel with the four angles and the shutter. After each photo it moves to the next empty
 * angle, and closes when there are none left.
 */
@Composable
fun LifeCamera(photos: LifePhotos, start: LifeAngle, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var angle by remember { mutableStateOf(start) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    fun took(photo: Bitmap) {
        haptics.medium()
        photos.add(angle, photo)
        val next = photos.next
        if (next == null) onClose() else angle = next
    }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { CutoutService.load(context, uri)?.let(::took) }
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

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (granted) {
            AndroidView(
                factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Box(
                    Modifier.size(54.dp).pressable(onClick = onClose).shadow(10.dp, CircleShape).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.BACK, size = 22.dp, tint = Ink) }
                Text(
                    "${LifeAngle.entries.indexOf(angle) + 1} of 4 · ${angle.label}",
                    style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Ink, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Brackets(Modifier.padding(horizontal = 36.dp).padding(top = 40.dp, bottom = 86.dp).fillMaxSize())
                if (!granted) {
                    Text(
                        "Allow the camera, or pick photos from your library",
                        style = opaw(15.sp, FontWeight.Bold), color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 70.dp),
                    )
                }
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
                    Text(angle.tip, style = opaw(15.sp, FontWeight.ExtraBold), color = Ink, maxLines = 1)
                }
            }
            // Controls on a white sheet with rounded top corners, as on every Opaw camera.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .navigationBarsPadding()
                    .padding(top = 18.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in LifeAngle.entries) {
                        val on = option == angle
                        val done = option in photos.shots
                        Row(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(if (on) Ink else Color(0xFFF1F1F2))
                                .clickable { haptics.select(); angle = option },
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (done) Icon(DesignIcon.CHECK, size = 11.dp, tint = if (on) Color.White else Ink)
                            Text(option.label, style = opaw(14.sp, FontWeight.ExtraBold), color = if (on) Color.White else Ink.copy(alpha = if (done) 1f else 0.55f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            .size(92.dp)
                            .pressable {
                                if (!granted) return@pressable openLibrary()
                                if (isCapturing) return@pressable
                                isCapturing = true
                                scope.launch {
                                    capture(controller, ContextCompat.getMainExecutor(context))?.let(::took)
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
