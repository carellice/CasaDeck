package dev.casadeck.ui

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Shows the system biometric / screen-lock prompt. */
fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(code: Int, msg: CharSequence) = onResult(false)
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("CasaDeck")
    // API 30+ allows biometric OR device PIN in one prompt; older versions need the legacy flag
    if (Build.VERSION.SDK_INT >= 30) info.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
    else @Suppress("DEPRECATION") info.setDeviceCredentialAllowed(true)
    prompt.authenticate(info.build())
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val cs = MaterialTheme.colorScheme
    fun ask() = authenticate(activity, "Sblocca CasaDeck") { if (it) onUnlock() }
    LaunchedEffect(Unit) { ask() }
    val t = rememberInfiniteTransition(label = "lock")
    val s by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        AnimatedBlobs(Modifier.fillMaxSize(), 0.3f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(cs.primary, Teal))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Lock, null, Modifier.size(44.dp), tint = cs.onPrimary) }
            Spacer(Modifier.height(24.dp))
            Text("CasaDeck è bloccata", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(::ask, Modifier.height(52.dp), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Sblocca")
            }
        }
    }
}
