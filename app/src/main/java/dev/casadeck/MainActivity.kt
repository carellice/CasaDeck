package dev.casadeck

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import dev.casadeck.ui.CasaTheme
import dev.casadeck.ui.MainScreen
import dev.casadeck.ui.OnboardingScreen

// FragmentActivity: required by BiometricPrompt
class MainActivity : FragmentActivity() {
    private val vm: CasaViewModel by viewModels()
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val ui by vm.ui.collectAsState()
            val stage by vm.stage.collectAsState()
            CasaTheme(ui) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(
                        stage,
                        transitionSpec = {
                            (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.92f)) togetherWith
                                (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.06f))
                        },
                        label = "stage",
                    ) { s -> if (s == Stage.Onboarding) OnboardingScreen(vm) else MainScreen(vm) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Tile and widgets start the app with an action or an app id. */
    private fun handle(i: Intent) {
        if (vm.stage.value != Stage.Main) return
        if (i.action == ACTION_QUICK) vm.quickPick.value = true
        i.getStringExtra(EXTRA_APP)?.let { vm.openAppId(it) }
    }

    override fun onStop() {
        super.onStop()
        Widgets.refresh(this) // widgets show fresh numbers after using the app
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // re-lock after 30 s in background (short app switches don't ask again)
        if (backgroundedAt > 0 && vm.prefs.lock && vm.stage.value == Stage.Main &&
            SystemClock.elapsedRealtime() - backgroundedAt > 30_000
        ) vm.locked.value = true
    }

    companion object {
        const val ACTION_QUICK = "dev.casadeck.QUICK_APPS"
        const val EXTRA_APP = "app"
    }
}
