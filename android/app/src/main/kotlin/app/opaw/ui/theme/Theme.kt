package app.opaw.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.R

// Mirrors Opaw/Components/Theme.swift. The brand follows the app icon, a white "O" paw on orange:
// soft white: light grey ground, white cards with soft shadows, orange pills for anything you do or pick,
// ink for words, rounded heavy type. Red only for attention.

@Immutable
data class OpawColors(
    /** The ground behind cards. */
    val cream: Color,
    /** Primary text and the primary button fill. */
    val ink: Color,
    /** Text drawn on top of [ink]. */
    val onInk: Color,
    /** Buttons, selected states, progress and the active tab. White bold labels sit on it. */
    val action: Color,
    /** Text and icons drawn on top of [action]. */
    val onAction: Color,
    /** The logo orange: splash, hero fills and illustration. */
    val brand: Color,
    /** Orange words and links on the light ground. */
    val brandText: Color,
    /** Cards, round buttons and sheets. */
    val surface: Color,
    /** Chips and inputs inside a card. */
    val chip: Color,
    /** Secondary text: captions, times and labels. */
    val muted: Color,
    /** Row dividers inside cards. */
    val hairline: Color,
    val faint: Color,
    val softText: Color,
    /** Only for days you can't pick yet and decoration. */
    val ghost: Color,
    /** Empty bar and track fills. */
    val track: Color,
    /** Only for things that need attention: overdue meds, warnings. */
    val alert: Color = Color(0xFFEC3013),
    val good: Color = Color(0xFF34C759),
    val warn: Color = Color(0xFFF5B300),
)

private val LightColors = OpawColors(
    cream = Color(0xFFF4F4F5),
    ink = Color(0xFF111111),
    onInk = Color(0xFFFFFFFF),
    action = Color(0xFFF2600C),
    onAction = Color(0xFFFFFFFF),
    brand = Color(0xFFFF7A1A),
    brandText = Color(0xFFB84300),
    surface = Color(0xFFFFFFFF),
    chip = Color(0xFFF1F1F2),
    muted = Color(0xFF666666),
    hairline = Color(0xFFEEEEEE),
    faint = Color(0xFF666666),
    softText = Color(0xFF666666),
    ghost = Color(0xFFBBBBBB),
    track = Color(0xFFE6E6E8),
)

private val DarkColors = OpawColors(
    cream = Color(0xFF0B0B0C),
    ink = Color(0xFFF5F5F5),
    onInk = Color(0xFF111111),
    action = Color(0xFFF2600C),
    onAction = Color(0xFFFFFFFF),
    brand = Color(0xFFFF7A1A),
    brandText = Color(0xFFFF9A4D),
    surface = Color(0xFF1C1C1E),
    chip = Color(0xFF2C2C2E),
    muted = Color(0xFF8E8E93),
    hairline = Color(0xFF2C2C2E),
    faint = Color(0xFF7C7C80),
    softText = Color(0xFFA1A1A6),
    ghost = Color(0xFF5C5C60),
    track = Color(0xFF2C2C2E),
)

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val control = 20.dp
    val card = 30.dp
    val sheet = 40.dp
}

/** Nunito, 500 to 900, as in the design. */
val Nunito = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/** Nunito at a weight; the counterpart of `Font.opaw(_:_:)`. */
fun opaw(size: TextUnit, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = Nunito, fontSize = size, fontWeight = weight)

/** Nunito Black display type. */
fun opawDisplay(size: TextUnit) = opaw(size, FontWeight.Black)

/** Nunito Black numerals for stats. */
fun opawNumber(size: TextUnit) = opaw(size, FontWeight.Black).copy(fontFeatureSettings = "tnum")

val LocalOpawColors = staticCompositionLocalOf { LightColors }

object Opaw {
    val colors: OpawColors @Composable get() = LocalOpawColors.current
}

@Composable
fun OpawTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.action, onPrimary = colors.onAction, background = colors.cream, surface = colors.surface, error = colors.alert)
    } else {
        lightColorScheme(primary = colors.action, onPrimary = colors.onAction, background = colors.cream, surface = colors.surface, error = colors.alert)
    }
    CompositionLocalProvider(LocalOpawColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.copy(bodyLarge = opaw(17.sp), bodyMedium = opaw(15.sp)),
            content = content,
        )
    }
}

/** A white card with a soft shadow; the counterpart of `.card()`. */
@Composable
fun Modifier.card(padding: Dp = 20.dp): Modifier = whiteCard().padding(padding)

/** White rounded background with the soft card shadow; no padding. */
@Composable
fun Modifier.whiteCard(radius: Dp = Radius.card): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(elevation = 12.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
        .background(Opaw.colors.surface, shape)
}
