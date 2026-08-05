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
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun CameraScreen(
    showFeedbackNotice: ShowFeedbackNotice,
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

    ActivityNavigationBar(
        backgroundColor = Color.Black,
        priority = ActivityNavigationBarOverlayPriority,
    )

    fun discardAndClose() {
        capturedPhoto?.delete()
        onClose()
    }

    BackHandler { discardAndClose() }

    DisposableEffect(lifecycleOwner, lensFacing, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false

        fun bindCamera() {
            if (disposed) return
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
                val useCases = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .setViewPort(requireNotNull(previewView.viewPort))
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, useCases)
                imageCapture = capture
            }.onFailure {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Die Kamera konnte nicht geöffnet werden.",
                )
            }
        }

        providerFuture.addListener({
            if (previewView.viewPort == null) {
                previewView.doOnLayout { bindCamera() }
            } else {
                bindCamera()
            }
        }, mainExecutor)

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

            CameraCloseButton(
                contentDescription = "Kamera schließen",
                onClick = ::discardAndClose,
            )

            CameraSwitchButton(
                contentDescription = "Kamera wechseln",
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
            )

            CameraCaptureButton(
                enabled = imageCapture != null && !isCapturing,
                contentDescription = "Foto aufnehmen",
                color = Color.White,
                onClick = cameraCapture@{
                    val capture = imageCapture ?: return@cameraCapture
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
                                    FeedbackNoticeKind.ERROR,
                                    "Das Foto konnte nicht gespeichert werden.",
                                )
                            }
                        },
                    )
                },
            )
        } else {
            var bitmap by remember(photo) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(photo) {
                bitmap = withContext(Dispatchers.IO) { decodePreviewBitmap(photo) }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val renderedBitmap = bitmap
                    if (renderedBitmap != null) {
                        val photoAspectRatio =
                            renderedBitmap.width.toFloat() /
                                renderedBitmap.height.coerceAtLeast(1)
                        val mediaModifier = if (maxWidth / maxHeight > photoAspectRatio) {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(photoAspectRatio)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(photoAspectRatio)
                        }
                        Image(
                            bitmap = renderedBitmap.asImageBitmap(),
                            contentDescription = "Aufgenommenes Foto",
                            modifier = mediaModifier,
                            alignment = Alignment.TopCenter,
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                AnimatedMediaConfirmationPanel(
                    onDiscard = {
                        photo.delete()
                        capturedPhoto = null
                    },
                    onAccept = { onPhotoAccepted(photo) },
                )
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
