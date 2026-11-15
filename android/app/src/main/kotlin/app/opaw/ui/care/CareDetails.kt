package app.opaw.ui.care

import app.opaw.data.CareEvent
import app.opaw.services.WidgetRefresher
import app.opaw.services.clockColumnWidth
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.CareKind
import app.opaw.core.OpawFormat
import app.opaw.core.PoopConsistency
import app.opaw.data.DogData
import app.opaw.data.Reminder
import app.opaw.data.ReminderRepeat
import app.opaw.data.WALK_POINTS
import app.opaw.data.dueDate
import app.opaw.data.loggerName
import app.opaw.data.points
import app.opaw.data.summary
import app.opaw.data.title
import app.opaw.services.CareRecorder
import app.opaw.services.ClockSettings
import app.opaw.services.NotificationService
import app.opaw.services.clock
import app.opaw.services.formatDate
import app.opaw.services.formatInstant
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.FilterPill
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.onHold
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Today (2b)

/** Day summary (2b): the week strip, six read-only tiles, and the day's log with points. Hold a log to delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(dogId: String) {
    val data = rememberDogData(dogId) ?: return
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    var day by remember { mutableStateOf(LocalDate.now(zone)) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val isToday = day == LocalDate.now(zone)

    Column(Modifier.fillMaxSize().background(Opaw.colors.cream).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            RoundIcon(DesignIcon.BACK, Modifier.align(Alignment.CenterStart), onClick = navigator::pop)
            Text(
                if (isToday) "Today" else formatDate(day, "EEEE"),
                style = opaw(24.sp, FontWeight.Black),
                color = Opaw.colors.ink,
                modifier = Modifier.align(Alignment.Center),
            )
            RoundIcon(DesignIcon.CALENDAR, Modifier.align(Alignment.CenterEnd), onClick = { navigator.push(Route.History(dogId)) })
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                // Pull to refresh: re-reads badges and the widgets, with a soft haptic as it lets go.
                haptics.soft()
                refreshing = true
                scope.launch {
                    CareRecorder.refreshBadges(app, dogId)
                    WidgetRefresher.refresh(app)
                    delay(600)
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 40.dp)) {
                WeekStrip(day, now, Modifier.padding(horizontal = 20.dp).padding(top = 18.dp)) { haptics.select(); day = it }
                AnimatedVisibility(app.toasts.isOffline) { OfflineChip(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp)) }
                Tiles(data, day, isToday, now, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
                val entries = entries(data, day)
                Row(Modifier.padding(horizontal = 26.dp).padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
                    Text("History", style = opaw(17.sp, FontWeight.Black), color = Opaw.colors.ink, modifier = Modifier.weight(1f))
                    if (entries.isNotEmpty()) Text("Hold a log to delete it", style = opaw(13.sp, FontWeight.SemiBold), color = Opaw.colors.muted)
                }
                LogCard(data, entries, isToday, Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) { entry ->
                    haptics.medium()
                    pendingDelete = PendingDelete(entry.id, clock(entry.date), entry.text, entry.points)
                }
            }
        }
    }
    DeleteLogConfirmation(pendingDelete) { pendingDelete = null }
}

@Composable
private fun WeekStrip(selected: LocalDate, now: Instant, modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    val colors = Opaw.colors
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..6).map { start.plusDays(it.toLong()) }.forEach { date ->
            val isSelected = date == selected
            val future = date.isAfter(today)
            Column(
                Modifier.weight(1f).height(64.dp).pressable(enabled = !future) { onSelect(date) }
                    .background(if (isSelected) Ink else Color.Transparent, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(formatDate(date, "EEEEE"), style = opaw(12.sp, FontWeight.SemiBold), color = if (isSelected) Color.White else if (future) colors.ghost else colors.muted)
                Text("${date.dayOfMonth}", style = opaw(17.sp, FontWeight.ExtraBold), color = if (isSelected) Color.White else if (future) colors.ghost else colors.ink)
            }
        }
    }
}

@Composable
private fun Tiles(data: DogData, day: LocalDate, isToday: Boolean, now: Instant, modifier: Modifier) {
    val zone = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zone).toInstant()
    val reference = if (isToday) now else day.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
    val meals = data.count(CareKind.MEAL, dayStart)
    val mealGoal = maxOf(1, data.scheduledMealHours.size)
    val medsNeeded = data.reminders(dayStart).count { it.kind == CareKind.MEDS }
    val medsTaken = data.count(CareKind.MEDS, dayStart)
    val due = if (isToday) data.dueKinds(now) else emptySet()
    val bath = data.daysSince(setOf(CareKind.BATH), reference)
    val groom = data.daysSince(setOf(CareKind.BRUSH, CareKind.NAILS), reference)
    val tiles = listOf(
        Tile("$meals", "/$mealGoal", "Food", CareKind.MEAL in due),
        Tile("${data.count(CareKind.PEE, dayStart)}", null, "Pee", CareKind.PEE in due),
        Tile("${data.count(CareKind.POOP, dayStart)}", null, "Poop", CareKind.POOP in due),
        Tile(bath?.let { "${it}d" } ?: "–", null, "Bath", CareKind.BATH in due),
        Tile(if (medsNeeded > 0) "$medsTaken/$medsNeeded" else "$medsTaken", null, "Meds", CareKind.MEDS in due),
        Tile(groom?.let { "${it}d" } ?: "–", null, "Groom", CareKind.BRUSH in due || CareKind.NAILS in due),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { TileView(it, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Tile(val value: String, val suffix: String?, val label: String, val alert: Boolean)

@Composable
private fun TileView(tile: Tile, modifier: Modifier) {
    val colors = Opaw.colors
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .then(if (tile.alert) Modifier else Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f)))
            .background(colors.surface, shape)
            .then(if (tile.alert) Modifier.border(2.dp, colors.alert, shape) else Modifier)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            buildAnnotatedString {
                append(tile.value)
                tile.suffix?.let { withStyle(SpanStyle(fontSize = 16.sp, color = colors.muted)) { append(it) } }
            },
            style = opaw(28.sp, FontWeight.Black),
            color = if (tile.alert) colors.alert else colors.ink,
            maxLines = 1,
        )
        Text(tile.label, style = opaw(15.sp, FontWeight.Bold), color = colors.ink)
    }
}

private data class Entry(val id: String, val date: Instant, val text: String, val points: Int, val isCareLog: Boolean)

private fun entries(data: DogData, day: LocalDate): List<Entry> {
    val zone = ZoneId.systemDefault()
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    val events = data.events(start).map { Entry(it.id, it.date, it.summary, it.kind.points, true) }
    val walks = data.walks.filter { it.startedAt >= start && it.startedAt < end }.map {
        Entry(it.id, it.startedAt, "${it.title} · ${OpawFormat.distance(it.distanceMeters)}", WALK_POINTS, false)
    }
    return (events + walks).sortedBy { it.date }
}

@Composable
private fun LogCard(data: DogData, rows: List<Entry>, isToday: Boolean, modifier: Modifier, onHoldEntry: (Entry) -> Unit) {
    val colors = Opaw.colors
    Column(modifier.fillMaxWidth().whiteCard().padding(horizontal = 22.dp, vertical = 6.dp)) {
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isToday) "Nothing logged yet" else "Nothing logged this day", style = opaw(17.sp, FontWeight.Black), color = colors.ink)
                if (isToday) {
                    Text("Log on Care when ${data.dog.name} eats, pees or poops and it shows up here.", style = opaw(14.sp, FontWeight.Medium), color = colors.muted, textAlign = TextAlign.Center)
                }
            }
        }
        rows.forEachIndexed { index, entry ->
            LogLine(clock(entry.date), entry.text, Modifier.onHold(enabled = entry.isCareLog) { onHoldEntry(entry) }, trailing = "+${entry.points}", timeWidth = 56, vertical = 13.dp)
            if (index < rows.lastIndex) Hairline()
        }
    }
}

/** Shown while the phone is offline (7f). Logging keeps working; everything stays on this phone. */
@Composable
private fun OfflineChip(modifier: Modifier) {
    val colors = Opaw.colors
    Row(
        modifier.fillMaxWidth().height(44.dp).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(colors.surface, CircleShape).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DesignIcon.CLOUD_OFF, size = 18.dp)
        Text("Offline", style = opaw(14.sp, FontWeight.Bold), color = colors.ink)
        Text("Logs stay on this phone", style = opaw(14.sp, FontWeight.Medium), color = colors.muted)
    }
}

// MARK: - Schedule (3c)

/** Schedule (3c): today's reminders as a timeline (done, due in red with a Log button, or later), then what's coming up. */
@Composable
fun ScheduleScreen(dogId: String) {
    val data = rememberDogData(dogId) ?: return
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    var showAdd by remember { mutableStateOf(false) }
    val colors = Opaw.colors

    fun log(reminder: Reminder) {
        val custom = reminder.kind == CareKind.CUSTOM
        val event = CareEvent(
            dogId = dogId,
            kindRaw = reminder.kindRaw,
            note = if (custom) "" else reminder.title,
            trackId = if (custom) reminder.trackId else null,
            trackName = if (custom) reminder.title else "",
        )
        scope.launch { app.toasts.show(CareRecorder.log(app, event)) }
    }

    fun delete(reminder: Reminder) {
        scope.launch {
            app.repository.deleteReminder(reminder)
            NotificationService.reschedule(app)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Schedule", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) }, trailing = { RoundIcon(DesignIcon.PLUS, onClick = { showAdd = true }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 60.dp)) {
            val reminders = data.reminders(now)
            val doneIds = data.doneReminderIds(now)
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 4.dp)) {
                Row(Modifier.padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Today", style = opaw(18.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                    Text("${reminders.count { it.id in doneIds }} of ${reminders.size} done", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
                }
                if (reminders.isEmpty()) {
                    Hairline()
                    Text("No reminders yet. Tap + to add meals, meds and more.", style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, modifier = Modifier.padding(vertical = 12.dp))
                }
                reminders.forEach { reminder ->
                    Hairline()
                    ReminderMenu(onDelete = { delete(reminder) }) { open ->
                        ScheduleRow(data, reminder, reminder.id in doneIds, now, Modifier.onHold(action = open)) { log(reminder) }
                    }
                }
            }
            val upcoming = data.upcomingReminders(now)
            if (upcoming.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("Coming up", style = opaw(18.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    upcoming.forEach { (date, reminder) ->
                        Hairline()
                        ReminderMenu(onDelete = { delete(reminder) }) { open ->
                            Row(Modifier.fillMaxWidth().onHold(action = open).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.size(50.dp, 54.dp).background(colors.cream, RoundedCornerShape(16.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(formatDate(date, "MMM").uppercase(), style = opaw(11.sp, FontWeight.Bold), color = colors.muted)
                                    Text("${date.dayOfMonth}", style = opaw(19.sp, FontWeight.Black), color = colors.ink)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(reminder.title, style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, maxLines = 1)
                                    Text(
                                        if (reminder.repeats == ReminderRepeat.MONTHLY) "Monthly · reminds you the night before" else reminder.repeats.title,
                                        style = opaw(13.sp, FontWeight.Medium), color = colors.muted, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddReminderSheet(data) { showAdd = false }
}

@Composable
private fun ReminderMenu(onDelete: () -> Unit, content: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        content { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Delete reminder", color = Opaw.colors.alert) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun ScheduleRow(data: DogData, reminder: Reminder, isDone: Boolean, now: Instant, modifier: Modifier, onLog: () -> Unit) {
    val colors = Opaw.colors
    val due = reminder.dueDate(now)
    val minutesUntil = Duration.between(now, due).seconds / 60.0
    // Due soon (within 3 hours) or late counts as needing attention.
    val needsAttention = !isDone && minutesUntil < 180
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(clock(due), style = opaw(14.sp, FontWeight.Bold), color = if (needsAttention) colors.alert else colors.faint, maxLines = 1,
            modifier = Modifier.width(clockColumnWidth(54).dp))
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            when {
                isDone -> Box(Modifier.size(18.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) { Icon(DesignIcon.CHECK, size = 11.dp, tint = Color.White) }
                needsAttention -> Box(Modifier.size(18.dp).border(2.5.dp, colors.alert, CircleShape))
                else -> Box(Modifier.size(18.dp).border(2.dp, Color(0xFFCCCCCC), CircleShape))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(reminder.title, style = opaw(15.sp, FontWeight.ExtraBold), color = if (needsAttention) colors.alert else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle(data, reminder, isDone, minutesUntil, now), style = opaw(13.sp, FontWeight.Medium), color = colors.muted, maxLines = 1)
        }
        if (needsAttention) {
            Box(Modifier.height(44.dp).pressable(onClick = onLog).background(colors.alert, CircleShape).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("Log", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
            }
        }
    }
}

private fun subtitle(data: DogData, reminder: Reminder, isDone: Boolean, minutesUntil: Double, now: Instant): String {
    if (isDone) {
        val due = reminder.dueDate(now)
        val event = data.careEvents.firstOrNull { it.kind == reminder.kind && it.date >= due.minusSeconds(90 * 60) && it.date <= due.plusSeconds(6 * 3600) }
        return loggerName(event?.loggedBy.orEmpty())
    }
    val timing = when {
        minutesUntil < -30 -> "Late"
        minutesUntil < 0 -> "Due now"
        minutesUntil < 60 -> "Due in ${minutesUntil.toInt()} min"
        minutesUntil < 180 -> "Due in ${(minutesUntil / 60).toInt()} h"
        else -> return if (reminder.note.isEmpty()) reminder.kind.title else reminder.note.replaceFirstChar(Char::uppercase)
    }
    return if (reminder.note.isEmpty()) timing else "$timing · ${reminder.note}"
}

/** A new reminder for meals, meds and more. Mirrors `AddReminderSheet` in CareViews.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderSheet(data: DogData, onDismiss: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    val dog = data.dog
    var kind by remember { mutableStateOf(CareKind.MEDS) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var repeats by remember { mutableStateOf(ReminderRepeat.DAILY) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var minutes by remember { mutableStateOf(9 * 60) }
    var pickTime by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    fun add() {
        val finalTitle = title.ifEmpty { "${dog.name}'s ${kind.title.lowercase()}" }
        app.scope.launch {
            app.repository.saveReminder(
                Reminder(
                    dogId = dog.id,
                    title = finalTitle,
                    kindRaw = kind.raw,
                    hour = minutes / 60,
                    minute = minutes % 60,
                    repeatRaw = repeats.raw,
                    startDate = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    note = note.trim(),
                ),
            )
            NotificationService.reschedule(app)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.cream,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = opaw(16.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Text("New reminder", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Add", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.clickable(onClick = ::add).padding(8.dp))
            }
            Text("Type", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CareKind.standard.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { ChoiceChip(it.title, it == kind) { kind = it } } }
                }
            }
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                PlainField(title, { title = it }, "${dog.name}'s ${kind.title.lowercase()}", opaw(16.sp, FontWeight.SemiBold), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), placeholderColor = colors.ghost)
                Hairline()
                PlainField(note, { note = it }, "e.g. with food", opaw(16.sp, FontWeight.Medium), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), placeholderColor = colors.ghost)
            }
            Text("Repeats", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ReminderRepeat.entries.forEach { ChoiceChip(it.title, it == repeats) { repeats = it } } }
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                if (repeats != ReminderRepeat.DAILY) {
                    Row(Modifier.fillMaxWidth().clickable { pickDate = true }.padding(vertical = 14.dp)) {
                        Text("Next on", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                        Text(formatDate(startDate, "d MMM yyyy"), style = opaw(16.sp, FontWeight.Bold), color = colors.ink)
                    }
                    Hairline()
                }
                Row(Modifier.fillMaxWidth().clickable { pickTime = true }.padding(vertical = 14.dp)) {
                    Text("Time", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                    Text(clock(LocalDate.now().atTime(minutes / 60, minutes % 60).atZone(ZoneId.systemDefault()).toInstant()), style = opaw(16.sp, FontWeight.Bold), color = colors.ink)
                }
            }
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = ClockSettings.is24Hour)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = colors.cream,
            text = { TimePicker(state) },
            confirmButton = { TextButton({ minutes = state.hour * 60 + state.minute; pickTime = false }) { Text("Done", color = colors.ink) } },
        )
    }
    if (pickDate) {
        val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Done", color = colors.ink) }
            },
        ) { DatePicker(state, showModeToggle = false) }
    }
}

// MARK: - History

internal enum class HistoryRange(val title: String, val days: Long) { WEEK("Week", 7), SIX_MONTHS("6 months", 183), YEAR("Year", 365) }

private sealed interface PoopDay {
    data object Empty : PoopDay
    data object Normal : PoopDay
    data class Other(val consistency: PoopConsistency?) : PoopDay
}

/** History: a plain-language summary, weight over time, the poop record, and a vet summary to share. */
@Composable
fun HistoryScreen(dogId: String) {
    val data = rememberDogData(dogId) ?: return
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = Opaw.colors
    var range by remember { mutableStateOf(HistoryRange.SIX_MONTHS) }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vetSummary(data))
        context.startActivity(Intent.createChooser(send, null))
    }

    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("History", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) }, trailing = { RoundIcon(DesignIcon.UPLOAD, onClick = ::share) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 40.dp)) {
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryRange.entries.forEach { option -> FilterPill(option.title, option == range) { range = option; haptics.select() } }
            }
            Row(
                Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).fillMaxWidth().background(Ink, RoundedCornerShape(30.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(DesignIcon.SPARKLE, Modifier.padding(top = 2.dp), size = 18.dp, tint = Color.White)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This week, summarised on your phone", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFFAAAAAA))
                    Text(weeklySummary(data), style = opaw(16.sp, FontWeight.Bold).copy(lineHeight = 22.sp), color = Color.White)
                }
            }
            WeightCard(data, range, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            PoopCard(data, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp).fillMaxWidth().height(62.dp).pressable(onClick = ::share).background(Ink, CircleShape),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcon.DOCUMENT, size = 20.dp, tint = Color.White)
                Text("Share vet summary", style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White)
            }
        }
    }
}

/** Plain sentences from this week's numbers. Computed on device; nothing leaves the phone. */
private fun weeklySummary(data: DogData): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant()
    val sentences = mutableListOf<String>()
    val thisWeek = data.walkedKm(at(today.minusDays(7)), at(today.plusDays(1)))
    val lastWeek = data.walkedKm(at(today.minusDays(14)), at(today.minusDays(7)))
    val name = data.dog.name
    when {
        thisWeek == 0.0 && lastWeek == 0.0 -> sentences += "No walks logged yet this week."
        lastWeek > 0 -> {
            val change = ((thisWeek - lastWeek) / lastWeek * 100).roundToInt()
            sentences += if (abs(change) < 5) "$name walked about as much as last week." else "$name walked ${abs(change)}% ${if (change > 0) "more" else "less"} than last week."
        }
        else -> sentences += "$name walked ${OpawFormat.distance(thisWeek * 1000)} this week."
    }
    val days = poopDays(data, 7)
    val logged = days.count { it != PoopDay.Empty }
    if (logged > 0) {
        val normal = days.count { it == PoopDay.Normal }
        sentences += "Poop was normal $normal of $logged day${if (logged == 1) "" else "s"}."
    }
    weightTrend(data)?.let { sentences += it }
    return sentences.joinToString(" ")
}

internal fun weightPoints(data: DogData) = data.careEvents.mapNotNull { e -> e.weightKg?.let { e.date to it } }.sortedBy { it.first }

private fun weightTrend(data: DogData): String? {
    val points = weightPoints(data)
    val latest = points.lastOrNull() ?: return null
    val monthAgo = latest.first.minusSeconds(30L * 86_400)
    val earlier = points.lastOrNull { it.first <= monthAgo } ?: (if (points.size > 1) points.first() else null)
        ?: return "Weight is ${DecimalFormat("0.0").format(latest.second)} kg."
    val change = (latest.second - earlier.second) / maxOf(0.1, earlier.second)
    if (abs(change) < 0.02) return "Weight is steady."
    return if (change > 0) "Weight is up a little." else "Weight is down a little."
}

/** One entry per day, oldest first. */
private fun poopDays(data: DogData, count: Int): List<PoopDay> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return (count - 1 downTo 0).map { offset ->
        val poops = data.events(today.minusDays(offset.toLong()).atStartOfDay(zone).toInstant()).filter { it.kind == CareKind.POOP }
        when {
            poops.isEmpty() -> PoopDay.Empty
            else -> poops.firstOrNull { (it.poopConsistency ?: PoopConsistency.NORMAL) != PoopConsistency.NORMAL || it.poopColor?.isConcerning == true }
                ?.let { PoopDay.Other(it.poopConsistency) } ?: PoopDay.Normal
        }
    }
}

@Composable
private fun WeightCard(data: DogData, range: HistoryRange, modifier: Modifier) {
    val colors = Opaw.colors
    val all = weightPoints(data)
    val start = LocalDate.now().minusDays(range.days).atStartOfDay(ZoneId.systemDefault()).toInstant()
    val points = all.filter { it.first >= start }
    Column(modifier.fillMaxWidth().whiteCard().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Weight", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
            all.lastOrNull()?.let { latest ->
                Text(
                    buildAnnotatedString {
                        append(DecimalFormat("0.0").format(OpawFormat.weightValue(latest.second)))
                        withStyle(SpanStyle(fontSize = 14.sp, color = colors.muted)) { append(" ${OpawFormat.weightUnit}") }
                    },
                    style = opaw(22.sp, FontWeight.Black),
                    color = colors.ink,
                )
            }
        }
        if (points.size < 2) {
            Text(if (points.isEmpty()) "Log a weight to start the chart." else "One more weigh-in and the line appears.", style = opaw(14.sp, FontWeight.SemiBold), color = colors.muted,
                modifier = Modifier.fillMaxWidth().height(80.dp))
        } else {
            WeightChart(points, range)
        }
    }
}

/** A line with dots, the last one filled, and month or weekday labels under it. */
@Composable
internal fun WeightChart(points: List<Pair<Instant, Double>>, range: HistoryRange) {
    val colors = Opaw.colors
    val minT = points.first().first.toEpochMilli().toDouble()
    val maxT = points.last().first.toEpochMilli().toDouble().let { if (it == minT) minT + 1 else it }
    val minW = points.minOf { it.second }
    val maxW = points.maxOf { it.second }.let { if (it == minW) minW + 1 else it }
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val pad = 8.dp.toPx()
            fun x(t: Instant) = pad + ((t.toEpochMilli() - minT) / (maxT - minT)).toFloat() * (size.width - 2 * pad)
            fun y(w: Double) = pad + (1 - ((w - minW) / (maxW - minW)).toFloat()) * (size.height - 2 * pad)
            val path = Path()
            points.forEachIndexed { i, (t, w) -> if (i == 0) path.moveTo(x(t), y(w)) else path.lineTo(x(t), y(w)) }
            drawPath(path, colors.ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, (t, w) ->
                val c = Offset(x(t), y(w))
                if (i == points.lastIndex) {
                    drawCircle(colors.ink, 6.dp.toPx(), c)
                } else {
                    drawCircle(colors.surface, 3.5.dp.toPx(), c)
                    drawCircle(colors.ink, 3.5.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                }
            }
        }
        val labels = (0 until 6).map { i -> Instant.ofEpochMilli((minT + (maxT - minT) * i / 5).toLong()) }
            .map { formatInstant(it, if (range == HistoryRange.WEEK) "EEE" else "MMM") }.distinct()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = opaw(12.sp, FontWeight.SemiBold), color = colors.faint) }
        }
    }
}

@Composable
private fun PoopCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val days = poopDays(data, 14)
    val normal = days.count { it == PoopDay.Normal }
    val others = days.filterIsInstance<PoopDay.Other>().groupingBy { (it.consistency?.title ?: "Off").lowercase() }.eachCount()
    val caption = (listOf("$normal normal") + others.entries.sortedByDescending { it.value }.map { "${it.value} ${it.key}" }).joinToString(" · ")
    Column(modifier.fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Poop, last 14 days", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
            Text(caption, style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                Box(
                    Modifier.weight(1f).height(26.dp).background(
                        when (day) {
                            PoopDay.Normal -> colors.ink
                            PoopDay.Empty -> Color(0xFFEEEEEF)
                            is PoopDay.Other -> Color(0xFFD9D9DC)
                        },
                        RoundedCornerShape(7.dp),
                    ),
                )
            }
        }
    }
}

/** A plain-text summary a vet can read in a message or email. */
private fun vetSummary(data: DogData): String {
    val dog = data.dog
    val lines = mutableListOf("${dog.name} — care summary from Opaw")
    val profile = listOfNotNull(dog.breed.ifEmpty { null }, data.ageDescription, dog.weightKg?.let { OpawFormat.weight(it) })
    if (profile.isNotEmpty()) lines += profile.joinToString(" · ")
    lines += ""
    lines += weeklySummary(data)
    lines += ""
    lines += "Last 14 days:"
    val since = Instant.now().minusSeconds(14L * 86_400)
    val recent = data.careEvents.filter { it.date >= since }
    CareKind.standard.forEach { kind ->
        val count = recent.count { it.kind == kind }
        if (count > 0) lines += "• ${kind.title}: $count"
    }
    recent.filter { it.kind == CareKind.CUSTOM }.map { it.trackName }.distinct().forEach { name ->
        lines += "• ${name.ifEmpty { "Custom" }}: ${recent.count { it.kind == CareKind.CUSTOM && it.trackName == name }}"
    }
    recent.filter { it.poopConsistency?.isConcerning == true || it.poopColor?.isConcerning == true }.sortedBy { it.date }.forEach { event ->
        var detail = listOfNotNull(event.poopConsistency?.title, event.poopColor?.title).joinToString(", ")
        if (event.note.isNotEmpty()) detail += " — ${event.note}"
        lines += "⚠︎ ${formatInstant(event.date, "d MMM yyyy")}, ${clock(event.date)}: poop $detail"
    }
    val walkKm = data.walkedKm(since, Instant.now().plusSeconds(60))
    lines += "• Walked: ${OpawFormat.distance(walkKm * 1000)}"
    return lines.joinToString("\n")
}
