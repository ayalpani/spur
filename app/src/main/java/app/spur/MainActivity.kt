package app.spur

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartedAt = SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        var splashExitComplete by mutableStateOf(false)
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStartedAt <
                MinimumSystemSplashDurationMillis
        }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(LoaderAsteriskAccelerationDurationMillis.toLong())
                .withEndAction {
                    provider.remove()
                    splashExitComplete = true
                }
                .start()
        }
        enableEdgeToEdge()
        setContent { SpurApp(splashExitComplete = splashExitComplete) }
    }
}
