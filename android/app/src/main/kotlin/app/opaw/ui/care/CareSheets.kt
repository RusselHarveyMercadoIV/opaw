package app.opaw.ui.care

import app.opaw.services.ClockSettings
import app.opaw.services.formatInstant
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.SpeechBubble
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.opaw.core.CareKind
import app.opaw.core.OpawFormat
import app.opaw.core.PoopColor
import app.opaw.core.PoopConsistency
import app.opaw.data.CareEvent
import app.opaw.data.CareTrack
import app.opaw.data.CustomTrack
import app.opaw.data.DogData
import app.opaw.data.Reminder
import app.opaw.data.TrackLogStyle
import app.opaw.data.WALKS_KEY
import app.opaw.data.allTracks
import app.opaw.services.NotificationService
import app.opaw.services.WidgetRefresher
import app.opaw.services.clock
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private val sheetShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)

/** A text field in the design's style: a placeholder in gray, no underline. */
@Composable
fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    placeholderColor: Color = Color(0xFFAAAAAA),
    focusRequester: FocusRequester? = null,
) {
    val colors = Opaw.colors
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = style, color = placeholderColor)
        BasicTextField(
            value, onChange,
            textStyle = style.copy(color = colors.ink),
            singleLine = singleLine,
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = keyboard,
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

// MARK: - Poop log (3b)

/** Poop logging over a dimmed screen, with the avatar asking for privacy. */
@Composable
fun PoopLogOverlay(data: DogData, onDone: (CareEvent?) -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val offset by animateDpAsState(if (appeared) 0.dp else 800.dp, spring(dampingRatio = 0.86f, stiffness = 300f), label = "poop")
    val image = rememberPetImage(data.dog)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF141416).copy(alpha = 0.38f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { onDone(null) },
        )
        Box(Modifier.align(Alignment.BottomCenter).offset(y = offset)) {
            StickerPet(image, Modifier.padding(start = 40.dp, top = 14.dp).size(130.dp, 150.dp), edge = 5.dp, motion = LoopMotion.BREATHE, duration = 2.6)
            SpeechBubble("a little privacy, please", Modifier.padding(start = 170.dp, top = 20.dp))
            Box(Modifier.padding(top = 120.dp)) {
                PoopLogForm(data, onLog = onDone)
            }
        }
    }
}

/** The poop form, as a bottom sheet (3b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoopLogForm(data: DogData, onLog: (CareEvent) -> Unit) {
    val haptics = rememberHaptics()
    var consistency by remember { mutableStateOf(PoopConsistency.NORMAL) }
    var color by remember { mutableStateOf(PoopColor.BROWN) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Instant.now()) }
    var editTime by remember { mutableStateOf(false) }
    val timeLabel = clock(date).let { if (Duration.between(date, Instant.now()).abs().seconds < 120) "Now · $it" else it }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, sheetShape)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).background(Color(0xFFDDDDDD), CircleShape))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poop", style = opaw(30.sp, FontWeight.Black), color = Ink, modifier = Modifier.weight(1f))
            Text(
                timeLabel,
                style = opaw(14.sp, FontWeight.Bold),
                color = Ink,
                modifier = Modifier.pressable { editTime = true }.background(Color(0xFFF1F1F2), CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Consistency", style = opaw(14.sp, FontWeight.Bold), color = Color(0xFF666666))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PoopConsistency.pickerCases.forEach { option ->
                    val selected = option == consistency
                    Box(
                        Modifier.weight(1f).height(44.dp).pressable { consistency = option; haptics.select() }
                            .background(if (selected) Ink else Color(0xFFF1F1F2), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(option.title, style = opaw(14.sp, FontWeight.Bold), color = if (selected) Color.White else Color(0xFF555555), maxLines = 1) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Color", style = opaw(14.sp, FontWeight.Bold), color = Color(0xFF666666))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PoopColor.entries.forEach { option ->
                    val selected = option == color
                    Column(Modifier.pressable { color = option; haptics.select() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.size(44.dp).then(if (selected) Modifier.border(2.5.dp, Ink, CircleShape) else Modifier).padding(5.5.dp)
                                .background(option.swatch, CircleShape)
                                .then(if (selected) Modifier else Modifier.border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape)),
                        )
                        Text(option.title, style = opaw(12.sp, FontWeight.SemiBold), color = if (selected) Ink else Color(0xFF666666), maxLines = 1)
                    }
                }
            }
        }
        PlainField(
            note, { note = it }, "Add a note (optional)", opaw(15.sp, FontWeight.Medium),
            Modifier.fillMaxWidth().background(Color(0xFFF6F6F7), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        BlackPillButton("Log poop", height = 62.dp) {
            onLog(
                CareEvent(
                    dogId = data.dog.id,
                    kindRaw = CareKind.POOP.raw,
                    date = date,
                    note = note.trim(),
                    poopConsistencyRaw = consistency.raw,
                    poopColorRaw = color.raw,
                ),
            )
        }
    }

    if (editTime) DateTimePicker(date, onDismiss = { editTime = false }) { date = it }
}

val PoopColor.swatch: Color
    get() = when (this) {
        PoopColor.BROWN -> Color(0xFF7A4A26)
        PoopColor.YELLOW -> Color(0xFFC9A45A)
        PoopColor.GREEN -> Color(0xFF6F7A3A)
        PoopColor.BLACK -> Color(0xFF2B2422)
        PoopColor.RED_TINGE -> Color(0xFF9B3A2C)
    }

/** A date, then a time, no later than now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePicker(initial: Instant, onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val colors = Opaw.colors
    val start = initial.atZone(zone)
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )
    val timeState = rememberTimePickerState(start.hour, start.minute, is24Hour = ClockSettings.is24Hour)
    if (day == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton({ day = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start.toLocalDate() }) {
                    Text("Next", color = colors.ink)
                }
            },
            dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.muted) } },
        ) { DatePicker(dateState, showModeToggle = false) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.cream,
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton({
                    val picked = ZonedDateTime.of(day, LocalTime.of(timeState.hour, timeState.minute), zone).toInstant()
                    onPick(minOf(picked, Instant.now()))
                    onDismiss()
                }) { Text("Done", color = colors.ink) }
            },
            dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.muted) } },
        )
    }
}

// MARK: - Log with details

/** Log any kind of care with optional details. Mirrors `CareLogSheet` in CareViews.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareLogSheet(data: DogData, initialKind: CareKind, onDismiss: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initialKind) }
    var date by remember { mutableStateOf(Instant.now()) }
    var note by remember { mutableStateOf("") }
    var consistency by remember { mutableStateOf(PoopConsistency.NORMAL) }
    var weightText by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf(false) }
    val weight = weightText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.cream, shape = sheetShape) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = opaw(16.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Text("Log for ${data.dog.name}", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                val enabled = !(kind == CareKind.WEIGHT && weight == null)
                Text(
                    "Save",
                    style = opaw(16.sp, FontWeight.ExtraBold),
                    color = if (enabled) colors.ink else colors.ghost,
                    modifier = Modifier.clickable(enabled = enabled) {
                        val kg = weight?.let { if (OpawFormat.usesImperial) it / OpawFormat.POUNDS_PER_KILOGRAM else it }
                        val event = CareEvent(
                            dogId = data.dog.id,
                            kindRaw = kind.raw,
                            date = date,
                            note = note.trim(),
                            poopConsistencyRaw = if (kind == CareKind.POOP) consistency.raw else null,
                            weightKg = if (kind == CareKind.WEIGHT) kg else null,
                        )
                        scope.launch {
                            val stamped = event.copy(loggedBy = app.settings.caretakerName.first())
                            app.repository.saveEvent(stamped)
                            if (kind == CareKind.WEIGHT && kg != null) app.repository.saveDog(data.dog.copy(weightKg = kg))
                            haptics.success()
                            WidgetRefresher.refresh(app)
                            onDismiss()
                        }
                    }.padding(8.dp),
                )
            }
            // The kinds, four across.
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareKind.standard.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = option == kind
                            Column(
                                Modifier.weight(1f).height(72.dp).background(if (selected) colors.chip else colors.surface, RoundedCornerShape(12.dp)).clickable { kind = option },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(Modifier.size(40.dp).background(if (selected) colors.ink else colors.chip, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(DesignIcon.forKind(option) ?: DesignIcon.SPARKLE, size = 20.dp, tint = if (selected) colors.onInk else colors.ink)
                                }
                                Text(option.title, style = opaw(12.sp, FontWeight.SemiBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Details", style = opaw(13.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp))
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().clickable { editTime = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("When", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                    Text(formatInstant(date, "d MMM") + ", " + clock(date), style = opaw(15.sp, FontWeight.Bold), color = colors.ink,
                        modifier = Modifier.background(colors.chip, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                if (kind == CareKind.POOP) {
                    Hairline()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Consistency", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PoopConsistency.pickerCases.forEach { option ->
                                ChoiceChip(option.title, option == consistency) { consistency = option }
                            }
                        }
                        if (consistency.isConcerning) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(DesignIcon.ALERT, size = 16.dp, tint = colors.alert)
                                Text("Keep an eye on this. If it continues for more than a day, call your vet.", style = opaw(13.sp, FontWeight.Medium), color = colors.alert)
                            }
                        }
                    }
                }
                if (kind == CareKind.WEIGHT) {
                    Hairline()
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Weight (${OpawFormat.weightUnit})", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                        PlainField(weightText, { weightText = it }, OpawFormat.weightUnit, opaw(16.sp, FontWeight.Bold), Modifier.width(90.dp), KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
                Hairline()
                PlainField(note, { note = it }, "Note (optional)", opaw(16.sp, FontWeight.Medium), Modifier.fillMaxWidth().padding(vertical = 14.dp), singleLine = false,
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        }
    }
    if (editTime) DateTimePicker(date, onDismiss = { editTime = false }) { date = it }
}

// MARK: - 3h Edit tracks

/** Show or hide tracks, drag them into order, and add custom ones (3h). Changes save as you make them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTracksSheet(data: DogData, onDismiss: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val order = remember { mutableStateListOf<CareTrack>().apply { addAll(data.allTracks) } }
    val shown = remember { mutableStateListOf<String>().apply { addAll(data.allTracks.filter { data.dog.tracks(it.key) }.map { it.key }) } }
    var editor by remember { mutableStateOf<String?>(null) } // "new" or a track id
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 57.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    // New and edited custom tracks join the list.
    LaunchedEffect(data.customTracks) {
        val keys = order.map { it.key }.toSet()
        data.allTracks.filter { it.key !in keys }.forEach { order += it; shown += it.key }
        val current = data.allTracks.associateBy { it.key }
        order.replaceAll { current[it.key] ?: it }
        order.removeAll { it.isCustom && it.key !in current }
    }

    fun save() {
        val keys = order.map { it.key }
        // Keep keys that aren't circles on Care, such as "walks" from setup.
        val extras = if (data.dog.trackedKindsRaw.isEmpty()) listOf(WALKS_KEY) else data.dog.trackedKindsRaw.filter { it !in keys && !it.startsWith("custom:") }
        scope.launch {
            val dog = app.repository.loadDogData(data.dog.id)?.dog ?: return@launch
            app.repository.saveDog(dog.copy(trackOrderRaw = keys, trackedKindsRaw = extras + keys.filter { it in shown }))
            WidgetRefresher.refresh(app)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface, shape = sheetShape) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Edit tracks", style = opaw(30.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                Text("Done", style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.pressable(onClick = onDismiss).background(Ink, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Text("Choose what shows on Care. Drag to reorder.", style = opaw(15.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.verticalScroll(rememberScrollState(), enabled = dragKey == null).padding(top = 10.dp, bottom = 24.dp)) {
                order.forEachIndexed { index, track ->
                    val isOn = track.key in shown
                    val dragging = dragKey == track.key
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .offset { androidx.compose.ui.unit.IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                            .then(if (dragging) Modifier.shadow(14.dp, RoundedCornerShape(18.dp)).background(colors.surface, RoundedCornerShape(18.dp)) else Modifier),
                    ) {
                        if (index > 0 && !dragging) Hairline()
                        Row(Modifier.height(rowHeight), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GripDots(
                                Modifier.size(24.dp, rowHeight).pointerInput(track.key) {
                                    detectDragGestures(
                                        onDragStart = { dragKey = track.key; dragOffset = 0f; haptics.select() },
                                        onDragEnd = { dragKey = null; dragOffset = 0f; save() },
                                        onDragCancel = { dragKey = null; dragOffset = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val current = order.indexOfFirst { it.key == track.key }
                                            val target = (current + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                            if (target != current) {
                                                order.add(target, order.removeAt(current))
                                                dragOffset -= (target - current) * rowPx
                                                haptics.select()
                                            }
                                        },
                                    )
                                },
                            )
                            Row(
                                Modifier.weight(1f).clickable(enabled = track.isCustom) { editor = track.trackId },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(38.dp).background(colors.chip, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(DesignIcon.fromRaw(track.icon) ?: DesignIcon.SPARKLE, size = 18.dp, tint = if (isOn) colors.ink else colors.faint)
                                }
                                Text(track.title, style = opaw(16.sp, FontWeight.Bold), color = if (isOn) colors.ink else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (track.isCustom) Text("Custom", style = opaw(12.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(end = 4.dp))
                            }
                            DesignToggle(isOn, { on ->
                                if (on) shown += track.key
                                else if (shown.size > 1) shown -= track.key // Care always keeps at least one circle.
                                save()
                            })
                        }
                    }
                }
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(56.dp).pressable { editor = "new" }.dashedCapsule(Color(0xFFD4D4D8)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DesignIcon.PLUS, size = 18.dp)
                    Text("Add custom track", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                }
            }
        }
    }

    editor?.let { target ->
        TrackEditorSheet(data, trackId = target.takeIf { it != "new" }, onDismiss = { editor = null })
    }
}

/** A capsule outline with dashes, for "Add" rows. */
fun Modifier.dashedCapsule(color: Color, width: Float = 2f, dash: Float = 6f, gap: Float = 5f): Modifier = drawBehind {
    val stroke = width * density
    drawRoundRect(
        color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density, gap * density))),
    )
}

/** Six dots, the drag handle in 3h. */
@Composable
private fun GripDots(modifier: Modifier) {
    Canvas(modifier) {
        val ox = (size.width - 18.dp.toPx()) / 2
        val oy = (size.height - 18.dp.toPx()) / 2
        for (x in listOf(9f, 15f)) for (y in listOf(6f, 12f, 18f)) {
            drawCircle(Color(0xFFCCCCCC), 1.6.dp.toPx(), Offset(ox + x * 0.75f * density, oy + y * 0.75f * density))
        }
    }
}

// MARK: - 3i New track

private val trackIcons = listOf(DesignIcon.SPARKLE, DesignIcon.BONE, DesignIcon.NAILS, DesignIcon.HEART_LINE, DesignIcon.STAR, DesignIcon.MOON, DesignIcon.SUN, DesignIcon.BOOK, DesignIcon.BALL)
private val moreTrackIcons = listOf(DesignIcon.PAW, DesignIcon.WATER, DesignIcon.MEAL, DesignIcon.MEDS, DesignIcon.BATH, DesignIcon.BRUSH, DesignIcon.WEIGHT, DesignIcon.VET, DesignIcon.CAMERA, DesignIcon.BELL)

/** Make or change a custom track: its name, icon, how it's logged, and a daily reminder (3i). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackEditorSheet(data: DogData, trackId: String?, onDismiss: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val existing = data.customTracks.firstOrNull { it.id == trackId }
    val existingReminder = existing?.let { t -> data.reminders.firstOrNull { it.trackId == t.id } }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(DesignIcon.fromRaw(existing?.icon) ?: DesignIcon.SPARKLE) }
    var style by remember { mutableStateOf(existing?.logStyle ?: TrackLogStyle.DONE) }
    var remind by remember { mutableStateOf(if (existing == null) true else existingReminder?.isEnabled == true) }
    var remindMinutes by remember { mutableStateOf(existingReminder?.let { it.hour * 60 + it.minute } ?: (21 * 60)) }
    var showMoreIcons by remember { mutableStateOf(existing != null && icon !in trackIcons) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val trimmed = name.trim()
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }

    fun save() {
        if (trimmed.isEmpty()) return
        app.scope.launch {
            val repo = app.repository
            val dog = repo.loadDogData(data.dog.id)?.dog ?: return@launch
            val track = existing?.copy(name = trimmed, icon = icon.raw, logStyleRaw = style.raw)
                ?: CustomTrack(dogId = dog.id, name = trimmed, icon = icon.raw, logStyleRaw = style.raw)
            repo.saveTrack(track)
            if (existing == null) {
                repo.saveDog(
                    dog.copy(
                        trackOrderRaw = if (dog.trackOrderRaw.isEmpty()) dog.trackOrderRaw else dog.trackOrderRaw + track.key,
                        trackedKindsRaw = if (dog.trackedKindsRaw.isEmpty()) dog.trackedKindsRaw else dog.trackedKindsRaw + track.key,
                    ),
                )
            }
            val reminders = data.reminders.filter { it.trackId == track.id }
            if (remind) {
                val first = reminders.firstOrNull()
                if (first != null) {
                    repo.saveReminder(first.copy(title = trimmed, hour = remindMinutes / 60, minute = remindMinutes % 60, isEnabled = true))
                    reminders.drop(1).forEach { repo.deleteReminder(it) }
                } else {
                    repo.saveReminder(Reminder(dogId = dog.id, title = trimmed, kindRaw = CareKind.CUSTOM.raw, hour = remindMinutes / 60, minute = remindMinutes % 60, trackId = track.id))
                }
            } else {
                reminders.forEach { repo.deleteReminder(it) }
            }
            WidgetRefresher.refresh(app)
            NotificationService.reschedule(app)
            haptics.success()
        }
        onDismiss()
    }

    fun delete() {
        existing ?: return
        app.scope.launch {
            val repo = app.repository
            val dog = repo.loadDogData(data.dog.id)?.dog ?: return@launch
            repo.saveDog(dog.copy(trackOrderRaw = dog.trackOrderRaw - existing.key, trackedKindsRaw = dog.trackedKindsRaw - existing.key))
            data.reminders.filter { it.trackId == existing.id }.forEach { repo.deleteReminder(it) }
            repo.deleteTrack(existing)
            WidgetRefresher.refresh(app)
            NotificationService.reschedule(app)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface, shape = sheetShape) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (trackId == null) "New track" else "Edit track", style = opaw(30.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                Text("Cancel", style = opaw(15.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().background(colors.chip.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, size = 22.dp, tint = Color.White) }
                    Column {
                        Text("Name", style = opaw(12.sp, FontWeight.SemiBold), color = colors.muted)
                        PlainField(name, { name = it }, "Brushing teeth", opaw(22.sp, FontWeight.Black), Modifier.fillMaxWidth(),
                            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), placeholderColor = colors.ghost, focusRequester = focus)
                    }
                }
                Text("Icon", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 20.dp))
                // Five across, spread edge to edge. The dashed + shows more icons.
                val cells: List<DesignIcon?> = (if (showMoreIcons) trackIcons + moreTrackIcons else trackIcons).let { if (showMoreIcons) it else it + listOf(null) }
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cells.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { option ->
                                if (option == null) {
                                    Box(
                                        Modifier.size(52.dp).pressable { haptics.select(); showMoreIcons = true }.dashedCapsule(Color(0xFFD4D4D8), dash = 4f, gap = 4f),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(DesignIcon.PLUS, size = 18.dp, tint = colors.muted) }
                                } else {
                                    val selected = option == icon
                                    Box(
                                        Modifier.size(52.dp).pressable { haptics.select(); icon = option }.background(if (selected) Ink else colors.chip, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(option, size = 22.dp, weight = if (option.isFilled) null else 2.3f, tint = if (selected) Color.White else colors.ink) }
                                }
                            }
                            repeat(5 - row.size) { Box(Modifier.size(52.dp)) }
                        }
                    }
                }
                Text("Log it as", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 20.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackLogStyle.entries.forEach { option ->
                        val selected = option == style
                        Text(
                            option.title,
                            style = opaw(14.sp, FontWeight.Bold),
                            color = if (selected) Color.White else Color(0xFF555555),
                            modifier = Modifier.pressable { haptics.select(); style = option }.background(if (selected) Ink else colors.chip, CircleShape).padding(horizontal = 16.dp, vertical = 11.dp),
                        )
                    }
                }
                Column(Modifier.padding(top = 20.dp)) {
                    Hairline()
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remind me", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                            Text("Daily · ${clockText(remindMinutes)}", style = opaw(13.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.clickable(enabled = remind) { showTime = true })
                        }
                        DesignToggle(remind, { remind = it })
                    }
                }
                if (trackId != null) {
                    Text(
                        "Delete track",
                        style = opaw(15.sp, FontWeight.ExtraBold),
                        color = colors.alert,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp).clickable { confirmDelete = true }.padding(8.dp),
                    )
                }
            }
            BlackPillButton(if (trackId == null) "Add track" else "Save", Modifier.padding(bottom = 8.dp), enabled = trimmed.isNotEmpty(), height = 62.dp, onClick = ::save)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(remindMinutes / 60, remindMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = colors.cream,
            text = { TimePicker(state) },
            confirmButton = { TextButton({ remindMinutes = state.hour * 60 + state.minute; showTime = false }) { Text("Done", color = colors.ink) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete ${trimmed.ifEmpty { "this track" }}?", style = opaw(19.sp, FontWeight.Black)) },
            text = { Text("It comes off Care and its reminder stops. Past logs stay in your history.", style = opaw(15.sp, FontWeight.Medium)) },
            confirmButton = { TextButton({ confirmDelete = false; delete() }) { Text("Delete track", color = colors.alert, style = opaw(15.sp, FontWeight.ExtraBold)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = colors.ink) } },
        )
    }
}

// MARK: - Amount log

/** Asks for a number when a custom track is logged as an amount. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountLogSheet(track: CareTrack, onDismiss: () -> Unit, onLog: (Double) -> Unit) {
    val colors = Opaw.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val value = text.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface, shape = sheetShape) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.title, style = opaw(26.sp, FontWeight.Black), color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                Text("Cancel", style = opaw(15.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            }
            PlainField(
                text, { text = it }, "Amount", opaw(32.sp, FontWeight.Black),
                Modifier.padding(top = 16.dp).fillMaxWidth().height(72.dp).background(colors.chip.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 20.dp),
                KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholderColor = colors.ghost, focusRequester = focus,
            )
            Spacer(Modifier.height(24.dp))
            BlackPillButton("Log", enabled = value != null, height = 62.dp) {
                value ?: return@BlackPillButton
                onLog(value)
                onDismiss()
            }
        }
    }
}
