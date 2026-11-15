package app.opaw.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.opaw.BuildConfig
import app.opaw.R
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.components.GhostCycle
import app.opaw.ui.components.LoopMotion
import app.opaw.ui.components.SpeechBubble
import app.opaw.ui.components.loopMotion
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The welcome screen (9a/9b), which is also Google sign-in: there is no guest mode, so the only
 * way on is "Continue with Google". Signing in goes straight to the photos step of onboarding.
 */
@Composable
fun SignInScreen() {
    val app = rememberApp()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var signing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = app.pack.isConfigured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    Column(Modifier.fillMaxSize().background(colors.cream).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp).height(380.dp)) {
            GhostCycle(Modifier.padding(top = 40.dp).size(300.dp, 340.dp).align(Alignment.TopCenter).loopMotion(LoopMotion.BOB, 3.2))
            SpeechBubble("hi! take my picture?", Modifier.align(Alignment.TopEnd).padding(end = 12.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your pet, alive on your phone", style = opaw(40.sp, FontWeight.Black).copy(lineHeight = 42.sp), color = colors.ink)
            Text(
                "Take a few photos and we turn them into an avatar that reacts to every walk, meal and cuddle you log.",
                style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp),
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val message = if (signing) null else error ?: if (!ready) "Sign-in isn't set up in this build yet (missing Supabase or Google client settings)." else null
            AnimatedVisibility(message != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                        .background(colors.surface, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(DesignIcon.ALERT, size = 20.dp, tint = colors.alert)
                    Text(message.orEmpty(), style = opaw(14.sp, FontWeight.Bold).copy(lineHeight = 19.sp), color = colors.ink)
                }
            }
            GoogleButton(
                label = when {
                    signing -> "Signing in…"
                    error != null -> "Try again with Google"
                    else -> "Continue with Google"
                },
                signing = signing,
                enabled = ready,
            ) {
                if (signing) return@GoogleButton
                haptics.tap()
                signing = true
                error = null
                scope.launch {
                    try {
                        val (idToken, rawNonce) = googleIdToken(context)
                        app.pack.signInWithGoogle(idToken, rawNonce)
                        haptics.success()
                    } catch (cancelled: GetCredentialCancellationException) {
                        // Closing the sheet is quiet. Google also reports setup problems this way
                        // (such as "[16] Account reauth failed" for an unregistered SHA-1), so show those.
                        val reason = cancelled.message.orEmpty()
                        if (!reason.contains("cancelled by the user", ignoreCase = true) && reason.isNotBlank()) {
                            error = failureText(reason)
                        }
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
            Text(
                "You need a Google account to use Opaw. It keeps your pet's logs safe and shared with your household. By continuing you agree to the Terms and Privacy Policy.",
                style = opaw(13.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp),
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            )
        }
    }
}

/** The design's message, plus Google's or Supabase's own reason in test builds, to find setup problems. */
private fun failureText(reason: String): String {
    val base = "Couldn't sign in with Google. Check your connection and try again."
    return if (BuildConfig.DEBUG && reason.isNotBlank()) "$base\n\n${reason.take(300)}" else base
}

/** The black 62pt pill with Google's "G" in a white circle, or a spinner while signing in. */
@Composable
private fun GoogleButton(label: String, signing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (signing) Color(0xFF3A3A3C) else Ink, label = "google")
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .pressable(enabled, onClick = onClick)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(fill, CircleShape),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (signing) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), strokeWidth = 3.dp)
            } else {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.google_logo), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(label, style = opaw(17.sp, FontWeight.ExtraBold), color = Color.White, maxLines = 1)
    }
}

/**
 * Shows Google's account sheet and returns its ID token, with the raw nonce whose SHA-256 is
 * baked into the token. Supabase checks the two match, so a stolen token can't be replayed.
 */
private suspend fun googleIdToken(context: Context): Pair<String, String> {
    val rawNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw IllegalStateException("Google didn't return an account.")
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken to rawNonce
}
