package app.opaw.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.data.Dog
import app.opaw.services.PhotoStore
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.Space
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val photoCache = LruCache<String, Bitmap>(24)

/** A saved pet photo, loaded off the main thread and cached. */
@Composable
fun rememberPhoto(path: String?, maxDimension: Int = 1200): Bitmap? {
    val context = LocalContext.current
    val cached = path?.let { photoCache.get("$it@$maxDimension") }
    val loaded by produceState(cached, path, maxDimension) {
        if (path == null) { value = null; return@produceState }
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { PhotoStore.load(context, path, maxDimension) }?.also { photoCache.put("$path@$maxDimension", it) }
    }
    return loaded
}

/** The pet's sticker image: the cutout when there is one, otherwise the photo. */
@Composable
fun rememberPetImage(dog: Dog?, maxDimension: Int = 1200): Bitmap? = rememberPhoto(dog?.cutoutPath ?: dog?.photoPath, maxDimension)

/** The pet switcher chip in screen headers: a small avatar and the pet's name, with a menu of pets. */
@Composable
fun PetChip(dog: Dog?, dogs: List<Dog> = emptyList(), onSelect: (Dog) -> Unit = {}) {
    val colors = Opaw.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .pressable(enabled = dogs.size > 1) { open = true }
                .floatingShadow()
                .background(colors.surface, CircleShape)
                .padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.border(3.dp, colors.surface, CircleShape)) {
                MiniFace(rememberPhoto(dog?.cutoutPath ?: dog?.photoPath, 200), size = 36.dp, rim = 0.dp, fill = colors.chip)
            }
            Text(dog?.name ?: "Pet", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            dogs.forEach { item ->
                DropdownMenuItem(text = { Text(item.name, style = opaw(15.sp, FontWeight.Bold)) }, onClick = { open = false; onSelect(item) })
            }
        }
    }
}

/** A screen header: round button, title, round button. */
@Composable
fun ScreenHeader(title: String, leading: @Composable () -> Unit, trailing: @Composable () -> Unit = { Spacer(Modifier.size(54.dp)) }) {
    Box(Modifier.fillMaxWidth().background(Opaw.colors.cream).padding(horizontal = Space.l).padding(top = 10.dp)) {
        Text(title, style = opaw(22.sp, FontWeight.Black), color = Opaw.colors.ink, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** A filter pill on the ground: black when selected, white with a soft shadow otherwise. At least 44 tall. */
@Composable
fun FilterPill(title: String, isSelected: Boolean, verticalPadding: Int = 10, onClick: () -> Unit) {
    val colors = Opaw.colors
    Box(
        Modifier
            .pressable(onClick = onClick)
            .then(if (isSelected) Modifier else Modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f)))
            .background(if (isSelected) colors.ink else colors.surface, CircleShape)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 18.dp, vertical = verticalPadding.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = opaw(14.sp, FontWeight.Bold), color = if (isSelected) colors.onInk else colors.ink)
    }
}

/** A pill-shaped choice: black when selected, light gray otherwise. */
@Composable
fun ChoiceChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = Opaw.colors
    Text(
        title,
        style = opaw(14.sp, FontWeight.Bold),
        color = if (isSelected) colors.onInk else colors.ink.copy(alpha = 0.7f),
        modifier = Modifier.pressable(onClick = onClick).background(if (isSelected) colors.ink else colors.chip, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** A hairline across a card. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Opaw.colors.hairline))
}

/** Pop (7d): counts, checks and badges overshoot once in 450 ms when their value changes. */
fun Modifier.popOnChange(value: Any?): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (first) { first = false; return@LaunchedEffect }
        scale.animateTo(1.22f, tween(150))
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.8f) }
}

/** Hold a row to act on it, as `.onLongPressGesture(minimumDuration: 0.5)`. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.onHold(enabled: Boolean = true, action: () -> Unit): Modifier = if (!enabled) this else composed {
    combinedClickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onLongClick = action, onClick = {})
}

/** A centered stat: big number over a quiet label. */
@Composable
fun StatColumn(value: String, label: String, modifier: Modifier = Modifier, valueSize: Int = 21, labelSize: Int = 14, valueColor: Color = Opaw.colors.ink) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = opaw(valueSize.sp, FontWeight.Black), color = valueColor, maxLines = 1)
        Text(label, style = opaw(labelSize.sp, FontWeight.Medium), color = Opaw.colors.muted, maxLines = 1)
    }
}

