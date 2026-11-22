package app.opaw.ui.walks

import app.opaw.services.formatDate
import app.opaw.ui.rememberDogData
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.OpawApplication
import app.opaw.core.CareKind
import app.opaw.core.GeoPoint
import app.opaw.core.Geo
import app.opaw.core.OpawFormat
import app.opaw.core.WalkCalculator
import app.opaw.core.WalkStats
import app.opaw.data.CareEvent
import app.opaw.data.Dog
import app.opaw.data.DogData
import app.opaw.data.Walk
import app.opaw.data.WalkActivity
import app.opaw.data.WalkPin
import app.opaw.data.medal
import app.opaw.data.title
import app.opaw.data.walkTitle
import app.opaw.services.CareRecorder
import app.opaw.services.Notice
import app.opaw.services.WalkRecorder
import app.opaw.services.WidgetRefresher
import app.opaw.services.clock
import app.opaw.services.formatInstant
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.TabBarState
import app.opaw.ui.care.rememberNow
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.PetChip
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.StatColumn
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.ToastHost
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.rememberActiveDog
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private val oneDecimal = DecimalFormat("0.0")
private val upToOne = DecimalFormat("0.#")
private val twoDecimals = DecimalFormat("0.00")

/** The Walk tab: Activity (3f), a live walk (2c), and the summary after (3e). Mirrors `WalkTabView`. */
@Composable
fun WalkTab() {
    val (dogs, data) = rememberActiveDog()
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var activity by rememberSaveable { mutableStateOf(WalkActivity.WALK) }
    var finished by remember { mutableStateOf<WalkRecorder.FinishedWalk?>(null) }
    val recording = WalkRecorder.state != WalkRecorder.State.IDLE
    DisposableEffect(recording) {
        TabBarState.walkActive = recording
        onDispose { TabBarState.walkActive = false }
    }

    val done = finished
    when {
        done != null && data != null -> WalkSummary(done, data, activity) { finished = null }
        recording -> LiveWalk(data, activity) { finished = it }
        else -> ActivityScreen(dogs, data, activity, onActivity = { activity = it }) { scope.launch { app.settings.setActiveDog(it.id) } }
    }
}

// MARK: - Activity (3f)

private fun weekStart(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun DogData.weekKm(): Double {
    val zone = ZoneId.systemDefault()
    val start = weekStart(zone)
    return walkedKm(start.atStartOfDay(zone).toInstant(), start.plusDays(7).atStartOfDay(zone).toInstant())
}

@Composable
private fun ActivityScreen(dogs: List<Dog>, data: DogData?, activity: WalkActivity, onActivity: (WalkActivity) -> Unit, onSelectDog: (Dog) -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val colors = Opaw.colors
    var authorized by remember { mutableStateOf(WalkRecorder.isAuthorized(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        authorized = WalkRecorder.isAuthorized(context)
        asked = true
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(authorized) {
        WalkRecorder.warmUp(context)
        onDispose { WalkRecorder.coolDown(context) }
    }

    Box(Modifier.fillMaxSize().background(colors.cream)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 200.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { PetChip(data?.dog, dogs, onSelectDog) }
                Text("Activity", style = opaw(22.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.align(Alignment.Center))
                RoundIcon(DesignIcon.TROPHY, Modifier.align(Alignment.CenterEnd), onClick = { navigator.push(Route.Challenges) })
            }
            if (data != null) {
                GoalCard(data, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
                BarsCard(data, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
                RecordsCard(data, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val denied = asked && !authorized
            Box(
                Modifier.weight(1f).height(62.dp).pressable {
                    when {
                        authorized -> {
                            if (android.os.Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                            WalkRecorder.start(context, data?.dog?.name ?: "your dog")
                            haptics.medium()
                        }
                        denied -> context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        else -> ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }.background(Action, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (authorized) "Start ${activity.title.lowercase()}" else if (denied) "Turn on location in Settings" else "Allow location to start",
                    style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1,
                )
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier.height(62.dp).pressable { menu = true }.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
                        .background(colors.surface, CircleShape).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("${activity.title} ▾", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    WalkActivity.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title, style = opaw(15.sp, FontWeight.Bold)) }, onClick = { menu = false; onActivity(option) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val dog = data.dog
    val goal = maxOf(0.5, dog.traits.recommendedWeeklyKm)
    val done = data.weekKm()
    Row(modifier.fillMaxWidth().whiteCard().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        GoalRing(done / goal, oneDecimal.format(OpawFormat.longDistance(done)), "of ${upToOne.format(OpawFormat.longDistance(goal))} ${OpawFormat.distanceUnit}", Modifier.size(118.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Weekly goal", style = opaw(20.sp, FontWeight.Black), color = colors.ink)
            Text(goalText(data, goal, done), style = opaw(14.sp, FontWeight.Medium).copy(lineHeight = 19.sp), color = Color(0xFF888888))
        }
    }
}

private fun goalText(data: DogData, goal: Double, done: Double): String {
    val dog = data.dog
    val who = mutableListOf<String>()
    dog.birthDate?.let {
        val years = java.time.Period.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).years
        if (years > 0) who += "$years-year-old"
    }
    who += if (dog.breed.isEmpty()) "${dog.size.title.lowercase()} dog" else dog.breed
    var text = "Set for a ${who.joinToString(" ")}"
    dog.weightKg?.let { text += " at ${OpawFormat.weight(it)}" }
    text += "."
    val left = goal - done
    text += if (left > 0.05) " ${oneDecimal.format(OpawFormat.longDistance(left))} ${OpawFormat.distanceUnit} to go." else " Goal reached. Good dog!"
    return text
}

/** A thick goal ring with the number in the middle (118pt, 13pt stroke on #eee). */
@Composable
fun GoalRing(progress: Double, value: String, caption: String, modifier: Modifier) {
    val colors = Opaw.colors
    val shown by animateFloatAsState(progress.coerceIn(0.0, 1.0).toFloat(), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val line = size.width * 13 / 120
            val inset = line / 2 + size.width * 4 / 120
            val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(colors.hairline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(line))
            drawArc(colors.action, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(line, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = opaw(26.sp, FontWeight.Black), color = colors.ink)
            Text(caption, style = opaw(12.sp, FontWeight.SemiBold), color = colors.muted)
        }
    }
}

@Composable
private fun BarsCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = weekStart(zone)
    val days = (0..6).map { offset ->
        val day = start.plusDays(offset.toLong())
        Triple(formatDate(day, "EEEEE"), data.walkedKm(day.atStartOfDay(zone).toInstant()), day)
    }
    val dayMax = maxOf(0.5, days.maxOf { it.second })
    Column(modifier.fillMaxWidth().whiteCard().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { (_, km, day) ->
                val isToday = day == today
                val empty = day.isAfter(today) || km == 0.0
                Box(
                    Modifier.weight(1f).height(if (empty) 6.dp else maxOf(6.0, 100 * km / dayMax).dp)
                        .background(if (isToday) colors.action else if (empty) colors.track else Color(0xFFCFCFD2), RoundedCornerShape(8.dp)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { (label, _, day) ->
                Text(label, style = opaw(12.sp, FontWeight.Bold), color = if (day == today) colors.ink else colors.faint, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RecordsCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val longest = data.walks.maxOfOrNull { it.distanceMeters } ?: 0.0
    val fastest = data.walks.filter { it.distanceMeters >= 1000 }.mapNotNull { it.stats.paceMinutesPerKm }.minOrNull()
    Column(modifier.fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Personal records", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        for ((label, value) in listOf(
            "Longest walk" to (if (longest > 0) OpawFormat.distance(longest) else "–"),
            (if (OpawFormat.usesImperial) "Fastest mile" else "Fastest km") to (fastest?.let { OpawFormat.pace(it) } ?: "–"),
        )) {
            Hairline()
            Row(Modifier.padding(vertical = 11.dp)) {
                Text(label, style = opaw(15.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                Text(value, style = opaw(15.sp, FontWeight.Black), color = colors.ink)
            }
        }
    }
}

// MARK: - Live walk (2c)

/** Times the dog stopped for at least 15 seconds within a few metres: sniff stops. */
fun sniffStops(points: List<GeoPoint>): Int {
    var anchor = points.firstOrNull() ?: return 0
    var stops = 0
    var counted = false
    for (point in points.drop(1)) {
        val distance = Geo.distance(anchor, point)
        if (distance > 8) {
            anchor = point
            counted = false
        } else if (!counted && java.time.Duration.between(anchor.timestamp, point.timestamp).seconds >= 15) {
            stops += 1
            counted = true
        }
    }
    return stops
}

@Composable
private fun LiveWalk(data: DogData?, activity: WalkActivity, onFinish: (WalkRecorder.FinishedWalk) -> Unit) {
    val haptics = rememberHaptics()
    val colors = Opaw.colors
    var confirmStop by remember { mutableStateOf(false) }
    val paused = WalkRecorder.state == WalkRecorder.State.PAUSED
    val image = rememberPetImage(data?.dog, 400)
    var petMenu by remember { mutableStateOf(false) }
    // Ticks every second for the timer.
    val now by produceState(Instant.now()) { while (true) { delay(1000); value = Instant.now() } }

    fun togglePause() {
        if (paused) { WalkRecorder.resume(); haptics.medium() } else { WalkRecorder.pause(); haptics.tap() }
    }

    fun finish() {
        val result = WalkRecorder.finish()
        if (result != null) { haptics.success(); onFinish(result) } else haptics.error()
    }

    BackHandler { confirmStop = true }

    Box(Modifier.fillMaxSize()) {
        OpawMap(WalkRecorder.points, MapCamera.Follow, Modifier.fillMaxSize(), follow = WalkRecorder.lastLocation ?: WalkRecorder.points.lastOrNull(), bottomPadding = 300) { toScreen ->
            RouteMarkers(WalkRecorder.points, WalkRecorder.pins, toScreen)
            (WalkRecorder.lastLocation ?: WalkRecorder.points.lastOrNull())?.let { here ->
                toScreen(here.latitude, here.longitude)?.let { at ->
                    // The pet trotting along the route. Tap it to mark a pee or poop here.
                    val density = LocalDensity.current
                    Box(Modifier.offset { IntOffset((at.x - with(density) { 48.dp.toPx() }).roundToInt(), (at.y - with(density) { 96.dp.toPx() }).roundToInt()) }) {
                        StickerPet(image, Modifier.size(96.dp).pressable { petMenu = true }, edge = 4.dp, motion = LoopMotion.TROT)
                        DropdownMenu(petMenu, onDismissRequest = { petMenu = false }) {
                            DropdownMenuItem(text = { Text("Pee here") }, leadingIcon = { Icon(DesignIcon.PEE, size = 18.dp) },
                                onClick = { petMenu = false; WalkRecorder.dropPin(CareKind.PEE); haptics.tap() })
                            DropdownMenuItem(text = { Text("Poop here") }, leadingIcon = { Icon(DesignIcon.POOP, size = 18.dp) },
                                onClick = { petMenu = false; WalkRecorder.dropPin(CareKind.POOP); haptics.tap() })
                        }
                    }
                }
            }
        }
        // Recording chip.
        Row(
            Modifier.statusBarsPadding().padding(start = 20.dp, top = 10.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .background(Color.White, CircleShape).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paused) Box(Modifier.size(9.dp).background(colors.muted, CircleShape)) else PulseDot()
            Text(if (paused) "Paused" else walkTitle(WalkRecorder.startedAt ?: Instant.now(), activity), style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
        }
        // The sheet.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(15.dp, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                .background(Color.White, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .navigationBarsPadding()
                .height(330.dp)
                .padding(horizontal = 24.dp).padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(twoDecimals.format(WalkRecorder.stats.distanceMeters / 1000), style = opaw(80.sp, FontWeight.Black).copy(letterSpacing = (-2.4).sp, fontFeatureSettings = "tnum"), color = Ink, maxLines = 1)
            Text("kilometres", style = opaw(16.sp, FontWeight.SemiBold), color = Color(0xFF999999), modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 22.dp)) {
                StatColumn(OpawFormat.duration(WalkRecorder.elapsed(now)), "Time", Modifier.weight(1f), valueColor = Ink)
                StatColumn(OpawFormat.pace(WalkRecorder.stats.paceMinutesPerKm), "Pace /${OpawFormat.distanceUnit}", Modifier.weight(1f), valueColor = Ink)
                StatColumn("${sniffStops(WalkRecorder.points)}", "Sniff stops", Modifier.weight(1f), valueColor = Ink)
            }
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp).pressable(onClick = ::togglePause).background(Color(0xFFF1F1F2), CircleShape), contentAlignment = Alignment.Center) {
                    if (paused) PlayGlyph() else Icon(DesignIcon.PAUSE, size = 22.dp, tint = Ink)
                }
                HoldToFinishButton(64, Modifier.weight(1f), onTap = { confirmStop = true }, onFinish = ::finish)
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = colors.surface,
            title = { Text("Finish this walk?", style = opaw(19.sp, FontWeight.Black)) },
            text = { Text("You can also hold Finish to save straight away.", style = opaw(15.sp, FontWeight.Medium)) },
            confirmButton = { TextButton({ confirmStop = false; finish() }) { Text("Finish and save", color = colors.ink, style = opaw(15.sp, FontWeight.ExtraBold)) } },
            dismissButton = { TextButton({ confirmStop = false; WalkRecorder.cancel() }) { Text("Discard walk", color = colors.alert, style = opaw(15.sp, FontWeight.Bold)) } },
        )
    }
}

/** A play triangle, for Resume. */
@Composable
private fun PlayGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.15f)
            lineTo(size.width * 0.88f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.85f)
            close()
        }
        drawPath(path, Action)
    }
}

/** A dot that fades in and out, for "recording". */
@Composable
fun PulseDot(color: Color = Opaw.colors.alert, size: Int = 9) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(0.25f, tween(600))
            alpha.animateTo(1f, tween(600))
        }
    }
    Box(Modifier.size(size.dp).alpha(alpha.value).background(color, CircleShape))
}

/** The start dot and pee/poop pins on a route. */
@Composable
fun BoxScope.RouteMarkers(points: List<GeoPoint>, pins: List<WalkPin>, toScreen: (Double, Double) -> Offset?) {
    val density = LocalDensity.current
    fun place(lat: Double, lon: Double, sizeDp: Int): IntOffset? = toScreen(lat, lon)?.let {
        val half = with(density) { (sizeDp / 2).dp.toPx() }
        IntOffset((it.x - half).roundToInt(), (it.y - half).roundToInt())
    }
    points.firstOrNull()?.let { first ->
        place(first.latitude, first.longitude, 18)?.let { at ->
            // The white start circle with a thick black ring.
            Box(Modifier.offset { at }.size(18.dp).background(Color.White, CircleShape).border(4.dp, Action, CircleShape))
        }
    }
    pins.forEach { pin ->
        place(pin.latitude, pin.longitude, 34)?.let { at -> PinBadge(pin.kind, Modifier.offset { at }) }
    }
}

/** A pee (white) or poop (black) marker on a map. */
@Composable
fun PinBadge(kind: String, modifier: Modifier = Modifier) {
    val isPoop = kind == CareKind.POOP.raw
    Box(
        modifier.size(34.dp).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(if (isPoop) Action else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(if (isPoop) DesignIcon.POOP else DesignIcon.PEE, size = 17.dp, tint = if (isPoop) Color.White else Ink) }
}

/**
 * Finish walk (7e): hold for about a second while the fill sweeps across, with a tick as it goes.
 * Letting go early slides the fill back. A plain tap asks first, with the option to discard.
 */
@Composable
private fun HoldToFinishButton(height: Int, modifier: Modifier, onTap: () -> Unit, onFinish: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val duration = 900
    val pressScale by animateFloatAsState(if (holding) 0.97f else 1f, tween(120), label = "hold")

    Box(
        modifier
            .height(height.dp)
            .scale(pressScale)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(CircleShape)
            .background(Action)
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (done) return@awaitEachGesture
                    holding = true
                    val start = System.currentTimeMillis()
                    var ticks = 0
                    val job = scope.launch {
                        while (true) {
                            val value = ((System.currentTimeMillis() - start) / duration.toFloat()).coerceAtMost(1f)
                            progress.snapTo(value)
                            val step = (value * 6).toInt()
                            if (step > ticks && value < 1) { ticks = step; haptics.tick(value.toDouble()) }
                            if (value >= 1f) { done = true; holding = false; onFinish(); break }
                            delay(16)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    if (!done) {
                        holding = false
                        if (System.currentTimeMillis() - start < 200) onTap()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (progress.value > 0) {
            val density = LocalDensity.current
            val fill = maxOf(with(density) { height.dp.toPx() }, width * progress.value)
            Box(Modifier.fillMaxHeight().width(with(density) { fill.toDp() }).background(Color.White.copy(alpha = 0.22f), CircleShape))
        }
        Text(if (holding) "Keep holding" else "Hold to finish", style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

// MARK: - Summary (3e)

/** Walks already saved, by start time, so the summary never saves twice. */
private val savedStarts = mutableMapOf<Instant, Boolean>()

/** Shown after a walk ends. The walk is saved straight away; back closes, share shares. */
@Composable
private fun WalkSummary(result: WalkRecorder.FinishedWalk, data: DogData, activity: WalkActivity, onClose: () -> Unit) {
    val app = rememberApp()
    val haptics = rememberHaptics()
    val navigator = LocalNavigator.current
    var isRecord by remember { mutableStateOf(savedStarts[result.startedAt] ?: false) }
    var saved by remember { mutableStateOf(savedStarts.containsKey(result.startedAt)) }
    val stats = remember(result) { WalkCalculator.stats(result.points) }

    suspend fun save() {
        if (savedStarts.containsKey(result.startedAt)) return
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val best = data.walks.filter { it.startedAt >= monthStart }.maxOfOrNull { it.distanceMeters } ?: 0.0
        isRecord = stats.distanceMeters > 200 && stats.distanceMeters > best
        val walk = Walk.create(data.dog.id, result.startedAt, result.endedAt, result.points, result.pins).copy(activityRaw = activity.raw)
        try {
            app.repository.transaction {
                app.repository.saveWalk(walk)
                // A walk earns +1 Paw per 100 m (16a).
                app.paws.earn(data.dog.id, (stats.distanceMeters / 100).toInt(), walk.id)
                // Pins dropped on the walk count as care logs too.
                val caretaker = app.settings.caretakerName.first()
                for (pin in result.pins) {
                    CareKind.fromRaw(pin.kind)?.let { kind ->
                        app.repository.saveEvent(CareEvent(dogId = data.dog.id, kindRaw = kind.raw, date = pin.date, note = "On a walk", loggedBy = caretaker))
                    }
                }
            }
        } catch (error: Exception) {
            app.toasts.show(Notice(Notice.Kind.FAILED, "Couldn't save the walk", "It's still on this phone", actionTitle = "Retry") { app.scope.launch { save() } })
            return
        }
        savedStarts[result.startedAt] = isRecord
        saved = true
        haptics.success()
        WidgetRefresher.refresh(app)
        CareRecorder.refreshBadges(app, data.dog.id).firstOrNull()?.let { badge ->
            app.toasts.show(Notice(Notice.Kind.MILESTONE, badge.title, "${data.dog.name} · ${badge.detail}", medal = badge.medal, actionTitle = "View") {
                onClose()
                navigator.push(Route.Challenges)
            })
        }
    }
    LaunchedEffect(result) { save() }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        WalkStory(
            points = result.points,
            pins = result.pins,
            title = walkTitle(result.startedAt, activity),
            subtitle = storySubtitle(result.startedAt, data.dog, app),
            stats = stats,
            isRecord = isRecord,
            data = data,
            extraKm = if (saved) 0.0 else stats.distanceMeters / 1000,
            onBack = onClose,
        )
        // The summary covers the tab bar, so it shows toasts itself.
        ToastHost(bottom = 24.dp)
    }
}

/** A past walk, opened from Memories. */
@Composable
fun WalkDetailScreen(walkId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val walk by produceState<Walk?>(null, walkId) { value = app.repository.walk(walkId) }
    val w = walk ?: return
    val data = rememberDogData(w.dogId)
    WalkStory(
        points = w.route,
        pins = w.pins,
        title = w.title,
        subtitle = data?.let { storySubtitle(w.startedAt, it.dog, app) } ?: (formatInstant(w.startedAt, "d MMM yyyy") + ", " + clock(w.startedAt)),
        stats = w.stats,
        isRecord = false,
        data = data,
        extraKm = 0.0,
        onBack = navigator::pop,
    )
}

@Composable
private fun storySubtitle(start: Instant, dog: Dog, app: OpawApplication): String {
    val walker by app.settings.caretakerName.collectAsStateWithLifecycle("")
    val zone = ZoneId.systemDefault()
    val day = if (start.atZone(zone).toLocalDate() == LocalDate.now(zone)) "Today" else formatInstant(start, "EEE d MMM")
    val with = if (walker.isEmpty()) dog.name else "${dog.name} and $walker"
    return "$day ${clock(start)} · with $with"
}

/** The 3e layout: route map, back and share, then the sheet with the proud pet, stats and weekly progress. */
@Composable
private fun WalkStory(
    points: List<GeoPoint>,
    pins: List<WalkPin>,
    title: String,
    subtitle: String,
    stats: WalkStats,
    isRecord: Boolean,
    data: DogData?,
    extraKm: Double,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = Opaw.colors
    val image = rememberPetImage(data?.dog, 400)
    val shareText = "$title: ${OpawFormat.distance(stats.distanceMeters)} in ${OpawFormat.duration(stats.elapsedSeconds)} with ${data?.dog?.name ?: "my dog"}. Logged with Opaw."
    fun share() = context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), null))

    Column(Modifier.fillMaxSize().background(colors.cream).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            OpawMap(points, MapCamera.FitRoute, Modifier.fillMaxSize(), interactive = false) { toScreen -> RouteMarkers(points, pins, toScreen) }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp).padding(top = 14.dp)) {
                RoundIcon(DesignIcon.BACK, onClick = onBack)
                Spacer(Modifier.weight(1f))
                RoundIcon(DesignIcon.UPLOAD, onClick = ::share)
            }
        }
        Box(Modifier.offset(y = (-30).dp)) {
            Column(
                Modifier.fillMaxWidth().background(colors.cream, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 40.dp).navigationBarsPadding(),
            ) {
                if (isRecord) {
                    Row(
                        Modifier.padding(bottom = 10.dp).background(Action, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DesignIcon.SPARKLE, size = 13.dp, tint = Color.White)
                        Text("Longest walk this month", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
                    }
                }
                Text(title, style = opaw(30.sp, FontWeight.Black), color = colors.ink)
                Text(subtitle, style = opaw(15.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(end = 120.dp))
                StatsGrid(stats, pins, Modifier.padding(top = 16.dp))
                if (data != null) WeekProgress(data, extraKm, Modifier.padding(top = 12.dp))
            }
            StickerPet(image, Modifier.align(Alignment.TopEnd).padding(end = 18.dp).offset(y = (-92).dp).size(110.dp, 120.dp), edge = 5.dp, motion = LoopMotion.BOB, duration = 1.2)
        }
    }
}

@Composable
private fun StatsGrid(stats: WalkStats, pins: List<WalkPin>, modifier: Modifier) {
    val colors = Opaw.colors
    val pee = pins.count { it.kind == CareKind.PEE.raw }
    val poop = pins.count { it.kind == CareKind.POOP.raw }
    Column(modifier.fillMaxWidth().whiteCard().padding(vertical = 6.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(OpawFormat.distance(stats.distanceMeters), "Distance", Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
            StatCell(OpawFormat.duration(stats.elapsedSeconds), "Duration", Modifier.weight(1f))
        }
        Hairline()
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(OpawFormat.pace(stats.paceMinutesPerKm), "Pace /${OpawFormat.distanceUnit}", Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
            // "2 pee · 1 poop" with the words drawn small and gray, over "Toilet stops".
            Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    buildAnnotatedString {
                        append("$pee ")
                        withStyle(SpanStyle(fontSize = 14.sp, color = colors.muted)) { append("pee") }
                        append(" · $poop ")
                        withStyle(SpanStyle(fontSize = 14.sp, color = colors.muted)) { append("poop") }
                    },
                    style = opaw(26.sp, FontWeight.Black), color = colors.ink, maxLines = 1,
                )
                Text("Toilet stops", style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted)
            }
        }
    }
}

/** A value such as "2.41 km" with the unit drawn small and gray. */
@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    val colors = Opaw.colors
    val parts = value.split(" ", limit = 2)
    val unit = parts.getOrNull(1)?.takeIf { it.all(Char::isLetter) }
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            buildAnnotatedString {
                append(if (unit != null) parts[0] else value)
                if (unit != null) withStyle(SpanStyle(fontSize = 14.sp, color = colors.muted)) { append(" $unit") }
            },
            style = opaw(26.sp, FontWeight.Black), color = colors.ink, maxLines = 1,
        )
        Text(label, style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted)
    }
}

@Composable
private fun WeekProgress(data: DogData, extraKm: Double, modifier: Modifier) {
    val colors = Opaw.colors
    val total = data.weekKm() + extraKm
    val goal = maxOf(0.5, data.dog.traits.recommendedWeeklyKm)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(10.dp).background(colors.track, CircleShape)) {
            Box(Modifier.fillMaxWidth((total / goal).coerceIn(0.0, 1.0).toFloat()).height(10.dp).background(colors.action, CircleShape))
        }
        Text("${oneDecimal.format(OpawFormat.longDistance(total))} / ${upToOne.format(OpawFormat.longDistance(goal))} ${OpawFormat.distanceUnit} this week",
            style = opaw(15.sp, FontWeight.Bold), color = colors.ink, maxLines = 1)
    }
}
