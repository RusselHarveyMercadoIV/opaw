package app.opaw.ui.welcome

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.BuildConfig
import app.opaw.R
import app.opaw.ui.components.Action
import app.opaw.ui.components.DesignArt
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.SvgArt
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.svgDoc
import app.opaw.ui.failureText
import app.opaw.ui.googleIdToken
import app.opaw.ui.home.RoomArt
import app.opaw.ui.onboarding.OrangeButton
import app.opaw.ui.onboarding.SetupLink
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/** The screens before sign-in (14a to 14j), in order. */
private enum class Beat { WELCOME, ALIVE, TAP, ON_TIME, TOGETHER, HELP, WHO, PLAN, GIFT, REMINDERS, SIGN_IN }

/**
 * Onboarding (v5 14a to 14j), before sign-in, drawn from the design artifact: the same art,
 * places, colours and motion. The welcome, four beats, two quick questions, a plan with a welcome
 * gift in a chest (50% off the first year, for 24 hours), reminders with a reason, then sign in.
 * The pet itself is added on Home.
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
            Beat.ALIVE -> AliveScreen(skip) { go(Beat.TAP) }
            Beat.TAP -> TapScreen(skip) { go(Beat.ON_TIME) }
            Beat.ON_TIME -> OnTimeScreen(skip) { go(Beat.TOGETHER) }
            Beat.TOGETHER -> TogetherScreen(skip) { go(Beat.HELP) }
            Beat.HELP -> QuestionScreen(0.33f, "What should Opaw help with?", "Pick all that fit.", enabled = focus.isNotEmpty(), onBack = { go(Beat.TOGETHER) }, onNext = { go(Beat.WHO) }) {
                focuses.forEachIndexed { i, f ->
                    val on = f.key in focus
                    ChoiceRow(i, { FocusArt(f.key) }, f.title, null, on, square = true) { haptics.select(); focus = if (on) focus - f.key else focus + f.key }
                }
            }
            Beat.WHO -> QuestionScreen(0.67f, "Who looks after your pet?", "So reminders go to the right people.", enabled = true, onBack = { go(Beat.HELP) }, onNext = { go(Beat.PLAN) }) {
                helpers.forEachIndexed { i, (key, title, detail) ->
                    ChoiceRow(i, { HelperDots(key) }, title, detail, who == key, square = false) { haptics.select(); who = key }
                }
                Text(
                    "You’ll invite them from Home once your pet is in.", style = opaw(14.sp, FontWeight.SemiBold).copy(lineHeight = 20.sp), color = Color(0xFF777777),
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
                )
            }
            Beat.PLAN -> PlanScreen(focus, who, onBack = { go(Beat.WHO) }) { go(Beat.GIFT) }
            Beat.GIFT -> GiftScreen(
                opened = offerEnds > 0,
                onOpen = { if (offerEnds == 0L) scope.launch { app.settings.setWelcomeOfferEnds(System.currentTimeMillis() + 24 * 3600 * 1000L) } },
                ends = offerEnds,
                onClaim = { scope.launch { app.settings.setClaimAfterSignIn(true) }; go(Beat.REMINDERS) },
                onLater = { go(Beat.REMINDERS) },
            )
            Beat.REMINDERS -> RemindersScreen { go(Beat.SIGN_IN) }
            Beat.SIGN_IN -> SignInScreen(offerEnds, busy = signing || restoring, error = error, onSignIn = ::signIn)
        }
    }
}

// MARK: - The design's phone

/**
 * The design's phone is 390 by 844. Each screen here is laid out in those units, scaled to this
 * screen's width, so everything sits where the design has it. [s] is dp per design unit.
 */
private class Stage(val s: Float) {
    fun d(px: Float): Dp = (px * s).dp
    fun d(px: Int): Dp = (px * s).dp
}

@Composable
private fun DesignScreen(background: Color = Color(0xFFF4F4F5), content: @Composable BoxScope.(Stage) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val stage = remember(maxWidth) { Stage(maxWidth.value / 390f) }
        content(stage)
    }
}

/** Places something at a spot on the design's phone. */
private fun Modifier.at(st: Stage, x: Number, y: Number) = offset(st.d(x.toFloat()), st.d(y.toFloat()))
private fun Modifier.at(st: Stage, x: Number, y: Number, w: Number, h: Number) = at(st, x, y).size(st.d(w.toFloat()), st.d(h.toFloat()))

/** Seconds since this screen appeared, ticking every frame: the clock for its looping motion. */
@Composable
private fun rememberSeconds(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    return t
}

/** Where a CSS animation of [period] seconds is, 0 to 1, starting after [delay]. Null before it starts. */
private fun phase(t: Float, period: Float, delay: Float = 0f): Float? = if (t < delay) null else ((t - delay) % period) / period

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

/** A value between keyframes ([at], [value]) pairs, eased between each, as CSS does. */
private fun key(p: Float, vararg keys: Pair<Float, Float>): Float {
    if (p <= keys.first().first) return keys.first().second
    for (i in 0 until keys.size - 1) {
        val (a, va) = keys[i]
        val (b, vb) = keys[i + 1]
        if (p <= b) return va + (vb - va) * ease((p - a) / (b - a).coerceAtLeast(1e-4f))
    }
    return keys.last().second
}

/** The sample pet, drawn by the same flat cartoon prompt the avatar maker uses (supabase/functions/pet-model PICTURE_PROMPT). */
@Composable
private fun DesignPet(modifier: Modifier) {
    Image(painterResource(R.drawable.welcome_pet), null, modifier, contentScale = ContentScale.Fit)
}

/** ob-hop: still, a squash, a jump, a landing (2.4 s). */
private fun Modifier.obHop(t: Float, st: Stage) = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 1f)
    val p = t % 2.4f / 2.4f
    translationY = key(p, 0f to 0f, 0.58f to 0f, 0.66f to 0f, 0.77f to -36f, 0.88f to 0f, 1f to 0f) * st.s * density
    scaleX = key(p, 0f to 1f, 0.58f to 1f, 0.66f to 1.06f, 0.77f to 0.96f, 0.88f to 1.05f, 0.94f to 1f, 1f to 1f)
    scaleY = key(p, 0f to 1f, 0.58f to 1f, 0.66f to 0.92f, 0.77f to 1.05f, 0.88f to 0.95f, 0.94f to 1f, 1f to 1f)
}

/** ob-twinkle: sparkles breathing in and out (2.2 s). */
private fun Modifier.obTwinkle(t: Float, delay: Float) = graphicsLayer {
    val p = phase(t, 2.2f, delay) ?: 0f
    val k = key(p, 0f to 0.6f, 0.5f to 1.15f, 1f to 0.6f)
    scaleX = k; scaleY = k
    alpha = key(p, 0f to 0.45f, 0.5f to 1f, 1f to 0.45f)
}

/** ob-float: up and down 10 (2.6 s). */
private fun Modifier.obFloat(t: Float, st: Stage, period: Float = 2.6f) = graphicsLayer {
    translationY = key(t % period / period, 0f to 0f, 0.5f to -10f, 1f to 0f) * st.s * density
}

/** ob-pop: pops in at half way, holds, then goes (2.4 s), from [origin]. */
private fun Modifier.obPop(t: Float, origin: TransformOrigin) = graphicsLayer {
    transformOrigin = origin
    val k = key(t % 2.4f / 2.4f, 0f to 0f, 0.5f to 0f, 0.58f to 1.14f, 0.64f to 1f, 0.92f to 1f, 1f to 0f)
    scaleX = k; scaleY = k
}

/** A sparkle at a spot. */
@Composable
private fun BoxScope.Sparkle(st: Stage, t: Float, x: Int, y: Int, size: Int, color: String, delay: Float) {
    SvgArt(DesignArt.sparkle(color), Modifier.at(st, x, y, size, size).obTwinkle(t, delay))
}

/** The 4-dot progress and Skip at the top of the beats. */
@Composable
private fun BoxScope.BeatTop(st: Stage, index: Int, skipColor: Color, onSkip: () -> Unit) {
    Row(Modifier.at(st, 28, 62).width(st.d(338)).height(st.d(30)), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(st.d(6))) {
            for (i in 0 until 4) Box(Modifier.size(st.d(if (i == index) 22 else 8), st.d(8)).background(if (i == index) Action else Color(0xFFDDDDE1), CircleShape))
        }
        Box(Modifier.weight(1f))
        Text("Skip", style = opaw(15.sp, FontWeight.ExtraBold), color = skipColor, modifier = Modifier.pressable(onClick = onSkip).padding(4.dp))
    }
}

/** The coloured top half of a beat: 540 tall, rounded at the bottom. */
@Composable
private fun BoxScope.Panel(st: Stage, fill: Brush, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().height(st.d(540)).clip(RoundedCornerShape(bottomStart = st.d(44), bottomEnd = st.d(44))).background(fill), content = content)
}

/** A beat's title and its line, at 578 (or [top]). */
@Composable
private fun BoxScope.BeatText(st: Stage, title: String, body: String, top: Int = 578) {
    Column(Modifier.at(st, 28, top).width(st.d(334)), verticalArrangement = Arrangement.spacedBy(st.d(10))) {
        Text(title, style = opaw(30.sp, FontWeight.Black).copy(lineHeight = 33.sp, letterSpacing = (-0.3).sp), color = Ink)
        Text(body, style = opaw(16.sp, FontWeight.SemiBold).copy(lineHeight = 23.sp), color = Color(0xFF666666))
    }
}

/** The buttons at the bottom of a screen, 36 up from the bottom as in the design. */
@Composable
private fun BoxScope.Foot(st: Stage, content: @Composable () -> Unit) {
    Column(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = st.d(24), vertical = 12.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** A soft round shadow under the pet. */
@Composable
private fun BoxScope.Shadow(st: Stage, x: Int, y: Int, w: Int, h: Int, alpha: Float = 0.1f) {
    Box(Modifier.at(st, x, y, w, h).background(Color.Black.copy(alpha = alpha), CircleShape))
}

// MARK: - 14a Welcome

/** The welcome (14a): brand orange, the pet hopping on its stage, one white button. */
@Composable
private fun WelcomeScreen(busy: Boolean, failed: Boolean, error: String?, onStart: () -> Unit, onSignIn: () -> Unit) {
    val t = rememberSeconds()
    DesignScreen(Color(0xFFFF6A0F)) { st ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F)))))
        Box(Modifier.at(st, 55, 150, 300, 300).background(Color.White.copy(alpha = 0.95f), CircleShape))
        Box(Modifier.at(st, 85, 370, 240, 48).background(Color(0xFFFFE3CF), CircleShape))
        SvgArt(
            RoomArt.pieces.getValue("plant-pot"),
            Modifier.at(st, 45, 304, 96, 96).graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = key(t % 3f / 3f, 0f to -3f, 0.5f to 3f, 1f to -3f) },
        )
        if (busy) {
            // 11b: while the pets come back, the pet lounges on its couch like royalty.
            Shadow(st, 95, 404, 220, 20)
            Image(
                painterResource(R.drawable.royal_pet), null,
                Modifier.at(st, 75, 158, 260, 260).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = key(t % 3.2f / 3.2f, 0f to -1.5f, 0.5f to 1.5f, 1f to -1.5f)
                },
                contentScale = ContentScale.Fit,
            )
        } else {
            SvgArt(RoomArt.pieces.getValue("bed-donut"), Modifier.at(st, 249, 294, 120, 120))
            Shadow(st, 142, 400, 126, 18)
            DesignPet(Modifier.at(st, 100, 180, 210, 230).obHop(t, st))
            SvgArt(RoomArt.pieces.getValue("toy-ball"), Modifier.at(st, 229, 380, 56, 56).obFloat(t, st))
        }
        Sparkle(st, t, 66, 168, 22, "#fff", 0f)
        Sparkle(st, t, 330, 150, 16, "#FFE3D1", 0.7f)
        Sparkle(st, t, 344, 380, 22, "#fff", 1.3f)
        Sparkle(st, t, 52, 420, 14, "#FFE3D1", 0.4f)
        Row(Modifier.at(st, 0, 66).width(st.d(390)), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            SvgArt(DesignArt.PAW_WHITE, Modifier.size(st.d(34)))
            Text("Opaw", style = opaw(26.sp, FontWeight.Black), color = Color.White)
        }
        Column(Modifier.at(st, 32, 520).width(st.d(326)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(st.d(10))) {
            Text("A home for your pet, right on your phone", style = opaw(32.sp, FontWeight.Black).copy(lineHeight = 35.sp, letterSpacing = (-0.3).sp), color = Color.White, textAlign = TextAlign.Center)
            Text("Look after them, and they come alive.", style = opaw(16.sp, FontWeight.SemiBold), color = Color(0xFFFFE3D1), textAlign = TextAlign.Center)
        }
        Foot(st) {
            val message = error ?: if (failed) "Your pets are safe. Check your connection and try again." else null
            AnimatedVisibility(message != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Ink, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
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

// MARK: - 14b to 14e The beats

/**
 * 14b: peach. What the app really does: a phone photo of a pet is scanned, a dotted trail
 * carries it across, and the same pet, drawn the way Opaw draws every avatar, hops and says hi.
 * One 2.4 s loop, in step with the hop: scan (0 to 0.4), trail (0.3 to 0.62), hop on arrival.
 */
@Composable
private fun AliveScreen(onSkip: () -> Unit, onNext: () -> Unit) {
    val t = rememberSeconds()
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFFFF1E6), Color(0xFFFFE6D3)))) {
            val p = t % 2.4f / 2.4f
            // The photo, a tilted print with the scan sweeping down it.
            Column(
                Modifier.at(st, 26, 128, 160, 190).graphicsLayer { rotationZ = -7f }
                    .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x30783C00), spotColor = Color(0x30783C00))
                    .background(Color.White, RoundedCornerShape(18.dp)).padding(st.d(10)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(st.d(140)).clip(RoundedCornerShape(12.dp))) {
                    Image(painterResource(R.drawable.welcome_pet_photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    val scan = key(p, 0f to -0.3f, 0.4f to 1.05f, 1f to 1.05f)
                    Box(
                        Modifier.fillMaxWidth().height(st.d(42)).graphicsLayer { translationY = scan * 140f * st.s * density }
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent))),
                    )
                }
                Text("Your photo", style = opaw(13.sp, FontWeight.ExtraBold), color = Color(0xFF8A7A6E), modifier = Modifier.padding(top = st.d(9)))
            }
            // The dotted trail from the photo over to the pet.
            val drawn = key(p, 0f to 0f, 0.3f to 0f, 0.62f to 1f, 1f to 1f)
            Canvas(Modifier.at(st, 0, 0, 390, 540)) {
                val u = size.width / 390f
                val path = Path().apply {
                    moveTo(190f * u, 176f * u)
                    cubicTo(222f * u, 116f * u, 268f * u, 124f * u, 264f * u, 226f * u)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * drawn, part, true)
                drawPath(part, Action, style = Stroke(5f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 13f * u))))
                if (drawn > 0.98f) {
                    val tip = Offset(264f * u, 230f * u)
                    drawPath(Path().apply { moveTo(tip.x - 9f * u, tip.y - 10f * u); lineTo(tip.x, tip.y); lineTo(tip.x + 9f * u, tip.y - 10f * u) }, Action, style = Stroke(5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // The pet, as the avatar maker draws it, hopping as the trail lands.
            Shadow(st, 196, 452, 136, 18)
            DesignPet(Modifier.at(st, 152, 222, 220, 236).obHop(t, st))
            Text(
                "Hi!", style = opaw(15.sp, FontWeight.ExtraBold), color = Color.White,
                modifier = Modifier.at(st, 330, 196).obPop(t, TransformOrigin(0f, 1f))
                    .background(Ink, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            )
            Sparkle(st, t, 236, 118, 22, "#FF8F2E", 0f)
            Sparkle(st, t, 340, 160, 16, "#FFB27A", 0.7f)
            Sparkle(st, t, 60, 400, 18, "#FFB27A", 1.4f)
        }
        BeatTop(st, 0, Color(0xFFB84300), onSkip)
        BeatText(st, "Your pet, alive in 3D", "One photo and your pet comes to life. It explores, naps and runs up to say hi.")
        Foot(st) { OrangeButton("Next", onClick = onNext) }
    }
}

/** 14c: butter yellow, a counter top. A fingertip taps the bowl, it squashes, three Paws fly up and "+5 Paws" pops. */
@Composable
private fun TapScreen(onSkip: () -> Unit, onNext: () -> Unit) {
    val t = rememberSeconds()
    val haptics = rememberHaptics()
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFFFF4D6)))) {
            Box(Modifier.at(st, 0, 420, 390, 120).background(Color(0xFFFFE9B8)))
            val p = t % 2.4f / 2.4f
            SvgArt(
                RoomArt.pieces.getValue("bowl-food"),
                Modifier.at(st, 85, 236, 240, 240).pressable { haptics.success() }.graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = key(p, 0f to 1f, 0.52f to 1f, 0.56f to 1.07f, 0.63f to 0.97f, 0.72f to 1f, 1f to 1f)
                    scaleY = key(p, 0f to 1f, 0.52f to 1f, 0.56f to 0.88f, 0.63f to 1.05f, 0.72f to 1f, 1f to 1f)
                },
            )
            for ((x, delay) in listOf(161 to 1.2f, 193 to 1.35f, 179 to 1.5f)) {
                val f = phase(t, 2.4f, delay)
                if (f != null) {
                    // s16-fly: up a little, then off to the Paws count at the top right.
                    SvgArt(
                        DesignArt.PAW_COIN,
                        Modifier.at(st, x, 330, 36, 36).graphicsLayer {
                            val early = (f / 0.15f).coerceAtMost(1f)
                            translationX = (if (f < 0.15f) 0f else 150f * ((f - 0.15f) / 0.85f).let { it * it }) * st.s * density
                            translationY = (if (f < 0.15f) -30f * early else -30f - 440f * ((f - 0.15f) / 0.85f).let { it * it }) * st.s * density
                            val sc = if (f < 0.15f) 0.6f + 0.4f * early else 1f - 0.3f * ((f - 0.15f) / 0.85f)
                            scaleX = sc; scaleY = sc
                            alpha = when {
                                f < 0.15f -> early
                                f < 0.7f -> 1f
                                else -> 1f - (f - 0.7f) / 0.3f
                            }
                        },
                    )
                }
            }
            // The fingertip comes in, taps, and goes.
            Box(
                Modifier.at(st, 245, 330, 54, 54).graphicsLayer {
                    val gone = key(p, 0f to 1f, 0.36f to 1f, 0.48f to 0f, 0.62f to 0f, 0.78f to 1f, 1f to 1f)
                    translationX = 22f * gone * st.s * density
                    translationY = -22f * gone * st.s * density
                    val sc = key(p, 0f to 1f, 0.48f to 1f, 0.54f to 0.8f, 0.62f to 1f, 1f to 1f)
                    scaleX = sc; scaleY = sc
                    alpha = 1f - gone
                }
                    .shadow(10.dp, CircleShape)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(st.d(4))
                    .border(st.d(10), Action.copy(alpha = 0.25f), CircleShape),
            )
            DesignPet(Modifier.at(st, 285, 296, 120, 140))
            Row(
                Modifier.at(st, 135, 150).obPop(t, TransformOrigin(0.5f, 0.5f)).height(st.d(46))
                    .shadow(10.dp, CircleShape, ambientColor = Color(0x29785000), spotColor = Color(0x29785000))
                    .background(Color.White, CircleShape).padding(start = st.d(8), end = st.d(18)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SvgArt(DesignArt.PAW_COIN, Modifier.size(st.d(32)))
                Text("+5 Paws", style = opaw(18.sp, FontWeight.Black), color = Ink)
            }
        }
        BeatTop(st, 1, Color(0xFF8A5A00), onSkip)
        BeatText(st, "Care earns Paws", "Tap the bowl. Every meal, walk and dose you log earns Paws to spend on your pet’s room.")
        Foot(st) { OrangeButton("Next", onClick = onNext) }
    }
}

/** The clock face from the design, without its moving hand, in a 100 box. */
private val CLOCK = svgDoc(
    "0 0 100 100",
    """<circle cx="50" cy="50" r="46" fill="#fff" stroke="#D6E6F7" stroke-width="4"/>""" +
        (0 until 12).joinToString("") { """<rect x="49" y="8" width="2" height="7" rx="1" fill="#C9D6E4" transform="rotate(${it * 30} 50 50)"/>""" } +
        """<path d="M50 50L50 30" stroke="#111" stroke-width="5" stroke-linecap="round"/>""",
)
private val CLOCK_HAND = svgDoc("0 0 100 100", """<path d="M50 50L50 16" stroke="#F2600C" stroke-width="3.5" stroke-linecap="round"/>""")
private val CLOCK_PIN = svgDoc("0 0 100 100", """<circle cx="50" cy="50" r="5" fill="#111"/>""")

/** 14d: sky blue, a big clock whose second hand sweeps round, and the reminder sliding down. */
@Composable
private fun OnTimeScreen(onSkip: () -> Unit, onNext: () -> Unit) {
    val t = rememberSeconds()
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFEAF3FD), Color(0xFFEAF3FD)))) {
            SvgArt(CLOCK, Modifier.at(st, 80, 170, 250, 250))
            SvgArt(CLOCK_HAND, Modifier.at(st, 80, 170, 250, 250).graphicsLayer { rotationZ = 360f * (t % 6f / 6f) })
            SvgArt(CLOCK_PIN, Modifier.at(st, 80, 170, 250, 250))
            val p = t % 4.5f / 4.5f
            Notice(
                st, "Dinner in 10 minutes", "Miso is getting hungry",
                Modifier.at(st, 22, 104).graphicsLayer {
                    val out = key(p, 0f to 1f, 0.12f to 0f, 0.8f to 0f, 0.92f to 1f, 1f to 1f)
                    translationY = -1.4f * size.height * out
                    alpha = 1f - out
                },
            )
        }
        BeatTop(st, 2, Color(0xFF3E6A99), onSkip)
        BeatText(st, "Never miss a meal or meds", "Opaw nudges you before dinner and when meds are due.")
        Foot(st) { OrangeButton("Next", onClick = onNext) }
    }
}

/** A notification card, as in 14d and 14i. */
@Composable
private fun Notice(st: Stage, title: String, detail: String, modifier: Modifier) {
    Row(
        modifier.width(st.d(366))
            .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x24783C00), spotColor = Color(0x24783C00))
            .background(Color.White, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F))), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { SvgArt(DesignArt.PAW_WHITE, Modifier.size(26.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
            Text(detail, style = opaw(13.sp, FontWeight.SemiBold), color = Color(0xFF666666))
        }
    }
}

/** The map from 14e, without the walk, in its 410 by 540 box. */
private val MAP = svgDoc(
    "0 0 410 540",
    """<rect width="410" height="540" fill="#F4F1EA"/><path d="M-10 150Q120 110 190 200T430 250" stroke="#fff" stroke-width="22" fill="none"/><path d="M90 -10Q118 220 60 560" stroke="#fff" stroke-width="18" fill="none"/><path d="M300 -10L330 560" stroke="#fff" stroke-width="18" fill="none"/><path d="M-10 420L430 395" stroke="#fff" stroke-width="16" fill="none"/><rect x="140" y="250" width="130" height="100" rx="28" fill="#CFE8C7"/><circle cx="176" cy="290" r="16" fill="#B5DBAA"/><circle cx="228" cy="318" r="13" fill="#B5DBAA"/><circle cx="240" cy="276" r="10" fill="#B5DBAA"/><rect x="345" y="70" width="80" height="110" rx="18" fill="#E9E4DA"/><rect x="-20" y="230" width="80" height="100" rx="18" fill="#E9E4DA"/><rect x="190" y="440" width="90" height="80" rx="18" fill="#E9E4DA"/>""",
)

/** 14e: the whole panel is the map. The walk draws itself from the start pin, then the pet pops up where it ended. */
@Composable
private fun TogetherScreen(onSkip: () -> Unit, onNext: () -> Unit) {
    val t = rememberSeconds()
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFF4F1EA), Color(0xFFF4F1EA)))) {
            // The map is 410 wide in a 390 panel, centred: 10 off each side.
            SvgArt(MAP, Modifier.at(st, -10, 0, 410, 540))
            val p = t % 4.4f / 4.4f
            val drawn = key(p, 0f to 0f, 0.55f to 1f, 1f to 1f)
            Canvas(Modifier.at(st, -10, 0, 410, 540)) {
                val u = size.width / 410f
                val path = Path().apply {
                    moveTo(100f * u, 470f * u)
                    cubicTo(104f * u, 400f * u, 96f * u, 330f * u, 140f * u, 300f * u)
                    cubicTo(184f * u, 270f * u, 250f * u, 300f * u, 268f * u, 262f * u)
                    cubicTo(286f * u, 224f * u, 296f * u, 214f * u, 300f * u, 206f * u)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * drawn, part, true)
                drawPath(part, Action, style = Stroke(9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color.White, 12f * u, Offset(100f * u, 470f * u))
                drawCircle(Action, 12f * u, Offset(100f * u, 470f * u), style = Stroke(5f * u))
            }
            Box(
                Modifier.at(st, 266, 128, 68, 68).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val k = key(p, 0f to 0f, 0.55f to 0f, 0.62f to 1.15f, 0.68f to 1f, 1f to 1f)
                    scaleX = k; scaleY = k
                }.shadow(8.dp, CircleShape).background(Color.White, CircleShape).border(4.dp, Action, CircleShape).clip(CircleShape),
            ) { DesignPet(Modifier.fillMaxSize()) }
            Row(
                Modifier.at(st, 24, 110).height(st.d(40)).shadow(10.dp, CircleShape).background(Color.White, CircleShape).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("2.1 km", style = opaw(16.sp, FontWeight.Black), color = Ink)
                Text("· 34 min", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFF888888))
            }
            Row(
                Modifier.at(st, 22, 452).width(st.d(366))
                    .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x24783C00), spotColor = Color(0x24783C00))
                    .background(Color.White, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    Box(Modifier.size(36.dp).background(Color(0xFFFFB27A), CircleShape).border(3.dp, Color.White, CircleShape))
                    Box(Modifier.offset(x = (-12).dp).size(36.dp).background(Color(0xFF8CC79A), CircleShape).border(3.dp, Color.White, CircleShape))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Lena walked Miso", style = opaw(15.sp, FontWeight.ExtraBold), color = Ink)
                    Text("Everyone at home can see it", style = opaw(13.sp, FontWeight.SemiBold), color = Color(0xFF666666))
                }
            }
        }
        BeatTop(st, 3, Color(0xFF6B5B3E), onSkip)
        BeatText(st, "Walk together, share the care", "Track every walk on the map, and invite family or a walker. Everyone sees who fed, walked and gave meds.")
        Foot(st) { OrangeButton("Continue", onClick = onNext) }
    }
}

// MARK: - 14f to 14h The questions and the plan

/** What Opaw can help with (14f). */
private class Focus(val key: String, val title: String)

private val focuses = listOf(
    Focus("meals", "Meals on time"),
    Focus("water", "Water and potty breaks"),
    Focus("walks", "Walks and exercise"),
    Focus("meds", "Meds and vet visits"),
    Focus("sleep", "Sleep and comfort"),
    Focus("play", "Play and training"),
)

/** Who looks after the pet (14g). */
private val helpers = listOf(
    Triple("me", "Just me", "I do all the care"),
    Triple("family", "Me and my family", "We share it at home"),
    Triple("walker", "A walker or sitter helps", "Someone outside the home"),
)

/** The little drawing for each thing Opaw can help with, from the room. */
@Composable
private fun FocusArt(key: String) {
    when (key) {
        "meals" -> SvgArt(RoomArt.pieces.getValue("bowl-food"), Modifier.size(46.dp))
        "water" -> SvgArt(RoomArt.pieces.getValue("bowl-water"), Modifier.size(46.dp))
        "walks" -> SvgArt(DesignArt.DOOR, Modifier.size(40.dp))
        "meds" -> Icon(DesignIcon.MEDS, size = 24.dp, weight = 2.4f, tint = Action)
        "sleep" -> SvgArt(RoomArt.pieces.getValue("bed-donut"), Modifier.size(44.dp))
        "play" -> SvgArt(RoomArt.pieces.getValue("toy-ball"), Modifier.size(46.dp))
        "walker" -> HelperDots("walker")
    }
}

/** The faces for who looks after the pet: one, or two overlapping. */
@Composable
private fun HelperDots(key: String) {
    Row {
        Box(Modifier.size(28.dp).background(Color(0xFFFFB27A), CircleShape).border(2.5.dp, Color(0xFFFFF1E6), CircleShape))
        if (key != "me") {
            Box(
                Modifier.offset(x = (-10).dp).size(28.dp).background(if (key == "walker") Color(0xFF9AD3FF) else Color(0xFF8CC79A), CircleShape)
                    .border(2.5.dp, Color(0xFFFFF1E6), CircleShape),
            )
        }
    }
}

/** A question screen (14f, 14g): back, the bar, the title, then the rows. */
@Composable
private fun QuestionScreen(progress: Float, title: String, subtitle: String, enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit, rows: @Composable () -> Unit) {
    DesignScreen { st ->
        QuestionTop(st, progress, onBack)
        Column(Modifier.at(st, 24, 130).width(st.d(342)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = opaw(30.sp, FontWeight.Black).copy(lineHeight = 33.sp), color = Ink)
            Text(subtitle, style = opaw(15.sp, FontWeight.SemiBold).copy(lineHeight = 21.sp), color = Color(0xFF666666))
        }
        Column(Modifier.at(st, 20, 236).width(st.d(350)), verticalArrangement = Arrangement.spacedBy(10.dp)) { rows() }
        Foot(st) { OrangeButton("Continue", enabled = enabled, onClick = onNext) }
    }
}

@Composable
private fun BoxScope.QuestionTop(st: Stage, progress: Float, onBack: () -> Unit) {
    Row(Modifier.at(st, 20, 62).width(st.d(346)), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).pressable(onClick = onBack).shadow(6.dp, CircleShape, ambientColor = Color(0x0F000000), spotColor = Color(0x0F000000)).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(DesignIcon.BACK, size = 20.dp, tint = Ink) }
        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color(0xFFE6E6E8))) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).background(Action, CircleShape))
        }
    }
}

/** A clean row (14f to 14h): a little drawing, the words, and a tick or a dot, rising in. */
@Composable
private fun ChoiceRow(index: Int, art: @Composable () -> Unit, title: String, detail: String?, on: Boolean, square: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(600, delayMillis = index * 70)) }
    Row(
        Modifier.fillMaxWidth().height(66.dp)
            .graphicsLayer { translationY = 14f * (1 - rise.value) * density; alpha = rise.value }
            .pressable(onClick = onClick)
            .then(
                if (on) Modifier.background(Color(0xFFFFF8F2), shape).border(2.5.dp, Action, shape)
                else Modifier.shadow(6.dp, shape, ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)).background(Color.White, shape),
            )
            .padding(start = 10.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Color(0xFFFFF1E6), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { art() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = opaw(16.sp, FontWeight.ExtraBold), color = Ink)
            if (detail != null) Text(detail, style = opaw(13.sp, FontWeight.SemiBold), color = Color(0xFF777777))
        }
        val mark: Shape = if (square) RoundedCornerShape(8.dp) else CircleShape
        Box(
            Modifier.size(26.dp).then(if (on) Modifier.background(Action, mark) else Modifier.border(2.5.dp, Color(0xFFD6D6DA), mark)),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(DesignIcon.CHECK, size = 16.dp, weight = 3f, tint = Color.White) }
    }
}

/** The plan (14h): the answers as a short plan, then the welcome gift in a chest. */
@Composable
private fun PlanScreen(focus: Set<String>, who: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = rememberSeconds()
    val shared = who != "me"
    val items = buildList {
        if ("meals" in focus) add(Triple("meals", "Meal reminders", if (shared) "At your times, for everyone at home" else "At your times"))
        if ("water" in focus) add(Triple("water", "Water and potty breaks", "A nudge when it’s been a while"))
        if ("walks" in focus) add(Triple("walks", "A daily walk goal", "Starts easy, grows with your pet"))
        if ("meds" in focus) add(Triple("meds", "Meds that nag nicely", if (shared) "The family sees when it’s given" else "Until you tap that it’s given"))
        if ("sleep" in focus) add(Triple("sleep", "Sleep and comfort", "A cosy bed in their room"))
        if ("play" in focus) add(Triple("play", "Play and training", "Little goals that earn Paws"))
        if (who == "walker") add(Triple("walker", "Walker invites", "They log walks, nothing else"))
    }.take(4)
    DesignScreen { st ->
        QuestionTop(st, 1f, onBack)
        Column(Modifier.at(st, 24, 130).width(st.d(342)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Here’s your plan", style = opaw(30.sp, FontWeight.Black), color = Ink)
            Text("Made from your answers.", style = opaw(15.sp, FontWeight.SemiBold), color = Color(0xFF666666))
        }
        Column(Modifier.at(st, 20, 220).width(st.d(350)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, (key, title, detail) -> ChoiceRow(i + 3, { FocusArt(key) }, title, detail, on = true, square = true) {} }
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Action.copy(alpha = 0.25f), spotColor = Action.copy(alpha = 0.25f))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F))), RoundedCornerShape(26.dp))
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("WELCOME GIFT", style = opaw(11.sp, FontWeight.Black).copy(letterSpacing = 1.1.sp), color = Color.White.copy(alpha = 0.9f))
                    Text("A surprise, just for new homes", style = opaw(20.sp, FontWeight.Black).copy(lineHeight = 23.sp), color = Color.White)
                }
                Box(Modifier.size(84.dp).background(Color.White, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Chest(Modifier.size(76.dp), wobble = t, open = 0f)
                }
            }
        }
        Foot(st) { OrangeButton("Claim my gift", onClick = onNext) }
    }
}

// MARK: - 14h2 The welcome chest

/**
 * The Opaw chest, from the design: it wobbles while closed ([wobble] is the clock, or null to keep
 * still), and [open] (0 to 1) flips the lid up and back as in ob-lid1.
 */
@Composable
private fun Chest(modifier: Modifier, wobble: Float?, open: Float, shake: Boolean = false) {
    Box(modifier) {
        SvgArt(DesignArt.CHEST_SHADOW, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // ob-wob (2.2 s) on the gift card; ob-shake (4.4 s) on the gift screen. Both turn about (50, 90).
                transformOrigin = TransformOrigin(0.5f, 0.9f)
                val w = wobble ?: return@graphicsLayer
                if (shake) {
                    val p = w % 4.4f / 4.4f
                    rotationZ = key(p, 0f to 0f, 0.04f to 0f, 0.07f to -5f, 0.10f to 5f, 0.13f to -5f, 0.16f to 4f, 0.19f to 0f, 1f to 0f)
                    translationY = key(p, 0f to 0f, 0.16f to 0f, 0.19f to -5f, 0.23f to 0f, 1f to 0f) * size.height / 100f
                } else {
                    val p = w % 2.2f / 2.2f
                    rotationZ = key(p, 0f to 0f, 0.55f to 0f, 0.61f to -6f, 0.67f to 6f, 0.73f to -5f, 0.79f to 4f, 0.86f to 0f, 1f to 0f)
                    translationY = key(p, 0f to 0f, 0.79f to 0f, 0.86f to -5f, 0.92f to 0f, 1f to 0f) * size.height / 100f
                }
            },
        ) {
            SvgArt(DesignArt.CHEST_BODY, Modifier.fillMaxSize())
            SvgArt(
                DesignArt.CHEST_LID,
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    // ob-lid1: up past it, then settles open: translate (-6, -24), turned -12.
                    val u = size.width / 100f
                    val over = if (open < 0.6f) open / 0.6f else 1f
                    val settle = if (open < 0.6f) 0f else (open - 0.6f) / 0.4f
                    translationX = (-8f * over + 2f * settle) * u
                    translationY = (-34f * over + 10f * settle) * u
                    rotationZ = -18f * over + 6f * settle
                },
            )
        }
    }
}

/** The 50% off ticket, as it rises out of the chest and floats at sign-in. */
@Composable
private fun Ticket(modifier: Modifier) {
    Box(
        modifier.size(236.dp, 128.dp).graphicsLayer { rotationZ = -4f }
            .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x38782800), spotColor = Color(0x38782800))
            .background(Color.White, RoundedCornerShape(22.dp))
            .drawBehind {
                val inset = 8.dp.toPx()
                drawRoundRect(
                    Color(0xFFFFD3A8), Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(16.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("WELCOME OFFER", style = opaw(11.sp, FontWeight.Black).copy(letterSpacing = 1.3.sp), color = Color(0xFFB84300))
            Text("50% OFF", style = opaw(46.sp, FontWeight.Black).copy(lineHeight = 46.sp, letterSpacing = (-0.9).sp), color = Action)
            Text("Opaw Premium · first year", style = opaw(13.sp, FontWeight.Bold), color = Color(0xFF666666))
        }
    }
}

/** "Ends in 23:59:41", ticking: a black pill with an orange clock. */
@Composable
internal fun Countdown(ends: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val left = ((if (ends > 0) ends else now + 24 * 3600 * 1000L) - now).coerceAtLeast(0) / 1000
    val text = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", left / 3600, left / 60 % 60, left % 60)
    Row(
        modifier.height(34.dp).background(Ink, CircleShape).padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DesignIcon.CLOCK, size = 15.dp, weight = 3f, tint = Color(0xFFFF9A4D))
        Text("Ends in $text", style = opaw(14.sp, FontWeight.Black), color = Color.White)
    }
}

/** Confetti falling over the orange (ob-fall, 3.2 s each). */
@Composable
private fun BoxScope.Confetti(st: Stage, t: Float) {
    val pieces = listOf(
        listOf(40, 80, 10, 16, 0xFFFFFFFF, 0), listOf(90, 60, 8, 12, 0xFFFFD36B, 50), listOf(150, 90, 10, 10, 0xFFFFFFFF, 110),
        listOf(250, 70, 8, 14, 0xFFFFE3CF, 30), listOf(310, 100, 10, 16, 0xFFFFFFFF, 160), listOf(360, 60, 8, 10, 0xFFFFD36B, 90),
        listOf(200, 50, 8, 12, 0xFFFFFFFF, 210), listOf(120, 40, 10, 10, 0xFFFFE3CF, 260),
    )
    for (piece in pieces) {
        val p = phase(t, 3.2f, piece[5].toFloat() / 100f) ?: continue
        Box(
            Modifier.at(st, piece[0], piece[1], piece[2], piece[3]).graphicsLayer {
                translationY = (-40f + 400f * p) * st.s * density
                rotationZ = 320f * p
                alpha = if (p < 0.1f) p / 0.1f else 1f - (p - 0.1f) / 0.9f
            }.background(Color(piece[4].toLong()), RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * The welcome chest (14h2): it wobbles; tap it and the lid flips open, light fans out and a 50%
 * off ticket for the first year rises out, with a 24-hour countdown. It opens once.
 */
@Composable
private fun GiftScreen(opened: Boolean, onOpen: () -> Unit, ends: Long, onClaim: () -> Unit, onLater: () -> Unit) {
    val t = rememberSeconds()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val lid = remember { Animatable(if (opened) 1f else 0f) }
    val glow = remember { Animatable(if (opened) 1f else 0f) }
    val loot = remember { Animatable(if (opened) 1f else 0f) }
    val isOpen = lid.value > 0f
    fun open() {
        if (lid.value > 0f) return
        haptics.milestone()
        onOpen()
        scope.launch { lid.animateTo(1f, tween(550)) }
        scope.launch { kotlinx.coroutines.delay(150); glow.animateTo(1f, tween(600)) }
        scope.launch { kotlinx.coroutines.delay(250); loot.animateTo(1f, tween(700)) }
    }
    val store = rememberApp().premium
    val offer = store.offerProduct
    val first = store.introPrice(offer) ?: "$14.99"
    val then = store.displayPrice(offer) ?: "$29.99"
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F)))) {
            Confetti(st, t)
            Box(Modifier.at(st, 65, 180, 280, 280).background(Color.White.copy(alpha = 0.95f), CircleShape))
            // The light, turning slowly once it's open.
            SvgArt(
                DesignArt.CHEST_RAYS,
                Modifier.at(st, 35, 208, 340, 340).graphicsLayer {
                    val g = glow.value
                    scaleX = 0.4f + 0.6f * g; scaleY = scaleX; alpha = g
                    rotationZ = 360f * (t % 14f / 14f)
                },
            )
            Chest(Modifier.at(st, 115, 288, 180, 180).pressable(!isOpen, onClick = ::open), wobble = if (isOpen) null else t, open = lid.value, shake = true)
            // The ticket and its countdown rise out (ob-loot1).
            val l = loot.value
            val rise = if (l < 0.7f) l / 0.7f else 1f
            val settle = if (l < 0.7f) 0f else (l - 0.7f) / 0.3f
            Ticket(
                Modifier.at(st, 87, 238).graphicsLayer {
                    translationY = (80f - 90f * rise + 10f * settle) * st.s * density
                    val sc = 0.4f + 0.66f * rise - 0.06f * settle
                    scaleX = sc; scaleY = sc; alpha = rise
                },
            )
            if (l > 0f) Countdown(ends, Modifier.at(st, 227, 200).graphicsLayer { alpha = rise })
            Text(
                "Your welcome chest", style = opaw(15.sp, FontWeight.Black), color = Color(0xFFB84300),
                modifier = Modifier.at(st, 113, 116).height(st.d(40))
                    .shadow(10.dp, CircleShape, ambientColor = Color(0x2E782800), spotColor = Color(0x2E782800))
                    .background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (!isOpen) {
                Text(
                    "Tap to open", style = opaw(14.sp, FontWeight.ExtraBold), color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = st.d(26)).obFloat(t, st, 1.8f),
                )
            }
        }
        BeatText(
            st,
            "Premium, 50% off",
            "Your welcome gift: $first for your first year of Opaw Premium, then $then a year. Only for the next 24 hours.",
            top = 566,
        )
        Foot(st) {
            OrangeButton("Claim 50% off") { if (isOpen) onClaim() else open() }
            SetupLink("Maybe later", color = Ink, onClick = onLater)
        }
    }
}

// MARK: - 14i Reminders

/** The bell from the design, in a 100 box. */
private val BELL = svgDoc(
    "0 0 100 100",
    """<path d="M50 10a6 6 0 0 1 6 6v3a26 26 0 0 1 20 25v20l8 10H16l8-10V44a26 26 0 0 1 20-25v-3a6 6 0 0 1 6-6z" fill="#fff" stroke="#DCD3F5" stroke-width="4" stroke-linejoin="round"/><path d="M42 80a8 8 0 0 0 16 0" fill="#FF9A4D"/><circle cx="76" cy="24" r="10" fill="#F2600C" stroke="#fff" stroke-width="4"/>""",
)

/** Reminders with a reason (14i): lilac. The bell rings, and the reminders they'll get stack in. */
@Composable
private fun RemindersScreen(onNext: () -> Unit) {
    val t = rememberSeconds()
    val haptics = rememberHaptics()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFF3EFFD), Color(0xFFF3EFFD)))) {
            SvgArt(
                BELL,
                Modifier.at(st, 130, 110, 150, 150).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.08f)
                    rotationZ = key(t % 2.6f / 2.6f, 0f to 0f, 0.48f to 0f, 0.54f to 16f, 0.6f to -13f, 0.66f to 9f, 0.72f to -5f, 0.78f to 2f, 1f to 0f)
                },
            )
            val items = listOf(
                Triple("Dinner in 10 minutes", "Miso is getting hungry", 290),
                Triple("Apoquel at 18:00", "Tap when it’s given", 364),
                Triple("Walk time", "It’s sunny, 18°", 438),
            )
            items.forEachIndexed { i, (title, detail, y) ->
                val p = phase(t, 4.2f, i * 0.25f) ?: 0f
                Notice(
                    st, title, detail,
                    Modifier.at(st, 22, y).graphicsLayer {
                        val inn = key(p, 0f to 0f, 0.08f to 0f, 0.22f to 1f, 1f to 1f)
                        translationY = 26f * (1 - inn) * st.s * density
                        alpha = inn
                    },
                )
            }
        }
        BeatText(st, "Turn on reminders?", "Only for what you picked: meals, walks and meds. Never ads.")
        Foot(st) {
            OrangeButton("Turn on reminders") {
                haptics.tap()
                // The system prompt only appears after Turn on.
                if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else onNext()
            }
            SetupLink("Not now", color = Ink, onClick = onNext)
        }
    }
}

// MARK: - 14j Sign in

/** Sign in to keep it (14j): back to brand orange, the 50% off ticket floating with its countdown. */
@Composable
private fun SignInScreen(offerEnds: Long, busy: Boolean, error: String?, onSignIn: () -> Unit) {
    val t = rememberSeconds()
    val ready = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val offer = offerEnds > 0
    DesignScreen { st ->
        Panel(st, Brush.verticalGradient(listOf(Color(0xFFFF8F2E), Color(0xFFFF6A0F)))) {
            Confetti(st, t)
            Box(Modifier.at(st, 75, 190, 260, 260).background(Color.White.copy(alpha = 0.95f), CircleShape))
            if (offer) {
                Ticket(Modifier.at(st, 87, 256).obFloat(t, st))
                Countdown(offerEnds, Modifier.at(st, 211, 226))
            } else {
                DesignPet(Modifier.at(st, 105, 210, 200, 220).obHop(t, st))
            }
            Sparkle(st, t, 84, 220, 20, "#fff", 0f)
            Sparkle(st, t, 320, 420, 16, "#FFE3D1", 0.8f)
            Text(
                if (offer) "Keep your offer" else "Almost there", style = opaw(15.sp, FontWeight.Black), color = Color(0xFFB84300),
                modifier = Modifier.at(st, if (offer) 129 else 137, 116).height(st.d(40))
                    .shadow(10.dp, CircleShape, ambientColor = Color(0x2E782800), spotColor = Color(0x2E782800))
                    .background(Color.White, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        BeatText(
            st,
            if (offer) "Save your plan and offer" else "Save your plan",
            (if (offer) "Sign in to keep your 50% off, and your pet’s room" else "Sign in to keep your pet’s room") + " and family care on any phone.",
        )
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = st.d(24)).padding(bottom = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val message = error ?: if (!ready) "Sign-in isn't set up in this build yet (missing Supabase or Google client settings)." else null
            if (message != null) {
                Text(
                    message, style = opaw(13.sp, FontWeight.SemiBold).copy(lineHeight = 18.sp), color = Color.White,
                    modifier = Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(18.dp)).padding(14.dp),
                )
            }
            // The design's Google button: white with a black outline.
            Row(
                Modifier.fillMaxWidth().height(62.dp).alpha(if (ready) 1f else 0.4f).pressable(ready && !busy, onClick = onSignIn)
                    .shadow(14.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
                    .background(Color.White, CircleShape)
                    .border(2.dp, if (busy) Color(0xFFD4D4D8) else Ink, CircleShape),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Action, strokeWidth = 3.dp)
                    Text("Signing in…", style = opaw(17.sp, FontWeight.ExtraBold), color = Color(0xFF666666))
                } else {
                    Box(Modifier.size(26.dp).background(Color(0xFFF4F4F5), CircleShape), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.google_logo), null, Modifier.size(18.dp))
                    }
                    Text("Continue with Google", style = opaw(17.sp, FontWeight.ExtraBold), color = Ink)
                }
            }
            Text(
                "By continuing you agree to the Terms and Privacy Policy.",
                style = opaw(12.sp, FontWeight.SemiBold).copy(lineHeight = 17.sp), color = Color(0xFF999999), textAlign = TextAlign.Center,
            )
        }
    }
}

