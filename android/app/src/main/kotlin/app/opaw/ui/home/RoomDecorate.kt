package app.opaw.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.core.CareTier
import app.opaw.core.MoodEngine
import app.opaw.data.DogData
import app.opaw.services.PetRoomState
import app.opaw.services.Premium
import app.opaw.services.RoomCatalog
import app.opaw.services.RoomItem
import app.opaw.services.RoomSlot
import app.opaw.services.ShopTab
import app.opaw.ui.LocalNavigator
import app.opaw.ui.Route
import app.opaw.ui.care.rememberNow
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.loopMotion
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import app.opaw.services.TreatItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * The pet standing in its room: the roaming 3D pet in the room's view, or the flat pet in its
 * place, which walks the design's path ([PetRoom] moves it).
 */
@Composable
internal fun RoomPet(data: DogData, view: Modifier, standing: Modifier, cue: RoomCue? = null) {
    val app = rememberApp()
    val now = rememberNow()
    val dog = data.dog
    val mood = MoodEngine.mood(data.careSnapshot(now), now)
    val image = rememberPetImage(dog)
    val roams = app.petModels.file(dog.id, mood) != null && app.petModels.motion(dog.id, "rest") != null
    if (roams) {
        PetAvatar(dog, image, mood, view, room = true, cue = cue)
    } else {
        // Its soft shadow on the floor, then the pet, as in the design.
        Box(standing.drawBehind {
            drawOval(Color.Black.copy(alpha = 0.14f), topLeft = Offset(size.width * 0.17f, size.height * 0.94f), size = Size(size.width * 0.66f, size.height * 0.095f))
        }) {
            PetAvatar(dog, image, mood, Modifier.fillMaxSize(), sticker = 4.dp)
        }
    }
}

/** The Paws coin: orange, with the O paw on it. */
@Composable
internal fun PawCoin(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    app.opaw.ui.components.SvgArt(app.opaw.ui.components.DesignArt.PAW_COIN, modifier.size(size))
}

/** "245" with the coin, as on Home and in the shop. */
@Composable
internal fun PawsChip(paws: Int, night: Boolean = false, modifier: Modifier = Modifier, coin: Modifier = Modifier) {
    Row(
        modifier.height(40.dp)
            .then(if (night) Modifier else Modifier.shadow(10.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000)))
            .background(if (night) Color.White.copy(alpha = 0.12f) else Color.White, CircleShape).padding(start = 8.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PawCoin(26.dp, coin)
        Text("$paws", style = opaw(15.sp, FontWeight.ExtraBold), color = if (night) Color.White else Ink)
    }
}

/**
 * The Shop (16b, 16c, 16e, 16f): the room turns into a store. Every spot that can change gets an
 * orange +; tap one and the sheet shows what fits there. Tapping a piece puts it straight into the
 * room so it's seen in place before spending; Buy for 120 keeps it, Keep looking puts the old one
 * back. Food is a can that fills the bowl; treats come in packs and Give tosses one to the pet.
 */
@Composable
fun RoomDecorateScreen(dogId: String) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val loaded by app.pets.collectAsStateWithLifecycle()
    val all = loaded ?: emptyList()
    val data = all.firstOrNull { it.dog.id == dogId } ?: return
    val state = app.paws.state(dogId)
    var tab by remember { mutableStateOf(ShopTab.BEDS) }
    // A piece being tried, before it's bought.
    var trying by remember { mutableStateOf<RoomItem?>(null) }
    val night = Opaw.colors.surface != Color.White
    // A treat just given (16f): it's tossed in, and the pet hops up to catch it.
    var tossed by remember { mutableStateOf<RoomCue?>(null) }
    LaunchedEffect(tossed?.id) {
        val cue = tossed ?: return@LaunchedEffect
        delay((cue.kind.seconds * 1000).toLong())
        if (tossed?.id == cue.id) tossed = null
    }

    Box(Modifier.fillMaxSize()) {
        PetRoom(
            night,
            pose = if (tossed != null) RoomPose.HOP else RoomPose.WALK,
            pieces = { slot -> trying?.takeIf { it.slot == slot }?.id ?: state.piece(slot) },
            actions = RoomActions({}, {}, {}, {}),
            markers = { spots ->
                if (trying == null) {
                    for ((slot, at) in spots) {
                        // The bowl's + is the food's too, while the Food tab is open (16e).
                        val food = slot == RoomSlot.BOWL && tab == ShopTab.FOOD
                        val inTab = slot in tab.slots || food
                        Box(
                            Modifier.offset { IntOffset((at.x - 18.dp.toPx()).roundToInt(), (at.y - 18.dp.toPx()).roundToInt()) }
                                .size(36.dp)
                                .pressable { haptics.select(); if (!food) tab = ShopTab.entries.first { slot in it.slots } }
                                .shadow(8.dp, CircleShape)
                                .background(if (inTab) Action else Color.White, CircleShape)
                                .border(3.dp, if (inTab) Color(0xFFFFC08A) else Action, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(DesignIcon.PLUS, size = 16.dp, weight = 3f, tint = if (inTab) Color.White else Action) }
                    }
                }
            },
        ) { view, standing -> RoomPet(data, view, standing, tossed) }
        tossed?.treatId?.let { TreatToss(it, tossed!!.id) }

        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp)) {
            Text(
                "Cancel", style = opaw(15.sp, FontWeight.ExtraBold), color = Ink,
                modifier = Modifier.pressable { navigator.pop() }.shadow(8.dp, CircleShape).background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text("${data.dog.name}’s room", style = opaw(17.sp, FontWeight.Black), color = if (night) Color.White else Ink, modifier = Modifier.align(Alignment.Center))
        }

        val tried = trying
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (tried != null) {
                TryCard(tried, owned = state.owns(tried), paws = state.paws, onKeepLooking = { trying = null }) {
                    when {
                        tried.premium && !Premium.isPremium -> navigator.push(Route.Paywall)
                        tried.premium || state.owns(tried) -> { app.paws.place(dogId, tried); haptics.success(); trying = null }
                        app.paws.buy(dogId, tried) -> { haptics.milestone(); trying = null }
                        else -> haptics.warning()
                    }
                }
            } else {
                ShopSheet(
                    tab, state, onTab = { tab = it }, onDone = { navigator.pop() },
                    onTreat = { treat ->
                        val have = state.treats[treat.id] ?: 0
                        when {
                            state.level < treat.level -> haptics.warning()
                            have > 0 -> if (app.paws.giveTreat(dogId, treat)) {
                                haptics.success()
                                val cue = RoomCue(RoomCue.Kind.TREAT, treat.id)
                                RoomCues.send(dogId, cue)
                                tossed = cue
                            } else haptics.warning()
                            treat.premium && !Premium.isPremium -> navigator.push(Route.Paywall)
                            app.paws.buyTreat(dogId, treat, Premium.isPremium) -> haptics.milestone()
                            else -> haptics.warning()
                        }
                    },
                ) { item ->
                    haptics.select()
                    when {
                        state.level < item.level -> haptics.warning()
                        state.piece(item.slot) == item.id -> Unit
                        state.owns(item) || (item.premium && Premium.isPremium) -> app.paws.place(dogId, item)
                        else -> trying = item
                    }
                }
            }
        }
    }
}

/**
 * The shop sheet (16b): tabs, then the pieces for them. Drag it down (or tap the handle) and it
 * tucks away to a slim bar so the whole room and its + spots are in view; drag it up to open it.
 */
@Composable
private fun ShopSheet(tab: ShopTab, state: PetRoomState, onTab: (ShopTab) -> Unit, onDone: () -> Unit, onTreat: (TreatItem) -> Unit, onPick: (RoomItem) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    // One offset moves the whole sheet: 0 is open, [travel] (the height of everything under the
    // header) is tucked away. The content never changes size, so the sheet follows the finger
    // exactly and settles without a jump.
    var travel by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var closed by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { d -> offset = (offset + d).coerceIn(0f, travel) }
    fun settle(close: Boolean, velocity: Float = 0f) {
        if (close != closed) haptics.select()
        closed = close
        scope.launch {
            drag.drag {
                var last = offset
                animate(offset, if (close) travel else 0f, velocity, spring(dampingRatio = 0.9f, stiffness = 450f)) { value, _ ->
                    dragBy(value - last)
                    last = value
                }
            }
        }
    }
    // Read only inside graphicsLayer, so dragging redraws the sheet without recomposing it.
    fun progress() = if (travel > 0f) offset / travel else 0f
    val fling = with(LocalDensity.current) { 900.dp.toPx() }
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { translationY = offset }
            .shadow(20.dp, shape).background(Color.White, shape)
            .draggable(
                drag, Orientation.Vertical,
                onDragStopped = { velocity ->
                    settle(if (abs(velocity) > fling) velocity > 0 else offset > travel / 2, velocity)
                },
            )
            .navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).pressable { settle(!closed) }.padding(horizontal = 30.dp, vertical = 4.dp),
        ) { Box(Modifier.size(40.dp, 5.dp).background(Color(0xFFDDDDDD), CircleShape)) }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shop", style = opaw(22.sp, FontWeight.Black), color = Ink, modifier = Modifier.weight(1f))
            PawCoin(20.dp)
            Text(" ${state.paws}", style = opaw(16.sp, FontWeight.Black), color = Ink)
            // A small Done fades in as the sheet tucks away, where the big one can't be seen.
            Text(
                "Done", style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White,
                modifier = Modifier.padding(start = 12.dp).graphicsLayer { alpha = ((progress() - 0.5f) * 2f).coerceIn(0f, 1f) }
                    .then(if (closed) Modifier.pressable(onClick = onDone) else Modifier)
                    .background(Ink, CircleShape).padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        Column(
            Modifier
                .onSizeChanged {
                    travel = it.height.toFloat()
                    offset = if (closed) travel else offset.coerceAtMost(travel)
                }
                .graphicsLayer { alpha = 1f - progress() },
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in ShopTab.entries) {
                    val on = t == tab
                    Text(
                        t.title, style = opaw(14.sp, FontWeight.ExtraBold), color = if (on) Color.White else Ink,
                        modifier = Modifier.pressable { onTab(t) }.background(if (on) Ink else Color(0xFFF2F2F4), CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == ShopTab.TREATS) {
                    for (treat in RoomCatalog.treats) TreatCard(treat, state, onTreat)
                } else {
                    for (item in RoomCatalog.items.filter { it.slot in tab.slots }) PieceCard(item, state, onPick)
                }
            }
            if (tab == ShopTab.TREATS) {
                // Three a day at most: treats are for fun, not meals.
                Text(
                    "${state.treatsLeft().coerceAtLeast(0)} of ${RoomCatalog.TREATS_A_DAY} treats left today",
                    style = opaw(12.sp, FontWeight.Bold), color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
                )
            }
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(54.dp).pressable(onClick = onDone).background(Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("Done", style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White) }
            }
        }
    }
}

/** One piece: its swatch, its name, and whether it's in the room, its price, or what unlocks it. */
@Composable
private fun PieceCard(item: RoomItem, state: PetRoomState, onPick: (RoomItem) -> Unit) {
    val placed = state.piece(item.slot) == item.id
    val locked = state.level < item.level
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(104.dp).alpha(if (locked) 0.55f else 1f).pressable { onPick(item) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).background(if (placed) Color(0xFFFFF1E6) else Color(0xFFF6F6F7), shape)
                .then(if (placed) Modifier.border(3.dp, Action, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Swatch(item) }
        Text(item.title, style = opaw(13.sp, FontWeight.ExtraBold), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val food = item.slot == RoomSlot.FOOD
        when {
            placed -> Text(if (food) "In the bowl" else "In the room", style = opaw(12.sp, FontWeight.ExtraBold), color = Action)
            locked -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DesignIcon.LOCK, size = 12.dp, tint = Color(0xFF888888))
                Text("Level ${item.level}", style = opaw(12.sp, FontWeight.Bold), color = Color(0xFF888888))
            }
            item.premium -> Text("Premium", style = opaw(12.sp, FontWeight.ExtraBold), color = Color(0xFF7B4FD1))
            state.owns(item) -> Text(if (food) "Owned" else "Yours", style = opaw(12.sp, FontWeight.Bold), color = Color(0xFF888888))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PawCoin(14.dp)
                Text("${item.price}", style = opaw(13.sp, FontWeight.Black), color = Ink)
            }
        }
    }
}

/** The piece's own art from the design, small, for its card. */
@Composable
private fun Swatch(item: RoomItem) {
    if (item.slot == RoomSlot.FOOD) {
        // Food is sold as a can (16e).
        ShopPicture(ShopArt.can(item.id), Modifier.size(76.dp))
    } else if (item.id.endsWith("-none")) {
        Icon(DesignIcon.CLOSE, size = 24.dp, tint = Color(0xFFBBBBBB))
    } else {
        PieceArt(item.id, Modifier.size(72.dp))
    }
}

/**
 * One treat (16f): its bag (or the treat itself), its name, and how many are on hand with Give,
 * or the pack and its price, or what unlocks it.
 */
@Composable
private fun TreatCard(treat: TreatItem, state: PetRoomState, onTap: (TreatItem) -> Unit) {
    val have = state.treats[treat.id] ?: 0
    val locked = state.level < treat.level
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(104.dp).alpha(if (locked) 0.55f else 1f).pressable { onTap(treat) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).background(if (have > 0) Color(0xFFFFF1E6) else Color(0xFFF6F6F7), shape)
                .then(if (have > 0) Modifier.border(3.dp, Action, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { ShopPicture(treat.card, Modifier.size(76.dp)) }
        Text(treat.title, style = opaw(13.sp, FontWeight.ExtraBold), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            have > 0 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("×$have", style = opaw(12.sp, FontWeight.ExtraBold), color = Ink)
                Text(
                    "Give", style = opaw(12.sp, FontWeight.Black), color = Color.White,
                    modifier = Modifier.background(Action, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            locked -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DesignIcon.LOCK, size = 12.dp, tint = Color(0xFF888888))
                Text("Level ${treat.level}", style = opaw(12.sp, FontWeight.Bold), color = Color(0xFF888888))
            }
            treat.premium -> Text("Premium", style = opaw(12.sp, FontWeight.ExtraBold), color = Color(0xFF7B4FD1))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("×${treat.pack} ·", style = opaw(13.sp, FontWeight.Black), color = Ink)
                PawCoin(14.dp)
                Text("${treat.price}", style = opaw(13.sp, FontWeight.Black), color = Ink)
            }
        }
    }
}

/**
 * A treat tossed into the room (16f): it arcs in from the right, spinning, to where the pet hops
 * up for it, and "Crunch!" pops up as it's caught.
 */
@Composable
private fun TreatToss(treatId: String, key: Long) {
    val flight = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { flight.animateTo(1f, tween(2200, easing = LinearEasing)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = constraints.maxWidth / 390f
        val density = LocalDensity.current.density
        // From the right of the room to the pet's mouth, as on the design's phone.
        val from = Offset(300f, 340f)
        val to = Offset(150f, 470f)
        val size = 40f
        Box(
            Modifier.size((size * s / density).dp).graphicsLayer {
                val t = (flight.value / 0.55f).coerceIn(0f, 1f)
                val x = from.x + (to.x - from.x) * t
                val y = from.y + (to.y - from.y) * t - 120f * t * (1f - t)
                translationX = (x - size / 2f) * s
                translationY = (y - size / 2f) * s
                rotationZ = -340f * t
                alpha = if (flight.value < 0.6f) 1f else 0f
            },
        ) { ShopPicture(treatId, Modifier.fillMaxSize()) }
        Text(
            "Crunch!", style = opaw(14.sp, FontWeight.ExtraBold), color = Ink,
            modifier = Modifier
                .graphicsLayer {
                    translationX = 166f * s
                    translationY = 400f * s
                    val shown = flight.value in 0.55f..0.95f
                    alpha = if (shown) 1f else 0f
                    val pop = ((flight.value - 0.55f) / 0.08f).coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * pop
                    scaleY = 0.6f + 0.4f * pop
                }
                .shadow(10.dp, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(Color.White, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

/** Try it first (16c): the piece is in the room; buy it to keep it, or keep looking. */
@Composable
private fun TryCard(item: RoomItem, owned: Boolean, paws: Int, onKeepLooking: () -> Unit, onBuy: () -> Unit) {
    val short = !owned && !item.premium && paws < item.price
    Column(
        Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth().shadow(18.dp, RoundedCornerShape(28.dp)).background(Color.White, RoundedCornerShape(28.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color(0xFFF6F6F7), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Swatch(item) }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = opaw(18.sp, FontWeight.Black), color = Ink)
                Text(if (short) "You need ${item.price - paws} more Paws" else "In the room now", style = opaw(13.sp, FontWeight.SemiBold), color = Color(0xFF888888))
            }
            if (!item.premium) {
                PawCoin(18.dp)
                Text("${item.price}", style = opaw(15.sp, FontWeight.Black), color = Ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(52.dp).pressable(onClick = onKeepLooking).background(Color(0xFFF2F2F4), CircleShape), contentAlignment = Alignment.Center) {
                Text("Keep looking", style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
            }
            Row(
                Modifier.weight(1f).height(52.dp).alpha(if (short) 0.4f else 1f).pressable(!short, onClick = onBuy).background(Action, CircleShape),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        item.premium && !Premium.isPremium -> "Try Premium"
                        owned || item.premium -> "Put it in"
                        else -> "Buy for"
                    },
                    style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White,
                )
                if (!owned && !item.premium) {
                    PawCoin(16.dp)
                    Text("${item.price}", style = opaw(15.sp, FontWeight.Black), color = Color.White)
                }
            }
        }
    }
}

/**
 * Level up (16d): good care unlocks new pieces. Shown once per level, over Home.
 */
@Composable
internal fun LevelUpCard(dogId: String, onSee: () -> Unit) {
    val app = rememberApp()
    val state = app.paws.state(dogId)
    if (state.level <= state.seenLevel) return
    val level = state.level
    val unlocked = RoomCatalog.items.filter { it.level == level }
    val next = RoomCatalog.levels.getOrNull(level)
    val start = RoomCatalog.levels[level - 1]
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pressable {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 28.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(30.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "LEVEL $level · ${RoomCatalog.levelNames[level - 1].uppercase()}", style = opaw(12.sp, FontWeight.Black).copy(letterSpacing = 1.sp), color = Color(0xFFB84300),
                modifier = Modifier.background(Color(0xFFFFF1E6), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Box(Modifier.size(110.dp).background(Color(0xFFFFF1E6), CircleShape), contentAlignment = Alignment.Center) {
                unlocked.firstOrNull()?.let { Swatch(it) } ?: PawCoin(56.dp)
            }
            Text(
                unlocked.firstOrNull()?.let { "${it.title} unlocked" } ?: "Level $level!",
                style = opaw(24.sp, FontWeight.Black), color = Ink, textAlign = TextAlign.Center,
            )
            Text(
                if (unlocked.isEmpty()) "Good care keeps the room growing." else unlocked.joinToString(" and ") { it.title.lowercase().replaceFirstChar(Char::uppercase) } + (if (unlocked.size > 1) " are" else " is") + " in the shop now.",
                style = opaw(14.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp), color = Color(0xFF666666), textAlign = TextAlign.Center,
            )
            if (next != null) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Level ${level + 1} · ${RoomCatalog.levelNames[level]}", style = opaw(12.sp, FontWeight.Bold), color = Ink, modifier = Modifier.weight(1f))
                    Text("${state.points - start} / ${next - start} care points", style = opaw(12.sp, FontWeight.Bold), color = Color(0xFF888888))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFEDEDF0), CircleShape)) {
                    Box(Modifier.fillMaxWidth(((state.points - start).toFloat() / (next - start)).coerceIn(0.03f, 1f)).height(6.dp).background(Action, CircleShape))
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(50.dp).pressable { app.paws.celebrated(dogId) }.background(Color(0xFFF2F2F4), CircleShape), contentAlignment = Alignment.Center) {
                    Text("Later", style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
                }
                Box(Modifier.weight(1f).height(50.dp).pressable { app.paws.celebrated(dogId); onSee() }.background(Action, CircleShape), contentAlignment = Alignment.Center) {
                    Text("See it in the room", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

