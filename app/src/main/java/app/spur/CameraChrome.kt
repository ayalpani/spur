package app.spur

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val CameraChrome = Color.Black.copy(alpha = 0.42f)

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
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 26.dp, bottom = 25.dp)
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
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 18.dp)
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .background(color, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    )
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
