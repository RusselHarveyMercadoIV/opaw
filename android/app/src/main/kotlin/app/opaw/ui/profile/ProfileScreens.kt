package app.opaw.ui.profile

import app.opaw.ui.home.AvatarClipStore
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.core.Badge
import app.opaw.core.DogSize
import app.opaw.core.EnergyLevel
import app.opaw.core.OpawFormat
import app.opaw.core.StreakCalculator
import app.opaw.data.DogData
import app.opaw.data.medal
import app.opaw.data.medalLabel
import app.opaw.services.NotificationService
import app.opaw.services.PhotoStore
import app.opaw.services.Premium
import app.opaw.services.WalkRecorder
import app.opaw.services.WidgetRefresher
import app.opaw.services.formatDate
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.care.PlainField
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.RowIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPhoto
import app.opaw.BuildConfig
import app.opaw.OpawApplication
import app.opaw.ui.rememberActiveDog
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import app.opaw.ui.walks.GoalRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val HELP_URL = "https://github.com/RusselHarveyMercadoIV/opaw/issues"

/** Settings (5e): the pet, the Premium banner for free users, then household, settings and Premium features. */
@Composable
fun ProfileScreen(dogId: String) {
    val app = rememberApp()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val colors = Opaw.colors
    val data = rememberDogData(dogId)
    val soundsOn by app.settings.soundsOn.collectAsStateWithLifecycle(true)
    val hapticsOn by app.settings.hapticsOn.collectAsStateWithLifecycle(true)
    var editing by remember { mutableStateOf(false) }
    var showWidgetHelp by remember { mutableStateOf(false) }
    var showBackupNote by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val isPremium = Premium.isPremium
    fun paywall() = navigator.push(Route.Paywall)
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    if (editing && data != null) {
        EditDogScreen(data) { editing = false }
        return
    }

    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Settings", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data?.let { d ->
                Row(Modifier.fillMaxWidth().pressable { editing = true }.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))) {
                        MiniFace(rememberPhoto(d.dog.cutoutPath ?: d.dog.photoPath, 300), size = 64.dp, rim = 4.dp, fill = colors.track)
                    }
                    Column {
                        Text(d.dog.name, style = opaw(22.sp, FontWeight.Black), color = colors.ink)
                        Text(subtitle(d, true), style = opaw(14.sp, FontWeight.Medium), color = colors.muted, maxLines = 1)
                    }
                }
            }
            if (!isPremium) {
                Row(
                    Modifier.fillMaxWidth().height(50.dp).pressable(onClick = ::paywall).background(Ink, CircleShape).padding(start = 18.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DesignIcon.SPARKLE, size = 18.dp, weight = 2.4f, tint = Color.White)
                    Text("Try Opaw Premium", style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.weight(1f))
                    Text("7 days free", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFFAAAAAA))
                    Icon(DesignIcon.CHEVRON_RIGHT, size = 16.dp, weight = 2.6f, tint = Color.White)
                }
            }
            Card {
                SettingsRow(DesignIcon.USERS, "Household", first = true) { navigator.push(Route.Household(dogId)) }
                SettingsRow(DesignIcon.BELL, "Notifications") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                SettingsRow(DesignIcon.RULER, "Units") { navigator.push(Route.Units) }
                SettingsRow(DesignIcon.HEART, "Friends nearby") { navigator.push(Route.Pack) }
            }
            Card {
                ToggleRow(DesignIcon.SPEAKER, "Sounds", soundsOn, first = true) { app.scope.launch { app.settings.setSoundsOn(it) } }
                ToggleRow(DesignIcon.VIBRATE, "Haptics", hapticsOn) { app.scope.launch { app.settings.setHapticsOn(it) } }
            }
            Card {
                SettingsRow(DesignIcon.GRID, "Home Screen widgets", first = true, locked = !isPremium) { if (isPremium) showWidgetHelp = true else paywall() }
                SettingsRow(DesignIcon.CLOUD, "Backup", locked = !isPremium) { if (isPremium) showBackupNote = true else paywall() }
                SettingsRow(DesignIcon.CHART, "Full history", locked = !isPremium) { if (isPremium) navigator.push(Route.History(dogId)) else paywall() }
            }
            Card {
                SettingsRow(DesignIcon.SPARKLE, "Subscription", value = if (isPremium) "Premium" else "Free", first = true) { navigator.push(Route.Subscription) }
                SettingsRow(DesignIcon.HELP, "Help") { open(HELP_URL) }
            }
            if (BuildConfig.DEBUG) {
                Card {
                    ToggleRow(DesignIcon.SPARKLE, "Premium (test build)", app.premium.testPremium, first = true) {
                        haptics.tap()
                        app.premium.switchTestPremium(it)
                    }
                }
                Text("Only in test builds. Turns Premium on without Google Play so you can try every feature.",
                    style = opaw(13.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Card {
                Row(Modifier.fillMaxWidth().clickable { confirmReset = true }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(colors.alert.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(DesignIcon.REFRESH, size = 17.dp, tint = colors.alert)
                    }
                    Text("Reset data", style = opaw(16.sp, FontWeight.Bold), color = colors.alert)
                }
            }
        }
    }

    if (showWidgetHelp) InfoDialog("Add an Opaw widget", "Touch and hold your home screen, tap Widgets, then find Opaw and drag it into place.") { showWidgetHelp = false }
    if (showBackupNote) InfoDialog("Backup", "Backup to the cloud is coming soon. For now, everything stays on this phone.") { showBackupNote = false }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = colors.surface,
            title = { Text("Reset all data?", style = opaw(19.sp, FontWeight.Black)) },
            text = {
                Text("This deletes your pets, logs, walks, memories and reminders on this phone, then starts setup again. It can't be undone. Your subscription isn't affected.",
                    style = opaw(15.sp, FontWeight.Medium))
            },
            confirmButton = {
                TextButton({
                    confirmReset = false
                    haptics.success()
                    navigator.popToRoot()
                    app.scope.launch {
                        // Let Settings close and the tabs leave the screen before anything is deleted.
                        delay(1000)
                        eraseAll(app)
                    }
                }) { Text("Erase everything", color = colors.alert, style = opaw(15.sp, FontWeight.ExtraBold)) }
            },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel", color = colors.ink) } },
        )
    }
}

/** Settings > Reset data: erases everything Opaw keeps on this phone so setup starts again. Mirrors DataReset.swift. */
private suspend fun eraseAll(app: OpawApplication) {
    if (WalkRecorder.state != WalkRecorder.State.IDLE) WalkRecorder.cancel()
    val offerShown = app.settings.premiumOfferShown.first()
    app.repository.allDogs().forEach { dog ->
        PhotoStore.delete(app, dog.photoPath)
        PhotoStore.delete(app, dog.cutoutPath)
    }
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.database.clearAllTables() }
    app.settings.clear()
    if (offerShown) app.settings.setPremiumOfferShown(true)
    app.settings.syncFormat()
    NotificationService.reschedule(app)
    WidgetRefresher.refresh(app)
}

private fun subtitle(data: DogData, includePlan: Boolean): String {
    val dog = data.dog
    val parts = mutableListOf<String>()
    if (dog.breed.isNotEmpty()) parts += dog.breed
    if (dog.birthDate != null) {
        val years = dog.ageYears.toInt()
        parts += if (years < 1) "${maxOf(1, (dog.ageYears * 12).toInt())} mo" else if (years == 1) "1 yr" else "$years yrs"
    }
    if (includePlan) parts += if (Premium.isPremium) "Premium" else "Free plan"
    return parts.joinToString(" · ")
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Opaw.colors.surface,
        title = { Text(title, style = opaw(19.sp, FontWeight.Black)) },
        text = { Text(text, style = opaw(15.sp, FontWeight.Medium)) },
        confirmButton = { TextButton(onDismiss) { Text("OK", color = Opaw.colors.ink) } },
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 4.dp), content = content)
}

@Composable
private fun SettingsRow(icon: DesignIcon, title: String, value: String? = null, first: Boolean = false, locked: Boolean = false, onClick: () -> Unit) {
    val colors = Opaw.colors
    if (!first) Hairline()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Text(title, style = opaw(16.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = opaw(14.sp, FontWeight.SemiBold), color = colors.muted) }
        if (locked) PremiumTag() else Icon(DesignIcon.CHEVRON_RIGHT, size = 16.dp, weight = 2.6f, tint = colors.ghost)
    }
}

@Composable
private fun ToggleRow(icon: DesignIcon, title: String, isOn: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = Opaw.colors
    if (!first) Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Text(title, style = opaw(16.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
        DesignToggle(isOn, onChange)
    }
}

/** The small "Premium" tag with a lock, on rows free users can't open yet. */
@Composable
fun PremiumTag() {
    Row(Modifier.background(Opaw.colors.chip, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(DesignIcon.LOCK, size = 12.dp, weight = 2.8f, tint = Color(0xFF888888))
        Text("Premium", style = opaw(12.sp, FontWeight.ExtraBold), color = Color(0xFF888888))
    }
}

/** Profile > Units: kilometres and kilograms, or miles and pounds. */
@Composable
fun UnitsScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val colors = Opaw.colors
    val imperial by app.settings.usesImperial.collectAsStateWithLifecycle(OpawFormat.usesImperial)
    fun set(value: Boolean) {
        haptics.select()
        app.scope.launch { app.settings.setUsesImperial(value); WidgetRefresher.refresh(app) }
    }
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Units", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 4.dp)) {
            for ((index, option) in listOf(Triple("Metric", "Kilometres and kilograms", false), Triple("Imperial", "Miles and pounds", true)).withIndex()) {
                if (index > 0) Hairline()
                val selected = option.third == imperial
                Row(Modifier.fillMaxWidth().clickable { set(option.third) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.first, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                        Text(option.second, style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
                    }
                    if (selected) {
                        Box(Modifier.size(22.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) { Icon(DesignIcon.CHECK, size = 12.dp, tint = Color.White) }
                    } else {
                        Box(Modifier.size(22.dp).border(2.dp, Color(0xFFD4D4D8), CircleShape))
                    }
                }
            }
        }
    }
}

/** Edit a pet's basics and activity goal, or delete them. Mirrors `EditDogView`. */
@Composable
internal fun EditDogScreen(data: DogData, onDone: () -> Unit) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val colors = Opaw.colors
    var dog by remember { mutableStateOf(data.dog) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun close() {
        app.scope.launch { app.repository.saveDog(dog) }
        onDone()
    }
    androidx.activity.compose.BackHandler(onBack = ::close)
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Edit ${dog.name}", leading = { RoundIcon(DesignIcon.BACK, onClick = ::close) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            Label("Basics")
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                PlainField(dog.name, { dog = dog.copy(name = it) }, "Name", opaw(16.sp, FontWeight.SemiBold), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    KeyboardOptions(capitalization = KeyboardCapitalization.Words), placeholderColor = colors.ghost)
                Hairline()
                PlainField(dog.breed, { dog = dog.copy(breed = it) }, "Breed", opaw(16.sp, FontWeight.SemiBold), Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    KeyboardOptions(capitalization = KeyboardCapitalization.Words), placeholderColor = colors.ghost)
                Hairline()
                val zone = ZoneId.systemDefault()
                BirthdayRow(dog.birthDate?.atZone(zone)?.toLocalDate() ?: LocalDate.now()) { dog = dog.copy(birthDate = it.atStartOfDay(zone).toInstant()) }
                Hairline()
                Text("Size", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DogSize.entries.forEach { ChoiceChip(it.title, it == dog.size) { dog = dog.copy(sizeRaw = it.raw) } } }
                Text("Energy", style = opaw(14.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyLevel.entries.forEach { ChoiceChip(it.title, it == dog.energy) { dog = dog.copy(energyRaw = it.raw) } }
                }
            }
            Label("Goals")
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily activity: ${dog.dailyGoalMinutes} min", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                    StepButton("−") { dog = dog.copy(dailyGoalMinutes = (dog.dailyGoalMinutes - 5).coerceIn(10, 240)) }
                    Spacer(Modifier.width(8.dp))
                    StepButton("+") { dog = dog.copy(dailyGoalMinutes = (dog.dailyGoalMinutes + 5).coerceIn(10, 240)) }
                }
                Hairline()
                val recommended = dog.traits.recommendedDailyActivityMinutes
                Text("Use recommended ($recommended min)", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink,
                    modifier = Modifier.fillMaxWidth().clickable { dog = dog.copy(dailyGoalMinutes = recommended) }.padding(vertical = 14.dp))
            }
            Text("A starting point based on size, age and energy. Your vet knows best.", style = opaw(13.sp, FontWeight.Medium), color = colors.muted,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Text("Delete ${dog.name}", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.alert, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).clickable { confirmDelete = true }.padding(vertical = 14.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete ${dog.name} and all their logs, walks and memories?", style = opaw(18.sp, FontWeight.Black)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    app.scope.launch {
                        PhotoStore.delete(app, dog.photoPath)
                        PhotoStore.delete(app, dog.cutoutPath)
                        AvatarClipStore.deleteAll(app, dog.id)
                        app.petModels.delete(dog.id)
                        app.database.dogs().delete(dog)
                        app.settings.setActiveDog(null)
                        NotificationService.reschedule(app)
                        WidgetRefresher.refresh(app)
                    }
                    navigator.popToRoot()
                }) { Text("Delete", color = colors.alert, style = opaw(15.sp, FontWeight.ExtraBold)) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = colors.ink) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = opaw(13.sp, FontWeight.Bold), color = Opaw.colors.muted, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp))
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp, 34.dp).pressable(onClick = onClick).background(Opaw.colors.chip, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(label, style = opaw(18.sp, FontWeight.Black), color = Opaw.colors.ink)
    }
}

// MARK: - Challenges (2f)

/** Challenges & badges (2f): this month's walking goal, the week-long logging challenge, and the badge collection. */
@Composable
fun ChallengesScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val colors = Opaw.colors
    val (_, data) = rememberActiveDog()
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Challenges", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        val d = data ?: return@Column
        val newestRaw by app.settings.newestBadge(d.dog.id).collectAsStateWithLifecycle(null)
        // The newest badge counts as new for three days.
        val newest = newestRaw?.let { Badge.fromRaw(it) }?.takeIf { d.dog.lastBadgeEarnedAt?.let { at -> ChronoUnit.HOURS.between(at, java.time.Instant.now()) < 72 } == true }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 40.dp)) {
            MonthCard(d, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
            StreakCard(d, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            val earned = d.dog.earnedBadges
            Row(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Text("Badges", style = opaw(21.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                Text("${earned.size} of ${Badge.entries.size}", style = opaw(14.sp, FontWeight.Bold), color = colors.muted)
            }
            // Earned first (newest at the front), then the rest.
            val ordered = Badge.entries.sortedBy { if (it == newest) 0 else if (it in earned) 1 else 2 }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ordered.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { BadgeMedal(it, it in earned, it == newest, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val oneDecimal = DecimalFormat("0.0")

@Composable
private fun MonthCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.withDayOfMonth(1)
    val end = start.plusMonths(1)
    val goal = maxOf(5.0, (data.dog.traits.recommendedWeeklyKm * 4.3 / 5).roundToInt() * 5.0)
    val done = data.walkedKm(start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
    val daysLeft = maxOf(0, ChronoUnit.DAYS.between(today, end).toInt())
    Row(modifier.fillMaxWidth().whiteCard(34.dp).padding(22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        GoalRing(done / goal, oneDecimal.format(OpawFormat.longDistance(done)), "of ${OpawFormat.longDistance(goal).roundToInt()} ${OpawFormat.distanceUnit}", Modifier.size(120.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatDate(today, "MMMM"), style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
            Text("Walk ${goal.toInt()} km this month", style = opaw(22.sp, FontWeight.Black), color = colors.ink)
            Text(if (daysLeft == 1) "1 day left" else "$daysLeft days left", style = opaw(14.sp, FontWeight.SemiBold), color = colors.softText)
        }
    }
}

@Composable
private fun StreakCard(data: DogData, modifier: Modifier) {
    val colors = Opaw.colors
    val streak = minOf(7, StreakCalculator.currentStreak(data.careEvents.map { it.date }))
    Column(modifier.fillMaxWidth().whiteCard().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Log every day for a week", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
            Text("$streak/7", style = opaw(16.sp, FontWeight.Black), color = colors.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { index -> Box(Modifier.weight(1f).height(10.dp).background(if (index < streak) colors.ink else colors.hairline, CircleShape)) }
        }
        Text("Reward: ${data.dog.name} earns the ${Badge.CARE_STREAK_7.title} badge", style = opaw(14.sp, FontWeight.Medium), color = colors.muted)
    }
}

/** A 94pt round badge: black when earned, red "NEW" when just earned, white while still to earn. */
@Composable
private fun BadgeMedal(badge: Badge, earned: Boolean, isNew: Boolean, modifier: Modifier) {
    val colors = Opaw.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fill = if (isNew) colors.alert else if (earned) Ink else colors.surface
        val shadow = if (isNew) colors.alert.copy(alpha = 0.35f) else Color.Black.copy(alpha = if (earned) 0.2f else 0.08f)
        Box(Modifier.size(94.dp).shadow(12.dp, CircleShape, ambientColor = shadow, spotColor = shadow).background(fill, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (isNew) "NEW" else badge.medal, style = opaw(26.sp, FontWeight.Black), color = if (earned || isNew) Color.White else colors.ink.copy(alpha = 0.35f), maxLines = 1)
        }
        Text(if (isNew) badge.title else badge.medalLabel, style = opaw(14.sp, FontWeight.Bold), color = if (earned || isNew) colors.ink else colors.muted,
            textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Household, from Settings. */
@Composable
fun HouseholdScreen(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val colors = Opaw.colors
    val data = rememberDogData(dogId)
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Household", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 60.dp)) {
            Text("Everyone logs to the same pet, so meals never get doubled.", style = opaw(15.sp, FontWeight.Medium), color = colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp))
            HouseholdCards(data?.dog?.name ?: "your pet", app.settings)
        }
    }
}
