package app.spur

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val CameraChrome = Color.Black.copy(alpha = 0.42f)

@Composable
internal fun CameraOrientation(): Boolean {
    val activity = LocalContext.current.findComponentActivity()
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    return LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}

internal fun cameraTargetRotation(displayRotation: Int?): Int = when (displayRotation) {
    Surface.ROTATION_0,
    Surface.ROTATION_90,
    Surface.ROTATION_180,
    Surface.ROTATION_270,
    -> displayRotation
    else -> Surface.ROTATION_0
}

@Composable
internal fun rememberCameraTargetRotation(): Int {
    val context = LocalContext.current
    val view = LocalView.current
    var targetRotation by remember(view) {
        mutableIntStateOf(cameraTargetRotation(view.display?.rotation))
    }
    DisposableEffect(context, view) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                cameraTargetRotationFromOrientation(orientation)?.let {
                    targetRotation = it
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose(listener::disable)
    }
    return targetRotation
}

internal fun cameraTargetRotationFromOrientation(orientation: Int): Int? = when (orientation) {
    OrientationEventListener.ORIENTATION_UNKNOWN -> null
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    in 0..359 -> Surface.ROTATION_0
    else -> null
}

@Composable
internal fun BoxScope.CameraCloseButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(18.dp)
            .size(52.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome,
            contentColor = Color.White,
        ),
    ) {
        CloseCameraIcon()
    }
}

@Composable
internal fun BoxScope.CameraSwitchButton(
    contentDescription: String,
    landscape: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(
                end = 26.dp,
                bottom = if (landscape) 18.dp else 25.dp,
            )
            .size(58.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome,
            contentColor = Color.White,
        ),
    ) {
        SwitchCameraIcon()
    }
}

@Composable
internal fun BoxScope.CameraCaptureButton(
    enabled: Boolean,
    contentDescription: String,
    color: Color,
    landscape: Boolean = false,
    shape: Shape = CircleShape,
    innerSize: Dp = 64.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                end = if (landscape) 18.dp else 0.dp,
                bottom = if (landscape) 0.dp else 18.dp,
            )
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(innerSize).background(color, shape))
    }
}

@Composable
internal fun CloseCameraIcon() = LucideIcon(
    paths = listOf("M18 6 6 18", "m6 6 12 12"),
    modifier = Modifier.size(24.dp),
    strokeWidth = LucideBoldStrokeWidth,
)

@Composable
internal fun SwitchCameraIcon() = LucideIcon(
    paths = listOf(
        "M11 19H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h5",
        "M13 5h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-5",
        "M15 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "m18 22-3-3 3-3",
        "m6 2 3 3-3 3",
    ),
    modifier = Modifier.size(28.dp),
    strokeWidth = LucideBoldStrokeWidth,
)
