package app.opaw

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opaw.ui.theme.opaw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.opaw.ui.profile.HouseholdJoinHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.services.CrashLog
import app.opaw.ui.MainTabs
import app.opaw.ui.OpawSplash
import app.opaw.ui.welcome.WelcomeFlow
import app.opaw.ui.onboarding.OnboardingFlow
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.OpawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpawApplication
        takeInvite(intent)
        // Shared pets: catch up with the household while the app is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    app.household.syncAll()
                    delay(45_000)
                }
            }
        }
        setContent {
            OpawTheme {
                RootView(app)
                HouseholdJoinHost(app)
                if (BuildConfig.DEBUG) CrashReport(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeInvite(intent)
    }

    /** An invite link (opaw://join?code=…) keeps its code until it can be joined. */
    private fun takeInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "opaw" || data.host != "join") return
        data.getQueryParameter("code")?.let { (application as OpawApplication).household.offer(it) }
    }
}

private enum class Root { OPENING, SIGN_IN, ONBOARDING, TABS }

/**
 * Google sign-in first (there's no anonymous mode), then onboarding until there's a dog, then
 * the tabs. Signing in again (a reinstall, a new phone) brings the account's pets back behind
 * the busy sign-in button and goes straight to Home. Signing out or deleting the account clears
 * the session and lands back on sign-in.
 */
@Composable
private fun RootView(app: OpawApplication) {
    val dogs by app.database.dogs().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val adding by app.settings.addingPet.collectAsStateWithLifecycle(initialValue = null)
    var signInShown by rememberSaveable { mutableStateOf(false) }
    var restoreFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    // The animated opening plays once per launch; saved so turning the phone doesn't replay it.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    // Wait for the first read so onboarding doesn't flash for returning users.
    val current = dogs.orEmpty()
    val loaded = dogs != null && adding != null
    // Signed in with no pets here: bring back the account's pets before choosing a screen.
    val needsRestore = loaded && app.pack.isSignedIn && current.isEmpty() && adding != true && !app.household.hasChecked
    LaunchedEffect(needsRestore, attempt) {
        if (needsRestore) {
            restoreFailed = false
            restoreFailed = !app.household.restore()
        }
    }
    val root = when {
        !splashDone || !loaded -> Root.OPENING
        !app.pack.isSignedIn -> Root.SIGN_IN
        // Just signed in, or the account couldn't be reached: stay on sign-in (11b, 11d).
        needsRestore -> if (signInShown || restoreFailed) Root.SIGN_IN else Root.OPENING
        // A new account lands on Home's empty room (14k); "Add your first pet" opens the photos.
        current.isEmpty() && adding == true -> Root.ONBOARDING
        else -> Root.TABS
    }
    LaunchedEffect(root) { if (root == Root.SIGN_IN) signInShown = true }
    AnimatedContent(
        targetState = root,
        transitionSpec = {
            (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 1.04f)) togetherWith fadeOut(tween(600))
        },
        modifier = Modifier.fillMaxSize().background(Opaw.colors.cream),
        label = "root",
    ) { screen ->
        when (screen) {
            Root.OPENING -> OpawSplash(onFinished = { splashDone = true })
            Root.SIGN_IN -> WelcomeFlow(
                restoring = needsRestore && !restoreFailed,
                restoreFailed = needsRestore && restoreFailed,
                onRetry = { attempt++ },
            )
            Root.ONBOARDING -> OnboardingFlow(app)
            Root.TABS -> MainTabs()
        }
    }
}

/** Test builds only: after a crash, the next launch shows why, ready to copy and send. */
@Composable
private fun CrashReport(app: OpawApplication) {
    var report by remember { mutableStateOf(CrashLog.take(app)) }
    val text = report ?: return
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text("Opaw crashed last time") },
        text = {
            SelectionContainer {
                Text(text, Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        },
        confirmButton = { TextButton({ clipboard.setText(AnnotatedString(text)); report = null }) { Text("Copy and close") } },
        dismissButton = { TextButton({ report = null }) { Text("Close") } },
    )
}
