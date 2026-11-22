package app.opaw.ui.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.services.PremiumStore
import app.opaw.services.formatInstant
import app.opaw.ui.LocalNavigator
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Hairline
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Action
import app.opaw.ui.components.Ink
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RoundIcon
import app.opaw.ui.components.ScreenHeader
import app.opaw.ui.components.SpeechBubble
import app.opaw.ui.components.StickerPet
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.rememberPetImage
import app.opaw.ui.components.rememberPhoto
import app.opaw.ui.rememberActiveDog
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val TERMS_URL = "https://play.google.com/about/play-terms/"
private const val PRIVACY_URL = "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md"

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun PremiumError(store: PremiumStore) {
    val message = store.errorMessage ?: return
    AlertDialog(
        onDismissRequest = { store.errorMessage = null },
        containerColor = Opaw.colors.surface,
        title = { Text("Opaw Premium", style = opaw(19.sp, FontWeight.Black)) },
        text = { Text(message, style = opaw(15.sp, FontWeight.Medium)) },
        confirmButton = { TextButton({ store.errorMessage = null }) { Text("OK", color = Opaw.colors.ink) } },
    )
}

/** Trial paywall (5a light, 5b dark): the pet, what Premium adds, yearly or monthly, and the trial timeline. */
@Composable
fun PaywallScreen(petName: String, petImage: Bitmap?, onClose: (Boolean) -> Unit) {
    val app = rememberApp()
    val store = app.premium
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Ink
    val muted = if (dark) Color(0xFF8E8E93) else Color(0xFF999999)
    val surface = if (dark) Color(0xFF1C1C1F) else Color.White
    val background = if (dark) Color(0xFF0D0D0F) else Color(0xFFF4F4F5)
    var plan by remember { mutableStateOf(PremiumStore.Plan.YEARLY) }
    val trialEligible = store.isEligibleForTrial(plan) || store.products.isEmpty()
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun priceLabel(option: PremiumStore.Plan): String {
        val price = store.displayPrice(store.product(option)) ?: if (option == PremiumStore.Plan.YEARLY) "$29.99" else "$4.99"
        return if (option == PremiumStore.Plan.YEARLY) "$price/yr" else "$price/mo"
    }
    // How much cheaper a year is than twelve months, as a whole percent.
    val saving: Int? = run {
        val yearly = store.price(store.product(PremiumStore.Plan.YEARLY))
        val monthly = store.price(store.product(PremiumStore.Plan.MONTHLY))
        if (yearly == null || monthly == null || monthly <= 0) return@run if (store.products.isEmpty()) 50 else null
        ((1 - yearly / (monthly * 12)) * 100).roundToInt().takeIf { it > 0 }
    }
    val price = store.displayPrice(store.product(plan)) ?: if (plan == PremiumStore.Plan.YEARLY) "$29.99" else "$4.99"
    val period = if (plan == PremiumStore.Plan.YEARLY) "year" else "month"
    val caption = if (trialEligible) "Then $price/$period. Cancel anytime." else "$price/$period. Cancel anytime."

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            StickerPet(petImage, Modifier.padding(top = 4.dp).size(112.dp, 126.dp), edge = 5.dp, motion = LoopMotion.BOB, duration = 3.2)
            Text("Opaw Premium", style = opaw(13.sp, FontWeight.ExtraBold), color = muted, modifier = Modifier.padding(top = 14.dp))
            Text("Give $petName the best care", style = opaw(30.sp, FontWeight.Black), color = ink, textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((icon, text) in listOf(
                    DesignIcon.PETS to "Multiple pets, each with an avatar",
                    DesignIcon.BELL to "Unlimited reminders",
                    DesignIcon.CHART to "Full history and stats",
                    DesignIcon.ACTIVITY to "Walks and memories",
                    DesignIcon.USERS to "Family sharing, widgets and backup",
                )) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).shadow(if (dark) 0.dp else 6.dp, CircleShape).background(surface, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, size = 17.dp, weight = if (icon == DesignIcon.ACTIVITY) null else 2.4f, tint = ink, cutoutColor = surface)
                        }
                        Text(text, style = opaw(16.sp, FontWeight.Bold), color = ink, maxLines = 1)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumStore.Plan.entries.forEach { option ->
                    val selected = plan == option
                    val shape = RoundedCornerShape(24.dp)
                    Row(
                        Modifier.fillMaxWidth().height(66.dp).pressable { haptics.select(); plan = option }.background(surface, shape)
                            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) ink else if (dark) Color(0xFF2C2C30) else Color(0xFFEEEEEE), shape)
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            Box(Modifier.size(22.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) { Icon(DesignIcon.CHECK, size = 12.dp, tint = if (dark) Ink else Color.White) }
                        } else {
                            Box(Modifier.size(22.dp).border(2.dp, if (dark) Color(0xFF48484D) else Color(0xFFD4D4D8), CircleShape))
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.title, style = opaw(17.sp, FontWeight.ExtraBold), color = ink)
                            if (option == PremiumStore.Plan.YEARLY && saving != null) {
                                Text("Save $saving%", style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White,
                                    modifier = Modifier.background(Opaw.colors.alert, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(priceLabel(option), style = opaw(17.sp, FontWeight.Black), color = ink)
                            if (option == PremiumStore.Plan.YEARLY) {
                                Text("${store.monthlyEquivalent(store.product(option)) ?: "$2.50"}/mo", style = opaw(12.sp, FontWeight.SemiBold), color = muted)
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                if (store.isPurchasing) {
                    Box(Modifier.fillMaxWidth().height(62.dp).background(if (dark) Color.White else Action, CircleShape), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = if (dark) Ink else Color.White, strokeWidth = 2.5.dp)
                    }
                } else {
                    BlackPillButton(if (trialEligible) "Start 7-day free trial" else "Continue", enabled = store.product(plan) != null, height = 62.dp, inverted = dark) {
                        val activity = context.findActivity() ?: return@BlackPillButton
                        val details = store.product(plan) ?: return@BlackPillButton
                        scope.launch { if (store.purchase(activity, details)) { haptics.success(); onClose(true) } }
                    }
                }
            }
            Text(caption, style = opaw(14.sp, FontWeight.SemiBold), color = muted, modifier = Modifier.padding(top = 10.dp))
            if (trialEligible) {
                // Today, a reminder on day 5, billing on day 7.
                Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 14.dp)) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 5.dp).height(2.5.dp).background(ink.copy(alpha = if (dark) 0.25f else 0.18f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for ((title, detail, filled) in listOf(Triple("Today", "Full access", true), Triple("Day 5", "Reminder", false), Triple("Day 7", "Billing starts", false))) {
                            Column(horizontalAlignment = if (title == "Today") Alignment.Start else if (title == "Day 7") Alignment.End else Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.padding(bottom = 6.dp).size(12.dp).background(if (filled) ink else background, CircleShape).border(2.5.dp, ink, CircleShape))
                                Text(title, style = opaw(13.sp, FontWeight.ExtraBold), color = ink)
                                Text(detail, style = opaw(12.sp, FontWeight.Medium), color = muted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Restore purchases", style = opaw(13.sp, FontWeight.Bold), color = muted, modifier = Modifier.clickable {
                    scope.launch { store.restore(); if (store.isPremium) onClose(true) }
                })
                Text("Terms", style = opaw(13.sp, FontWeight.Bold), color = muted, modifier = Modifier.clickable { open(TERMS_URL) })
                Text("Privacy", style = opaw(13.sp, FontWeight.Bold), color = muted, modifier = Modifier.clickable { open(PRIVACY_URL) })
            }
        }
        Box(
            Modifier.statusBarsPadding().padding(start = 20.dp, top = 6.dp).size(54.dp).pressable { onClose(false) }
                .shadow(if (dark) 0.dp else 10.dp, CircleShape).background(surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(DesignIcon.CLOSE, size = 22.dp, tint = ink) }
    }
    PremiumError(store)
}

/** One-time offer (5c): shown once after the paywall is closed. It lasts until midnight. */
@Composable
fun OneTimeOffer(petImage: Bitmap?, onDone: (Boolean) -> Unit) {
    val app = rememberApp()
    val store = app.premium
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val offset by animateDpAsState(if (appeared) 0.dp else 700.dp, spring(dampingRatio = 0.88f, stiffness = 300f), label = "offer")
    val offer = store.offerProduct
    val regular = store.displayPrice(offer) ?: store.displayPrice(store.product(PremiumStore.Plan.YEARLY)) ?: "$29.99"
    val firstYear = store.introPrice(offer) ?: "$14.99"
    val monthly = offer?.subscriptionOfferDetails?.firstOrNull { it.offerId != null }?.pricingPhases?.pricingPhaseList?.firstOrNull()?.let { phase ->
        java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(phase.priceCurrencyCode) }.format(phase.priceAmountMicros / 12 / 1_000_000.0)
    } ?: "$1.25"

    Box(Modifier.fillMaxSize().background(Color(0xFF141416).copy(alpha = if (appeared) 0.38f else 0f))) {
        Column(Modifier.align(Alignment.BottomCenter).offset(y = offset)) {
            Box(Modifier.fillMaxWidth().height(160.dp).offset(y = 10.dp)) {
                StickerPet(petImage, Modifier.padding(start = 34.dp, top = 10.dp).size(130.dp, 148.dp), edge = 5.dp, motion = LoopMotion.BOB, duration = 3.2)
                SpeechBubble("wait, one more thing", Modifier.padding(start = 168.dp, top = 18.dp))
            }
            Column(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)).navigationBarsPadding().height(440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).background(Color(0xFFDDDDDD), CircleShape))
                Row(Modifier.padding(top = 20.dp).background(Opaw.colors.alert, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DesignIcon.CLOCK, size = 14.dp, tint = Color.White)
                    Text("One-time offer", style = opaw(13.sp, FontWeight.ExtraBold), color = Color.White)
                }
                Text("Today only. Ends at midnight.", style = opaw(14.sp, FontWeight.SemiBold), color = Color(0xFF666666), modifier = Modifier.padding(top = 14.dp))
                Text("50% off your first year", style = opaw(34.sp, FontWeight.Black), color = Ink, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 22.dp).padding(horizontal = 40.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(regular, style = opaw(20.sp, FontWeight.Bold).copy(textDecoration = TextDecoration.LineThrough), color = Color(0xFF666666))
                    Text(buildAnnotatedString {
                        append(firstYear)
                        withStyle(SpanStyle(fontSize = 16.sp, color = Color(0xFF666666))) { append("/year") }
                    }, style = opaw(30.sp, FontWeight.Black), color = Ink)
                }
                Text("$monthly/month, renews at $regular", style = opaw(14.sp, FontWeight.SemiBold), color = Color(0xFF666666))
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    BlackPillButton(if (store.isPurchasing) "…" else "Claim offer", enabled = offer != null && !store.isPurchasing, height = 62.dp) {
                        val activity = context.findActivity() ?: return@BlackPillButton
                        offer ?: return@BlackPillButton
                        scope.launch { if (store.purchase(activity, offer, useTrial = false)) { haptics.success(); onDone(true) } }
                    }
                }
                Text("No thanks", style = opaw(15.sp, FontWeight.Bold), color = Color(0xFF666666), modifier = Modifier.clickable { onDone(false) }.padding(12.dp))
            }
        }
    }
    PremiumError(store)
}

/** The paywall for the active pet, from anywhere. */
@Composable
fun PaywallRoute() {
    val navigator = LocalNavigator.current
    val (_, data) = rememberActiveDog()
    PaywallScreen(data?.dog?.name ?: "your pet", rememberPetImage(data?.dog)) { navigator.pop() }
}

/** Settings: Subscription (5f): the trial countdown or plan, renewal and billing, and Manage subscription. */
@Composable
fun SubscriptionScreen() {
    val app = rememberApp()
    val store = app.premium
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var showPaywall by remember { mutableStateOf(false) }
    if (showPaywall) {
        val (_, data) = rememberActiveDog()
        androidx.activity.compose.BackHandler { showPaywall = false }
        PaywallScreen(data?.dog?.name ?: "your pet", rememberPetImage(data?.dog)) { showPaywall = false }
        return
    }
    val zone = ZoneId.systemDefault()
    fun daysLeft(end: Instant) = maxOf(0, ChronoUnit.DAYS.between(LocalDate.now(zone), end.atZone(zone).toLocalDate()).toInt())

    Box(Modifier.fillMaxSize().background(colors.cream).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Subscription", leading = { RoundIcon(DesignIcon.BACK, onClick = navigator::pop) })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 160.dp)) {
                val status = store.status
                if (status != null) {
                    Column(Modifier.fillMaxWidth().whiteCard().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Opaw Premium", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
                            Text(if (status.isTrial) "Free trial" else if (status.willRenew) "Active" else "Ending", style = opaw(12.sp, FontWeight.ExtraBold), color = Color.White,
                                modifier = Modifier.background(Action, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        val end = status.expirationDate
                        Text(
                            if (status.isTrial && end != null) daysLeft(end).let { if (it == 1) "1 day left" else "$it days left" } else status.plan.title,
                            style = opaw(44.sp, FontWeight.Black), color = colors.ink, maxLines = 1, modifier = Modifier.padding(top = 14.dp),
                        )
                        if (status.isTrial && end != null) {
                            val total = maxOf(1, ChronoUnit.DAYS.between(status.purchaseDate, end).toInt())
                            val used = (total - daysLeft(end)).coerceIn(0, total)
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(total) { Box(Modifier.weight(1f).height(8.dp).background(if (it < used) Action else colors.track, CircleShape)) }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("Started ${formatInstant(status.purchaseDate, "MMM d")}", style = opaw(12.sp, FontWeight.SemiBold), color = colors.faint, modifier = Modifier.weight(1f))
                            end?.let { Text("${if (status.isTrial || !status.willRenew) "Ends" else "Renews"} ${formatInstant(it, "MMM d")}", style = opaw(12.sp, FontWeight.SemiBold), color = colors.faint) }
                        }
                    }
                    Column(Modifier.padding(top = 12.dp).fillMaxWidth().whiteCard().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        @Composable
                        fun row(label: String, value: String, first: Boolean = false) {
                            if (!first) Hairline()
                            Row(Modifier.padding(vertical = 14.dp)) {
                                Text(label, style = opaw(15.sp, FontWeight.SemiBold), color = colors.muted, modifier = Modifier.weight(1f))
                                Text(value, style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, textAlign = TextAlign.End)
                            }
                        }
                        row("Plan", listOfNotNull(status.plan.title, store.displayPrice(store.products[status.productId])).joinToString(" · "), first = true)
                        status.expirationDate?.let { row(if (status.willRenew) "Renews" else "Ends", formatInstant(it, "MMM d, yyyy")) }
                        store.trialReminderDate?.let { row("Reminder", "${formatInstant(it, "MMM d")}, by notification") }
                        row("Billed through", "Google Play")
                    }
                    status.expirationDate?.let { end ->
                        val day = formatInstant(end, "MMM d")
                        Text(
                            if (status.isTrial) "Cancel at least 24 hours before $day and you won’t be charged."
                            else if (status.willRenew) "Cancel at least 24 hours before $day to stop it renewing." else "Premium stays on until $day.",
                            style = opaw(14.sp, FontWeight.Medium), color = colors.muted, modifier = Modifier.padding(horizontal = 8.dp).padding(top = 14.dp),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth().whiteCard().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Opaw", style = opaw(15.sp, FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
                            Text("Free", style = opaw(12.sp, FontWeight.ExtraBold), color = Color(0xFF888888), modifier = Modifier.background(colors.chip, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        Text("Free plan", style = opaw(44.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 14.dp))
                        Text("One pet, with care logs, walks and memories. Premium adds more pets, unlimited reminders, full history, widgets and backup.",
                            style = opaw(14.sp, FontWeight.Medium).copy(lineHeight = 19.sp), color = colors.muted, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (store.isPremium) {
                BlackPillButton("Manage subscription", height = 62.dp) {
                    val id = store.status?.productId.orEmpty()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=$id&package=${context.packageName}")))
                }
            } else {
                BlackPillButton("Try Opaw Premium", height = 62.dp) { showPaywall = true }
            }
            Text("Restore purchases", style = opaw(15.sp, FontWeight.Bold), color = colors.ink, modifier = Modifier.clickable { scope.launch { store.restore() } })
        }
    }
    PremiumError(store)
}

/** Locked: adding a second pet (5d). Free covers one pet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetLimitSheet(dogPhoto: String?, onDismiss: () -> Unit, onTryPremium: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), dragHandle = null) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 4.dp).height(400.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(40.dp, 5.dp).background(Color(0xFFDDDDDD), CircleShape))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                Box(Modifier.shadow(6.dp, CircleShape)) { MiniFace(rememberPhoto(dogPhoto, 200), size = 60.dp, rim = 4.dp, fill = Opaw.colors.track) }
                repeat(2) {
                    Box(Modifier.size(60.dp).background(Color.White, CircleShape).padding(4.dp).background(Color(0xFFF1F1F2), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(DesignIcon.LOCK, size = 20.dp, tint = Color(0xFF999999))
                    }
                }
            }
            Text("Room for more pets", style = opaw(26.sp, FontWeight.Black), color = Ink, modifier = Modifier.padding(top = 16.dp))
            Text("Free covers one pet. Premium adds unlimited pets, each with their own avatar.", style = opaw(15.sp, FontWeight.Medium).copy(lineHeight = 21.sp),
                color = Color(0xFF999999), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.weight(1f))
            BlackPillButton("Try Premium free for 7 days", height = 62.dp) { onDismiss(); onTryPremium() }
            Text("Not now", style = opaw(15.sp, FontWeight.Bold), color = Color(0xFF999999), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(12.dp))
        }
    }
}
