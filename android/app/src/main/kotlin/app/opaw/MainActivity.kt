package app.opaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
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
import app.opaw.ui.SignInScreen
import app.opaw.ui.onboarding.OnboardingFlow
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.OpawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpawApplication
        setContent {
            OpawTheme {
                RootView(app)
                if (BuildConfig.DEBUG) CrashReport(app)
            }
        }
    }
}

private enum class Root { SIGN_IN, ONBOARDING, TABS }

/**
 * Google sign-in first (there's no anonymous mode), then onboarding until there's a dog, then
 * the tabs. Signing out or deleting the account clears the session and lands back on sign-in.
 */
@Composable
private fun RootView(app: OpawApplication) {
    val dogs by app.database.dogs().observeAll().collectAsStateWithLifecycle(initialValue = null)
    Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) {
        // Wait for the first read so onboarding doesn't flash for returning users.
        val current = dogs ?: return@Box
        val root = when {
            !app.pack.isSignedIn -> Root.SIGN_IN
            current.isEmpty() -> Root.ONBOARDING
            else -> Root.TABS
        }
        AnimatedContent(
            targetState = root,
            transitionSpec = {
                (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 1.04f)) togetherWith fadeOut(tween(600))
            },
            label = "root",
        ) { screen ->
            when (screen) {
                Root.SIGN_IN -> SignInScreen()
                Root.ONBOARDING -> OnboardingFlow(app)
                Root.TABS -> MainTabs()
            }
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
