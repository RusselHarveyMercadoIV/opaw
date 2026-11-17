package app.opaw.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.opaw.core.DogSize
import app.opaw.core.EnergyLevel
import app.opaw.core.OpawFormat
import app.opaw.services.CutoutService
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.onboarding.BringToLifeCard
import app.opaw.ui.onboarding.ScanPhase
import app.opaw.ui.onboarding.ScanViewfinder
import app.opaw.ui.onboarding.rememberScanPhase
import app.opaw.ui.premium.PaywallScreen
import app.opaw.ui.rememberApp
import app.opaw.services.formatDate
import app.opaw.ui.care.PlainField
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.home.PhotoSource
import app.opaw.ui.onboarding.CaptureResult
import app.opaw.ui.onboarding.DogDraft
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Add another pet from Home (2h): the details, then a portrait that's cut out and checked for a breed
 * on device. Mirrors AddDogSheet, DogDetailsForm and AvatarCaptureStep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDogSheet(source: PhotoSource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    var draft by remember { mutableStateOf(DogDraft(trackedKinds = emptyList())) }
    var weightText by remember { mutableStateOf("") }
    var onPhoto by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf(CaptureResult()) }
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var wantsLife by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    val app = rememberApp()

    fun use(image: Bitmap) {
        capture = CaptureResult(original = image)
        processing = true
        message = null
        wantsLife = false
        scope.launch {
            val started = System.currentTimeMillis()
            val scan = CutoutService.scan(image)
            val remaining = 1600 - (System.currentTimeMillis() - started)
            if (remaining > 0) kotlinx.coroutines.delay(remaining)
            if (capture.original !== image) return@launch
            capture = CaptureResult(image, scan.segmentation?.cutout, scan.guess, scan.segmentation, scan.issues)
            processing = false
            if (scan.segmentation == null) message = "We couldn't find your dog in this photo. Try one where your dog fills more of the frame."
            scan.guess?.let { guess ->
                if (draft.breed.isEmpty()) draft = draft.copy(breed = guess.breed.name, size = guess.breed.size, energy = guess.breed.energy)
            }
        }
    }

    val cameraFile = remember { File(context.cacheDir, "add-pet.jpg") }
    val cameraUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { CutoutService.load(context, cameraUri)?.let(::use) }
    }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { scope.launch { CutoutService.load(context, it)?.let(::use) } }
    }
    fun open(from: PhotoSource) = when (from) {
        PhotoSource.CAMERA -> camera.launch(cameraUri)
        PhotoSource.LIBRARY -> library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(onPhoto) { if (onPhoto && capture.original == null) open(source) }

    fun finish() {
        if (saving) return
        saving = true
        val kg = weightText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }?.let { if (OpawFormat.usesImperial) it / OpawFormat.POUNDS_PER_KILOGRAM else it }
        scope.launch {
            val dog = draft.copy(weightKg = kg).save(context, capture.original, capture.cutout)
            // Bring to life needs a few more angles, so the photos screen opens next.
            if (wantsLife && app.premium.isPremium) navigator.push(Route.LifePhotos(dog.id))
            haptics.success()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.cream,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = opaw(16.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Text(if (onPhoto) draft.trimmedName else "New dog", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.padding(28.dp))
            }
            if (!onPhoto) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Section("Basics") {
                        PlainField(draft.name, { draft = draft.copy(name = it) }, "Name", opaw(16.sp, FontWeight.SemiBold), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words), placeholderColor = colors.ghost)
                        Hairline()
                        PlainField(draft.breed, { draft = draft.copy(breed = it) }, "Breed (optional)", opaw(16.sp, FontWeight.SemiBold), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words), placeholderColor = colors.ghost)
                    }
                    Section("Age and size") {
                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("I know their birthday", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                            DesignToggle(draft.hasBirthDate, { draft = draft.copy(hasBirthDate = it) })
                        }
                        if (draft.hasBirthDate) {
                            Hairline()
                            BirthdayRow(draft.birthDate) { draft = draft.copy(birthDate = it) }
                        }
                        Hairline()
                        Text("Size", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DogSize.entries.forEach { ChoiceChip(it.title, it == draft.size) { draft = draft.copy(size = it) } } }
                        Text("Energy", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EnergyLevel.entries.forEach { ChoiceChip(it.title, it == draft.energy) { draft = draft.copy(energy = it) } }
                        }
                        Hairline()
                        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Weight (${OpawFormat.weightUnit})", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                            PlainField(weightText, { weightText = it }, "Optional", opaw(16.sp, FontWeight.Bold), Modifier.padding(start = 8.dp).fillMaxWidth(0.4f),
                                KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholderColor = colors.ghost)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                BlackPillButton("Next", enabled = draft.isValid, height = 62.dp) { onPhoto = true }
            } else {
                Text(
                    "Now, a portrait\nof ${draft.trimmedName}.",
                    style = opaw(36.sp, FontWeight.Black).copy(lineHeight = 38.sp), color = colors.ink, modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Whole body, good light. We'll lift them out of the photo and spot the breed, right here on your phone.",
                    style = opaw(16.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(top = 12.dp),
                )
                val phase = rememberScanPhase(capture, processing)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (capture.original != null) {
                        ScanViewfinder(capture.original, capture.segmentation, capture.cutout, phase, Modifier.padding(top = 20.dp).fillMaxWidth().height(320.dp))
                    } else {
                        Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(320.dp).clip(RoundedCornerShape(30.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
                            Text("No photo yet", style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted)
                        }
                    }
                    message?.let { Text(it, style = opaw(14.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(top = 10.dp)) }
                    capture.guess?.takeIf { phase == ScanPhase.LIFTED }?.let { Text("Looks like a ${it.breed.name}", style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.padding(top = 10.dp)) }
                    if (capture.original != null && phase == ScanPhase.LIFTED) {
                        BringToLifeCard(
                            name = draft.trimmedName,
                            issues = capture.issues,
                            isPremium = app.premium.isPremium,
                            selected = wantsLife,
                            selectedText = "Next: a few more angles of ${draft.trimmedName}",
                            onBringToLife = { if (app.premium.isPremium) wantsLife = true else showPaywall = true },
                            onRetake = { open(PhotoSource.CAMERA) },
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BlackPillButton("Camera", Modifier.weight(1f), enabled = !processing, height = 56.dp, inverted = true) { open(PhotoSource.CAMERA) }
                    BlackPillButton(if (capture.original == null) "Choose photo" else "Change", Modifier.weight(1f), enabled = !processing, height = 56.dp, inverted = true) { open(PhotoSource.LIBRARY) }
                }
                Spacer(Modifier.height(16.dp))
                BlackPillButton("Add ${draft.trimmedName}", enabled = !processing && phase == ScanPhase.LIFTED && !saving, height = 62.dp, onClick = ::finish)
                if (capture.original == null) {
                    Text("Skip for now", style = opaw(15.sp, FontWeight.Medium), color = colors.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = ::finish).padding(12.dp))
                }
            }
        }
    }
    if (showPaywall) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPaywall = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            PaywallScreen(draft.trimmedName, capture.cutout ?: capture.original) { purchased ->
                showPaywall = false
                if (purchased) wantsLife = true
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = Opaw.colors
    Text(title, style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp))
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayRow(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val colors = Opaw.colors
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 14.dp)) {
        Text("Birthday", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
        Text(formatDate(date, "d MMM yyyy"), style = opaw(16.sp, FontWeight.Bold), color = colors.ink)
    }
    if (open) {
        val utc = java.time.ZoneOffset.UTC
        val today = LocalDate.now().atStartOfDay(utc).toInstant().toEpochMilli()
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(utc).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    state.selectedDateMillis?.let { onChange(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) }
                    open = false
                }) { Text("Done", color = colors.ink) }
            },
        ) { androidx.compose.material3.DatePicker(state, showModeToggle = false) }
    }
}
