package app.opaw.ui.pack

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.core.OpawFormat
import app.opaw.services.CareRecorder
import app.opaw.services.Notice
import app.opaw.services.PackDog
import app.opaw.services.PackPost
import app.opaw.services.RemoteImages
import app.opaw.services.WalkRecorder
import app.opaw.ui.LocalNavigator
import app.opaw.ui.care.PlainField
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.DesignToggle
import app.opaw.ui.components.FilterPill
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.StatColumn
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.ToastHost
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.walks.MapCamera
import app.opaw.ui.walks.OpawMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private const val PRIVACY_URL = "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md"

/** The social screen: dogs nearby on a map, with join, profile, report and block. Mirrors PackView.swift. */
@Composable
fun PackScreen() {
    val app = rememberApp()
    val pack = app.pack
    val navigator = LocalNavigator.current
    val colors = Opaw.colors
    when {
        !pack.isConfigured -> Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
            ScreenHeader("Pack", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(DesignIcon.USERS, size = 44.dp, tint = colors.muted)
                Text("The Pack is almost ready", style = opaw(22.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
                Text("Connect Opaw to its Supabase server to meet dogs nearby. See docs/SETUP.md in the project.", style = opaw(15.sp, FontWeight.Medium),
                    color = colors.muted, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
        }
        !pack.isSignedIn || !pack.hasAcceptedRules -> JoinPack()
        else -> PackHome()
    }
}

// MARK: - Joining

private val rules = listOf(
    "Be kind. No bullying, hate or harassment.",
    "Only post your own photos, and keep them about pets.",
    "No spam, ads or selling animals.",
    "Never share someone else's location or personal details.",
    "Report anything that breaks these rules. We review reports within 24 hours and remove offenders.",
)

@Composable
private fun JoinPack() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var name by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        ScreenHeader("Join the pack", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Meet dogs nearby, share walks and cheer each other on. Your exact location is never shared: other people only see a rough area, and the start and end of shared walks are hidden.",
                style = opaw(15.sp, FontWeight.Medium).copy(lineHeight = 21.sp), color = colors.ink,
            )
            Text("Your name in the pack", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
            PlainField(name, { name = it }, "e.g. Max's human", opaw(16.sp, FontWeight.SemiBold),
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp), placeholderColor = colors.ghost)
            Text("Community rules", style = opaw(13.sp, FontWeight.Bold), color = colors.muted)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rules.forEach { rule ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(DesignIcon.CHECK, size = 16.dp, weight = 3f)
                        Text(rule, style = opaw(14.sp, FontWeight.Medium), color = colors.ink)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I agree to the community rules", style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
                    DesignToggle(accepted, { accepted = it })
                }
            }
            error?.let { Text(it, style = opaw(14.sp, FontWeight.Medium), color = colors.alert) }
            if (joining) {
                Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.ink) }
            } else {
                BlackPillButton("Join the pack", enabled = accepted && name.isNotBlank(), height = 62.dp) {
                    joining = true
                    scope.launch {
                        runCatching { app.pack.join(name.trim()) }.onFailure { error = it.message }
                        joining = false
                    }
                }
            }
        }
    }
}

// MARK: - Nearby (2e)

@Composable
private fun PackHome() {
    val app = rememberApp()
    val pack = app.pack
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    val myDogs by app.repository.dogs.collectAsStateWithLifecycle(emptyList())
    var nearby by remember { mutableStateOf(true) }
    var dogs by remember { mutableStateOf<List<PackDog>>(emptyList()) }
    var posts by remember { mutableStateOf<List<PackPost>>(emptyList()) }
    var following by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<PackDog?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<PackDog?>(null) }
    var reporting by remember { mutableStateOf<PackDog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    val walkingNow = posts.filter { it.kind == "walk" && Duration.between(it.createdAt, Instant.now()).toHours() < 2 }.map { it.dogId }.toSet()
    val placed = dogs.filter { it.lat != null && it.lon != null }
    val shown = if (nearby) placed.filter { (it.distanceM ?: 0.0) <= 1000 } else placed.filter { it.id in walkingNow }

    DisposableEffect(Unit) {
        WalkRecorder.warmUp(context)
        onDispose { WalkRecorder.coolDown(context) }
    }

    suspend fun load() {
        val here = WalkRecorder.lastLocation
        if (here == null) {
            status = "Waiting for your location…"
            delay(2000)
            if (WalkRecorder.lastLocation != null) load()
            return
        }
        try {
            dogs = pack.nearbyDogs(here.latitude, here.longitude)
            following = pack.followedDogIds()
            posts = runCatching { pack.feed(100) }.getOrDefault(emptyList())
            status = if (dogs.isEmpty()) "No pets nearby yet. Invite your dog park friends!" else null
        } catch (error: Exception) {
            status = "Couldn't load pets nearby."
            app.toasts.show(Notice(Notice.Kind.FAILED, "Couldn't load pets nearby", if (app.toasts.isOffline) "You're offline" else "Check your connection", actionTitle = "Retry") {
                scope.launch { load() }
            })
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    fun shareMyDogs(): Unit {
        scope.launch {
            val here = WalkRecorder.lastLocation ?: run { status = "We need your location first."; return@launch }
            try {
                myDogs.forEach { pack.shareDog(it, here.latitude, here.longitude) }
                haptics.success()
            } catch (error: Exception) {
                app.toasts.show(Notice(Notice.Kind.FAILED, "Couldn't put your pet on the map", "Check your connection", actionTitle = "Retry") { shareMyDogs() })
            }
        }
    }

    fun wave(dog: PackDog): Unit {
        scope.launch {
            val follow = dog.id !in following
            try {
                pack.setFollowing(follow, dog.id)
                following = if (follow) following + dog.id else following - dog.id
                haptics.success()
                myDogs.forEach { CareRecorder.refreshBadges(app, it.id, friendsCount = following.size) }
            } catch (error: Exception) {
                app.toasts.show(Notice(Notice.Kind.FAILED, if (follow) "Couldn't wave at ${dog.name}" else "Couldn't update ${dog.name}", "Check your connection", actionTitle = "Retry") { wave(dog) })
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.cream)) {
        val here = WalkRecorder.lastLocation
        OpawMap(listOfNotNull(here), MapCamera.Follow, Modifier.fillMaxSize(), follow = here) { toScreen ->
            val density = LocalDensity.current
            here?.let { me ->
                toScreen(me.latitude, me.longitude)?.let { at ->
                    val half = with(density) { 45.dp.toPx() }
                    Box(Modifier.offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }.size(90.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(90.dp).background(colors.alert.copy(alpha = 0.14f), CircleShape))
                        Box(Modifier.size(20.dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(4.dp).background(colors.alert, CircleShape))
                    }
                }
            }
            shown.forEach { dog ->
                toScreen(dog.lat ?: 0.0, dog.lon ?: 0.0)?.let { at ->
                    val x = with(density) { 50.dp.toPx() }
                    val y = with(density) { 108.dp.toPx() }
                    Column(
                        Modifier.offset { IntOffset((at.x - x).roundToInt(), (at.y - y).roundToInt()) }.width(100.dp).pressable { selected = dog; haptics.select() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val walking = dog.id in walkingNow
                        StickerPet(rememberRemote(pack.publicUrl(dog.avatarPath)), Modifier.size(74.dp), edge = 4.dp,
                            motion = if (walking) LoopMotion.TROT else LoopMotion.BREATHE, duration = if (walking) 0.6 else 3.0)
                        Text(dog.name, style = opaw(13.sp, FontWeight.ExtraBold), color = Ink,
                            modifier = Modifier.shadow(6.dp, CircleShape).background(Color.White, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
            }
        }
        // Back button, then the filters where a title would go.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(DesignIcon.BACK, onClick = navigator::pop)
            FilterPill("Within 1 km", nearby) { nearby = true }
            FilterPill("Out walking", !nearby) { nearby = false }
            Spacer(Modifier.weight(1f))
            Box {
                Box(Modifier.size(44.dp).pressable { menu = true }.shadow(6.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text("•••", style = opaw(13.sp, FontWeight.Black), color = Ink)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Show my dog on the map") }, leadingIcon = { Icon(DesignIcon.LOCATION, size = 18.dp) }, onClick = { menu = false; shareMyDogs() })
                    DropdownMenuItem(text = { Text("Privacy policy") }, leadingIcon = { Icon(DesignIcon.LOCK, size = 18.dp) },
                        onClick = { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) })
                    DropdownMenuItem(text = { Text("Leave the pack and delete my account", color = colors.alert) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        // The card.
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
            val dog = selected ?: shown.firstOrNull()
            AnimatedContent(dog?.id ?: if (loading) "loading" else "status", transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "card") { key ->
                when {
                    dog != null && key == dog.id -> DogCard(dog, posts, dog.id in following, dog.id in walkingNow, onProfile = { profile = dog }, onWave = { wave(dog) })
                    key == "loading" -> LoadingCard()
                    status != null -> Text(status!!, style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(36.dp)).padding(20.dp))
                }
            }
        }
        // Nearby opens over the tab view, so it shows toasts itself.
        ToastHost(bottom = 24.dp)
    }

    profile?.let { dog ->
        DogProfileSheet(dog, onDismiss = { profile = null }, onReport = { profile = null; reporting = dog }) {
            profile = null
            scope.launch {
                runCatching { pack.block(dog.ownerId) }
                selected = null
                load()
            }
        }
    }
    reporting?.let { dog ->
        ReportSheet(dog.name, onDismiss = { reporting = null }) { reason -> pack.report(dogId = dog.id, reason = reason) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete your Pack account?", style = opaw(19.sp, FontWeight.Black)) },
            text = { Text("This removes your shared dogs, posts, likes and photos from the Pack. Your logs, walks and memories stay on this phone.", style = opaw(15.sp, FontWeight.Medium)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch { runCatching { pack.deleteAccount() }.onFailure { deleteError = it.message } }
                }) { Text("Delete account", color = colors.alert, style = opaw(15.sp, FontWeight.ExtraBold)) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = colors.ink) } },
        )
    }
    deleteError?.let {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            containerColor = colors.surface,
            title = { Text("Couldn't delete your account", style = opaw(19.sp, FontWeight.Black)) },
            text = { Text(it, style = opaw(15.sp, FontWeight.Medium)) },
            confirmButton = { TextButton({ deleteError = null }) { Text("OK", color = colors.ink) } },
        )
    }
}

@Composable
private fun rememberRemote(url: String?): Bitmap? {
    val image by produceState<Bitmap?>(null, url) { value = url?.let { RemoteImages.load(it) } }
    return image
}

@Composable
private fun PackAvatar(url: String?, size: Dp) {
    val image = rememberRemote(url)
    Box(Modifier.size(size).clip(CircleShape).background(Opaw.colors.chip), contentAlignment = Alignment.Center) {
        if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Icon(DesignIcon.PAW, size = size * 0.35f, tint = Opaw.colors.muted)
    }
}

@Composable
private fun DogCard(dog: PackDog, posts: List<PackPost>, isFriend: Boolean, walking: Boolean, onProfile: () -> Unit, onWave: () -> Unit) {
    val app = rememberApp()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone).atStartOfDay(zone).toInstant()
    val theirWalks = posts.filter { it.kind == "walk" && it.dogId == dog.id }
    val todayKm = theirWalks.filter { it.createdAt >= today }.sumOf { it.distanceM ?: 0.0 }
    val weekWalks = theirWalks.count { it.createdAt >= Instant.now().minusSeconds(7 * 86_400) }
    val details = listOfNotNull(dog.breed.ifEmpty { null }, dog.distanceM?.let { OpawFormat.distance(it) + " away" }) + listOfNotNull(if (walking) "walking now" else null)
    Column(Modifier.fillMaxWidth().shadow(22.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
        .background(Color.White, RoundedCornerShape(36.dp)).padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PackAvatar(app.pack.publicUrl(dog.avatarPath), 66.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(dog.name, style = opaw(23.sp, FontWeight.Black), color = Ink)
                Text(details.joinToString(" · "), style = opaw(14.sp, FontWeight.Medium), color = Color(0xFF999999), maxLines = 1)
            }
        }
        Row(Modifier.padding(vertical = 18.dp)) {
            StatColumn(OpawFormat.distance(todayKm), "Today", Modifier.weight(1f), valueSize = 18, labelSize = 13, valueColor = Ink)
            StatColumn("$weekWalks×", "Walks this week", Modifier.weight(1f), valueSize = 18, labelSize = 13, valueColor = Ink)
            StatColumn(if (isFriend) "Friends" else "New", "With you", Modifier.weight(1f), valueSize = 18, labelSize = 13, valueColor = Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(58.dp).pressable(onClick = onProfile).background(Color(0xFFF1F1F2), CircleShape), contentAlignment = Alignment.Center) {
                Text("View profile", style = opaw(16.sp, FontWeight.Bold), color = Color(0xFF777777))
            }
            Box(Modifier.weight(1f).height(58.dp).pressable(onClick = onWave).background(Action, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (isFriend) "Friends ✓" else "Wave hello", style = opaw(16.sp, FontWeight.Bold), color = Color.White)
            }
        }
    }
}

/** The pet card's shape in grey while pets nearby load (7f). */
@Composable
private fun LoadingCard() {
    val gray = Color(0xFFECECEE)
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(36.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(66.dp).background(gray, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(120.dp, 22.dp).background(gray, RoundedCornerShape(8.dp)))
                Box(Modifier.size(170.dp, 14.dp).background(gray, RoundedCornerShape(8.dp)))
            }
        }
        Row {
            repeat(3) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(52.dp, 18.dp).background(gray, RoundedCornerShape(8.dp)))
                    Box(Modifier.size(70.dp, 12.dp).background(gray, RoundedCornerShape(8.dp)))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(54.dp).background(gray, RoundedCornerShape(8.dp)))
    }
}

/** Another pet's profile: photo, breed, and report or block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DogProfileSheet(dog: PackDog, onDismiss: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit) {
    val app = rememberApp()
    val colors = Opaw.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StickerPet(rememberRemote(app.pack.publicUrl(dog.avatarPath)), Modifier.size(140.dp), edge = 5.dp, motion = null)
            Text(dog.name, style = opaw(30.sp, FontWeight.Black), color = colors.ink)
            Text(listOf(dog.breed, dog.size.replaceFirstChar(Char::uppercase)).filter { it.isNotEmpty() }.joinToString(" · "), style = opaw(15.sp, FontWeight.Medium), color = colors.muted)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(58.dp).pressable(onClick = onReport).background(Color(0xFFF1F1F2), CircleShape), contentAlignment = Alignment.Center) {
                    Text("Report", style = opaw(16.sp, FontWeight.Bold), color = Color(0xFF777777))
                }
                Box(Modifier.weight(1f).height(58.dp).pressable(onClick = onBlock).background(colors.alert, CircleShape), contentAlignment = Alignment.Center) {
                    Text("Block owner", style = opaw(16.sp, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

private val reasons = listOf("Inappropriate content", "Harassment or hate", "Spam or selling animals", "Animal welfare concern", "Something else")

/** Collects a reason and files a report. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(subject: String, onDismiss: () -> Unit, submit: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var reason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.cream, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = opaw(16.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Text("Report $subject", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                Text("Send", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.clickable {
                    scope.launch {
                        runCatching { submit(if (details.isEmpty()) reason else "$reason: $details") }
                            .onSuccess { onDismiss() }.onFailure { error = it.message }
                    }
                }.padding(8.dp))
            }
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                reasons.forEachIndexed { index, option ->
                    if (index > 0) Hairline()
                    Row(Modifier.fillMaxWidth().clickable { reason = option }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option, style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                        if (option == reason) Icon(DesignIcon.CHECK, size = 16.dp)
                    }
                }
            }
            PlainField(details, { details = it }, "Details (optional)", opaw(16.sp, FontWeight.Medium),
                Modifier.padding(top = 12.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp), singleLine = false,
                keyboard = KeyboardOptions.Default, placeholderColor = colors.ghost)
            error?.let { Text(it, style = opaw(14.sp, FontWeight.Medium), color = colors.alert, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
