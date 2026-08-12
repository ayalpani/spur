package app.spur

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun SelfieButtonPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraAllowed = context.hasCameraPermission()
    var previewUnavailable by remember(cameraAllowed) { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(cameraAllowed, lifecycleOwner, previewView) {
        if (!cameraAllowed) {
            onDispose {}
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            var cameraPreview: Preview? = null
            var disposed = false
            providerFuture.addListener(
                {
                    if (disposed) return@addListener
                    runCatching {
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        providerFuture.get().bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                        )
                        cameraPreview = preview
                    }.onFailure { previewUnavailable = true }
                },
                executor,
            )
            onDispose {
                disposed = true
                cameraPreview?.let { preview ->
                    if (providerFuture.isDone) {
                        runCatching { providerFuture.get().unbind(preview) }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(SelfieButtonPreviewSize)
            .clip(CircleShape)
            .background(NeutralSurface)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (cameraAllowed && !previewUnavailable) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            MomentVideoIcon()
        }
    }
}

private val SelfieButtonPreviewSize = 36.dp
