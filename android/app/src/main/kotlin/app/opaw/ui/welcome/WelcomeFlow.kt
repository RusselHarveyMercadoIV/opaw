package app.opaw.ui.welcome

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.opaw.BuildConfig
import app.opaw.R
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.failureText
import app.opaw.ui.googleIdToken
import app.opaw.ui.onboarding.OrangeButton
import app.opaw.ui.onboarding.SetupHeader
import app.opaw.ui.onboarding.SetupLink
import app.opaw.ui.onboarding.drift
import app.opaw.ui.onboarding.hop
import app.opaw.ui.onboarding.popIn
import app.opaw.ui.onboarding.pulse
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The screens before sign-in (14a to 14j), in order. */
private enum class Beat { WELCOME, ALIVE, TAP, ON_TIME, TOGETHER, HELP, WHO, PLAN, GIFT, REMINDERS, SIGN_IN }

/** What Opaw can help with (14f): its key, its words and its icon. */
private class Focus(val key: String, val title: String, val icon: DesignIcon)

private val focuses = listOf(
    Focus("meals", "Meals on time", DesignIcon.MEAL),
    Focus("water", "Water and potty breaks", DesignIcon.WATER),
    Focus("walks", "Walks and exercise", DesignIcon.ACTIVITY),
    Focus("meds", "Meds and vet visits", DesignIcon.MEDS),
    Focus("sleep", "Sleep and comfort", DesignIcon.MOON),
    Focus("play", "Play and training", DesignIcon.BALL),
)

/** Who looks after the pet (14g). */
private val helpers = listOf(
    Triple("me", "Just me", "I do all the care"),
    Triple("family", "Me and my family", "We share it at home"),
    Triple("walker", "A walker or sitter helps", "Someone outside the home"),
)

private val Peach = Color(0xFFFFF1E6)
private val Butter = Color(0xFFFFF4CC)
private val Sky = Color(0xFFE6F1FF)
private val Lilac = Color(0xFFF1ECFF)
private val Muted = Color(0xFF666666)

/**
 * Onboarding (v5 14a to 14j), before sign-in: the welcome, four beats (one you play: tap the
 * bowl, earn Paws), two quick questions, a plan with a welcome gift in a chest (50% off the first
 * year, for 24 hours), reminders with a reason, then sign in. The pet itself is added on Home.
 *
 * Signing in again starts from "I already have an account" on the welcome, which stays busy while
 * the account's pets come back ([restoring], 11b), or says there's no connection ([restoreFailed], 11d).
 */
@Composable
fun WelcomeFlow(restoring: Boolean = false, restoreFailed: Boolean = false, onRetry: () -> Unit = {}) {
    val app = rememberApp()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var beat by rememberSaveable { mutableStateOf(Beat.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    var focus by remember { mutableStateOf(setOf("meals", "walks", "meds")) }
    var who by rememberSaveable { mutableStateOf("family") }
    var signing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val offerEnds by app.settings.welcomeOfferEnds.collectAsStateWithLifecycle(0L)

    fun go(next: Beat) {
        forward = next > beat
        haptics.soft()
        beat = next
    }

    fun signIn() {
        if (signing || restoring) return
        haptics.tap()
        signing = true
        error = null
        scope.launch {
            try {
                app.settings.setCareAnswers(focus, who)
                val (idToken, rawNonce) = googleIdToken(context)
                app.pack.signInWithGoogle(idToken, rawNonce)
                haptics.success()
            } catch (cancelled: GetCredentialCancellationException) {
                val reason = cancelled.message.orEmpty()
                if (!reason.contains("cancelled by the user", ignoreCase = true) && reason.isNotBlank()) error = failureText(reason)
            } catch (none: NoCredentialException) {
                error = "No Google account on this phone. Add one in Settings, then try again."
            } catch (stop: CancellationException) {
                throw stop
            } catch (failure: Exception) {
                error = failureText(failure.message.orEmpty())
            } finally {
                signing = false
            }
        }
    }

    val previous = when (beat) {
        Beat.WELCOME -> null
        Beat.SIGN_IN -> if (offerEnds > 0) Beat.REMINDERS else Beat.WELCOME
        else -> Beat.entries[beat.ordinal - 1]
    }
    BackHandler(enabled = previous != null && !signing) { previous?.let(::go) }

    AnimatedContent(
        targetState = beat,
        transitionSpec = {
            val direction = if (forward) 1 else -1
            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { it / 6 * direction } + fadeIn()) togetherWith
                (slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { -it / 6 * direction } + fadeOut())
        },
        modifier = Modifier.fillMaxSize(),
        label = "welcome",
    ) { current ->
        val skip = { go(Beat.SIGN_IN) }
        when (current) {
            Beat.WELCOME -> WelcomeScreen(
                busy = signing || restoring,
                failed = restoreFailed,
                error = error,
                onStart = { go(Beat.ALIVE) },
                onSignIn = { if (restoreFailed) onRetry() else signIn() },
            )
            Beat.ALIVE -> StoryScreen(0, Peach, "Your pet, alive in 3D", "One photo and your pet comes to life. It explores, naps and runs up to say hi.", "Next", skip, { go(Beat.TAP) }) { AliveArt() }
            Beat.TAP -> StoryScreen(1, Butter, "Care earns Paws", "Tap the bowl. Every meal, walk and dose you log earns Paws to spend on your pet’s room.", "Next", skip, { go(Beat.ON_TIME) }) { BowlArt() }
            Beat.ON_TIME -> StoryScreen(2, Sky, "Never miss a\nmeal or meds", "Opaw nudges you before dinner and when meds are due.", "Next", skip, { go(Beat.TOGETHER) }) { ClockArt() }
            Beat.TOGETHER -> StoryScreen(3, Color(0xFFF1F2EC), "Walk together,\nshare the care", "Track every walk on the map, and invite family or a walker. Everyone sees who fed, walked and gave meds.", "Continue", skip, { go(Beat.HELP) }) { MapArt() }
            Beat.HELP -> QuestionScreen(1, "What should\nOpaw help with?", "Pick all that fit.", enabled = focus.isNotEmpty(), onBack = { go(Beat.TOGETHER) }, onNext = { go(Beat.WHO) }) {
                for (f in focuses) {
                    val on = f.key in focus
                    ChoiceRow(f.icon, f.title, null, on, square = true) { haptics.select(); focus = if (on) focus - f.key else focus + f.key }
                }
            }
            Beat.WHO -> QuestionScreen(2, "Who looks after\nyour pet?", "So reminders go to the right people.", enabled = true, onBack = { go(Beat.HELP) }, onNext = { go(Beat.PLAN) }) {
                for ((key, title, detail) in helpers) {
                    ChoiceRow(if (key == "me") DesignIcon.PAW else DesignIcon.USERS, title, detail, who == key, square = false) { haptics.select(); who = key }
                }
                Text("You’ll invite them from Home once your pet is in.", style = opaw(13.sp, FontWeight.SemiBold), color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
            Beat.PLAN -> PlanScreen(focus, who, onBack = { go(Beat.WHO) }) { go(Beat.GIFT) }
            Beat.GIFT -> GiftScreen(
                offerEnds,
                onOpen = { if (offerEnds == 0L) scope.launch { app.settings.setWelcomeOfferEnds(System.currentTimeMillis() + 24 * 3600 * 1000L) } },
                onClaim = { scope.launch { app.settings.setClaimAfterSignIn(true) }; go(Beat.REMINDERS) },
                onLater = { go(Beat.REMINDERS) },
            )
            Beat.REMINDERS -> RemindersScreen { go(Beat.SIGN_IN) }
            Beat.SIGN_IN -> SignInBeat(offerEnds, busy = signing || restoring, error = error, onSignIn = ::signIn)
        }
    }
}

// MARK: - 14a Welcome

/** The welcome (14a): brand orange, the pet hopping on its stage, one white button. */
@Composable
private fun WelcomeScreen(busy: Boolean, failed: Boolean, error: String?, onStart: () -> Unit, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF8A2A), Color(0xFFF2600C))))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.opaw_logo), null, Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)))
                Text("Opaw", style = opaw(24.sp, FontWeight.Black), color = Color.White)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Sparkles(Modifier.size(330.dp))
                Box(Modifier.size(270.dp).background(Color.White, CircleShape))
                // The plant sways at the back, the ball floats up front.
                Plant(Modifier.offset((-96).dp, 34.dp).size(44.dp, 70.dp).sway())
                Box(Modifier.offset(96.dp, 78.dp).size(30.dp).drift(2600).background(Action, CircleShape).border(3.dp, Color(0xFFFFC08A), CircleShape))
                Image(painterResource(R.drawable.welcome_pet), null, Modifier.offset(y = (-8).dp).size(190.dp).hop(2200, 16.dp), contentScale = ContentScale.Fit)
            }
            Text(
                "A home for your pet,\nright on your phone",
                style = opaw(28.sp, FontWeight.Black).copy(lineHeight = 32.sp), color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Look after them, and they come alive.",
                style = opaw(15.sp, FontWeight.SemiBold), color = Color(0xFFFFE3D1), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp),
            )
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val message = error ?: if (failed) "Your pets are safe. Check your connection and try again." else null
                AnimatedVisibility(message != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color(0xFF141416), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(DesignIcon.CLOUD_OFF, size = 20.dp, tint = Color.White)
                        Column {
                            if (failed && error == null) Text("No connection", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White)
                            Text(message.orEmpty(), style = opaw(13.sp, FontWeight.SemiBold).copy(lineHeight = 18.sp), color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                when {
                    busy -> {
                        // Signing in again (11b): the pets come back behind this.
                        Row(
                            Modifier.fillMaxWidth().height(58.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), strokeWidth = 3.dp)
                            Text("Signing in…", style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White)
                        }
                        Text("Bringing your pets home", style = opaw(15.sp, FontWeight.ExtraBold), color = Color(0xFFFFE3D1), modifier = Modifier.padding(8.dp))
                    }
                    failed -> {
                        OrangeButton("Try again", white = true, onClick = onSignIn)
                        SetupLink("Get started instead", color = Color.White, onClick = onStart)
                    }
                    else -> {
                        OrangeButton("Get started", white = true, onClick = onStart)
                        SetupLink("I already have an account", color = Color.White, enabled = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(), onClick = onSignIn)
                    }
                }
            }
        }
    }
}

// MARK: - 14b to 14e The beats

/**
 * One beat (14b to 14e): a big coloured picture that moves, then one title and one line, with
 * the progress dots and Skip at the top.
 */
@Composable
private fun StoryScreen(
    index: Int,
    tint: Color,
    title: String,
    body: String,
    button: String,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    art: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(tint)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 44.dp), contentAlignment = Alignment.Center) { art() }
            Row(
                Modifier.safeDrawingPadding().fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 4) {
                    Box(Modifier.size(if (i == index) 22.dp else 6.dp, 6.dp).background(if (i == index) Action else Color.Black.copy(alpha = 0.12f), CircleShape))
                }
                Spacer(Modifier.weight(1f))
                Text("Skip", style = opaw(15.sp, FontWeight.ExtraBold), color = Action, modifier = Modifier.pressable(onClick = onSkip).padding(8.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).safeDrawingPadding().padding(horizontal = 24.dp).padding(top = 26.dp, bottom = 12.dp)) {
            Text(title, style = opaw(26.sp, FontWeight.Black).copy(lineHeight = 30.sp), color = Ink)
            Text(body, style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 21.sp), color = Muted, modifier = Modifier.padding(top = 10.dp, bottom = 26.dp))
            OrangeButton(button, onClick = onNext)
        }
    }
}

/** 14b: the pet hops, rings pulse out from it like a heartbeat, and a "Hi!" pops up. */
@Composable
private fun AliveArt() {
    val t = loopClock(1800)
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0 until 2) {
                val p = (t + k * 0.5f) % 1f
                drawOval(
                    Action.copy(alpha = 0.5f * (1 - p)),
                    topLeft = Offset(size.width * (0.2f - 0.15f * p), size.height * (0.72f - 0.05f * p)),
                    size = Size(size.width * (0.6f + 0.3f * p), size.height * (0.16f + 0.1f * p)),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        Box(Modifier.size(220.dp).background(Color.White, CircleShape))
        Image(painterResource(R.drawable.welcome_pet), null, Modifier.size(200.dp).hop(1600, 14.dp), contentScale = ContentScale.Fit)
        Text(
            "Hi!", style = opaw(18.sp, FontWeight.Black), color = Ink,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 30.dp).popIn(3200, 0.2f)
                .shadow(10.dp, RoundedCornerShape(16.dp)).background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** 14c: tap the bowl. It squashes, three Paws fly up and "+5 Paws" pops. The pet peeks in. */
@Composable
private fun BowlArt() {
    val haptics = rememberHaptics()
    val squash = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    var tapped by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun tap() {
        haptics.success()
        tapped = true
        scope.launch {
            launch { squash.snapTo(1f); squash.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
            fly.snapTo(0f)
            fly.animateTo(1f, tween(900))
        }
    }
    // Until it's tapped, a fingertip shows where to tap.
    LaunchedEffect(Unit) { delay(2600); if (!tapped) tap() }
    Box(Modifier.fillMaxSize()) {
        // The counter top.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.3f).background(Color(0xFFF5E2A2)))
        Image(
            painterResource(R.drawable.welcome_pet), null,
            Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = (-50).dp).size(150.dp).drift(2400, 6.dp), contentScale = ContentScale.Fit,
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp).size(170.dp, 90.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(
                Modifier.fillMaxSize().pressable(onClick = ::tap).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = 1f + 0.12f * squash.value
                    scaleY = 1f - 0.18f * squash.value
                },
            ) {
                val w = size.width
                val h = size.height
                val bowl = Path().apply { moveTo(0f, h * 0.25f); lineTo(w, h * 0.25f); lineTo(w * 0.82f, h); lineTo(w * 0.18f, h); close() }
                drawPath(bowl, Color(0xFFE8590C))
                drawOval(Color(0xFF6E3712), Offset(w * 0.06f, h * 0.08f), Size(w * 0.88f, h * 0.32f))
                for (i in 0 until 9) drawCircle(Color(0xFF9A5420), w * 0.04f, Offset(w * (0.18f + i * 0.08f), h * (0.2f + 0.06f * ((i % 3) - 1))))
            }
            if (!tapped) Box(Modifier.align(Alignment.Center).size(40.dp).pulse().background(Color.White.copy(alpha = 0.8f), CircleShape))
        }
        if (fly.value > 0f) {
            for (k in 0 until 3) {
                val p = (fly.value * 1.2f - k * 0.1f).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomCenter).offset(x = ((k - 1) * 46).dp * p, y = (-150).dp - 170.dp * p).alpha(1f - p * 0.6f)
                        .size(34.dp).background(Action, CircleShape).border(3.dp, Color(0xFFFFC08A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(DesignIcon.PAW, size = 16.dp, tint = Color.White) }
            }
            Text(
                "+5 Paws", style = opaw(17.sp, FontWeight.Black), color = Color.White,
                modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp).graphicsLayer { val s = (fly.value * 2f).coerceAtMost(1f); scaleX = s; scaleY = s }
                    .background(Action, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** 14d: a big clock whose second hand sweeps round, and the reminder sliding down from the top. */
@Composable
private fun ClockArt() {
    val t = loopClock(6000)
    val drop = remember { Animatable(-200f) }
    LaunchedEffect(Unit) { delay(500); drop.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 200f)) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.Center).padding(top = 40.dp).size(230.dp)) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(Color.White, r, c)
            drawCircle(Color(0xFFD5E4F5), r, c, style = Stroke(6.dp.toPx()))
            for (i in 0 until 12) {
                val a = i * PI / 6
                val inner = r * 0.82f
                drawLine(Color(0xFFB8C6D6), c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()), c + Offset((cos(a) * r * 0.9f).toFloat(), (sin(a) * r * 0.9f).toFloat()), 4.dp.toPx(), cap = StrokeCap.Round)
            }
            rotate(-60f, c) { drawLine(Ink, c, c + Offset(0f, -r * 0.45f), 7.dp.toPx(), cap = StrokeCap.Round) }
            rotate(0f, c) { drawLine(Ink, c, c + Offset(0f, -r * 0.65f), 5.dp.toPx(), cap = StrokeCap.Round) }
            rotate(360f * t, c) { drawLine(Action, c + Offset(0f, r * 0.15f), c + Offset(0f, -r * 0.72f), 3.dp.toPx(), cap = StrokeCap.Round) }
            drawCircle(Ink, 7.dp.toPx(), c)
        }
        Notice(
            "Dinner in 10 minutes", "Miso is getting hungry",
            Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp).padding(top = 4.dp).graphicsLayer { translationY = drop.value * density },
        )
    }
}

/** 14e: the map, with the walk drawing itself from the start pin, then the pet where it ended. */
@Composable
private fun MapArt() {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(300); draw.animateTo(1f, tween(2200)) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Streets and a park.
            drawRoundRect(Color(0xFFCDE8C4), Offset(w * 0.42f, h * 0.26f), Size(w * 0.3f, h * 0.24f), androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()))
            for (y in listOf(0.18f, 0.58f, 0.82f)) drawLine(Color.White, Offset(0f, h * y), Offset(w, h * (y + 0.04f)), 14.dp.toPx())
            for (x in listOf(0.2f, 0.82f)) drawLine(Color.White, Offset(w * x, 0f), Offset(w * (x - 0.05f), h), 14.dp.toPx())
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.86f)
                cubicTo(w * 0.2f, h * 0.55f, w * 0.38f, h * 0.62f, w * 0.5f, h * 0.48f)
                cubicTo(w * 0.62f, h * 0.34f, w * 0.78f, h * 0.36f, w * 0.8f, h * 0.2f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * draw.value, part, true)
            drawPath(part, Action, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Action, 8.dp.toPx(), Offset(w * 0.18f, h * 0.86f))
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(w * 0.18f, h * 0.86f))
        }
        Text(
            "2.1 km · 34 min", style = opaw(14.sp, FontWeight.Black), color = Ink,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp).shadow(8.dp, CircleShape).background(Color.White, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (draw.value >= 1f) {
            Image(painterResource(R.drawable.welcome_pet), null, Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 30.dp).size(80.dp).hop(1800, 8.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp)).background(Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                Box(Modifier.size(30.dp).background(Color(0xFFFFB27A), CircleShape).border(2.dp, Color.White, CircleShape))
                Box(Modifier.size(30.dp).background(Color(0xFF8CC79A), CircleShape).border(2.dp, Color.White, CircleShape))
            }
            Column {
                Text("Lena walked Miso", style = opaw(14.sp, FontWeight.ExtraBold), color = Ink)
                Text("Everyone at home can see it", style = opaw(12.sp, FontWeight.SemiBold), color = Muted)
            }
        }
    }
}

/** A phone notification card, as the reminders land. */
@Composable
private fun Notice(title: String, detail: String, modifier: Modifier = Modifier, icon: DesignIcon = DesignIcon.BELL) {
    Row(
        modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F))), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, size = 20.dp, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = opaw(14.sp, FontWeight.ExtraBold), color = Ink)
            Text(detail, style = opaw(12.sp, FontWeight.SemiBold), color = Muted)
        }
        Text("now", style = opaw(11.sp, FontWeight.Bold), color = Color(0xFF999999))
    }
}

// MARK: - 14f, 14g The questions

@Composable
private fun QuestionScreen(step: Int, title: String, subtitle: String, enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit, rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Spacer(Modifier.height(8.dp))
        SetupHeader(step, 3, null, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = opaw(28.sp, FontWeight.Black).copy(lineHeight = 32.sp), color = Ink)
            Text(subtitle, style = opaw(15.sp, FontWeight.SemiBold), color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            rows()
        }
        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) { OrangeButton("Continue", enabled = enabled, onClick = onNext) }
    }
}

/** A clean row with a little drawing, ticked (or picked) in orange. */
@Composable
private fun ChoiceRow(icon: DesignIcon, title: String, detail: String?, on: Boolean, square: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick)
            .background(if (on) Peach else Color.White, shape)
            .border(if (on) 2.dp else 1.dp, if (on) Action else Color(0xFFEDEDF0), shape)
            .padding(horizontal = 14.dp, vertical = if (detail == null) 14.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(if (on) Color.White else Peach, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, size = 20.dp, tint = Action)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
            if (detail != null) Text(detail, style = opaw(12.sp, FontWeight.SemiBold), color = Muted)
        }
        val mark = if (square) RoundedCornerShape(7.dp) else CircleShape
        Box(
            Modifier.size(24.dp).background(if (on) Action else Color.White, mark).border(if (on) 0.dp else 2.dp, Color(0xFFD6D6DA), mark),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(DesignIcon.CHECK, size = 14.dp, weight = 3.2f, tint = Color.White) }
    }
}

// MARK: - 14h The plan

/** The answers turned into a short plan (14h), then a welcome gift in a chest. */
@Composable
private fun PlanScreen(focus: Set<String>, who: String, onBack: () -> Unit, onNext: () -> Unit) {
    val shared = who != "me"
    val items = buildList {
        if ("meals" in focus) add(Triple(DesignIcon.MEAL, "Meal reminders", if (shared) "At your times, for everyone at home" else "At your times"))
        if ("water" in focus) add(Triple(DesignIcon.WATER, "Water and potty breaks", "A nudge when it’s been a while"))
        if ("walks" in focus) add(Triple(DesignIcon.ACTIVITY, "A daily walk goal", "Starts easy, grows with your pet"))
        if ("meds" in focus) add(Triple(DesignIcon.MEDS, "Meds that nag nicely", if (shared) "The family sees when it’s given" else "Until you tap that it’s given"))
        if ("sleep" in focus) add(Triple(DesignIcon.MOON, "Sleep and comfort", "A cosy bed in their room"))
        if ("play" in focus) add(Triple(DesignIcon.BALL, "Play and training", "Little goals that earn Paws"))
        if (who == "walker") add(Triple(DesignIcon.USERS, "Walker invites", "They log walks, nothing else"))
    }
    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Spacer(Modifier.height(8.dp))
        SetupHeader(3, 3, null, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Here’s your plan", style = opaw(28.sp, FontWeight.Black), color = Ink)
            Text("Made from your answers.", style = opaw(15.sp, FontWeight.SemiBold), color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            items.forEach { (icon, title, detail) ->
                ChoiceRow(icon, title, detail, on = true, square = true) {}
            }
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Action.copy(alpha = 0.3f), spotColor = Action.copy(alpha = 0.3f))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F))), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("WELCOME GIFT", style = opaw(11.sp, FontWeight.Black).copy(letterSpacing = 1.5.sp), color = Color(0xFFFFE3D1))
                    Text("A surprise, just for\nnew homes", style = opaw(18.sp, FontWeight.Black).copy(lineHeight = 22.sp), color = Color.White)
                }
                Chest(Modifier.size(64.dp).wobble(), open = 0f)
            }
        }
        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) { OrangeButton("Claim my gift", onClick = onNext) }
    }
}

// MARK: - 14h2 The gift

/**
 * The welcome chest (14h2): it wobbles; tap it and the lid flips open, light fans out and a 50%
 * off ticket for the first year of Premium rises out, with a 24-hour countdown.
 */
@Composable
private fun GiftScreen(offerEnds: Long, onOpen: () -> Unit, onClaim: () -> Unit, onLater: () -> Unit) {
    val app = rememberApp()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val open = remember { Animatable(if (offerEnds > 0) 1f else 0f) }
    val store = app.premium
    val offer = store.offerProduct
    val first = store.introPrice(offer) ?: "$14.99"
    val then = store.displayPrice(offer) ?: "$29.99"
    fun tapChest() {
        if (open.value > 0f) return
        haptics.milestone()
        onOpen()
        scope.launch { open.animateTo(1f, tween(1100)) }
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF8A2A), Color(0xFFF2600C))))) {
        Box(Modifier.fillMaxWidth().weight(1f).safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Text(
                "Your welcome chest", style = opaw(14.sp, FontWeight.ExtraBold), color = Action,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.size(260.dp).background(Color.White.copy(alpha = 0.95f), CircleShape))
            if (open.value > 0.3f) LightFan(Modifier.size(300.dp).alpha((open.value - 0.3f) / 0.7f))
            Chest(Modifier.offset(y = 30.dp).size(150.dp).then(if (open.value == 0f) Modifier.wobble().pressable(onClick = ::tapChest) else Modifier), open = open.value)
            if (open.value > 0.5f) {
                val rise = ((open.value - 0.5f) / 0.5f)
                Ticket(offerEnds, Modifier.offset(y = (20 - 110 * rise).dp).graphicsLayer { val s = 0.6f + 0.4f * rise; scaleX = s; scaleY = s; alpha = rise })
            }
            if (open.value == 0f) {
                Text("Tap to open", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).drift(1800, 4.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).safeDrawingPadding().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 12.dp)) {
            Text(if (open.value > 0f) "Premium, 50% off" else "Something’s inside", style = opaw(26.sp, FontWeight.Black), color = Ink)
            Text(
                if (open.value > 0f) "Your welcome gift: $first for your first year of Opaw Premium, then $then a year. Only for the next 24 hours."
                else "Every new home gets a welcome gift. Tap the chest to open it.",
                style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 21.sp), color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
            )
            if (open.value > 0f) {
                OrangeButton("Claim 50% off", onClick = onClaim)
                SetupLink("Maybe later", color = Ink, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onLater)
            } else {
                OrangeButton("Open it", onClick = ::tapChest)
                SetupLink("Maybe later", color = Ink, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onLater)
            }
        }
    }
}

/** The Opaw chest: an orange box with a paw on its lock. [open] flips the lid from 0 to 1. */
@Composable
private fun Chest(modifier: Modifier, open: Float) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Offset(w * 0.08f, h * 0.42f)
        drawRoundRect(Color(0xFFE8590C), body, Size(w * 0.84f, h * 0.5f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
        drawRect(Color(0xFFFFC08A), Offset(w * 0.08f, h * 0.55f), Size(w * 0.84f, h * 0.06f))
        // The lid, hinged at the back, flips up and over.
        rotate(-110f * open, Offset(w * 0.5f, h * 0.42f)) {
            drawRoundRect(Color(0xFFFF7A1A), Offset(w * 0.05f, h * 0.18f), Size(w * 0.9f, h * 0.28f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f))
            drawRect(Color(0xFFFFC08A), Offset(w * 0.05f, h * 0.36f), Size(w * 0.9f, h * 0.05f))
        }
        // The lock with the paw.
        drawRoundRect(Color.White, Offset(w * 0.39f, h * 0.48f), Size(w * 0.22f, h * 0.22f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        val c = Offset(w * 0.5f, h * 0.61f)
        drawCircle(Color(0xFFE8590C), w * 0.045f, c)
        for (dx in listOf(-0.055f, 0f, 0.055f)) drawCircle(Color(0xFFE8590C), w * 0.018f, c + Offset(w * dx, -h * 0.06f))
    }
}

/** Soft light fanning out of the open chest. */
@Composable
private fun LightFan(modifier: Modifier) {
    val t = loopClock(12000)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height * 0.55f)
        for (i in 0 until 9) {
            rotate(-80f + i * 20f + 6f * sin(t * 2 * PI.toFloat()), c) {
                val p = Path().apply { moveTo(c.x, c.y); lineTo(c.x - 14.dp.toPx(), c.y - size.height * 0.5f); lineTo(c.x + 14.dp.toPx(), c.y - size.height * 0.5f); close() }
                drawPath(p, Color(0x33FFC94A))
            }
        }
    }
}

/** The 50% off ticket with its countdown, as on the chest and at sign-in. */
@Composable
private fun Ticket(offerEnds: Long, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(
            Modifier.padding(top = 14.dp).rotateTicket().shadow(16.dp, RoundedCornerShape(18.dp)).background(Color.White, RoundedCornerShape(18.dp))
                .border(2.dp, Color(0xFFFFD2B0), RoundedCornerShape(18.dp)).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("WELCOME OFFER", style = opaw(11.sp, FontWeight.Black).copy(letterSpacing = 1.5.sp), color = Action)
            Text("50% OFF", style = opaw(34.sp, FontWeight.Black), color = Action)
            Text("Opaw Premium · first year", style = opaw(12.sp, FontWeight.Bold), color = Muted)
        }
        Countdown(offerEnds, Modifier.align(Alignment.TopEnd).offset(x = 16.dp))
    }
}

private fun Modifier.rotateTicket() = graphicsLayer { rotationZ = -4f }

/** "Ends in 23:59:41", ticking. */
@Composable
internal fun Countdown(ends: Long, modifier: Modifier = Modifier, dark: Boolean = true) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val left = ((if (ends > 0) ends else now + 24 * 3600 * 1000L) - now).coerceAtLeast(0) / 1000
    val text = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", left / 3600, left / 60 % 60, left % 60)
    Row(
        modifier.background(if (dark) Color(0xFF141416) else Color.White, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DesignIcon.CLOCK, size = 12.dp, tint = if (dark) Color.White else Action)
        Text("Ends in $text", style = opaw(12.sp, FontWeight.ExtraBold), color = if (dark) Color.White else Action)
    }
}

// MARK: - 14i Reminders

/** Reminders with a reason (14i): the bell rings, and the three reminders they'll get stack in. */
@Composable
private fun RemindersScreen(onNext: () -> Unit) {
    val haptics = rememberHaptics()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    val t = loopClock(1400)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Lilac).safeDrawingPadding()) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 30.dp).size(120.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.1f)
                rotationZ = 14f * sin(t * 2 * PI.toFloat()) * (if (t < 0.5f) 1f else 0.3f)
            }) {
                Icon(DesignIcon.BELL, Modifier.align(Alignment.Center), size = 110.dp, weight = 1.6f, tint = Color.White)
                Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).size(22.dp).background(Action, CircleShape).border(3.dp, Lilac, CircleShape))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val items = listOf(
                    Triple("Dinner in 10 minutes", "Miso is getting hungry", DesignIcon.MEAL),
                    Triple("Apoquel at 18:00", "Tap when it’s given", DesignIcon.MEDS),
                    Triple("Walk time", "It’s sunny, 18°", DesignIcon.ACTIVITY),
                )
                items.forEachIndexed { i, (title, detail, icon) ->
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(400L + 500L * i); shown = true }
                    AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { -it }) { Notice(title, detail, icon = icon) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 12.dp)) {
            Text("Turn on reminders?", style = opaw(26.sp, FontWeight.Black), color = Ink)
            Text("Only for what you picked: meals, walks and meds. Never ads.", style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 21.sp), color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
            OrangeButton("Turn on reminders") {
                haptics.tap()
                // The system prompt only appears after Turn on.
                if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else onNext()
            }
            SetupLink("Not now", color = Ink, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = onNext)
        }
    }
}

// MARK: - 14j Sign in

/** Sign in to keep it (14j): back to brand orange, the 50% off ticket floating with its countdown. */
@Composable
private fun SignInBeat(offerEnds: Long, busy: Boolean, error: String?, onSignIn: () -> Unit) {
    val ready = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF8A2A), Color(0xFFF2600C))))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (offerEnds > 0) {
                    Text(
                        "Keep your offer", style = opaw(14.sp, FontWeight.ExtraBold), color = Action,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Box(Modifier.size(250.dp).background(Color.White.copy(alpha = 0.16f), CircleShape))
                    Ticket(offerEnds, Modifier.drift(3000, 10.dp))
                } else {
                    Box(Modifier.size(250.dp).background(Color.White, CircleShape))
                    Image(painterResource(R.drawable.welcome_pet), null, Modifier.size(180.dp).hop(2200, 14.dp))
                }
            }
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (offerEnds > 0) "Save your plan and offer" else "Save your plan",
                    style = opaw(28.sp, FontWeight.Black), color = Color.White, textAlign = TextAlign.Center,
                )
                Text(
                    (if (offerEnds > 0) "Sign in to keep your 50% off, and your pet’s room" else "Sign in to keep your pet’s room") + " and family care on any phone.",
                    style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 21.sp), color = Color(0xFFFFE3D1), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
                )
                val message = error ?: if (!ready) "Sign-in isn't set up in this build yet (missing Supabase or Google client settings)." else null
                if (message != null) {
                    Text(
                        message, style = opaw(13.sp, FontWeight.SemiBold).copy(lineHeight = 18.sp), color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color(0xFF141416), RoundedCornerShape(18.dp)).padding(14.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(58.dp).alpha(if (ready) 1f else 0.4f).pressable(ready && !busy, onClick = onSignIn)
                        .shadow(14.dp, CircleShape, ambientColor = Color(0xFF782800).copy(alpha = 0.3f), spotColor = Color(0xFF782800).copy(alpha = 0.3f))
                        .background(Color.White, CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Action, strokeWidth = 3.dp)
                        Text("Signing in…", style = opaw(17.sp, FontWeight.ExtraBold), color = Ink)
                    } else {
                        Image(painterResource(R.drawable.google_logo), null, Modifier.size(20.dp))
                        Text("Continue with Google", style = opaw(17.sp, FontWeight.ExtraBold), color = Ink)
                    }
                }
                Text(
                    "By continuing you agree to the Terms and Privacy Policy.",
                    style = opaw(12.sp, FontWeight.SemiBold), color = Color(0xFFFFE3D1), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

// MARK: - Motion and little drawings

/** A 0 to 1 clock that loops every [millis]. */
@Composable
private fun loopClock(millis: Int): Float {
    val t by rememberInfiniteTransition(label = "loop").animateFloat(0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = "t")
    return t
}

/** A chest's wobble: still, then a quick shake, as if something's inside. */
private fun Modifier.wobble(): Modifier = composed {
    val t = loopClock(2200)
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        rotationZ = if (t > 0.7f) 7f * sin((t - 0.7f) / 0.3f * 4 * PI.toFloat()) else 0f
    }
}

/** A plant's slow sway. */
private fun Modifier.sway(): Modifier = composed {
    val t = loopClock(3600)
    graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = 5f * sin(t * 2 * PI.toFloat()) }
}

/** A potted plant, for the welcome stage. */
@Composable
private fun Plant(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for ((a, len) in listOf(-30f to 0.55f, -8f to 0.68f, 14f to 0.6f, 34f to 0.48f)) {
            rotate(a, Offset(w / 2, h * 0.58f)) { drawOval(Color(0xFF7DBB86), Offset(w / 2 - w * 0.13f, h * 0.58f - h * len), Size(w * 0.26f, h * len)) }
        }
        val pot = Path().apply { moveTo(w * 0.18f, h * 0.56f); lineTo(w * 0.82f, h * 0.56f); lineTo(w * 0.72f, h); lineTo(w * 0.28f, h); close() }
        drawPath(pot, Color(0xFFF4F4F5))
        drawRect(Action, Offset(w * 0.2f, h * 0.66f), Size(w * 0.6f, h * 0.06f))
    }
}

/** Little sparkles twinkling round the stage. */
@Composable
private fun Sparkles(modifier: Modifier) {
    val t = loopClock(2400)
    Canvas(modifier) {
        val spots = listOf(0.08f to 0.2f, 0.9f to 0.14f, 0.94f to 0.62f, 0.05f to 0.72f, 0.7f to 0.95f)
        spots.forEachIndexed { i, (x, y) ->
            val a = ((sin((t + i * 0.2f) * 2 * PI.toFloat()) + 1) / 2)
            val c = Offset(size.width * x, size.height * y)
            val r = (5 + 4 * a).dp.toPx()
            drawLine(Color.White.copy(alpha = 0.4f + 0.6f * a), c - Offset(r, 0f), c + Offset(r, 0f), 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.4f + 0.6f * a), c - Offset(0f, r), c + Offset(0f, r), 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

