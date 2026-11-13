package app.opaw.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.opaw.OpawApplication
import app.opaw.core.BreedCatalog
import app.opaw.core.BreedInfo
import app.opaw.core.CareKind
import app.opaw.core.DogSize
import app.opaw.core.OpawFormat
import app.opaw.data.Dog
import app.opaw.services.CutoutService
import app.opaw.ui.care.MealTimesCard
import app.opaw.ui.care.TrackGrid
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.GhostCycle
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.ProgressBar
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.RowIcon
import app.opaw.ui.components.SparkleChip
import app.opaw.ui.components.SpeechBubble
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.profile.HouseholdCards
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class Step { WELCOME, CAMERA, DETAILS, REVEAL, TRACK, HOUSEHOLD, PERMISSIONS }

/**
 * First run (4a to 4g): welcome, photo first, details with the detected breed, the avatar reveal,
 * what to track, the household and permissions, then home. Mirrors OnboardingFlow.swift.
 * The paywall that follows on iOS (5a, 5c) arrives with Play Billing in Phase 7.
 */
@Composable
fun OnboardingFlow(app: OpawApplication) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf(DogDraft()) }
    var capture by remember { mutableStateOf(CaptureResult()) }
    var isProcessing by remember { mutableStateOf(false) }
    var foundDog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun go(next: Step) {
        forward = next > step
        haptics.soft()
        step = next
    }

    /** Takes the new photo, moves on to the details, and builds the avatar in the background. */
    fun use(image: Bitmap) {
        capture = CaptureResult(original = image)
        isProcessing = true
        go(Step.DETAILS)
        scope.launch {
            val (cutout, hasDog, guess) = coroutineScope {
                val cutout = async { CutoutService.cutout(image) }
                val hasDog = async { CutoutService.containsDog(image) }
                val guess = async { CutoutService.guessBreed(image) }
                Triple(cutout.await(), hasDog.await(), guess.await())
            }
            if (capture.original !== image) return@launch
            capture = CaptureResult(image, cutout, guess)
            foundDog = hasDog || guess != null
            isProcessing = false
            if (guess != null && draft.breed.isEmpty()) {
                draft = draft.copy(breed = guess.breed.name, size = guess.breed.size, energy = guess.breed.energy)
            }
            haptics.success()
        }
    }

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            draft.save(context, capture.original, capture.cutout)
            haptics.success()
        }
    }

    val previous = when (step) {
        Step.WELCOME -> null
        Step.CAMERA -> Step.WELCOME
        Step.DETAILS -> Step.CAMERA
        Step.REVEAL -> Step.DETAILS
        Step.TRACK -> Step.REVEAL
        Step.HOUSEHOLD -> Step.TRACK
        Step.PERMISSIONS -> Step.HOUSEHOLD
    }
    BackHandler(enabled = previous != null) { previous?.let(::go) }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val direction = if (forward) 1 else -1
            (slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = 300f)) { it / 7 * direction } + fadeIn()) togetherWith
                (slideOutHorizontally(spring(dampingRatio = 0.88f, stiffness = 300f)) { -it / 7 * direction } + fadeOut())
        },
        modifier = Modifier.fillMaxSize().background(Opaw.colors.cream),
        label = "onboarding",
    ) { current ->
        val padded = Modifier.fillMaxSize().safeDrawingPadding()
        when (current) {
            Step.WELCOME -> WelcomeStep(padded) { go(Step.CAMERA) }
            Step.CAMERA -> CameraStep(onBack = { go(Step.WELCOME) }, onPhoto = ::use)
            Step.DETAILS -> DetailsStep(padded, draft, { draft = it }, capture, isProcessing, foundDog, onBack = { go(Step.CAMERA) }) { go(Step.REVEAL) }
            Step.REVEAL -> RevealStep(padded, draft.displayName, capture, isProcessing, onBack = { go(Step.DETAILS) }, onRetake = { go(Step.CAMERA) }) { go(Step.TRACK) }
            Step.TRACK -> TrackStep(padded, draft, { draft = it }, onBack = { go(Step.REVEAL) }) { go(Step.HOUSEHOLD) }
            Step.HOUSEHOLD -> HouseholdStep(padded, draft.displayName, app, onBack = { go(Step.TRACK) }) { go(Step.PERMISSIONS) }
            Step.PERMISSIONS -> PermissionsStep(padded, draft.displayName, capture.cutout ?: capture.original, onBack = { go(Step.HOUSEHOLD) }, onNext = ::finish)
        }
    }
}

// MARK: - Shared layout

/** Back button, six progress bars and "n of 6". */
@Composable
internal fun StepHeader(index: Int, inset: Int = 20, onBack: () -> Unit) {
    val colors = Opaw.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = inset.dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(DesignIcon.BACK, onClick = onBack)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (bar in 1..6) {
                val fill by androidx.compose.animation.animateColorAsState(if (bar <= index) Ink else Color(0xFFDCDCDF), tween(300), label = "bar")
                Box(Modifier.weight(1f).height(6.dp).background(fill, CircleShape))
            }
        }
        Text("$index of 6", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

/** A step's big title and gray subtitle. */
@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = opaw(30.sp, FontWeight.Black), color = Opaw.colors.ink)
        Text(subtitle, style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp), color = Opaw.colors.muted)
    }
}

/** The primary button pinned to the bottom, with an optional text button under it. */
@Composable
private fun BottomActions(
    title: String,
    enabled: Boolean = true,
    secondary: String? = null,
    secondaryColor: Color = Opaw.colors.ink,
    secondaryAction: (() -> Unit)? = null,
    action: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BlackPillButton(title, enabled = enabled, onClick = action)
        if (secondary != null && secondaryAction != null) {
            Text(secondary, style = opaw(15.sp, FontWeight.Bold), color = secondaryColor, modifier = Modifier.clickable(onClick = secondaryAction).padding(4.dp))
        }
    }
}

// MARK: - 4a Welcome

@Composable
private fun WelcomeStep(modifier: Modifier, onStart: () -> Unit) {
    val colors = Opaw.colors
    var restoreNote by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(top = 56.dp).height(330.dp)) {
            GhostCycle(Modifier.padding(top = 40.dp).size(230.dp, 260.dp).align(Alignment.TopCenter).loopMotion(LoopMotion.BOB, 3.2))
            SpeechBubble("hi! take my picture?", Modifier.align(Alignment.TopEnd).padding(end = 12.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your pet, alive on your phone", style = opaw(40.sp, FontWeight.Black).copy(lineHeight = 42.sp), color = colors.ink)
            Text(
                "Snap one photo and we turn it into an avatar that reacts to every walk, meal and cuddle you log.",
                style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp),
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(24.dp))
        BottomActions("Get started", secondary = "I already have an account", secondaryAction = {
            restoreNote = "Opaw keeps your pets on this phone, so there's nothing to sign in to. Tap Get started to add your pet."
        }, action = onStart)
    }
    restoreNote?.let { note ->
        AlertDialog(
            onDismissRequest = { restoreNote = null },
            title = { Text("Welcome back", style = opaw(20.sp, FontWeight.Black)) },
            text = { Text(note, style = opaw(15.sp, FontWeight.Medium)) },
            confirmButton = { TextButton({ restoreNote = null }) { Text("OK", color = colors.ink) } },
            containerColor = colors.surface,
        )
    }
}

// MARK: - 4c Details

private enum class Field(val title: String) { SPECIES("Species"), BREED("Breed"), BIRTHDAY("Birthday"), WEIGHT("Weight"), SEX("Sex") }

@Composable
private fun DetailsStep(
    modifier: Modifier,
    draft: DogDraft,
    onDraft: (DogDraft) -> Unit,
    capture: CaptureResult,
    isProcessing: Boolean,
    foundDog: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Opaw.colors
    val focus = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    var field by remember { mutableStateOf<Field?>(null) }
    // Fills slowly while the avatar is built, as in the design.
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (started) 0.9f else 0f, tween(6000), label = "build")
    LaunchedEffect(Unit) {
        started = true
        kotlinx.coroutines.delay(400)
        if (draft.name.isEmpty()) runCatching { nameFocus.requestFocus() }
    }

    val title = when {
        capture.guess != null -> {
            val name = capture.guess.breed.name
            "Looks like a${if (name.first().uppercaseChar() in "AEIOU") "n" else ""} $name"
        }
        isProcessing -> "Taking a good look…"
        else -> "Tell us about your pet"
    }

    Column(modifier) {
        StepHeader(2, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xFFE6E6E8))) {
                    capture.original?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = opaw(26.sp, FontWeight.Black), color = colors.ink)
                    Text(if (isProcessing) "Building the avatar while you finish" else "Avatar ready", style = opaw(14.sp, FontWeight.Medium), color = colors.muted)
                }
            }
            ProgressBar(if (isProcessing) progress else 1f, Modifier.padding(horizontal = 20.dp).padding(top = 18.dp))

            Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 2.dp)) {
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Name", style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted)
                    Box {
                        if (draft.name.isEmpty()) Text("Their name", style = opaw(26.sp, FontWeight.Black), color = colors.ghost)
                        BasicTextField(
                            draft.name, { onDraft(draft.copy(name = it)) },
                            textStyle = opaw(26.sp, FontWeight.Black).copy(color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.ink),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                        )
                    }
                }
                @Composable
                fun row(f: Field, value: String, detected: Boolean = false) = DetailRow(f.title, value, detected) {
                    focus.clearFocus()
                    field = f
                }
                row(Field.SPECIES, draft.species.replaceFirstChar(Char::uppercase), detected = foundDog && draft.species == "dog")
                row(Field.BREED, draft.breed.ifEmpty { "Add" }, detected = draft.breed.isNotEmpty() && capture.guess?.breed?.name == draft.breed)
                row(Field.BIRTHDAY, birthdayText(draft))
                row(Field.WEIGHT, draft.weightKg?.let { OpawFormat.weight(it) } ?: "Add")
                row(Field.SEX, sexText(draft))
            }
            Text(
                "Breed and age set walk goals and food portions. Tap any detected field to change it.",
                style = opaw(14.sp, FontWeight.Medium).copy(lineHeight = 19.sp),
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 12.dp),
            )
        }
        BottomActions("Continue", enabled = draft.isValid, action = onNext)
    }

    field?.let { FieldSheet(it, draft, onDraft) { field = null } }
}

@Composable
private fun DetailRow(label: String, value: String, detected: Boolean, onClick: () -> Unit) {
    val colors = Opaw.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(Modifier.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted)
            if (detected) {
                Spacer(Modifier.width(8.dp))
                SparkleChip("Detected", size = 11, iconSize = 11.dp)
            }
            Spacer(Modifier.weight(1f).width(8.dp))
            Text(
                value,
                style = opaw(16.sp, FontWeight.ExtraBold),
                color = if (value == "Add") colors.ghost else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(DesignIcon.CHEVRON_DOWN, size = 16.dp, tint = colors.ghost)
        }
    }
}

private fun birthdayText(draft: DogDraft): String {
    if (!draft.hasBirthDate) return "Add"
    val years = Dog.age(draft.birthInstant).toInt()
    val age = when {
        years < 1 -> "puppy"
        years == 1 -> "1 yr"
        else -> "$years yrs"
    }
    return "${draft.birthDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))} · $age"
}

private fun sexText(draft: DogDraft): String {
    if (draft.sex.isEmpty()) return "Add"
    val fixed = if (draft.sex == "female") "spayed" else "neutered"
    val sex = draft.sex.replaceFirstChar(Char::uppercase)
    return if (draft.isNeutered) "$sex · $fixed" else sex
}

/** Changes one detail from 4c. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldSheet(field: Field, draft: DogDraft, onDraft: (DogDraft) -> Unit, onDismiss: () -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    var weightText by remember {
        mutableStateOf(draft.weightKg?.let { kg -> OpawFormat.weightValue(kg).let { v -> if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", v) } }.orEmpty())
    }
    val today = LocalDate.now()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = draft.birthDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )

    fun save() {
        when (field) {
            Field.WEIGHT -> {
                val normalized = weightText.replace(",", ".").trim()
                val value = normalized.toDoubleOrNull()
                if (value != null && value > 0) {
                    val kg = if (OpawFormat.usesImperial) value / OpawFormat.POUNDS_PER_KILOGRAM else value
                    var next = draft.copy(weightKg = kg)
                    if (draft.breed.isEmpty() || BreedCatalog.lookup(draft.breed) == null) {
                        next = next.copy(size = if (kg < 10) DogSize.SMALL else if (kg < 25) DogSize.MEDIUM else if (kg < 45) DogSize.LARGE else DogSize.GIANT)
                    }
                    onDraft(next)
                } else if (normalized.isEmpty()) {
                    onDraft(draft.copy(weightKg = null))
                }
            }
            Field.BIRTHDAY -> dateState.selectedDateMillis?.let {
                onDraft(draft.copy(hasBirthDate = true, birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
            }
            else -> Unit
        }
    }

    @Composable
    fun choice(title: String, selected: Boolean, action: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .pressable { haptics.select(); action() }
                .background(if (selected) Ink else colors.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = opaw(17.sp, FontWeight.ExtraBold), color = if (selected) Color.White else colors.ink, modifier = Modifier.weight(1f))
            if (selected) Icon(DesignIcon.CHECK, size = 14.dp, tint = Color.White)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { save(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = field == Field.BREED || field == Field.BIRTHDAY),
        containerColor = colors.cream,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.title, style = opaw(22.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                Text("Done", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.clickable { save(); onDismiss() }.padding(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            when (field) {
                Field.SPECIES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (species in listOf("dog", "cat", "bunny")) {
                        choice(species.replaceFirstChar(Char::uppercase), draft.species == species) { onDraft(draft.copy(species = species)) }
                    }
                }
                Field.BREED -> BreedSearch(draft.breed, { onDraft(draft.copy(breed = it)) }) { info ->
                    onDraft(draft.copy(breed = info.name, size = info.size, energy = info.energy))
                    onDismiss()
                }
                Field.BIRTHDAY -> DatePicker(
                    dateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = colors.surface,
                        selectedDayContainerColor = colors.ink,
                        selectedDayContentColor = colors.onInk,
                        todayDateBorderColor = colors.ink,
                        todayContentColor = colors.ink,
                        selectedYearContainerColor = colors.ink,
                    ),
                    modifier = Modifier.whiteCard(),
                )
                Field.WEIGHT -> Row(
                    Modifier.fillMaxWidth().height(72.dp).whiteCard().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (weightText.isEmpty()) Text("0", style = opaw(34.sp, FontWeight.Black), color = colors.ghost)
                        BasicTextField(
                            weightText, { weightText = it },
                            textStyle = opaw(34.sp, FontWeight.Black).copy(color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(OpawFormat.weightUnit, style = opaw(20.sp, FontWeight.ExtraBold), color = colors.muted)
                }
                Field.SEX -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choice("Female", draft.sex == "female") { onDraft(draft.copy(sex = "female")) }
                    choice("Male", draft.sex == "male") { onDraft(draft.copy(sex = "male")) }
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().height(60.dp).whiteCard(18.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (draft.sex == "male") "Neutered" else "Spayed", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
                        DesignToggle(draft.isNeutered, { onDraft(draft.copy(isNeutered = it)) })
                    }
                }
            }
        }
    }
}

/** Type a breed, with suggestions from the catalog. */
@Composable
private fun BreedSearch(breed: String, onBreed: (String) -> Unit, onPick: (BreedInfo) -> Unit) {
    val colors = Opaw.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).whiteCard(18.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (breed.isEmpty()) Text("Breed or mix", style = opaw(20.sp, FontWeight.ExtraBold), color = colors.ghost)
            BasicTextField(
                breed, onBreed,
                textStyle = opaw(20.sp, FontWeight.ExtraBold).copy(color = colors.ink),
                singleLine = true,
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(Modifier.height(360.dp).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            BreedCatalog.suggestions(breed, limit = 12).forEach { info ->
                Row(Modifier.fillMaxWidth().clickable { onPick(info) }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(info.name, style = opaw(16.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
                    Text(info.size.title, style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            }
        }
    }
}

// MARK: - 4d Reveal

@Composable
private fun RevealStep(
    modifier: Modifier,
    name: String,
    capture: CaptureResult,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Opaw.colors
    val image = capture.cutout ?: capture.original
    Column(modifier) {
        StepHeader(3, onBack = onBack)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(300.dp)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).size(230.dp, 250.dp), contentAlignment = Alignment.Center) {
                // The avatar pops in when the cutout is ready.
                val shown by animateFloatAsState(if (isProcessing) 0f else 1f, spring(dampingRatio = 0.75f, stiffness = 200f), label = "reveal")
                if (isProcessing) CircularProgressIndicator(color = colors.ink)
                StickerPet(
                    if (isProcessing) null else image,
                    Modifier.fillMaxSize().graphicsLayer { val s = 0.7f + 0.3f * shown; scaleX = s; scaleY = s; alpha = shown },
                    edge = 5.dp,
                    motion = LoopMotion.BOB,
                    duration = 3.2,
                )
            }
            SpeechBubble("is that me?!", Modifier.padding(start = 218.dp, top = 6.dp).alpha(if (isProcessing) 0f else 1f))
            Box(Modifier.padding(start = 24.dp, top = 14.dp)) {
                if (isProcessing) {
                    Row(
                        Modifier.background(Ink, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("Building avatar", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
                    }
                } else {
                    SparkleChip("Avatar ready")
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Say hi to $name", style = opaw(30.sp, FontWeight.Black), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$name's mood follows the care you log.", style = opaw(15.sp, FontWeight.Medium), color = colors.muted, textAlign = TextAlign.Center)
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((label, motion, duration) in listOf(Triple("Walked", LoopMotion.BOB, 1.4), Triple("Fed", LoopMotion.BREATHE, 2.6), Triple("Ignored", LoopMotion.DROOP, 3.0))) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(108.dp).whiteCard(24.dp), contentAlignment = Alignment.Center) {
                        StickerPet(
                            if (isProcessing) null else image,
                            Modifier.size(76.dp, 84.dp).alpha(if (motion == LoopMotion.DROOP) 0.6f else 1f),
                            edge = 3.dp,
                            motion = motion,
                            duration = duration,
                            desaturate = motion == LoopMotion.DROOP,
                        )
                    }
                    Text(label, style = opaw(13.sp, FontWeight.Bold), color = colors.ink)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(64.dp).pressable(onClick = onRetake).shadow(10.dp, CircleShape).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(DesignIcon.REFRESH, size = 22.dp, tint = Ink) }
            BlackPillButton("Looks like $name", Modifier.weight(1f), enabled = !isProcessing, onClick = onNext)
        }
    }
}

// MARK: - 4e Track

@Composable
private fun TrackStep(modifier: Modifier, draft: DogDraft, onDraft: (DogDraft) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val tracksMeals = CareKind.MEAL.raw in draft.trackedKinds
    Column(modifier) {
        StepHeader(4, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            StepTitle("What should we track?", "You can change this any time in Care.")
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                TrackGrid(draft.trackedKinds, { onDraft(draft.copy(trackedKinds = it)) })
            }
            AnimatedVisibility(tracksMeals) {
                Box(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                    MealTimesCard(draft.mealTimes, { onDraft(draft.copy(mealTimes = it)) }, draft.weightKg, draft.species)
                }
            }
        }
        BottomActions("Continue", enabled = draft.trackedKinds.isNotEmpty(), action = onNext)
    }
}

// MARK: - 4f Household

@Composable
private fun HouseholdStep(modifier: Modifier, name: String, app: OpawApplication, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier) {
        StepHeader(5, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            StepTitle("Who else looks after $name?", "Everyone logs to the same pet, so meals never get doubled.")
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) { HouseholdCards(name, app.settings) }
        }
        BottomActions("Continue", secondary = "Skip for now", secondaryAction = onNext, action = onNext)
    }
}

// MARK: - 4g Permissions

@Composable
private fun PermissionsStep(modifier: Modifier, name: String, image: Bitmap?, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun notificationsAllowed() = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    fun locationAllowed() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    var notificationsOn by remember { mutableStateOf(notificationsAllowed()) }
    var locationOn by remember { mutableStateOf(locationAllowed()) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    var locationAsked by rememberSaveable { mutableStateOf(false) }

    // Re-check when coming back from system settings.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = notificationsAllowed()
                locationOn = locationAllowed()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = it }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locationOn = locationAllowed() }
    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    Column(modifier) {
        StepHeader(6, onBack = onBack)
        StepTitle("Two quick permissions", "So $name can ask for dinner and walks get tracked.")
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp)) {
            PermissionRow(DesignIcon.BELL, "Notifications", "Meal and meds reminders, plus a nudge when $name feels left out", notificationsOn, first = true) { on ->
                if (on && !notificationsAsked && Build.VERSION.SDK_INT >= 33) {
                    notificationsAsked = true
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openSettings()
                }
            }
            PermissionRow(DesignIcon.LOCATION, "Location while walking", "Maps your walk route. Never shared without asking.", locationOn) { on ->
                if (on && !locationAsked) {
                    locationAsked = true
                    askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    openSettings()
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(200.dp).padding(bottom = 16.dp)) {
            StickerPet(image, Modifier.padding(start = 26.dp, top = 30.dp).size(150.dp, 170.dp), edge = 5.dp, motion = LoopMotion.BREATHE, duration = 2.6)
            SpeechBubble("don’t forget my dinner", Modifier.padding(start = 168.dp, top = 20.dp))
        }
        BottomActions("Meet $name at home", action = onNext)
    }
}

@Composable
private fun PermissionRow(icon: DesignIcon, title: String, detail: String, isOn: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = Opaw.colors
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon, size = 44.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                Text(detail, style = opaw(13.sp, FontWeight.Medium).copy(lineHeight = 17.sp), color = colors.muted)
            }
            DesignToggle(isOn, onChange)
        }
    }
}
