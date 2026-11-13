package app.opaw.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.data.Relative
import app.opaw.services.CutoutService
import app.opaw.services.PhotoStore
import app.opaw.services.Premium
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.care.PlainField
import app.opaw.ui.care.dashedCapsule
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.ChoiceChip
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPhoto
import app.opaw.ui.premium.PetLimitSheet
import app.opaw.ui.profile.AddDogSheet
import app.opaw.ui.rememberApp
import app.opaw.ui.rememberDogData
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch
import java.util.UUID

/** Family tree (2i): parents above, siblings at the sides, the pet in the middle, and the pets at home. */
@Composable
fun FamilyTreeScreen(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = Opaw.colors
    val data = rememberDogData(dogId) ?: return
    val dogs by app.repository.dogs.collectAsStateWithLifecycle(emptyList())
    val dog = data.dog
    var addRole by remember { mutableStateOf<Relative.Role?>(null) }
    var editing by remember { mutableStateOf<Relative?>(null) }
    var addSource by remember { mutableStateOf(false) }
    var petLimit by remember { mutableStateOf(false) }
    val mom = dog.relatives.firstOrNull { it.role == Relative.Role.MOM }
    val dad = dog.relatives.firstOrNull { it.role == Relative.Role.DAD }
    val siblings = dog.relatives.filter { it.role == Relative.Role.BROTHER || it.role == Relative.Role.SISTER }
    val nextOpenRole = when {
        mom == null -> Relative.Role.MOM
        dad == null -> Relative.Role.DAD
        siblings.size % 2 == 0 -> Relative.Role.BROTHER
        else -> Relative.Role.SISTER
    }

    fun save(relative: Relative) {
        val all = dog.relatives.toMutableList()
        val index = all.indexOfFirst { it.id == relative.id }
        if (index >= 0) all[index] = relative else all += relative
        app.scope.launch { app.repository.saveDog(dog.copy(relatives = all)) }
        haptics.success()
    }

    fun remove(relative: Relative) {
        app.scope.launch { app.repository.saveDog(dog.copy(relatives = dog.relatives.filter { it.id != relative.id })) }
    }

    fun invite() {
        val text = "${dog.name} is on Opaw, a little app where our pets come to life. Join ${dog.name}'s family tree!"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    @Composable
    fun node(relative: Relative?, role: Relative.Role, size: Dp) {
        Column(Modifier.width(110.dp).pressable { if (relative != null) editing = relative else addRole = role }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (relative != null) {
                Box(Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))) {
                    MiniFace(rememberPhoto(relative.photoPath, 300), size = size, rim = 4.dp, fill = colors.chip, initial = relative.name.take(1))
                }
            } else {
                Box(Modifier.size(size).dashedCapsule(Color(0xFFC4C4C6), dash = 5f, gap = 4f), contentAlignment = Alignment.Center) { Icon(DesignIcon.PLUS, size = 22.dp, tint = colors.muted) }
            }
            Text(relative?.name ?: "Add ${role.title.lowercase()}", style = opaw(15.sp, FontWeight.Black), color = if (relative == null) colors.muted else colors.ink, maxLines = 1)
            Text(
                relative?.let { if (it.place.isEmpty()) it.role.title else "${it.role.title} · ${it.place}" } ?: role.title,
                style = opaw(12.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(colors.cream)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("${dog.name}'s family", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) }, trailing = { RoundIcon(DesignIcon.PLUS, onClick = { addRole = nextOpenRole }) })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp).height(400.dp)) {
                    val sx = maxWidth / 390
                    val line = Color(0xFFD6D6D8)
                    // The lines joining parents, pet and siblings, on the design's 390-point screen.
                    Box(Modifier.offset(x = sx * 95, y = 46.dp).size(sx * 200, 3.dp).background(line))
                    Box(Modifier.offset(x = sx * 193, y = 46.dp).size(3.dp, 118.dp).background(line))
                    Box(Modifier.offset(x = sx * 65, y = 162.dp).size(sx * 260, 3.dp).background(line))
                    Box(Modifier.offset(x = sx * 65, y = 162.dp).size(3.dp, 40.dp).background(line))
                    Box(Modifier.offset(x = sx * 322, y = 162.dp).size(3.dp, 40.dp).background(line))
                    // Centred on the design's points; 55 is half a node's width.
                    Box(Modifier.offset(x = sx * 95 - 55.dp, y = 27.dp)) { node(mom, Relative.Role.MOM, 80.dp) }
                    Box(Modifier.offset(x = sx * 295 - 55.dp, y = 27.dp)) { node(dad, Relative.Role.DAD, 80.dp) }
                    Box(Modifier.offset(x = sx * 67 - 55.dp, y = 221.dp)) { node(siblings.firstOrNull(), Relative.Role.BROTHER, 76.dp) }
                    Box(Modifier.offset(x = sx * 323 - 55.dp, y = 221.dp)) { node(siblings.getOrNull(1), Relative.Role.SISTER, 76.dp) }
                    // The pet in the middle.
                    Column(
                        Modifier.offset(x = sx * 195 - 55.dp, y = 197.dp).width(110.dp).pressable { navigator.push(Route.Profile(dog.id)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.shadow(10.dp, CircleShape).border(4.dp, Ink, CircleShape)) {
                            MiniFace(rememberPhoto(dog.cutoutPath ?: dog.photoPath, 300), size = 104.dp, rim = 4.dp, fill = colors.surface)
                        }
                        Text(dog.name, style = opaw(18.sp, FontWeight.Black), color = colors.ink, maxLines = 1)
                        val years = dog.ageYears.toInt()
                        Text(if (dog.birthDate == null) "You" else "You · ${maxOf(years, 0)} ${if (years == 1) "yr" else "yrs"}", style = opaw(12.sp, FontWeight.SemiBold), color = colors.muted, maxLines = 1)
                    }
                }
                // At home.
                Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("At home", style = opaw(18.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.weight(1f))
                        Text("Swipe between them on Home", style = opaw(13.sp, FontWeight.SemiBold), color = colors.muted)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        dogs.forEach { item ->
                            Box(
                                Modifier.pressable {
                                    haptics.select()
                                    app.scope.launch { app.settings.setActiveDog(item.id) }
                                    navigator.pop()
                                }.then(if (item.id == dog.id) Modifier.border(3.dp, Ink, CircleShape) else Modifier).padding(3.dp),
                            ) { MiniFace(rememberPhoto(item.cutoutPath ?: item.photoPath, 200), size = 58.dp, rim = 0.dp, fill = colors.chip) }
                        }
                        Box(
                            Modifier.padding(3.dp).size(58.dp).pressable { if (Premium.isPremium) addSource = true else petLimit = true }
                                .dashedCapsule(Color(0xFFC4C4C6), dash = 5f, gap = 4f),
                            contentAlignment = Alignment.Center,
                        ) { Icon(DesignIcon.PLUS, size = 20.dp, tint = colors.muted) }
                    }
                }
            }
        }
        BlackPillButton("Invite family", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 4.dp), height = 62.dp, onClick = ::invite)
    }

    addRole?.let { role -> RelativeSheet(Relative(name = "", role = role), onDismiss = { addRole = null }, onSave = ::save) }
    editing?.let { relative -> RelativeSheet(relative, onDelete = { remove(relative) }, onDismiss = { editing = null }, onSave = ::save) }
    if (addSource) AddDogSheet(PhotoSource.CAMERA) { addSource = false }
    if (petLimit) PetLimitSheet(dog.cutoutPath ?: dog.photoPath, onDismiss = { petLimit = false }) { navigator.push(Route.Paywall) }
}

/** Adds or edits one relative: photo, name, role and where they live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelativeSheet(initial: Relative, onDelete: (() -> Unit)? = null, onDismiss: () -> Unit, onSave: (Relative) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var relative by remember { mutableStateOf(initial) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val image = CutoutService.load(context, uri, 240) ?: return@launch
            relative = relative.copy(photoPath = PhotoStore.save(context, image, "relative-${UUID.randomUUID()}.jpg", png = false))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.shadow(10.dp, CircleShape).pressable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                    MiniFace(rememberPhoto(relative.photoPath, 300), size = 80.dp, rim = 4.dp, fill = colors.chip)
                    if (relative.photoPath == null) Icon(DesignIcon.CAMERA, size = 24.dp, tint = colors.muted)
                }
                Text(relative.name.ifEmpty { "New relative" }, style = opaw(30.sp, FontWeight.Black), color = colors.ink, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Who", style = opaw(14.sp, FontWeight.Bold), color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Relative.Role.entries.forEach { role -> ChoiceChip(role.title, relative.role == role) { relative = relative.copy(role = role) } }
                }
            }
            PlainField(relative.name, { relative = relative.copy(name = it) }, "Name", opaw(15.sp, FontWeight.Medium),
                Modifier.fillMaxWidth().background(Color(0xFFF6F6F7), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PlainField(relative.place, { relative = relative.copy(place = it) }, "Where they live (optional)", opaw(15.sp, FontWeight.Medium),
                Modifier.fillMaxWidth().background(Color(0xFFF6F6F7), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Spacer(Modifier.height(8.dp))
            BlackPillButton("Save", enabled = relative.name.isNotBlank(), height = 62.dp) {
                onSave(relative.copy(name = relative.name.trim()))
                onDismiss()
            }
            if (onDelete != null) {
                Text("Remove from tree", style = opaw(15.sp, FontWeight.Bold), color = colors.alert, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { onDelete(); onDismiss() }.padding(8.dp))
            }
        }
    }
}
