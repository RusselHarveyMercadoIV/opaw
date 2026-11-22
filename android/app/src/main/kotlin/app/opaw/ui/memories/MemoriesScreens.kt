package app.opaw.ui.memories

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.opaw.core.OpawFormat
import app.opaw.data.Dog
import app.opaw.data.DogData
import app.opaw.data.Memory
import app.opaw.data.Walk
import app.opaw.data.medal
import app.opaw.data.title
import app.opaw.services.CareRecorder
import app.opaw.services.CutoutService
import app.opaw.services.Notice
import app.opaw.services.PhotoStore
import app.opaw.services.clock
import app.opaw.services.formatInstant
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.care.DateTimePicker
import app.opaw.ui.care.PlainField
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.FilterPill
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.PetChip
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ToastHost
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPhoto
import app.opaw.ui.rememberActiveDog
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import app.opaw.ui.walks.MapCamera
import app.opaw.ui.walks.OpawMap
import app.opaw.ui.walks.RouteMarkers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

private enum class Filter(val title: String) { ALL("All"), WALKS("Walks"), MILESTONES("Milestones") }

/** One entry in the feed behind Memories. Mirrors `MemoryFeed` in MemoriesView.swift. */
private sealed interface Item {
    val id: String
    val date: Instant

    data class WalkItem(val walk: Walk, val photo: Memory?) : Item {
        override val id = "walk-${walk.id}"
        override val date: Instant = walk.startedAt
    }
    data class Photo(val memory: Memory) : Item {
        override val id = "photo-${memory.id}"
        override val date: Instant = memory.date
    }
    data class Pair(val first: Memory, val second: Memory?) : Item {
        override val id = "pair-${first.id}"
        override val date: Instant = first.date
    }
    data class Milestone(override val date: Instant, val text: String) : Item {
        override val id = "milestone-${date.toEpochMilli()}-${text.hashCode()}"
    }
}

private fun dayText(date: Instant): String {
    val zone = ZoneId.systemDefault()
    val day = date.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatInstant(date, "d MMM")
    }
}

private fun items(data: DogData, filter: Filter): List<Item> {
    val memories = data.memories
    val walks = data.walks
    val items = mutableListOf<Item>()
    if (filter != Filter.MILESTONES) {
        walks.forEach { walk -> items += Item.WalkItem(walk, memories.firstOrNull { it.walkId == walk.id && it.photoPath != null }) }
    }
    if (filter == Filter.ALL) {
        val linked = walks.map { it.id }.toSet()
        val loose = memories.filter { it.walkId == null || it.walkId !in linked }.sortedByDescending { it.date }
        val pending = mutableListOf<Memory>()
        fun flush() {
            val first = pending.firstOrNull() ?: return
            items += Item.Pair(first, pending.getOrNull(1))
            pending.clear()
        }
        for (memory in loose) {
            if (memory.caption.isBlank()) {
                pending += memory
                if (pending.size == 2) flush()
            } else {
                flush()
                items += Item.Photo(memory)
            }
        }
        flush()
    }
    if (filter != Filter.WALKS) items += milestones(data).map { Item.Milestone(it.first, it.second) }
    return items.sortedByDescending { it.date }
}

/** Milestones worked out from the dog's history. */
private fun milestones(data: DogData, now: Instant = Instant.now()): List<kotlin.Pair<Instant, String>> {
    val dog = data.dog
    val zone = ZoneId.systemDefault()
    val result = mutableListOf<kotlin.Pair<Instant, String>>()
    val walks = data.walks.sortedBy { it.startedAt }
    walks.firstOrNull()?.let { result += it.startedAt to "First walk together. ${dog.name} is officially on the map." }
    var total = 0.0
    val marks = mutableListOf(50.0, 100.0, 250.0, 500.0, 1000.0)
    for (walk in walks) {
        total += walk.distanceMeters / 1000
        while (marks.isNotEmpty() && total >= marks.first()) {
            result += walk.startedAt to "${marks.first().toInt()} km walked together."
            marks.removeAt(0)
        }
    }
    val created = dog.createdAt.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(created, now.atZone(zone).toLocalDate())
    var month = 30L
    while (month <= days) {
        result += dog.createdAt.plus(month, ChronoUnit.DAYS) to "$month days of great care. ${dog.name} thanks you."
        month += 30
    }
    dog.birthDate?.let { birth ->
        val birthDay = birth.atZone(zone).toLocalDate()
        val years = Period.between(birthDay, now.atZone(zone).toLocalDate()).years
        for (age in maxOf(1, years - 2)..maxOf(1, years)) {
            if (age > years) continue
            val date = birthDay.plusYears(age.toLong()).atStartOfDay(zone).toInstant()
            if (date >= dog.createdAt.minusSeconds(86_400)) result += date to "${dog.name} turned $age. Happy birthday!"
        }
    }
    return result
}

/** Memories (2d): walks, photos and milestones as one feed. */
@Composable
fun MemoriesScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    val (dogs, data) = rememberActiveDog()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showAdd by remember { mutableStateOf(false) }

    fun toggleFavorite(walk: Walk) = scope.launch { app.repository.saveWalk(walk.copy(isFavorite = !walk.isFavorite)) }
    fun toggleFavorite(memory: Memory) = scope.launch { app.repository.saveMemory(memory.copy(isFavorite = !memory.isFavorite)) }

    Column(Modifier.fillMaxSize().background(colors.cream).verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 120.dp)) {
        // Same header as Care: pet chip, centred title, round action.
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { PetChip(data?.dog, dogs) { scope.launch { app.settings.setActiveDog(it.id) } } }
            Text("Memories", style = opaw(22.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.align(Alignment.Center))
            RoundIcon(DesignIcon.CAMERA, Modifier.align(Alignment.CenterEnd), onClick = { if (data != null) showAdd = true })
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { option -> FilterPill(option.title, option == filter) { filter = option; haptics.select() } }
        }
        val d = data ?: return@Column
        val list = items(d, filter)
        if (list.isEmpty()) {
            Text(
                when (filter) {
                    Filter.ALL -> "Walks, photos and milestones with ${d.dog.name} will show up here."
                    Filter.WALKS -> "Finish a walk and it shows up here."
                    Filter.MILESTONES -> "Milestones arrive as you care for ${d.dog.name}."
                },
                style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 28.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            list.forEach { item ->
                when (item) {
                    is Item.WalkItem -> BigMemoryCard(
                        item.walk.title,
                        "${dayText(item.walk.startedAt)} · ${OpawFormat.distance(item.walk.distanceMeters)} · ${(item.walk.elapsedSeconds / 60).roundToInt()} min",
                        item.walk.isFavorite, { toggleFavorite(item.walk) }, { navigator.push(Route.WalkDetail(item.walk.id)) },
                    ) {
                        val photo = rememberPhoto(item.photo?.photoPath)
                        if (photo != null) {
                            Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            OpawMap(item.walk.route, MapCamera.FitRoute, Modifier.fillMaxSize(), interactive = false) { toScreen -> RouteMarkers(item.walk.route, item.walk.pins, toScreen) }
                        }
                    }
                    is Item.Photo -> BigMemoryCard(
                        item.memory.caption, dayText(item.memory.date), item.memory.isFavorite,
                        { toggleFavorite(item.memory) }, { navigator.push(Route.MemoryDetail(item.memory.id)) },
                    ) { MemoryPhoto(item.memory, Modifier.fillMaxSize()) }
                    is Item.Pair -> Row(Modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PairTile(item.first, Modifier.weight(1f))
                        if (item.second != null) PairTile(item.second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    }
                    is Item.Milestone -> Row(
                        Modifier.fillMaxWidth().background(Action, RoundedCornerShape(30.dp)).padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniFace(rememberPhoto(d.dog.cutoutPath ?: d.dog.photoPath, 200), size = 64.dp, rim = 0.dp, fill = Color.White)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${formatInstant(item.date, "d MMM")} · Milestone", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFFAAAAAA))
                            Text(item.text, style = opaw(16.sp, FontWeight.ExtraBold).copy(lineHeight = 21.sp), color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showAdd && data != null) AddMemorySheet(data.dog) { showAdd = false }
}

@Composable
private fun PairTile(memory: Memory, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Box(modifier.fillMaxSize().pressable { navigator.push(Route.MemoryDetail(memory.id)) }.clip(RoundedCornerShape(26.dp))) { MemoryPhoto(memory, Modifier.fillMaxSize()) }
}

/** White card with a big rounded photo, a title, a subtitle and a heart. */
@Composable
private fun BigMemoryCard(title: String, subtitle: String, isFavorite: Boolean, toggleFavorite: () -> Unit, onClick: () -> Unit, photo: @Composable () -> Unit) {
    val colors = Opaw.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().pressable(onClick = onClick).whiteCard().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp)).background(colors.track)) { photo() }
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = opaw(18.sp, FontWeight.ExtraBold), color = colors.ink, maxLines = 1)
                Text(subtitle, style = opaw(14.sp, FontWeight.Medium), color = colors.muted, maxLines = 1)
            }
            Box(Modifier.size(44.dp).pressable { toggleFavorite(); haptics.tap() }, contentAlignment = Alignment.Center) {
                Icon(DesignIcon.HEART, size = 22.dp, tint = if (isFavorite) colors.alert else colors.ghost)
            }
        }
    }
}

/** A memory's photo filling its frame, or a soft placeholder. */
@Composable
private fun MemoryPhoto(memory: Memory, modifier: Modifier) {
    val image = rememberPhoto(memory.photoPath)
    Box(modifier.background(Opaw.colors.chip), contentAlignment = Alignment.Center) {
        if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(DesignIcon.PAW, size = 30.dp, tint = Opaw.colors.muted)
    }
}

/** One memory: the photo, when it happened, and an editable caption. */
@Composable
fun MemoryDetailScreen(memoryId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val colors = Opaw.colors
    val loaded by produceState<Memory?>(null, memoryId) { value = app.repository.memory(memoryId) }
    val memory = loaded ?: return
    var caption by remember(memory.id) { mutableStateOf(memory.caption) }
    var menu by remember { mutableStateOf(false) }
    val image = rememberPhoto(memory.photoPath, 2400)
    fun close() {
        if (caption != memory.caption) app.scope.launch { app.repository.saveMemory(memory.copy(caption = caption)) }
        navigator.pop()
    }
    androidx.activity.compose.BackHandler(onBack = ::close)

    Column(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            RoundIcon(DesignIcon.BACK, Modifier.align(Alignment.CenterStart), onClick = ::close)
            Text("Memory", style = opaw(18.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                RoundIcon(DesignIcon.UPLOAD, onClick = { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    memory.photoPath?.let { path ->
                        DropdownMenuItem(text = { Text("Share photo") }, onClick = {
                            menu = false
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.filesDir, path))
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                        })
                    }
                    if (app.pack.isSignedIn) {
                        DropdownMenuItem(text = { Text("Share with the pack") }, onClick = {
                            menu = false
                            app.scope.launch {
                                runCatching { app.pack.shareMemory(memory.copy(caption = caption)) }
                                    .onSuccess { app.toasts.show(Notice(Notice.Kind.FAMILY, "Shared with the pack", memory.caption.ifEmpty { "Your memory is in the feed" })) }
                                    .onFailure { app.toasts.show(Notice(Notice.Kind.FAILED, "Couldn't share with the pack", it.message ?: "Check your connection")) }
                            }
                        })
                    }
                    DropdownMenuItem(text = { Text("Delete", color = colors.alert) }, onClick = {
                        menu = false
                        app.scope.launch {
                            PhotoStore.delete(app, memory.photoPath)
                            app.repository.deleteMemory(memory)
                        }
                        navigator.pop()
                    })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            image?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.FillWidth) }
            Text("${formatInstant(memory.date, "d MMMM yyyy")}, ${clock(memory.date)}", style = opaw(15.sp, FontWeight.Medium), color = colors.muted)
            PlainField(caption, { caption = it }, "Caption", opaw(20.sp, FontWeight.Bold), Modifier.fillMaxWidth(), singleLine = false,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), placeholderColor = colors.ghost)
        }
    }
}

/** A new photo memory. Mirrors `AddMemorySheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemorySheet(dog: Dog, onDismiss: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var caption by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Instant.now()) }
    var pickDate by remember { mutableStateOf(false) }
    val cameraFile = remember { File(context.cacheDir, "memory.jpg") }
    val cameraUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { image = CutoutService.load(context, cameraUri, 2400) }
    }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { scope.launch { image = CutoutService.load(context, it, 2400) } }
    }

    fun save() {
        val id = UUID.randomUUID().toString()
        scope.launch {
            try {
                val path = image?.let { PhotoStore.save(context, it, "memory-$id.jpg", png = false) }
                app.repository.saveMemory(Memory(id = id, dogId = dog.id, date = date, caption = caption, photoPath = path))
            } catch (error: Exception) {
                app.toasts.show(Notice(Notice.Kind.FAILED, "Couldn't save the memory", "Your photo is still here", actionTitle = "Retry") { save() })
                return@launch
            }
            haptics.success()
            onDismiss()
            CareRecorder.refreshBadges(app, dog.id).firstOrNull()?.let { badge ->
                app.toasts.show(Notice(Notice.Kind.MILESTONE, badge.title, "${dog.name} · ${badge.detail}", medal = badge.medal, actionTitle = "View") { app.toasts.showChallenges = true })
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.cream,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Box {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cancel", style = opaw(16.sp, FontWeight.Bold), color = colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                    Text("New memory", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    val enabled = image != null || caption.isNotEmpty()
                    Text("Save", style = opaw(16.sp, FontWeight.ExtraBold), color = if (enabled) colors.ink else colors.ghost,
                        modifier = Modifier.clickable(enabled = enabled, onClick = ::save).padding(8.dp))
                }
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                    image?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 12.dp), contentScale = ContentScale.Fit) }
                    Text(if (image == null) "Choose a photo" else "Choose another", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink,
                        modifier = Modifier.fillMaxWidth().clickable { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(vertical = 14.dp))
                    Hairline()
                    Text("Take a photo", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink,
                        modifier = Modifier.fillMaxWidth().clickable { camera.launch(cameraUri) }.padding(vertical = 14.dp))
                }
                Column(Modifier.padding(top = 16.dp).fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
                    PlainField(caption, { caption = it }, "What happened?", opaw(16.sp, FontWeight.Medium), Modifier.fillMaxWidth().padding(vertical = 14.dp), singleLine = false,
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), placeholderColor = colors.ghost)
                    Hairline()
                    Row(Modifier.fillMaxWidth().clickable { pickDate = true }.padding(vertical = 14.dp)) {
                        Text("Date", style = opaw(16.sp, FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                        Text("${formatInstant(date, "d MMM yyyy")}, ${clock(date)}", style = opaw(16.sp, FontWeight.Bold), color = colors.ink)
                    }
                }
            }
            // The sheet covers the tab view, so it shows toasts itself.
            ToastHost(bottom = 24.dp)
        }
    }
    if (pickDate) DateTimePicker(date, onDismiss = { pickDate = false }) { date = it }
}
