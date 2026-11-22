package app.opaw.ui.welcome

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.OpawApplication
import app.opaw.ui.components.Action
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.premium.findActivity
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Opens the store sheet for the welcome gift: the yearly plan with 50% off the first year. */
internal suspend fun claimWelcomeOffer(app: OpawApplication, context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    val offer = app.premium.offerProduct ?: run { runCatching { app.premium.load() }; app.premium.offerProduct } ?: return false
    return app.premium.purchase(activity, offer, useTrial = false)
}

/**
 * After signing in: if "Claim 50% off" was tapped on the welcome chest, the store sheet opens
 * right away (14j). Once only.
 */
@Composable
fun ClaimAfterSignIn() {
    val app = rememberApp()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!app.settings.claimAfterSignIn.first()) return@LaunchedEffect
        app.settings.setClaimAfterSignIn(false)
        if (app.premium.isPremium || app.settings.welcomeOfferEnds.first() < System.currentTimeMillis()) return@LaunchedEffect
        delay(800)
        claimWelcomeOffer(app, context)
    }
}

/**
 * "Premium 50% off · 23:41:10 left · Claim" on Home (14k), while the welcome gift lasts and
 * Premium isn't on.
 */
@Composable
fun WelcomeOfferBar(modifier: Modifier = Modifier) {
    val app = rememberApp()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ends by app.settings.welcomeOfferEnds.collectAsStateWithLifecycle(0L)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ends) { while (ends > now) { delay(1000); now = System.currentTimeMillis() } }
    if (ends <= now || app.premium.isPremium) return
    val left = (ends - now) / 1000
    val text = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", left / 3600, left / 60 % 60, left % 60)
    Row(
        modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F))), CircleShape)
            .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Premium 50% off", style = opaw(14.sp, FontWeight.Black), color = Color.White)
        Text("· $text left", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFFFFE3D1))
        Spacer(Modifier.weight(1f))
        Text(
            "Claim", style = opaw(14.sp, FontWeight.ExtraBold), color = Action,
            modifier = Modifier.pressable { haptics.tap(); scope.launch { claimWelcomeOffer(app, context) } }
                .background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
