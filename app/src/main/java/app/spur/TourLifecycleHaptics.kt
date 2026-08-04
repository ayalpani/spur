package app.spur

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal fun Context.vibrateTourStarted() {
    vibrateTourLifecycle(
        predefinedEffect = VibrationEffect.EFFECT_CLICK,
        fallbackTimings = longArrayOf(0L, 45L),
    )
}

internal fun Context.vibrateTourEnded() {
    vibrateTourLifecycle(
        predefinedEffect = VibrationEffect.EFFECT_DOUBLE_CLICK,
        fallbackTimings = longArrayOf(0L, 45L, 60L, 70L),
    )
}

private fun Context.vibrateTourLifecycle(
    predefinedEffect: Int,
    fallbackTimings: LongArray,
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    } ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(predefinedEffect)
    } else {
        VibrationEffect.createWaveform(fallbackTimings, -1)
    }
    vibrator.vibrate(effect)
}
