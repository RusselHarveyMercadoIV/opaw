package app.opaw.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.CareKind
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** One choice on the "What should we track?" grid. */
data class TrackOption(val title: String, val icon: DesignIcon, val keys: List<String>) {
    companion object {
        const val WALK_KEY = "walks"

        val all = listOf(
            TrackOption("Meals", DesignIcon.MEAL, listOf(CareKind.MEAL.raw)),
            TrackOption("Water", DesignIcon.WATER, listOf(CareKind.WATER.raw)),
            TrackOption("Walks", DesignIcon.ACTIVITY, listOf(WALK_KEY)),
            TrackOption("Poop", DesignIcon.POOP, listOf(CareKind.POOP.raw, CareKind.PEE.raw)),
            TrackOption("Meds", DesignIcon.MEDS, listOf(CareKind.MEDS.raw)),
            TrackOption("Grooming", DesignIcon.BATH, listOf(CareKind.BATH.raw, CareKind.BRUSH.raw, CareKind.NAILS.raw)),
            TrackOption("Weight", DesignIcon.WEIGHT, listOf(CareKind.WEIGHT.raw)),
            TrackOption("Vet visits", DesignIcon.VET, listOf(CareKind.VET.raw)),
        )
    }
}

/**
 * The four-across grid of things to track. Tapping a circle turns it on or off. On a white page,
 * [offFill] colours the circles that are off (light grey, flat) so they still show.
 */
@Composable
fun TrackGrid(trackedKinds: List<String>, onChange: (List<String>) -> Unit, keepsOne: Boolean = false, offFill: Color? = null) {
    val haptics = rememberHaptics()
    fun isOn(option: TrackOption) = option.keys.all { it in trackedKinds }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TrackOption.all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val on = isOn(option)
                    Column(
                        Modifier.weight(1f).pressable {
                            if (on) {
                                if (keepsOne && TrackOption.all.count(::isOn) <= 1) {
                                    haptics.soft()
                                    return@pressable
                                }
                                onChange(trackedKinds.filter { it !in option.keys })
                            } else {
                                onChange(trackedKinds + option.keys.filter { it !in trackedKinds })
                            }
                            haptics.select()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val flat = !on && offFill != null
                        Box(
                            Modifier
                                .size(68.dp)
                                .then(if (flat) Modifier else Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f)))
                                .background(if (on) Action else offFill ?: Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(option.icon, size = 26.dp, weight = if (option.icon == DesignIcon.ACTIVITY) null else 2.3f, tint = if (on) Color.White else Ink)
                        }
                        Text(option.title, style = opaw(13.sp, FontWeight.Bold), color = Opaw.colors.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "07:30" from minutes after midnight. */
fun clockText(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

/** The "Meal times" card: a chip per meal, tap one to change or remove it, + to add one (up to five). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTimesCard(mealTimes: List<Int>, onChange: (List<Int>) -> Unit, weightKg: Double?, species: String = "dog") {
    val colors = Opaw.colors
    // null = closed, -1 = adding, otherwise the index being edited.
    var editing by remember { mutableStateOf<Int?>(null) }
    val sorted = mealTimes.sorted()

    Column(
        Modifier.fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Meal times", style = opaw(17.sp, FontWeight.Black), color = colors.ink)
            Spacer(Modifier.weight(1f))
            portionGrams(weightKg, sorted.size, species)?.let {
                Text("$it g each", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            sorted.forEachIndexed { index, time ->
                Text(
                    clockText(time),
                    style = opaw(15.sp, FontWeight.ExtraBold),
                    color = colors.ink,
                    modifier = Modifier
                        .pressable { editing = index }
                        .background(colors.chip, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (sorted.size < 5) {
                Box(
                    Modifier.size(42.dp).pressable { editing = -1 }.background(colors.chip, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.PLUS, size = 18.dp) }
            }
        }
    }

    editing?.let { index -> MealTimeDialog(sorted, index, onChange) { editing = null } }
}

/**
 * The meal times as a list (10h): a row per meal with its name and time, then "+ Add a meal".
 * Tap a time to change or remove it. The card's look comes from [modifier].
 */
@Composable
fun MealTimesList(mealTimes: List<Int>, onChange: (List<Int>) -> Unit, weightKg: Double?, species: String = "dog", modifier: Modifier = Modifier) {
    val colors = Opaw.colors
    var editing by remember { mutableStateOf<Int?>(null) }
    val sorted = mealTimes.sorted()
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Meal times", style = opaw(16.sp, FontWeight.Black), color = colors.ink)
            Spacer(Modifier.weight(1f))
            portionGrams(weightKg, 1, species)?.let { daily ->
                Text("About $daily g a day", style = opaw(12.sp, FontWeight.Bold), color = colors.muted)
            }
        }
        sorted.forEachIndexed { index, time ->
            Row(Modifier.fillMaxWidth().pressable { editing = index }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Action.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(DesignIcon.MEAL, size = 20.dp, tint = Action)
                }
                Text(mealName(time, index, sorted.size), style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
                Text(
                    clockText(time), style = opaw(15.sp, FontWeight.Black), color = colors.ink,
                    modifier = Modifier.background(colors.cream, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (sorted.size < 5) {
            Text("+ Add a meal", style = opaw(14.sp, FontWeight.ExtraBold), color = Action, modifier = Modifier.pressable { editing = -1 })
        }
    }
    editing?.let { index -> MealTimeDialog(sorted, index, onChange) { editing = null } }
}

/** "Breakfast" before 10, "Lunch" before 3, "Dinner" after; "Meal 2" when two fall in the same part of the day. */
private fun mealName(minutes: Int, index: Int, count: Int): String {
    val part = when {
        minutes < 10 * 60 -> "Breakfast"
        minutes < 15 * 60 -> "Lunch"
        else -> "Dinner"
    }
    return if (count > 3) "Meal ${index + 1} · ${part.lowercase()}" else part
}

/** Change, add (index -1) or remove one meal time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTimeDialog(sorted: List<Int>, index: Int, onChange: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    val colors = Opaw.colors
    val initial = if (index >= 0) sorted[index] else 12 * 60
    val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cream,
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    selectorColor = colors.action,
                    timeSelectorSelectedContainerColor = colors.action,
                    timeSelectorSelectedContentColor = colors.onAction,
                    clockDialSelectedContentColor = colors.onAction,
                ),
            )
        },
        confirmButton = {
            TextButton({
                val minutes = state.hour * 60 + state.minute
                val times = sorted.toMutableList()
                if (index >= 0) times[index] = minutes else if (minutes !in times) times += minutes
                onChange(times.distinct().sorted())
                onDismiss()
            }) { Text("Done", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink) }
        },
        dismissButton = {
            if (index >= 0 && sorted.size > 1) {
                TextButton({
                    onChange(sorted.filterIndexed { i, _ -> i != index })
                    onDismiss()
                }) { Text("Remove", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.alert) }
            }
        },
    )
}

/** Rough dry-food grams per meal: resting energy × 1.6, at about 3.6 kcal a gram. */
private fun portionGrams(weightKg: Double?, meals: Int, species: String): Int? {
    val kg = weightKg ?: return null
    if (kg <= 0 || meals == 0 || species != "dog") return null
    val kcal = 70 * kg.pow(0.75) * 1.6
    val grams = kcal / 3.6 / meals
    return (grams / 5).roundToInt() * 5
}
