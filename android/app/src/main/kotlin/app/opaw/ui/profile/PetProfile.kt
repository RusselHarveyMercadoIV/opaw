package app.opaw.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.CareKind
import app.opaw.core.OpawFormat
import app.opaw.data.Dog
import app.opaw.data.DogData
import app.opaw.services.formatDate
import app.opaw.services.formatInstant
import app.opaw.ui.LocalNavigator
import app.opaw.ui.care.CareLogSheet
import app.opaw.ui.care.HistoryRange
import app.opaw.ui.care.PlainField
import app.opaw.ui.care.WeightChart
import app.opaw.ui.care.rememberNow
import app.opaw.ui.care.weightPoints
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.StatColumn
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPhoto
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Pet profile (2j): age, weight, birthday and gotcha day, the weight line, and the details a vet asks for. */
@Composable
fun PetProfileScreen(dogId: String) {
    val navigator = LocalNavigator.current
    val colors = Opaw.colors
    val data = rememberDogData(dogId) ?: return
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        EditDogScreen(data) { editing = false }
        return
    }
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader(
            "Profile",
            leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) },
            trailing = { RoundIcon(DesignIcon.PENCIL, onClick = { editing = true }) },
        )
        PetProfileBody(data, Modifier.weight(1f))
    }
}

private enum class Editor { BIRTHDAY, GOTCHA, SEX, MICROCHIP, VET, WEIGHT }

@Composable
private fun PetProfileBody(data: DogData, modifier: Modifier) {
    val app = rememberApp()
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val dog = data.dog
    var editor by remember { mutableStateOf<Editor?>(null) }
    fun save(updated: Dog) {
        haptics.success()
        app.scope.launch { app.repository.saveDog(updated) }
    }

    Column(modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 40.dp)) {
        // The pet.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))) {
                MiniFace(rememberPhoto(dog.cutoutPath ?: dog.photoPath, 300), size = 72.dp, rim = 4.dp, fill = colors.track)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dog.name, style = opaw(30.sp, FontWeight.Black), color = colors.ink, maxLines = 1)
                listOfNotNull(dog.breed.ifEmpty { null }, sexText(dog)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1)
                }
            }
        }

        // Age, weight, birthday and gotcha day.
        val birth = dog.birthDate?.atZone(zone)?.toLocalDate()
        val gotcha = dog.gotchaDate?.atZone(zone)?.toLocalDate()
        val lastWeighIn = weightPoints(data).lastOrNull()
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val age = birth?.let { Period.between(it, today) }
                FactCard(
                    "Age", Modifier.weight(1f),
                    value = age?.let { if (it.years > 0) "${it.years}" else "${maxOf(1, it.months)}" },
                    unit = age?.let { ageUnit(it) },
                    caption = birth?.let { "Born ${formatDate(it, "d MMM yyyy")}" } ?: "Add birthday",
                ) { editor = Editor.BIRTHDAY }
                val kg = dog.weightKg
                FactCard(
                    "Weight", Modifier.weight(1f),
                    value = kg?.let { DecimalFormat("0.#").format(OpawFormat.weightValue(it)) },
                    unit = kg?.let { " ${OpawFormat.weightUnit}" },
                    caption = lastWeighIn?.let { "Weighed ${formatInstant(it.first, "d MMM")}" } ?: if (kg == null) "Log weight" else "Not weighed yet",
                ) { editor = Editor.WEIGHT }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FactCard(
                    "Birthday", Modifier.weight(1f),
                    value = birth?.let { formatDate(it, "d MMM") },
                    caption = birth?.let { untilNext(it, today) } ?: "Add date",
                ) { editor = Editor.BIRTHDAY }
                FactCard(
                    "Gotcha day", Modifier.weight(1f),
                    value = gotcha?.let { formatDate(it, "d MMM") },
                    caption = gotcha?.let { withYou(it, today) } ?: "Add the day they came home",
                ) { editor = Editor.GOTCHA }
            }
        }

        // Weight, last 6 months.
        val sixMonthsAgo = today.minusDays(HistoryRange.SIX_MONTHS.days).atStartOfDay(zone).toInstant()
        val points = weightPoints(data).filter { it.first >= sixMonthsAgo }
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().whiteCard().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weight, last 6 months", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(44.dp).pressable { editor = Editor.WEIGHT }.background(Action, CircleShape).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Log weight", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White) }
            }
            if (points.size < 2) {
                Text(
                    if (points.isEmpty()) "Log a weight to start the line." else "One more weigh-in and the line appears.",
                    style = opaw(14.sp, FontWeight.SemiBold), color = colors.muted, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                )
            } else {
                WeightChart(points, HistoryRange.SIX_MONTHS)
            }
        }

        // Care score, streak and longest walk.
        val longestMeters = data.walks.maxOfOrNull { it.distanceMeters } ?: 0.0
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().whiteCard().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatColumn("${data.careReport(now).score}", "Care score", Modifier.weight(1f), labelSize = 13)
            Box(Modifier.width(1.dp).height(38.dp).background(colors.hairline))
            val streak = data.careStreak
            StatColumn(if (streak == 1) "1 day" else "$streak days", "Streak", Modifier.weight(1f), labelSize = 13)
            Box(Modifier.width(1.dp).height(38.dp).background(colors.hairline))
            StatColumn(if (longestMeters > 0) OpawFormat.distance(longestMeters) else "None", "Longest walk", Modifier.weight(1f), labelSize = 13)
        }

        // Sex, microchip and vet.
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 4.dp)) {
            DetailRow("Sex", sexText(dog), "Add") { editor = Editor.SEX }
            Hairline()
            DetailRow("Microchip", dog.microchip.ifEmpty { null }, "Add number") { editor = Editor.MICROCHIP }
            Hairline()
            DetailRow("Vet", dog.vetClinic.ifEmpty { null }, "Add clinic") { editor = Editor.VET }
        }
    }

    when (editor) {
        Editor.BIRTHDAY -> DayPicker(dog.birthDate, onDismiss = { editor = null }) { save(dog.copy(birthDate = it)) }
        Editor.GOTCHA -> DayPicker(dog.gotchaDate, onDismiss = { editor = null }) { save(dog.copy(gotchaDate = it)) }
        Editor.SEX -> SexDialog(dog, onDismiss = { editor = null }) { save(it) }
        Editor.MICROCHIP -> TextDialog("Microchip number", dog.microchip, KeyboardOptions(capitalization = KeyboardCapitalization.Characters), onDismiss = { editor = null }) {
            save(dog.copy(microchip = it))
        }
        Editor.VET -> TextDialog("Vet clinic", dog.vetClinic, KeyboardOptions(capitalization = KeyboardCapitalization.Words), onDismiss = { editor = null }) {
            save(dog.copy(vetClinic = it))
        }
        Editor.WEIGHT -> CareLogSheet(data, CareKind.WEIGHT) { editor = null }
        null -> Unit
    }
}

/** "Female · spayed", "Male", or null when not set. */
private fun sexText(dog: Dog): String? {
    if (dog.sex.isEmpty()) return null
    val sex = dog.sex.replaceFirstChar(Char::uppercase)
    if (!dog.isNeutered) return sex
    return "$sex · ${if (dog.sex == "female") "spayed" else "neutered"}"
}

/** " yrs 8 mo", " yr", " mo": the unit after the big number on the Age card. */
private fun ageUnit(age: Period): String {
    if (age.years == 0) return if (maxOf(1, age.months) == 1) " month" else " months"
    val years = if (age.years == 1) " yr" else " yrs"
    return if (age.months > 0) "$years ${age.months} mo" else years
}

/** "Today!", "Tomorrow", "In 12 days" or "In 4 months". */
private fun untilNext(birth: LocalDate, today: LocalDate): String {
    var next = birth.withYear(today.year)
    if (next.isBefore(today)) next = next.plusYears(1)
    val days = ChronoUnit.DAYS.between(today, next)
    val months = ChronoUnit.MONTHS.between(today, next)
    return when {
        days == 0L -> "Today!"
        days == 1L -> "Tomorrow"
        months < 1 -> "In $days days"
        months == 1L -> "In 1 month"
        else -> "In $months months"
    }
}

/** "3 years with you", "5 months with you" or "Came home today". */
private fun withYou(gotcha: LocalDate, today: LocalDate): String {
    val period = Period.between(gotcha, today)
    return when {
        period.isNegative || gotcha == today -> "Came home today"
        period.years > 0 -> if (period.years == 1) "1 year with you" else "${period.years} years with you"
        period.months > 0 -> if (period.months == 1) "1 month with you" else "${period.months} months with you"
        else -> ChronoUnit.DAYS.between(gotcha, today).let { if (it == 1L) "1 day with you" else "$it days with you" }
    }
}

/** A white card: a label, a big value with a small unit, and a caption. Without a value it invites adding one. */
@Composable
private fun FactCard(label: String, modifier: Modifier, value: String?, unit: String? = null, caption: String, onClick: () -> Unit) {
    val colors = Opaw.colors
    Column(
        modifier.pressable(onClick = onClick).whiteCard(26.dp).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
        if (value != null) {
            Text(
                buildAnnotatedString {
                    append(value)
                    unit?.let { withStyle(SpanStyle(fontSize = 15.sp, color = colors.muted)) { append(it) } }
                },
                style = opaw(26.sp, FontWeight.Black), color = colors.ink, maxLines = 1,
            )
        } else {
            Text("Add", style = opaw(26.sp, FontWeight.Black), color = colors.ghost, maxLines = 1)
        }
        Text(caption, style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1)
    }
}

@Composable
private fun DetailRow(title: String, value: String?, placeholder: String, onClick: () -> Unit) {
    val colors = Opaw.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, modifier = Modifier.weight(1f))
        Text(value ?: placeholder, style = opaw(15.sp, FontWeight.ExtraBold), color = if (value != null) colors.ink else colors.muted, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(initial: Instant?, onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    val utc = ZoneOffset.UTC
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now().atStartOfDay(utc).toInstant().toEpochMilli()
    val start = initial?.atZone(zone)?.toLocalDate() ?: LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay(utc).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(utc).toLocalDate().atStartOfDay(zone).toInstant()) }
                onDismiss()
            }) { Text("Done", color = Opaw.colors.ink) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Opaw.colors.muted) } },
    ) { DatePicker(state, showModeToggle = false) }
}

@Composable
private fun SexDialog(dog: Dog, onDismiss: () -> Unit, onSave: (Dog) -> Unit) {
    val colors = Opaw.colors
    var sex by remember { mutableStateOf(dog.sex) }
    var fixed by remember { mutableStateOf(dog.isNeutered) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Sex", style = opaw(19.sp, FontWeight.Black)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Female", sex == "female") { sex = "female" }
                    ChoiceChip("Male", sex == "male") { sex = "male" }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sex == "male") "Neutered" else "Spayed", style = opaw(16.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
                    DesignToggle(fixed, { fixed = it })
                }
            }
        },
        confirmButton = {
            TextButton({
                onSave(dog.copy(sex = sex, isNeutered = fixed))
                onDismiss()
            }, enabled = sex.isNotEmpty()) { Text("Save", color = colors.ink, style = opaw(15.sp, FontWeight.ExtraBold)) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.muted) } },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, keyboard: KeyboardOptions, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = Opaw.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, style = opaw(19.sp, FontWeight.Black)) },
        text = {
            PlainField(
                text, { text = it }, title, opaw(17.sp, FontWeight.SemiBold),
                Modifier.fillMaxWidth().background(colors.chip, CircleShape).padding(horizontal = 16.dp, vertical = 14.dp),
                keyboard, placeholderColor = colors.ghost,
            )
        },
        confirmButton = {
            TextButton({
                onSave(text.trim())
                onDismiss()
            }) { Text("Save", color = colors.ink, style = opaw(15.sp, FontWeight.ExtraBold)) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = colors.muted) } },
    )
}
