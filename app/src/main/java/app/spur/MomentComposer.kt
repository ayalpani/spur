package app.spur

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MomentComposer(
    target: MomentPlacementTarget?,
    onDismiss: () -> Unit,
    onPhotoAccepted: (MomentPlacementTarget, File) -> Unit,
) {
    val context = LocalContext.current
    var stage by remember(target) {
        mutableStateOf(if (target == null) ComposerStage.CLOSED else ComposerStage.PICKER)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            stage = ComposerStage.CAMERA
        } else {
            Toast.makeText(
                context,
                "Für Fotos braucht Spur Zugriff auf die Kamera.",
                Toast.LENGTH_LONG,
            ).show()
            onDismiss()
        }
    }
    val placementTarget = target ?: return

    if (stage == ComposerStage.PICKER) {
        MomentPickerSheet(
            sheetState = sheetState,
            onDismiss = onDismiss,
            onSelect = { type ->
                when (type) {
                    MomentType.VOICE -> context.comingSoon("Sprachaufnahme")
                    MomentType.EMOJI -> context.comingSoon("Emojimarker")
                    MomentType.VIDEO -> context.comingSoon("Videomarker")
                    MomentType.PHOTO -> {
                        if (context.hasCameraPermission()) {
                            stage = ComposerStage.CAMERA
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            },
        )
    }

    if (stage == ComposerStage.CAMERA) {
        CameraScreen(
            onClose = onDismiss,
            onPhotoAccepted = { photo -> onPhotoAccepted(placementTarget, photo) },
        )
    }
}

private fun android.content.Context.comingSoon(name: String) {
    Toast.makeText(this, "$name kommt als Nächstes.", Toast.LENGTH_SHORT).show()
}

private enum class ComposerStage {
    CLOSED,
    PICKER,
    CAMERA,
}
