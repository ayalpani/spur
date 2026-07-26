package app.spur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private val CameraChrome = Color.Black.copy(alpha = 0.42f)

@Composable
internal fun CameraScreen(
    showFeedbackNotice: (String) -> Unit,
    onClose: () -> Unit,
    onPhotoAccepted: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedPhoto by remember { mutableStateOf<File?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    fun discardAndClose() {
        capturedPhoto?.delete()
        onClose()
    }

    BackHandler { discardAndClose() }

    DisposableEffect(lifecycleOwner, lensFacing, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false

        providerFuture.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    imageCapture = capture
                }.onFailure {
                    showFeedbackNotice("Die Kamera konnte nicht geöffnet werden.")
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            imageCapture = null
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val photo = capturedPhoto
        if (photo == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Kameravorschau" },
            )

            IconButton(
                onClick = ::discardAndClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(18.dp)
                    .size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CameraChrome,
                    contentColor = Color.White,
                ),
            ) {
                CloseCameraIcon()
            }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 26.dp, bottom = 25.dp)
                    .size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CameraChrome,
                    contentColor = Color.White,
                ),
            ) {
                SwitchCameraIcon()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
                    .size(78.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(7.dp)
                    .background(Color.White, CircleShape)
                    .clickable(enabled = imageCapture != null && !isCapturing) {
                        val capture = imageCapture ?: return@clickable
                        val photoDirectory = File(context.filesDir, "moments/photos").apply {
                            mkdirs()
                        }
                        val output = File(photoDirectory, "photo-${System.currentTimeMillis()}.jpg")
                        previewView.display?.rotation?.let { capture.targetRotation = it }
                        isCapturing = true
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(output).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    outputFileResults: ImageCapture.OutputFileResults,
                                ) {
                                    isCapturing = false
                                    capturedPhoto = output
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    output.delete()
                                    showFeedbackNotice(
                                        "Das Foto konnte nicht gespeichert werden.",
                                    )
                                }
                            },
                        )
                    }
                    .semantics { contentDescription = "Foto aufnehmen" },
            )
        } else {
            val bitmap = remember(photo) { decodePreviewBitmap(photo) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Aufgenommenes Foto",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Button(
                    onClick = {
                        photo.delete()
                        capturedPhoto = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Verwerfen")
                }
                Button(
                    onClick = { onPhotoAccepted(photo) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Verwenden")
                }
            }
        }
    }
}

private fun decodePreviewBitmap(file: File): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > 2048) {
                    val scale = 2048f / maxSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt(),
                    )
                }
            }
        } else {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = 4 },
            )
        }
    }.getOrNull()

@Composable
private fun CloseCameraIcon() = LucideIcon(
    paths = listOf("M18 6 6 18", "m6 6 12 12"),
    modifier = Modifier.size(24.dp),
    strokeWidth = LucideBoldStrokeWidth,
)

@Composable
private fun SwitchCameraIcon() = LucideIcon(
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
