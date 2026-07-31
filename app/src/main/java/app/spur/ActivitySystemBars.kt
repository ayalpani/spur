@file:Suppress("DEPRECATION")

package app.spur

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

internal const val ActivityNavigationBarOverlayPriority = 1

@Composable
internal fun ActivityNavigationBar(
    backgroundColor: Color,
    priority: Int = 0,
) {
    val activity = LocalContext.current.findComponentActivity()
    DisposableEffect(activity, backgroundColor, priority) {
        val token = activity?.let {
            ActivityNavigationBarOverrides.add(
                activity = it,
                color = backgroundColor,
                priority = priority,
            )
        }
        onDispose {
            if (activity != null && token != null) {
                ActivityNavigationBarOverrides.remove(activity, token)
            }
        }
    }
}

private object ActivityNavigationBarOverrides {
    private data class Baseline(
        val color: Int,
        val contrastEnforced: Boolean?,
        val useDarkIcons: Boolean?,
    )

    private data class Override(
        val token: Any,
        val color: Color,
        val priority: Int,
        val sequence: Long,
    )

    private data class State(
        val baseline: Baseline,
        val overrides: MutableList<Override> = mutableListOf(),
    )

    private val states = WeakHashMap<ComponentActivity, State>()
    private var nextSequence = 0L

    fun add(
        activity: ComponentActivity,
        color: Color,
        priority: Int,
    ): Any {
        val token = Any()
        val state = states.getOrPut(activity) { State(activity.captureNavigationBar()) }
        state.overrides += Override(
            token = token,
            color = color,
            priority = priority,
            sequence = nextSequence++,
        )
        activity.applyNavigationBar(state.overrides.top().color)
        return token
    }

    fun remove(activity: ComponentActivity, token: Any) {
        val state = states[activity] ?: return
        state.overrides.removeAll { it.token === token }
        val current = state.overrides.topOrNull()
        if (current != null) {
            activity.applyNavigationBar(current.color)
        } else {
            activity.restoreNavigationBar(state.baseline)
            states.remove(activity)
        }
    }

    private fun List<Override>.top(): Override = maxWith(
        compareBy<Override> { it.priority }.thenBy { it.sequence },
    )

    private fun List<Override>.topOrNull(): Override? = maxWithOrNull(
        compareBy<Override> { it.priority }.thenBy { it.sequence },
    )

    private fun ComponentActivity.captureNavigationBar(): Baseline {
        val window = window
        return Baseline(
            color = window.navigationBarColor,
            contrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced
            } else {
                null
            },
            useDarkIcons = WindowCompat.getInsetsController(
                window,
                window.decorView,
            ).isAppearanceLightNavigationBars,
        )
    }

    private fun ComponentActivity.applyNavigationBar(color: Color) {
        val colorInt = color.toArgb()
        val useDarkIcons = color.luminance() > 0.5f
        enableEdgeToEdge(
            navigationBarStyle = navigationBarStyle(
                color = colorInt,
                useDarkIcons = useDarkIcons,
            ),
        )
        window.navigationBarColor = colorInt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(
            window,
            window.decorView,
        ).isAppearanceLightNavigationBars = useDarkIcons
    }

    private fun ComponentActivity.restoreNavigationBar(baseline: Baseline) {
        val useDarkIcons = baseline.useDarkIcons ?: (Color(baseline.color).luminance() > 0.5f)
        enableEdgeToEdge(
            navigationBarStyle = navigationBarStyle(
                color = baseline.color,
                useDarkIcons = useDarkIcons,
            ),
        )
        window.navigationBarColor = baseline.color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseline.contrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
        }
        baseline.useDarkIcons?.let {
            WindowCompat.getInsetsController(
                window,
                window.decorView,
            ).isAppearanceLightNavigationBars = it
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun navigationBarStyle(
    color: Int,
    useDarkIcons: Boolean,
): SystemBarStyle = if (useDarkIcons) {
    SystemBarStyle.light(color, color)
} else {
    SystemBarStyle.dark(color)
}
