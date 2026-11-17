package app.opaw.ui.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.CareKind
import app.opaw.data.CareEvent
import app.opaw.data.CareTrack
import app.opaw.data.DogData
import app.opaw.data.TrackLogStyle
import app.opaw.data.loggerName
import app.opaw.data.points
import app.opaw.data.shownTracks
import app.opaw.data.summary
import app.opaw.services.CareRecorder
import app.opaw.services.CareToast
import app.opaw.services.clock
import app.opaw.services.clockColumnWidth
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.rememberActiveDog
import app.opaw.ui.rememberApp
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.PetChip
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.onHold
import app.opaw.ui.components.popOnChange
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant

/** The current time, updating every minute, like `TimelineView(.periodic(from: .now, by: 60))`. */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Instant.now()
        }
    }
    return now
}

/** A log waiting for "Log another?" because it looks like a repeat. */
class PendingLog(val message: String, val perform: () -> Unit)

/** A log waiting for "Delete this log?" after it was held. */
data class PendingDelete(val id: String, val time: String, val text: String, val points: Int)

/** Care (3a): the streak, one-tap log circles, and who's been caring today. */
@Composable
fun CareScreen() {
    val app = rememberApp()
    val (dogs, data) = rememberActiveDog()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val now = rememberNow()

    var showPoop by remember { mutableStateOf(false) }
    var detailKind by remember { mutableStateOf<CareKind?>(null) }
    var showEditTracks by remember { mutableStateOf(false) }
    var amountTrack by remember { mutableStateOf<CareTrack?>(null) }
    var pendingLog by remember { mutableStateOf<PendingLog?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    val data0 = data ?: return
    val dog = data0.dog

    fun record(event: CareEvent) {
        scope.launch { app.toasts.show(CareRecorder.log(app, event)) }
    }

    /** Logs straight away, or first asks "Log another?" when it looks like a repeat or more than planned. */
    fun confirmIfRepeat(kind: CareKind, trackId: String? = null, title: String, perform: () -> Unit) {
        val message = CareRecorder.repeatWarning(kind, trackId, title, data0)
        if (message != null) {
            haptics.warning()
            pendingLog = PendingLog(message, perform)
        } else {
            perform()
        }
    }

    fun customEvent(track: CareTrack) = CareEvent(dogId = dog.id, kindRaw = CareKind.CUSTOM.raw, trackId = track.trackId, trackName = track.title)

    fun tap(kind: CareKind) {
        when (kind) {
            CareKind.POOP -> { haptics.tap(); showPoop = true }
            CareKind.WEIGHT, CareKind.VET -> detailKind = kind
            else -> confirmIfRepeat(kind, title = kind.title) { record(CareEvent(dogId = dog.id, kindRaw = kind.raw)) }
        }
    }

    fun tapCustom(track: CareTrack, logs: List<CareEvent>) {
        when (track.logStyle) {
            TrackLogStyle.DONE -> if (logs.isEmpty()) {
                record(customEvent(track))
            } else {
                // Done or not: a second tap takes today's tick back.
                haptics.soft()
                scope.launch { logs.forEach { app.repository.deleteEvent(it) } }
            }
            TrackLogStyle.COUNT -> confirmIfRepeat(CareKind.CUSTOM, track.trackId, track.title) { record(customEvent(track)) }
            TrackLogStyle.AMOUNT -> confirmIfRepeat(CareKind.CUSTOM, track.trackId, track.title) { haptics.tap(); amountTrack = track }
        }
    }

    Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 120.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    PetChip(dog, dogs) { scope.launch { app.settings.setActiveDog(it.id) } }
                }
                Text("Care", style = opaw(22.sp, FontWeight.Black), color = Opaw.colors.ink, modifier = Modifier.align(Alignment.Center))
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIcon(DesignIcon.PENCIL, onClick = { showEditTracks = true })
                    RoundIcon(DesignIcon.SCHEDULE, onClick = { navigator.push(Route.Schedule(dog.id)) })
                }
            }
            StreakCard(data0, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) { navigator.push(Route.History(dog.id)) }
            CareGrid(data0, now, Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), onKind = { kind -> tap(kind) }, onDetails = { detailKind = it }, onCustom = ::tapCustom)
            CaringCard(data0, now, Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) { pendingDelete = it }
        }

        AnimatedVisibility(showPoop, enter = fadeIn(), exit = fadeOut()) {
            PoopLogOverlay(data0) { event ->
                event?.let(::record)
                showPoop = false
            }
        }
    }

    detailKind?.let { kind ->
        CareLogSheet(data0, kind, onDismiss = { detailKind = null })
    }
    if (showEditTracks) EditTracksSheet(data0) { showEditTracks = false }
    amountTrack?.let { track ->
        AmountLogSheet(track, onDismiss = { amountTrack = null }) { value -> record(customEvent(track).copy(amount = value)) }
    }
    RepeatLogConfirmation(pendingLog) { pendingLog = null }
    DeleteLogConfirmation(pendingDelete) { pendingDelete = null }
}

@Composable
private fun StreakCard(data: DogData, modifier: Modifier, onClick: () -> Unit) {
    val colors = Opaw.colors
    val streak = data.careStreak
    val counts = data.lastSevenDayCounts
    // Today sits fifth, as in the design: four days before it and two still to come.
    val bars: List<Int?> = counts.takeLast(5) + listOf(null, null)
    Row(
        modifier.fillMaxWidth().pressable(onClick = onClick).whiteCard().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (streak == 1) "1-day streak" else "$streak-day streak", style = opaw(22.sp, FontWeight.Black), color = colors.ink)
            Text("Log 3 things a day", style = opaw(14.sp, FontWeight.Medium), color = colors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { index, count ->
                Box(
                    Modifier
                        .size(12.dp, if (index == 4) 30.dp else 22.dp)
                        .background(if ((count ?: 0) >= 3) colors.ink else colors.track, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun CareGrid(
    data: DogData,
    now: Instant,
    modifier: Modifier,
    onKind: (CareKind) -> Unit,
    onDetails: (CareKind) -> Unit,
    onCustom: (CareTrack, List<CareEvent>) -> Unit,
) {
    val due = data.dueKinds(now)
    val dueTracks = data.dueTrackIds(now)
    val tracks = data.shownTracks
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tracks.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { track ->
                    Box(Modifier.weight(1f)) {
                        val trackId = track.trackId
                        if (trackId != null) {
                            val logs = data.events(trackId, now)
                            val alert = trackId in dueTracks
                            CareCircle(
                                icon = DesignIcon.fromRaw(track.icon) ?: DesignIcon.SPARKLE,
                                label = track.title,
                                badge = if (alert) "!" else customBadge(track, logs),
                                alert = alert,
                                onClick = { onCustom(track, logs) },
                            )
                        } else {
                            val kind = track.kind
                            val count = data.count(kind, now)
                            val alert = kind in due
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                CareCircle(
                                    icon = DesignIcon.forKind(kind) ?: DesignIcon.SPARKLE,
                                    label = kind.title,
                                    badge = if (alert) "!" else if (count > 0) "$count" else null,
                                    alert = alert,
                                    onClick = { onKind(kind) },
                                    onLongClick = { menu = true },
                                )
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Log with details") }, onClick = { menu = false; onDetails(kind) })
                                }
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val amountFormat = DecimalFormat("0.#")

private fun customBadge(track: CareTrack, logs: List<CareEvent>): String? {
    if (logs.isEmpty()) return null
    return when (track.logStyle) {
        TrackLogStyle.DONE -> "✓"
        TrackLogStyle.COUNT -> "${logs.size}"
        TrackLogStyle.AMOUNT -> amountFormat.format(logs.mapNotNull { it.amount }.sum())
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CareCircle(
    icon: DesignIcon,
    label: String,
    badge: String?,
    alert: Boolean,
    selected: Boolean = false,
    size: Dp = 68.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Opaw.colors
    Column(
        Modifier.fillMaxWidth().pressable(onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(size)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                    .background(if (selected) Ink else colors.surface, CircleShape)
                    .then(if (alert) Modifier.border(2.5.dp, colors.alert, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, size = size * 0.38f, tint = if (selected) Color.White else if (alert) colors.alert else colors.ink, cutoutColor = if (selected) Ink else colors.surface)
            }
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .popOnChange(badge)
                        .background(colors.cream, CircleShape)
                        .padding(2.dp)
                        .defaultMinSize(22.dp, 22.dp)
                        .background(if (alert) colors.alert else Ink, CircleShape)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White)
                }
            }
        }
        Text(label, style = opaw(13.sp, FontWeight.Bold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CaringCard(data: DogData, now: Instant, modifier: Modifier, onHoldLog: (PendingDelete) -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val events = data.events(now).sortedByDescending { it.date }
    val people = events.map { loggerName(it.loggedBy) }.distinct()
    Column(modifier.fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Who's caring today", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                people.take(3).forEach { MiniFace(null, size = 32.dp, rim = 3.dp, fill = colors.track, initial = it.take(1)) }
            }
        }
        if (events.isEmpty()) {
            Hairline()
            Text(
                "Nothing logged yet. Tap a circle above when ${data.dog.name} eats, pees or poops.",
                style = opaw(15.sp, FontWeight.SemiBold),
                color = colors.muted,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        events.take(6).forEach { event ->
            Hairline()
            LogLine(clock(event.date), caringLine(event), Modifier.onHold {
                haptics.medium()
                onHoldLog(PendingDelete(event.id, clock(event.date), caringLine(event), event.kind.points))
            })
        }
    }
}

/** A time and a line of text, as in the day's log lists. */
@Composable
fun LogLine(time: String, text: String, modifier: Modifier = Modifier, trailing: String? = null, timeWidth: Int = 52, vertical: Dp = 10.dp) {
    val colors = Opaw.colors
    Row(modifier.fillMaxWidth().padding(vertical = vertical), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = opaw(15.sp, FontWeight.SemiBold), color = colors.faint, maxLines = 1, modifier = Modifier.width(clockColumnWidth(timeWidth).dp))
        Text(text, style = opaw(15.sp, FontWeight.SemiBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = opaw(15.sp, FontWeight.SemiBold), color = colors.faint)
        }
    }
}

fun caringLine(event: CareEvent): String {
    val who = loggerName(event.loggedBy)
    val detail = event.summary.split(" · ").drop(1).joinToString(" · ")
    val what = when (event.kind) {
        CareKind.MEAL -> "a meal"
        CareKind.WATER -> "water"
        CareKind.PEE -> "a pee"
        CareKind.POOP -> "a poop"
        CareKind.MEDS -> "meds"
        CareKind.BATH -> "a bath"
        CareKind.BRUSH -> "a brush"
        CareKind.NAILS -> "a nail trim"
        CareKind.WEIGHT -> "a weigh-in"
        CareKind.VET -> "a vet visit"
        CareKind.PLAY -> "play time"
        CareKind.CUSTOM -> if (event.trackName.isEmpty()) "a custom log" else event.trackName.lowercase()
    }
    return if (detail.isEmpty()) "$who logged $what" else "$who logged $what · $detail"
}

/** Asks "Log another?" for a pending repeat log. */
@Composable
fun RepeatLogConfirmation(pending: PendingLog?, onDone: () -> Unit) {
    pending ?: return
    val colors = Opaw.colors
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = colors.surface,
        title = { Text("Log again?", style = opaw(19.sp, FontWeight.Black)) },
        text = { Text(pending.message, style = opaw(15.sp, FontWeight.Medium)) },
        confirmButton = { TextButton({ onDone(); pending.perform() }) { Text("Log another", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink) } },
        dismissButton = { TextButton(onDone) { Text("Cancel", style = opaw(15.sp, FontWeight.Bold), color = colors.muted) } },
    )
}

/** "Delete this log?" after a log is held (7a, 7h). Delete shows a white toast with Undo. */
@Composable
fun DeleteLogConfirmation(pending: PendingDelete?, onDone: () -> Unit) {
    pending ?: return
    val app = rememberApp()
    val colors = Opaw.colors
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = colors.surface,
        title = { Text("Delete this log?", style = opaw(19.sp, FontWeight.Black)) },
        text = { Text("${pending.time}  ${pending.text}  +${pending.points}\nYou can undo it right after.", style = opaw(15.sp, FontWeight.Medium)) },
        confirmButton = {
            TextButton({
                onDone()
                app.scope.launch {
                    // Let the dialog finish closing so no row still reads the log.
                    delay(250)
                    app.toasts.show(CareRecorder.remove(app, pending.id, CareToast.Style.DELETED))
                }
            }) { Text("Delete", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.alert) }
        },
        dismissButton = { TextButton(onDone) { Text("Keep it", style = opaw(15.sp, FontWeight.Bold), color = colors.ink) } },
    )
}
