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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Brush
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
import app.opaw.core.Temperament
import app.opaw.core.BreedCatalog
import app.opaw.core.BreedInfo
import app.opaw.core.CareKind
import app.opaw.core.DogSize
import app.opaw.core.OpawFormat
import app.opaw.data.Dog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.services.CutoutService
import app.opaw.ui.premium.OneTimeOffer
import app.opaw.ui.premium.PaywallScreen
import app.opaw.ui.care.MealTimesList
import app.opaw.ui.care.TrackGrid
import app.opaw.ui.care.TrackOption
import app.opaw.ui.life.LifeAngle
import app.opaw.ui.life.LifeCamera
import app.opaw.ui.life.LifePhotos
import app.opaw.ui.life.LifePhotosGrid
import app.opaw.ui.life.LifeProgressScreen
import app.opaw.ui.life.LifeRevealScreen
import app.opaw.ui.life.LifeToast
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.GhostCycle
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
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
import app.opaw.ui.profile.SetupHousehold
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class Step { PHOTOS, CAMERA, DETAILS, TEMPERAMENT, TRACK, HOUSEHOLD, PERMISSIONS }

/** The screen over the steps while a pet comes to life: its progress, or meeting it in 3D. */
private enum class LifeScreen { NONE, PROGRESS, REVEAL }

/**
 * Adding a pet (v5 10c to 10l), from "Add your first pet" on Home: photos from every side, the
 * camera, the quick check of the drawing, about the pet, how it plays, what to track, the
 * household and permissions, then meeting the pet in 3D and home. Clean white screens, one idea
 * per screen, and the pet itself on every step. "Bring to life" starts from the photos and keeps
 * going through the rest of setup, in the orange bar at the top.
 */
@Composable
fun OnboardingFlow(app: OpawApplication) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.PHOTOS) }
    var forward by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf(DogDraft()) }
    // Saved to the pet as they're taken, so "Bring to life" later reuses them instead of asking again.
    val photos = remember { LifePhotos(scope, LifePhotos.folder(context, draft.id)) }
    var cameraAngle by remember { mutableStateOf(LifeAngle.FRONT) }
    var lifeScreen by remember { mutableStateOf(LifeScreen.NONE) }
    var foundDog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var showOffer by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    // The paywall was opened from "Bring to life" on the photos, not at the end of setup.
    var lifePaywall by remember { mutableStateOf(false) }
    // Meeting the pet at the end of setup (10k) leads home, rather than back to the steps.
    var meetThenFinish by remember { mutableStateOf(false) }
    val offerShown by app.settings.premiumOfferShown.collectAsStateWithLifecycle(true)
    val model = app.petModels.state(draft.id)

    // The front photo is the pet's picture everywhere else in setup.
    val front = photos.shots[LifeAngle.FRONT]
    val capture = remember(front) {
        CaptureResult(front?.photo, front?.cutout, front?.scan?.guess, front?.scan?.segmentation, front?.scan?.issues)
    }
    // What to track starts from what Opaw was asked to help with before sign-in (14f).
    LaunchedEffect(Unit) {
        val focus = app.settings.careFocus.first()
        if (focus.isEmpty()) return@LaunchedEffect
        val kinds = buildList {
            if ("meals" in focus) add(CareKind.MEAL.raw)
            if ("water" in focus) addAll(listOf(CareKind.WATER.raw, CareKind.PEE.raw, CareKind.POOP.raw))
            if ("walks" in focus) add(TrackOption.WALK_KEY)
            if ("meds" in focus) addAll(listOf(CareKind.MEDS.raw, CareKind.VET.raw))
            add(CareKind.WEIGHT.raw)
        }
        draft = draft.copy(trackedKinds = kinds)
    }
    LaunchedEffect(front?.scan) {
        val scan = front?.scan ?: return@LaunchedEffect
        foundDog = scan.hasDog || scan.guess != null
        scan.guess?.let { guess ->
            if (draft.breed.isEmpty()) draft = draft.copy(breed = guess.breed.name, size = guess.breed.size, energy = guess.breed.energy)
        }
    }

    fun go(next: Step) {
        forward = next > step
        haptics.soft()
        step = next
    }

    fun startLife() {
        haptics.medium()
        app.petModels.start(draft.id, photos.photos(), draft.size)
        lifeScreen = LifeScreen.PROGRESS
    }

    /** "Bring to life" is Premium: free users see the paywall first. */
    fun bringToLife() {
        if (app.premium.isPremium) startLife() else { lifePaywall = true; showPaywall = true }
    }

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            draft.save(context, capture.original, capture.cutout)
            app.settings.setAddingPet(false)
            haptics.success()
        }
    }

    val previous = when (step) {
        // Welcome is the Google sign-in screen, before onboarding; there's no going back to it.
        Step.PHOTOS -> null
        Step.CAMERA -> Step.PHOTOS
        Step.DETAILS -> Step.PHOTOS
        Step.TEMPERAMENT -> Step.DETAILS
        Step.TRACK -> Step.TEMPERAMENT
        Step.HOUSEHOLD -> Step.TRACK
        Step.PERMISSIONS -> Step.HOUSEHOLD
    }
    BackHandler(enabled = previous != null) { previous?.let(::go) }

    // The toast rides along on every setup screen once the job has started.
    val showsToast = model.isStarted && lifeScreen == LifeScreen.NONE && step != Step.CAMERA
    val toastSpace by animateDpAsState(if (showsToast) 80.dp else 0.dp, label = "toast")
    val name = draft.displayName
    // The pet's face on the rail, in the toast and on the last steps.
    val face = capture.cutout ?: capture.original

    val ground = Setup.ground
    Box(Modifier.fillMaxSize().background(ground)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (forward) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = 300f)) { it / 7 * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.88f, stiffness = 300f)) { -it / 7 * direction } + fadeOut())
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding",
        ) { current ->
            val padded = Modifier.fillMaxSize().safeDrawingPadding().padding(top = toastSpace + 8.dp)
            when (current) {
                Step.PHOTOS -> LifePhotosGrid(
                    photos, name, model, padded,
                    onOpen = { cameraAngle = it; go(Step.CAMERA) },
                    // Back goes to Home, where "Add your first pet" waits; or join someone else's pet with a code.
                    header = {
                        SetupHeader(
                            photos.good, LifeAngle.entries.size, null,
                            onBack = if (model.isStarted) null else ({ scope.launch { app.settings.setAddingPet(false) } }),
                            right = if (model.isStarted) null else ({ HeaderPill("Join with a code") { showJoin = true } }),
                        )
                    },
                ) {
                    when {
                        model.isReady -> SetupFoot {
                            OrangeButton("Meet $name") { lifeScreen = LifeScreen.REVEAL }
                            SetupLink("Continue setup", color = Opaw.colors.ink) { go(Step.DETAILS) }
                        }
                        model.isStarted -> SetupFoot { OrangeButton("Continue setup") { go(Step.DETAILS) } }
                        // The 3D pet with Premium once there are enough photos, or one photo for the
                        // free sticker once the front has been looked at.
                        else -> PhotoActions(
                            name, app.premium.isPremium, photos,
                            onTake = { cameraAngle = it; go(Step.CAMERA) },
                            onLife = ::bringToLife,
                            onFree = if (front?.scan != null) ({ go(Step.DETAILS) }) else null,
                        )
                    }
                }
                Step.CAMERA -> LifeCamera(photos, cameraAngle) { go(Step.PHOTOS) }
                Step.DETAILS -> DetailsStep(padded, draft, { draft = it }, capture, foundDog, onBack = { go(Step.PHOTOS) }) { go(Step.TEMPERAMENT) }
                Step.TEMPERAMENT -> TemperamentStep(padded, draft, face, { draft = it }, onBack = { go(Step.DETAILS) }) { go(Step.TRACK) }
                Step.TRACK -> TrackStep(padded, draft, { draft = it }, onBack = { go(Step.TEMPERAMENT) }) { go(Step.HOUSEHOLD) }
                Step.HOUSEHOLD -> HouseholdStep(padded, name, draft, face, onBack = { go(Step.TRACK) }) { go(Step.PERMISSIONS) }
                Step.PERMISSIONS -> PermissionsStep(padded, name, face, onBack = { go(Step.HOUSEHOLD) }) {
                    when {
                        !app.premium.isPremium -> showPaywall = true
                        // A 3D pet that hasn't been met yet is met on the way home (10k).
                        model.isReady && !model.seen -> { meetThenFinish = true; lifeScreen = LifeScreen.REVEAL }
                        else -> finish()
                    }
                }
            }
        }
        AnimatedVisibility(
            showsToast,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 8.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        ) {
            LifeToast(name, model, capture.cutout ?: capture.original) {
                lifeScreen = if (model.isReady) LifeScreen.REVEAL else LifeScreen.PROGRESS
            }
        }
        // Progress and the 3D reveal sit over the steps, so setup is right where it was left.
        AnimatedContent(lifeScreen, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "life") { screen ->
            val over = Modifier.fillMaxSize().background(ground).safeDrawingPadding()
            when (screen) {
                LifeScreen.NONE -> Unit
                LifeScreen.PROGRESS -> {
                    BackHandler { lifeScreen = LifeScreen.NONE }
                    LifeProgressScreen(
                        draft.id, name, capture.cutout ?: capture.original, over,
                        leaveTitle = "Keep setting up while it works",
                        onLeave = {
                            lifeScreen = LifeScreen.NONE
                            if (step == Step.PHOTOS && model.isWorking) go(Step.DETAILS)
                        },
                        onMeet = { lifeScreen = LifeScreen.REVEAL },
                        onRetake = { lifeScreen = LifeScreen.NONE; go(Step.PHOTOS) },
                    )
                }
                LifeScreen.REVEAL -> {
                    BackHandler { lifeScreen = LifeScreen.NONE }
                    val temperament = draft.temperament ?: Temperament.guess(draft.energy)
                    LifeRevealScreen(
                        draft.id, name, Modifier.fillMaxSize(), note = temperament.homeNote(),
                        onBack = { lifeScreen = LifeScreen.NONE; meetThenFinish = false },
                    ) {
                        lifeScreen = LifeScreen.NONE
                        when {
                            meetThenFinish -> finish()
                            step == Step.PHOTOS -> go(Step.DETAILS)
                        }
                    }
                }
            }
        }
        // Join with a code (10l) sits over the photos, like the 3D screens.
        AnimatedVisibility(showJoin, enter = fadeIn(tween(250)) + slideInVertically { it / 8 }, exit = fadeOut(tween(200))) {
            BackHandler { showJoin = false }
            JoinCodeScreen(app, Modifier.fillMaxSize().background(ground).safeDrawingPadding()) { showJoin = false }
        }
    }

    // Then the paywall (5a), the one-time offer (5c) once, and home.
    if (showPaywall && lifePaywall) {
        // From "Bring to life": back to the photos either way, and it starts after a purchase.
        BackHandler { showPaywall = false; lifePaywall = false }
        PaywallScreen(name, capture.cutout ?: capture.original) { purchased ->
            showPaywall = false
            lifePaywall = false
            if (purchased) {
                scope.launch { app.settings.setPremiumOfferShown(true) }
                if (photos.isReady) startLife()
            }
        }
    } else if (showPaywall) {
        BackHandler { showPaywall = false; afterPaywall(app, offerShown, { showOffer = true }, ::finish) }
        PaywallScreen(name, capture.cutout ?: capture.original) { purchased ->
            showPaywall = false
            if (purchased) scope.launch { app.settings.setPremiumOfferShown(true) }
            afterPaywall(app, offerShown || purchased, { showOffer = true }, ::finish)
        }
    }
    if (showOffer) {
        OneTimeOffer(capture.cutout ?: capture.original) {
            showOffer = false
            finish()
        }
    }
}

private fun afterPaywall(app: OpawApplication, offerShown: Boolean, showOffer: () -> Unit, finish: () -> Unit) {
    if (app.premium.isPremium || offerShown) {
        finish()
    } else {
        app.scope.launch { app.settings.setPremiumOfferShown(true) }
        showOffer()
    }
}

// MARK: - 10c Photos

/**
 * The buttons under the photos (10c): take the next side, or once there are enough, bring the pet
 * to life in 3D (Premium). One photo still works for the free sticker.
 */
@Composable
private fun PhotoActions(
    name: String,
    isPremium: Boolean,
    photos: LifePhotos,
    onTake: (LifeAngle) -> Unit,
    onLife: () -> Unit,
    /** Null until the front photo has been looked at. */
    onFree: (() -> Unit)?,
) {
    SetupFoot {
        if (photos.isReady) {
            OrangeButton("Bring $name to life", icon = DesignIcon.SPARKLE, badge = if (isPremium) "3D" else "3D · Premium", onClick = onLife)
        } else {
            val retake = LifeAngle.entries.firstOrNull { photos.problem(it) != null }
            val angle = retake ?: photos.next ?: LifeAngle.FRONT
            OrangeButton(
                "${if (retake != null) "Retake" else "Take"} the ${angle.title.lowercase()}",
                enabled = !photos.scanning || photos.next != null,
                icon = DesignIcon.CAMERA,
            ) { onTake(angle) }
        }
        SetupLink("Use one photo instead · free sticker", enabled = onFree != null) { onFree?.invoke() }
    }
}

// MARK: - 10f About

private enum class Field(val title: String) { SPECIES("Species"), BREED("Breed"), BIRTHDAY("Birthday"), WEIGHT("Weight"), SEX("Sex") }

@Composable
private fun DetailsStep(
    modifier: Modifier,
    draft: DogDraft,
    onDraft: (DogDraft) -> Unit,
    capture: CaptureResult,
    foundDog: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Opaw.colors
    val focus = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    var field by remember { mutableStateOf<Field?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        if (draft.name.isEmpty()) runCatching { nameFocus.requestFocus() }
    }

    Column(modifier) {
        SetupHeader(1, 5, "About", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PetDisc(capture.cutout ?: capture.original, 96.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Their name", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
                    Box {
                        if (draft.name.isEmpty()) Text("Name", style = opaw(34.sp, FontWeight.Black), color = colors.ghost)
                        BasicTextField(
                            draft.name, { onDraft(draft.copy(name = it)) },
                            textStyle = opaw(34.sp, FontWeight.Black).copy(color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(Action),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).fillMaxWidth().setupCard().padding(horizontal = 18.dp, vertical = 2.dp)) {
                @Composable
                fun row(f: Field, value: String, detected: Boolean = false, last: Boolean = false) = DetailRow(f.title, value, detected, last) {
                    focus.clearFocus()
                    field = f
                }
                row(Field.SPECIES, draft.species.replaceFirstChar(Char::uppercase), detected = foundDog && draft.species == "dog")
                row(Field.BREED, draft.breed.ifEmpty { "Add" }, detected = draft.breed.isNotEmpty() && capture.guess?.breed?.name == draft.breed)
                row(Field.BIRTHDAY, birthdayText(draft))
                row(Field.WEIGHT, draft.weightKg?.let { OpawFormat.weight(it) } ?: "Add")
                row(Field.SEX, sexText(draft), last = true)
            }
            Text(
                "Breed and age set walk goals and food portions. Tap any row to change it.",
                style = opaw(14.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp),
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 26.dp).padding(top = 12.dp),
            )
        }
        SetupFoot { OrangeButton("Continue", enabled = draft.isValid, onClick = onNext) }
    }

    field?.let { FieldSheet(it, draft, onDraft) { field = null } }
}

@Composable
private fun DetailRow(label: String, value: String, detected: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = Opaw.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = opaw(15.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.width(86.dp))
            if (detected) {
                Text(
                    "Detected", style = opaw(11.sp, FontWeight.ExtraBold), color = Setup.deep,
                    modifier = Modifier.background(Setup.peach, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = opaw(15.sp, FontWeight.ExtraBold),
                color = if (value == "Add") colors.ghost else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(DesignIcon.CHEVRON_DOWN, size = 14.dp, weight = 3f, tint = colors.ghost)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Setup.line))
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
                .background(if (selected) Action else Setup.fill, RoundedCornerShape(18.dp))
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
        containerColor = Setup.ground,
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
                        selectedDayContainerColor = colors.action,
                        selectedDayContentColor = colors.onAction,
                        todayDateBorderColor = colors.ink,
                        todayContentColor = colors.ink,
                        selectedYearContainerColor = colors.action,
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

// MARK: - 10g Temperament

/** The few words under each temperament card. */
private val Temperament.short: String
    get() = when (this) {
        Temperament.PLAYFUL -> "Spins and zooms"
        Temperament.CALM -> "Strolls and sniffs"
        Temperament.CURIOUS -> "Comes up close"
        Temperament.SHY -> "Peeks at you"
        Temperament.GOOFY -> "Wobbly hops"
        Temperament.SLEEPY -> "Naps a lot"
    }

/**
 * How the pet plays (10g): six cards, each with the pet already acting the mood out, so owners
 * pick by recognising their pet, not by reading. The guess from their breed's energy is picked first.
 */
@Composable
private fun TemperamentStep(modifier: Modifier, draft: DogDraft, image: Bitmap?, onDraft: (DogDraft) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val guess = Temperament.guess(draft.energy)
    val current = draft.temperament ?: guess
    val name = draft.displayName
    Column(modifier) {
        SetupHeader(2, 5, "About", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SetupTitle(
                "How does $name play?",
                "Each one plays out. " + if (draft.breed.isNotEmpty()) {
                    "${draft.breed}s are ${draft.energy.title.lowercase()} energy, so we guessed ${guess.title}."
                } else {
                    "We guessed ${guess.title} from their energy."
                },
                Modifier.padding(top = 18.dp),
            )
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in Temperament.entries.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (option in row) {
                            val on = option == current
                            val shape = RoundedCornerShape(24.dp)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(154.dp)
                                    .pressable { haptics.select(); onDraft(draft.copy(temperament = option)) }
                                    .background(if (on) Setup.peach else Setup.soft, shape)
                                    .then(if (on) Modifier.border(3.dp, Action, shape) else Modifier)
                                    .clip(shape),
                            ) {
                                PlainPet(
                                    image,
                                    Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(76.dp, 80.dp).temperamentMotion(option, amplitude = 76f / 58f),
                                )
                                if (on) Tick(24.dp, Modifier.align(Alignment.TopEnd).padding(10.dp))
                                Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp, end = 8.dp)) {
                                    Text(option.title, style = opaw(16.sp, FontWeight.Black), color = colors.ink)
                                    Text(
                                        if (option == guess) "Our guess · ${option.short.lowercase()}" else option.short,
                                        style = opaw(12.sp, FontWeight.Bold), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SetupFoot {
            OrangeButton("Continue") {
                if (draft.temperament == null) onDraft(draft.copy(temperament = current))
                onNext()
            }
        }
    }
}

// MARK: - 10h Track

/** What to track (10h): the round care icons, orange when on and grey when off, then the meal times. */
@Composable
private fun TrackStep(modifier: Modifier, draft: DogDraft, onDraft: (DogDraft) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val tracksMeals = CareKind.MEAL.raw in draft.trackedKinds
    Column(modifier) {
        SetupHeader(3, 5, "Care", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SetupTitle("What should we track?", "You can change this any time in Care.", Modifier.padding(top = 18.dp))
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                TrackGrid(draft.trackedKinds, { onDraft(draft.copy(trackedKinds = it)) }, offFill = Setup.fill)
            }
            AnimatedVisibility(tracksMeals) {
                Box(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                    MealTimesList(draft.mealTimes, { onDraft(draft.copy(mealTimes = it)) }, draft.weightKg, draft.species, Modifier.setupCard())
                }
            }
        }
        SetupFoot { OrangeButton("Continue", enabled = draft.trackedKinds.isNotEmpty(), onClick = onNext) }
    }
}

// MARK: - 10i Household

@Composable
private fun HouseholdStep(modifier: Modifier, name: String, draft: DogDraft, face: Bitmap?, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier) {
        SetupHeader(4, 5, "Family", onBack = onBack)
        // The pet isn't saved yet: an invite shares it as set up so far, and the rest follows once it is.
        SetupHousehold(draft.id, name, face, Modifier.weight(1f), pet = { draft.makeDog() }, onNext = onNext)
    }
}

// MARK: - 10j Permissions

/**
 * Two quick permissions (10j), shown as what you'll get: the reminder the pet will send floats
 * over it, then the two switches. Both can be turned on later in Settings.
 */
@Composable
private fun PermissionsStep(modifier: Modifier, name: String, image: Bitmap?, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val colors = Opaw.colors
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
        SetupHeader(5, 5, "Last step", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 100.dp).size(180.dp).background(Setup.fill, CircleShape))
                PlainPet(image, Modifier.align(Alignment.TopCenter).padding(top = 102.dp).size(116.dp, 150.dp).hop(2200, 14.dp))
                // The reminder the pet will send.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .drift(3000)
                        .width(330.dp)
                        .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                        .background(colors.surface, RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).background(Brush.verticalGradient(listOf(Setup.glowTop, Setup.glowBottom)), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(DesignIcon.BELL, size = 22.dp, tint = Color.White) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Dinner in 10 minutes", style = opaw(14.sp, FontWeight.ExtraBold), color = colors.ink)
                        Text("$name is getting hungry", style = opaw(12.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("now", style = opaw(11.sp, FontWeight.Bold), color = colors.ghost)
                }
            }
            SetupTitle("Two quick permissions", "So $name can ask for dinner, and walks get mapped.")
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth().setupCard(24.dp).padding(horizontal = 16.dp)) {
                PermissionRow(DesignIcon.BELL, "Notifications", "Meals and meds, plus a nudge when $name feels left out", notificationsOn) { on ->
                    if (on && !notificationsAsked && Build.VERSION.SDK_INT >= 33) {
                        notificationsAsked = true
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openSettings()
                    }
                }
                PermissionRow(DesignIcon.LOCATION, "Location while walking", "Maps the route. Never shared without asking.", locationOn, last = true) { on ->
                    if (on && !locationAsked) {
                        locationAsked = true
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        openSettings()
                    }
                }
            }
        }
        SetupFoot { OrangeButton("Take $name home", onClick = onNext) }
    }
}

@Composable
private fun PermissionRow(icon: DesignIcon, title: String, detail: String, isOn: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = Opaw.colors
    Column {
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Setup.peach, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, size = 22.dp, tint = Action)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink)
                Text(detail, style = opaw(12.sp, FontWeight.SemiBold).copy(lineHeight = 16.sp), color = colors.muted)
            }
            DesignToggle(isOn, onChange)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Setup.line))
    }
}
