package app.opaw.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.AvatarMood
import app.opaw.data.AvatarStatus
import app.opaw.services.CutoutService
import app.opaw.services.PhotoIssue
import app.opaw.services.PhotoStore
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.components.ProgressBar
import app.opaw.ui.onboarding.BringToLifeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Lets the owner preview each mood and generate realistic motion clips of their pet. Mirrors `AvatarStudioView`. */
@Composable
fun AvatarStudioScreen(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    val data = rememberDogData(dogId) ?: return
    val dog = data.dog
    var mood by remember { mutableStateOf(AvatarMood.HAPPY) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pack = app.pack
    val clips = remember(dog.id) { AvatarClipStore.directory(app, dog.id).apply { mkdirs() } }
    val model = app.petModels.state(dog.id)
    // The original photo is checked the same way as at capture, since Meshy needs a clear one.
    var issues by remember(dog.photoPath, dog.cutoutPath) { mutableStateOf<List<PhotoIssue>?>(null) }
    LaunchedEffect(dog.photoPath, dog.cutoutPath) {
        val photo = withContext(Dispatchers.IO) { PhotoStore.load(app, dog.photoPath, maxDimension = 1600) }
        issues = when {
            photo != null -> CutoutService.scan(photo).issues
            dog.cutoutPath != null -> emptyList()
            else -> listOf(PhotoIssue.NO_PET)
        }
    }

    // Polls the avatar worker while clips are being made.
    LaunchedEffect(dog.avatarStatus, dog.avatarJobId) {
        val job = dog.avatarJobId ?: return@LaunchedEffect
        while (dog.avatarStatus == AvatarStatus.GENERATING) {
            try {
                if (pack.downloadClips(job, clips) != null) {
                    app.repository.saveDog(dog.copy(avatarStatusRaw = AvatarStatus.READY.raw))
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                error = e.message
                app.repository.saveDog(dog.copy(avatarStatusRaw = AvatarStatus.FAILED.raw))
                return@LaunchedEffect
            }
            delay(15_000)
        }
    }

    fun request() {
        working = true
        error = null
        scope.launch {
            runCatching { pack.requestAvatarClips(dog) }
                .onSuccess { app.repository.saveDog(dog.copy(avatarJobId = it, avatarStatusRaw = AvatarStatus.GENERATING.raw)) }
                .onFailure { error = it.message }
            working = false
        }
    }

    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Avatar", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PetAvatar(dog, rememberPetImage(dog), mood, Modifier.fillMaxWidth().height(280.dp), sticker = 6.dp, interactive = true)
            if (model.isReady) {
                Text("Drag to spin ${dog.name} around", style = opaw(13.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarMood.entries.forEach { option -> ChoiceChip(option.raw.replaceFirstChar(Char::uppercase), option == mood) { mood = option } }
            }
            Text("3D pet", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 8.dp))
            when {
                model.isWorking -> Column(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.ink, strokeWidth = 2.dp)
                        Text("Bringing ${dog.name} to life…", style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
                        Text("${model.progress}%", style = opaw(14.sp, FontWeight.Bold), color = colors.muted)
                    }
                    ProgressBar(model.progress / 100f)
                    Text("${model.label}. This takes a few minutes, and you can leave this screen.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                }
                model.isReady -> Column(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesignIcon.CHECK, size = 18.dp, tint = colors.good)
                        Text("${dog.name} is alive in 3D", style = opaw(15.sp, FontWeight.Bold), color = colors.good)
                    }
                    Text("Walks when happy, runs when restless or proud, and rests the rest of the time.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                    BlackPillButton("Rebuild", enabled = app.premium.isPremium && issues?.isEmpty() == true, height = 52.dp, inverted = true, fontSize = 16) {
                        app.petModels.start(dog)
                    }
                }
                else -> {
                    BringToLifeCard(
                        name = dog.name,
                        issues = issues,
                        isPremium = app.premium.isPremium,
                        selected = false,
                        selectedText = "",
                        onBringToLife = { if (app.premium.isPremium) app.petModels.start(dog) else navigator.push(Route.Paywall) },
                        onRetake = null,
                        working = !pack.isConfigured,
                    )
                    model.error?.takeIf { model.stage == "failed" }?.let { Text(it, style = opaw(13.sp, FontWeight.Medium), color = colors.alert) }
                    if (!pack.isConfigured) Text("Needs the Opaw server. See docs/SETUP.md.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                    if (issues?.isNotEmpty() == true) {
                        Text("Change ${dog.name}'s photo in their profile to try again.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                    }
                }
            }
            Text(
                "We send ${dog.name}'s cutout to Meshy, which sculpts a textured 3D model and gives it a four-legged skeleton with walk and run animations.",
                style = opaw(13.sp, FontWeight.Medium).copy(lineHeight = 18.sp), color = colors.muted,
            )
            Text("Realistic motion", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (dog.avatarStatus) {
                    AvatarStatus.LIVING_PHOTO, AvatarStatus.FAILED -> {
                        BlackPillButton(if (dog.avatarStatus == AvatarStatus.FAILED) "Try again" else "Generate motion clips",
                            enabled = !working && pack.isConfigured && (dog.cutoutPath != null || dog.photoPath != null), height = 56.dp, onClick = ::request)
                        if (!pack.isConfigured) Text("Needs the Opaw server. See docs/SETUP.md.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                    }
                    AvatarStatus.GENERATING -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.ink, strokeWidth = 2.dp)
                        Text("Generating ${dog.name}'s clips…", style = opaw(15.sp, FontWeight.SemiBold), color = colors.ink)
                    }
                    AvatarStatus.READY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(DesignIcon.CHECK, size = 18.dp, tint = colors.good)
                            Text("Motion clips ready", style = opaw(15.sp, FontWeight.Bold), color = colors.good)
                        }
                        BlackPillButton("Regenerate", enabled = !working, height = 56.dp, inverted = true) {
                            clips.listFiles()?.forEach { it.delete() }
                            request()
                        }
                    }
                }
                error?.let { Text(it, style = opaw(13.sp, FontWeight.Medium), color = colors.alert) }
            }
            Text(
                "We send ${dog.name}'s cutout to the Opaw avatar worker, which uses the open-source Wan 2.2 model to make short clips of ${dog.name} wagging, sleeping, celebrating and more. It takes a few minutes and runs once.",
                style = opaw(13.sp, FontWeight.Medium).copy(lineHeight = 18.sp), color = colors.muted,
            )
        }
    }
}
