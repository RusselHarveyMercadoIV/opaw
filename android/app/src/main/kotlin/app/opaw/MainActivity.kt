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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.ui.MainTabs
import app.opaw.ui.onboarding.OnboardingPlaceholder
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
            }
        }
    }
}

/** Onboarding until there's a dog, then the tabs. Mirrors `RootView` in OpawApp.swift. */
@Composable
private fun RootView(app: OpawApplication) {
    val dogs by app.database.dogs().observeAll().collectAsStateWithLifecycle(initialValue = null)
    Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) {
        // Wait for the first read so onboarding doesn't flash for returning users.
        val current = dogs ?: return@Box
        AnimatedContent(
            targetState = current.isEmpty(),
            transitionSpec = {
                (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 1.04f)) togetherWith fadeOut(tween(600))
            },
            label = "root",
        ) { showsOnboarding ->
            if (showsOnboarding) OnboardingPlaceholder(app) else MainTabs()
        }
    }
}
