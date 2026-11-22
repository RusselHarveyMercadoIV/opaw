package app.opaw.ui.life

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.opaw.services.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData

/** The angle photos for a pet that's already in the app, opened from the Avatar screen. */
@Composable
fun LifePhotosRoute(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val dog = rememberDogData(dogId)?.dog ?: return
    val context = LocalContext.current
    // The photos already taken for this pet (during setup or last time) fill the grid; retake any.
    val photos = remember(dogId) { LifePhotos(scope, LifePhotos.folder(context, dogId)) }
    LaunchedEffect(photos) {
        photos.restore()
        // Pets added before the angles were saved, or through Add pet: their picture is the front.
        if (LifeAngle.FRONT !in photos.shots) {
            withContext(Dispatchers.IO) { PhotoStore.load(context, dog.photoPath, maxDimension = 1600) }?.let { photos.add(LifeAngle.FRONT, it) }
        }
    }
    var camera by remember { mutableStateOf<LifeAngle?>(null) }
    val model = app.petModels.state(dogId)

    camera?.let { angle ->
        BackHandler { camera = null }
        LifeCamera(photos, angle) { camera = null }
        return
    }
    LifePhotosGrid(
        // A finished or failed model doesn't lock the grid here: this screen is for making a new one.
        photos, dog.name, if (model.isWorking) model else app.petModels.state(dogId).copy(stage = "none"),
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        onOpen = { camera = it },
        header = { ScreenHeader("Bring to life", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) }) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
            if (model.isWorking) {
                BlackPillButton("See progress") { navigator.pop(); navigator.push(Route.Life(dogId)) }
            } else {
                BlackPillButton("Bring ${dog.name} to life", enabled = photos.isReady && app.pack.isConfigured) {
                    if (!app.premium.isPremium) return@BlackPillButton navigator.push(Route.Paywall)
                    haptics.medium()
                    app.petModels.start(dogId, photos.photos(), dog.size)
                    navigator.pop()
                    navigator.push(Route.Life(dogId))
                }
            }
        }
    }
}

/** A pet coming to life: the checkpoints while it works, then the 3D reveal. */
@Composable
fun LifeRoute(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val dog = rememberDogData(dogId)?.dog ?: return
    val model = app.petModels.state(dogId)
    // Stay on the checkpoints when the pet becomes ready, until "Meet" is tapped.
    var meeting by remember { mutableStateOf(model.isReady) }
    val modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
    fun retake() {
        navigator.pop()
        navigator.push(Route.LifePhotos(dogId))
    }
    if (meeting && model.isReady) {
        LifeRevealScreen(dogId, dog.name, Modifier.fillMaxSize(), onBack = navigator::pop, onRetake = if (app.premium.isPremium) ::retake else null, doneTitle = "Back to ${dog.name}", onDone = navigator::pop)
    } else {
        LifeProgressScreen(
            dogId, dog.name, rememberPetImage(dog, 600), modifier,
            leaveTitle = "Back to ${dog.name}", onLeave = navigator::pop, onMeet = { meeting = true }, onRetake = ::retake,
        )
    }
}

/**
 * Shows the "Bringing Miso to life" toast at the top of this box, over every tab and screen,
 * until the pet has been met. Hidden on the screens the toast itself leads to.
 */
@Composable
fun BoxScope.LifeToastHost() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val dogs by app.repository.dogs.collectAsStateWithLifecycle(emptyList())
    val top = navigator.stack.lastOrNull()
    val covered = top is Route.Life || top is Route.LifePhotos || top is Route.Paywall
    val dog = if (covered) null else dogs.firstOrNull { app.petModels.showsToast(it.id) }
    // Keeps the last pet while the toast slides away.
    var last by remember { mutableStateOf(dog) }
    if (dog != null) last = dog
    AnimatedVisibility(
        dog != null,
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 8.dp),
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val shown = last ?: return@AnimatedVisibility
        LifeToast(shown.name, app.petModels.state(shown.id), rememberPetImage(shown, 300), onDismiss = { app.petModels.hideToast(shown.id) }) {
            navigator.push(Route.Life(shown.id))
        }
    }
}
