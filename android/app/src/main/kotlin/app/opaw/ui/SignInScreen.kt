package app.opaw.ui

import android.content.Context
import androidx.compose.foundation.background
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
import app.opaw.ui.components.BlackPillButton
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

/** The first screen until there's a Google account. Opaw has no anonymous or guest mode. */
@Composable
fun SignInScreen() {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Opaw.colors
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = app.pack.isConfigured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    Column(Modifier.fillMaxSize().background(colors.cream).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(top = 56.dp).height(330.dp)) {
            GhostCycle(Modifier.padding(top = 40.dp).size(230.dp, 260.dp).align(Alignment.TopCenter).loopMotion(LoopMotion.BOB, 3.2))
            SpeechBubble("hi! who's my human?", Modifier.align(Alignment.TopEnd).padding(end = 12.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Welcome to Opaw", style = opaw(40.sp, FontWeight.Black).copy(lineHeight = 42.sp), color = colors.ink)
            Text(
                "Sign in with your Google account to keep your pets, walks and Pack friends tied to you.",
                style = opaw(16.sp, FontWeight.Medium).copy(lineHeight = 22.sp),
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val message = error ?: if (!ready) "Sign-in isn't set up in this build yet (missing Supabase or Google client settings)." else null
            message?.let { Text(it, style = opaw(14.sp, FontWeight.Medium), color = colors.alert, textAlign = TextAlign.Center) }
            if (working) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.ink) }
            } else {
                BlackPillButton("Continue with Google", enabled = ready) {
                    working = true
                    error = null
                    scope.launch {
                        try {
                            val (idToken, rawNonce) = googleIdToken(context)
                            app.pack.signInWithGoogle(idToken, rawNonce)
                        } catch (cancelled: GetCredentialCancellationException) {
                            // The user closed the account picker; stay here quietly.
                        } catch (none: NoCredentialException) {
                            error = "Add a Google account to this phone in Settings, then try again."
                        } catch (stop: CancellationException) {
                            throw stop
                        } catch (failure: Exception) {
                            error = failure.message ?: "Couldn't sign in. Please try again."
                        } finally {
                            working = false
                        }
                    }
                }
            }
        }
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
