package app.opaw.ui.home

import android.content.Intent
import app.opaw.ui.onboarding.OrangeButton
import app.opaw.services.RoomSlot
import app.opaw.services.RoomCatalog
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import app.opaw.core.CareNeed
import app.opaw.core.CareReport
import app.opaw.data.CareEvent
import app.opaw.services.CareRecorder
import app.opaw.ui.AppTab
import app.opaw.ui.LocalSelectTab
import app.opaw.ui.welcome.WelcomeOfferBar
import java.time.Instant
import java.time.ZoneId
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
import androidx.compose.foundation.layout.width
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
import app.opaw.ui.profile.JoinCodeDialog
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
import app.opaw.ui.components.Action
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

/**
 * Home is the pet's room (13a to 13c): the whole screen is its room, and the 3D pet explores it.
 * The top bar and tabs float over the room; under the bar sits one row with the pet's name, its
 * mood and the Today ring. Swipe for the next pet's room; the last page has room for one more.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val selectTab = LocalSelectTab.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val loaded by app.pets.collectAsStateWithLifecycle()
    val activeId by app.settings.activeDogId.collectAsStateWithLifecycle(null)
    var addSource by remember { mutableStateOf<PhotoSource?>(null) }
    var showJoin by remember { mutableStateOf(false) }
    // Still loading: draw nothing rather than the empty room.
    val all = loaded ?: return
    if (all.isEmpty()) {
        // A new account lands here (14k): the room is ready, and adding the first pet opens the photos.
        EmptyRoom(
            first = true, locked = false,
            onAdd = { scope.launch { app.settings.setAddingPet(true) } },
            onJoin = { showJoin = true },
        ) { navigator.push(Route.Paywall) }
        if (showJoin) JoinCodeDialog(app) { showJoin = false }
        return
    }

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

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val data = all.getOrNull(page)
            if (data != null) {
                PetRoomPage(
                    data,
                    openToday = { navigator.push(Route.Today(data.dog.id)) },
                    openDecorate = { navigator.push(Route.Decorate(data.dog.id)) },
                    openWalk = { selectTab(AppTab.ACTIVITY) },
                    openStudio = { navigator.push(Route.Studio(data.dog.id)) },
                )
            } else {
                EmptyRoom(first = false, locked = !Premium.isPremium, onAdd = { addSource = PhotoSource.CAMERA }, onJoin = null) { navigator.push(Route.Paywall) }
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
        // A new level unlocks pieces in the shop (16d).
        if (pager.currentPage != addPage) LevelUpCard(current.dog.id) { navigator.push(Route.Decorate(current.dog.id)) }
    }

    addSource?.let { source -> AddDogSheet(source, onDismiss = { addSource = null }) }
}

/** Night is from nine in the evening until six, and always in dark mode. */
@Composable
private fun isNight(now: Instant): Boolean {
    val hour = now.atZone(ZoneId.systemDefault()).hour
    return hour >= 21 || hour < 6 || Opaw.colors.surface != Color.White
}

/** "breakfast", "lunch" or "dinner", by the time of day. */
private fun mealName(hour: Int) = when {
    hour < 11 -> "breakfast"
    hour < 16 -> "lunch"
    else -> "dinner"
}

/** How the pet is, in a few words, and the colour of its dot: "Happy · fed and walked". */
private fun moodLine(report: CareReport, night: Boolean, meal: String): Pair<String, Color> = when (report.needs.firstOrNull()) {
    null -> if (night) "Asleep · all done today" to Color(0xFF34C759) else "Happy · fed and walked" to Action
    CareNeed.MEAL -> "Hungry · $meal is due" to Color(0xFFEC3013)
    CareNeed.MEDS -> "Waiting · meds are late" to Color(0xFFEC3013)
    CareNeed.WATER -> "Thirsty · the water bowl is low" to Color(0xFFEC3013)
    CareNeed.POTTY -> "Restless · needs to go out" to Color(0xFFEC3013)
    CareNeed.WALK -> "Bored · no walk yet today" to Color(0xFFF5B300)
}

// MARK: - The pet's room

@Composable
private fun PetRoomPage(data: DogData, openToday: () -> Unit, openDecorate: () -> Unit, openWalk: () -> Unit, openStudio: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val dog = data.dog
    val report = data.careReport(now)
    val mood = MoodEngine.mood(data.careSnapshot(now), now)
    val image = rememberPetImage(dog)
    // A rigged 3D pet roams the room; a flat one stands on the rug.
    val roams = app.petModels.file(dog.id, mood) != null && app.petModels.motion(dog.id, "rest") != null
    val night = isNight(now)
    val hour = now.atZone(ZoneId.systemDefault()).hour
    val meal = mealName(hour)

    fun log(kind: CareKind) {
        haptics.tap()
        scope.launch { app.toasts.show(CareRecorder.log(app, CareEvent(dogId = dog.id, kindRaw = kind.raw))) }
    }

    Box(Modifier.fillMaxSize()) {
        val room = app.paws.state(dog.id)
        PetRoom(
            night,
            mealDue = if (CareNeed.MEAL in report.needs) meal else null,
            pose = when {
                CareNeed.MEAL in report.needs -> RoomPose.WAIT
                night && report.needs.isEmpty() -> RoomPose.SLEEP
                else -> RoomPose.WALK
            },
            pieces = { room.piece(it) },
            actions = RoomActions(onBowl = { log(CareKind.MEAL) }, onWater = { log(CareKind.WATER) }, onDoor = openWalk, onBed = openToday),
        ) { view, standing -> RoomPet(data, view, standing) }
        // The name, mood and Today ring, under the top bar and above anywhere the pet can reach.
        val (line, dot) = moodLine(report, night, meal)
        val ink = if (night) Color.White else colors.ink
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 80.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tap the name and mood for the day's list (the Today ring lives there now).
            Column(Modifier.weight(1f).pressable(onClick = openToday), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dog.name, style = opaw(34.sp, FontWeight.Black).copy(letterSpacing = (-0.3).sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Text(line, style = opaw(13.sp, FontWeight.ExtraBold), color = ink.copy(alpha = 0.75f), maxLines = 1)
                }
            }
            // The Paws count, and the brush that opens Decorate (16a).
            PawsChip(room.paws, night, Modifier.pressable(onClick = openDecorate))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).pressable(onClick = openDecorate).background(if (night) Color.White.copy(alpha = 0.12f) else colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(DesignIcon.PENCIL, size = 18.dp, tint = if (night) Color.White else colors.ink) }
        }
        WelcomeOfferBar(Modifier.statusBarsPadding().padding(top = 148.dp).padding(horizontal = 24.dp))
    }
}

/** "3/5 Today" with a ring that fills as the day's care gets done. Opens the day's list. */
@Composable
private fun TodayRing(done: Int, total: Int, night: Boolean, onClick: () -> Unit) {
    val colors = Opaw.colors
    val track = if (night) Color.White.copy(alpha = 0.2f) else colors.track
    Row(
        Modifier
            .pressable(onClick = onClick)
            .then(if (night) Modifier else Modifier.floatingShadow())
            .background(if (night) Color.White.copy(alpha = 0.12f) else colors.surface, CircleShape)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val stroke = 4.5.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, inset, box, style = Stroke(stroke))
            drawArc(Action, -90f, 360f * done / total, false, inset, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column {
            Text("$done/$total", style = opaw(14.sp, FontWeight.Black), color = if (night) Color.White else colors.ink)
            Text("Today", style = opaw(11.sp, FontWeight.Bold), color = if (night) Color.White.copy(alpha = 0.7f) else colors.muted)
        }
    }
}

// MARK: - Room for a pet (14k)

/**
 * The room before there's a pet in it (14k), as in the design: the starter cloud bed, a dashed
 * spot on the rug with an orange +, "Your pet's room is ready" with 50 Paws, the welcome offer
 * counting down, then Add your first pet. At the end of the pages it's room for one more;
 * [locked] when the free plan already has its one pet.
 */
@Composable
private fun EmptyRoom(first: Boolean, locked: Boolean, onAdd: () -> Unit, onJoin: (() -> Unit)?, onPremium: () -> Unit) {
    val colors = Opaw.colors
    val night = Opaw.colors.surface != Color.White
    val ink = if (night) Color.White else colors.ink
    val add = if (locked) onPremium else onAdd
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = maxWidth.value / 390f
        PetRoom(
            night, pose = RoomPose.STILL, water = false,
            pieces = { if (it == RoomSlot.BED) "bed-cloud" else RoomCatalog.starter(it).id },
            actions = RoomActions({}, {}, {}, {}),
        ) { _, _ -> }
        // The dashed spot waiting on the rug.
        Box(
            Modifier.offset((109 * s).dp, (410 * s).dp).size((160 * s).dp, (190 * s).dp).drawBehind {
                val r = 80.dp.toPx() * s
                val b = 36.dp.toPx() * s
                val outline = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                            topLeft = androidx.compose.ui.geometry.CornerRadius(r), topRight = androidx.compose.ui.geometry.CornerRadius(r),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius(b), bottomLeft = androidx.compose.ui.geometry.CornerRadius(b),
                        ),
                    )
                }
                drawPath(outline, Color(0xFFCFCFD3), style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))))
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).pressable(onClick = add)
                    .shadow(12.dp, CircleShape, ambientColor = Action.copy(alpha = 0.3f), spotColor = Action.copy(alpha = 0.3f))
                    .background(Action, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(if (locked) DesignIcon.LOCK else DesignIcon.PLUS, size = 28.dp, weight = 3f, tint = Color.White) }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = if (first) 24.dp else 80.dp).padding(start = 24.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (first) "Your pet\u2019s room\nis ready" else if (locked) "Room for one more" else "Add a pet",
                style = opaw(30.sp, FontWeight.Black).copy(lineHeight = 32.sp), color = ink, modifier = Modifier.weight(1f),
            )
            // A new home starts with 50 Paws for its first pet.
            if (first) PawsChip(50, night)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WelcomeOfferBar(Modifier.padding(bottom = 12.dp))
            if (!first) {
                Text(
                    if (locked) "Free covers one pet. Premium adds as many as you like, each with their own room." else "One photo and they move into a room of their own.",
                    style = opaw(14.sp, FontWeight.SemiBold).copy(lineHeight = 20.sp), color = ink.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            OrangeButton(
                when {
                    locked -> "Try Premium free for 7 days"
                    first -> "Add your first pet"
                    else -> "Add a pet"
                },
                onClick = add,
            )
            if (onJoin != null) {
                Text(
                    "Have an invite code?", style = opaw(15.sp, FontWeight.ExtraBold), color = ink, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable(onClick = onJoin).padding(8.dp),
                )
            }
        }
    }
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

