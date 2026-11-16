package app.opaw.ui.home

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.core.CareKind
import app.opaw.core.CareTier
import app.opaw.core.MoodEngine
import app.opaw.core.OpawFormat
import app.opaw.data.Dog
import app.opaw.data.DogData
import app.opaw.services.Premium
import app.opaw.services.clock
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.care.dashedCapsule
import app.opaw.ui.care.rememberNow
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.GhostCycle
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.StatColumn
import app.opaw.ui.components.floatingShadow
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.components.rememberPhoto
import app.opaw.ui.profile.AddDogSheet
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch
import java.io.File

/** How the avatar moves: a bouncy bob, a slow breath, or a droop. */
val CareTier.motion: LoopMotion
    get() = when (this) {
        CareTier.THRIVING -> LoopMotion.BOB
        CareTier.OKAY -> LoopMotion.BREATHE
        CareTier.NEEDS_YOU -> LoopMotion.DROOP
    }

/** Home (2a): the pet as a big living sticker. Swipe between pets; the last page adds a new one (2h). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val all by app.repository.allDogData.collectAsStateWithLifecycle(emptyList())
    val activeId by app.settings.activeDogId.collectAsStateWithLifecycle(null)
    var addSource by remember { mutableStateOf<PhotoSource?>(null) }
    if (all.isEmpty()) return

    val pager = rememberPagerState(initialPage = all.indexOfFirst { it.dog.id == activeId }.coerceAtLeast(0)) { all.size + 1 }
    val addPage = all.size
    LaunchedEffect(pager, all.size) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val dog = all.getOrNull(page)?.dog ?: return@collect
            if (dog.id != activeId) {
                app.settings.setActiveDog(dog.id)
                haptics.select()
            }
        }
    }
    val current = all.getOrNull(pager.currentPage) ?: all.firstOrNull { it.dog.id == activeId } ?: all.first()

    val dots: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            all.forEachIndexed { index, _ ->
                val selected = index == pager.currentPage
                Box(Modifier.size(if (selected) 22.dp else 8.dp, 8.dp).background(if (selected) Opaw.colors.ink else Color(0xFFCCCCCC), CircleShape))
            }
            if (pager.currentPage == addPage) {
                Box(Modifier.size(22.dp, 8.dp).background(Opaw.colors.ink, CircleShape))
            } else {
                Box(Modifier.padding(start = 2.dp).size(16.dp).pressable { scope.launch { pager.animateScrollToPage(addPage) } }.dashedCapsule(Opaw.colors.ghost, dash = 3f, gap = 2.5f))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val data = all.getOrNull(page)
            if (data != null) {
                PetPage(data, dots, openStudio = { navigator.push(Route.Studio(data.dog.id)) }, openProfile = { navigator.push(Route.PetProfile(data.dog.id)) }) { navigator.push(Route.Today(data.dog.id)) }
            } else {
                AddPetPage(dots, locked = !Premium.isPremium && all.isNotEmpty(), onAdd = { addSource = it }) { navigator.push(Route.Paywall) }
            }
        }
        // Top bar.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FamilyChip(all.map { it.dog }, current) { navigator.push(Route.FamilyTree(current.dog.id)) }
            Spacer(Modifier.weight(1f))
            if (pager.currentPage != addPage) {
                ShareAvatarButton(current.dog)
                TopCircle(onClick = { navigator.push(Route.Profile(current.dog.id)) }) { Icon(DesignIcon.SETTINGS, size = 23.dp) }
            }
        }
    }

    addSource?.let { source -> AddDogSheet(source, onDismiss = { addSource = null }) }
}

enum class PhotoSource { CAMERA, LIBRARY }

/** A 58pt white circle used in the Home top bar. */
@Composable
private fun TopCircle(size: Int = 58, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).pressable(onClick = onClick).floatingShadow().background(Opaw.colors.surface, CircleShape), contentAlignment = Alignment.Center) { content() }
}

/** Shares the pet's sticker image. */
@Composable
private fun ShareAvatarButton(dog: Dog) {
    val context = LocalContext.current
    TopCircle(onClick = {
        val path = dog.cutoutPath ?: dog.photoPath
        val send = Intent(Intent.ACTION_SEND)
        if (path != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.filesDir, path))
            send.setType(if (path.endsWith(".png")) "image/png" else "image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Meet ${dog.name} on Opaw.")
        }
        context.startActivity(Intent.createChooser(send, null))
    }) { Icon(DesignIcon.SHARE, size = 22.dp) }
}

/** The design's family chip: two faces, "Family" and a chevron. */
@Composable
private fun FamilyChip(dogs: List<Dog>, current: DogData, onClick: () -> Unit) {
    val colors = Opaw.colors
    val paths = dogs.take(2).map { it.cutoutPath ?: it.photoPath }.toMutableList()
    if (paths.size < 2) current.dog.relatives.firstOrNull()?.let { paths += it.photoPath }
    while (paths.size < 2) paths += null
    Row(
        Modifier.height(58.dp).pressable(onClick = onClick).floatingShadow().background(colors.surface, CircleShape).padding(start = 8.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
            paths.forEachIndexed { index, path -> MiniFace(rememberPhoto(path, 200), size = 42.dp, fill = if (index == 0) colors.chip else colors.track) }
        }
        Text("Family", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink)
        Icon(DesignIcon.CHEVRON_RIGHT, size = 16.dp)
    }
}

// MARK: - Pet page

@Composable
private fun PetPage(data: DogData, dots: @Composable () -> Unit, openStudio: () -> Unit, openProfile: () -> Unit, openToday: () -> Unit) {
    val colors = Opaw.colors
    val now = rememberNow()
    val dog = data.dog
    val report = data.careReport(now)
    val mood = MoodEngine.mood(data.careSnapshot(now), now)
    val image = rememberPetImage(dog)
    val app = rememberApp()
    // A rigged 3D pet roams the whole width, so it gets no side margins or bobbing of its own.
    val roams = app.petModels.file(dog.id, mood) != null && app.petModels.motion(dog.id, "rest") != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 92.dp, bottom = 120.dp).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The stage: the avatar and the sound it makes.
        Box(Modifier.fillMaxWidth().height(if (roams) 320.dp else 290.dp)) {
            PetAvatar(
                dog, image, mood,
                if (roams) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(horizontal = 78.dp).loopMotion(report.tier.motion),
                sticker = 7.dp,
                faded = report.tier == CareTier.NEEDS_YOU,
                onTap = openStudio,
            )
            OutlinedText(report.tier.sound, Modifier.align(Alignment.TopEnd).padding(end = 52.dp, top = 90.dp).loopMotion(LoopMotion.TAG_POP))
        }
        Column(Modifier.padding(horizontal = 36.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(dog.name, style = opaw(50.sp, FontWeight.Black).copy(letterSpacing = (-0.5).sp), color = colors.ink, maxLines = 1)
            dots()
            ChipRow(data, onClick = openProfile)
            Text("“${report.line}”", style = opaw(17.sp, FontWeight.Bold).copy(lineHeight = 23.sp), color = colors.ink, textAlign = TextAlign.Center)
        }
        val meals = data.count(CareKind.MEAL, now)
        val mealGoal = maxOf(1, data.scheduledMealHours.size)
        val meds = data.medsStatus(now).second
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp).fillMaxWidth().pressable(onClick = openToday).whiteCard().padding(vertical = 18.dp, horizontal = 12.dp)) {
            StatColumn("$meals/$mealGoal", "Meals", Modifier.weight(1f))
            StatColumn(OpawFormat.distance(data.walkedKm(now) * 1000), "Walked", Modifier.weight(1f))
            StatColumn(meds?.let { clock(it) } ?: "None", "Meds due", Modifier.weight(1f), valueColor = if (meds == null) colors.ink else colors.alert)
        }
    }
}

/** Breed, age and weight. Tapping them opens the pet's profile (2j). */
@Composable
private fun ChipRow(data: DogData, onClick: () -> Unit) {
    val dog = data.dog
    val chips = buildList {
        if (dog.breed.isNotEmpty()) add(dog.breed)
        data.ageDescription?.let { add(it) }
        dog.weightKg?.let { add(OpawFormat.weight(it)) }
        if (isEmpty()) add(dog.size.title)
    }
    Row(Modifier.pressable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            Text(
                chip, style = opaw(14.sp, FontWeight.ExtraBold), color = Opaw.colors.ink, maxLines = 1,
                modifier = Modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                    .background(Opaw.colors.surface, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Text with a thick white outline, like a sticker label. */
@Composable
fun OutlinedText(text: String, modifier: Modifier = Modifier, size: Int = 28) {
    val style = opaw(size.sp, FontWeight.Black)
    Box(modifier) {
        for ((dx, dy) in listOf(3 to 0, -3 to 0, 0 to 3, 0 to -3, 2 to 2, -2 to -2, 2 to -2, -2 to 2)) {
            Text(text, style = style, color = Color.White, modifier = Modifier.padding(start = (3 + dx).dp, top = (3 + dy).dp))
        }
        Text(text, style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.15f), androidx.compose.ui.geometry.Offset(0f, 12f), 10f)),
            color = Ink, modifier = Modifier.padding(start = 3.dp, top = 3.dp))
    }
}

// MARK: - Add a pet (2h)

@Composable
private fun AddPetPage(dots: @Composable () -> Unit, locked: Boolean, onAdd: (PhotoSource) -> Unit, onPremium: () -> Unit) {
    val colors = Opaw.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 92.dp, bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(290.dp)) {
            Box(Modifier.fillMaxSize().padding(horizontal = 78.dp).dashedRounded(Color(0xFFCFCFD3)))
            GhostCycle(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).size(190.dp, 210.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 66.dp).size(56.dp)
                    .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f)).background(Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(if (locked) DesignIcon.LOCK else DesignIcon.CAMERA, size = 24.dp, weight = if (locked) 2.6f else 2.4f, tint = Color.White) }
        }
        Column(Modifier.padding(horizontal = 36.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add a pet", style = opaw(40.sp, FontWeight.Black), color = colors.ink)
            dots()
            Text(
                if (locked) "Free covers one pet. Premium adds as many as you like, each with their own avatar."
                else "Take one photo of a dog, cat or bunny and they come to life next to your pet.",
                style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp), color = colors.muted, textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (locked) {
                BlackPillButton("Try Premium free for 7 days", height = 60.dp, onClick = onPremium)
                BlackPillButton("See what Premium adds", height = 60.dp, inverted = true, onClick = onPremium)
            } else {
                BlackPillButton("Take a photo", height = 60.dp) { onAdd(PhotoSource.CAMERA) }
                BlackPillButton("Choose from library", height = 60.dp, inverted = true) { onAdd(PhotoSource.LIBRARY) }
            }
        }
    }
}

private fun Modifier.dashedRounded(color: Color): Modifier = drawBehind {
    val stroke = 2.5f * density
    drawRoundRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(48 * density),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8 * density, 6 * density))),
    )
}
