package app.spur

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun VideoCameraScreen(
    showFeedbackNotice: ShowFeedbackNotice,
    onClose: () -> Unit,
    onVideoAccepted: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val landscape = CameraOrientation()
    val targetRotation = rememberCameraTargetRotation()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val discardRequested = remember { AtomicBoolean(false) }
    val closeAfterDiscard = remember { AtomicBoolean(false) }
    val accepted = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var cameraPreview by remember { mutableStateOf<Preview?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    var capturedVideo by remember { mutableStateOf<File?>(null) }
    var recordedDurationMillis by remember { mutableLongStateOf(0L) }
    var isFinalizing by remember { mutableStateOf(false) }
    val currentRecording by rememberUpdatedState(recording)
    val currentPendingVideo by rememberUpdatedState(pendingVideo)
    val currentCapturedVideo by rememberUpdatedState(capturedVideo)

    ActivityNavigationBar(
        backgroundColor = Color.Black,
        priority = ActivityNavigationBarOverlayPriority,
    )

    fun discardAndClose() {
        discardRequested.set(true)
        closeAfterDiscard.set(true)
        capturedVideo?.let {
            it.delete()
            onClose()
            return
        }
        val activeRecording = recording
        if (activeRecording != null) {
            isFinalizing = true
            activeRecording.stop()
        } else {
            pendingVideo?.delete()
            onClose()
        }
    }

    BackHandler(onBack = ::discardAndClose)

    DisposableEffect(lifecycleOwner, lensFacing, previewView, landscape, capturedVideo) {
        if (capturedVideo != null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var bindingDisposed = false

            providerFuture.addListener(
                {
                    if (bindingDisposed) return@addListener
                    previewView.doOnLayout {
                        if (bindingDisposed) return@doOnLayout
                        runCatching {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder()
                                .setTargetRotation(targetRotation)
                                .build()
                                .also { it.surfaceProvider = previewView.surfaceProvider }
                            val capture = VideoCapture.withOutput(Recorder.Builder().build()).also {
                                it.targetRotation = targetRotation
                            }
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
                            cameraPreview = preview
                            videoCapture = capture
                        }.onFailure {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "Die Videokamera konnte nicht geöffnet werden.",
                            )
                        }
                    }
                },
                mainExecutor,
            )

            onDispose {
                bindingDisposed = true
                cameraPreview = null
                videoCapture = null
                if (providerFuture.isDone) {
                    runCatching { providerFuture.get().unbindAll() }
                }
            }
        }
    }

    LaunchedEffect(targetRotation, cameraPreview, videoCapture, recording) {
        cameraPreview?.targetRotation = targetRotation
        if (recording == null) videoCapture?.targetRotation = targetRotation
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            discardRequested.set(true)
            currentRecording?.close()
            if (currentRecording == null) currentPendingVideo?.delete()
            if (!accepted.get()) currentCapturedVideo?.delete()
        }
    }

    fun startRecording() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Für Videos braucht Spur Zugriff auf Kamera und Mikrofon.",
            )
            return
        }
        val capture = videoCapture ?: return
        cameraPreview?.targetRotation = targetRotation
        capture.targetRotation = targetRotation
        val video = context.createMomentFile(MomentType.VIDEO)
        val output = FileOutputOptions.Builder(video).build()
        discardRequested.set(false)
        closeAfterDiscard.set(false)
        pendingVideo = video
        recordedDurationMillis = 0L
        runCatching {
            recording = capture.output
                .prepareRecording(context, output)
                .withAudioEnabled()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Status -> {
                            recordedDurationMillis =
                                event.recordingStats.recordedDurationNanos / 1_000_000L
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            pendingVideo = null
                            isFinalizing = false
                            val successful = !event.hasError() &&
                                video.isFile &&
                                video.length() > 0L
                            if (discardRequested.get() || !successful) {
                                video.delete()
                            } else {
                                capturedVideo = video
                            }
                            if (closeAfterDiscard.get() && !disposed.get()) {
                                onClose()
                            } else if (!successful && !disposed.get()) {
                                showFeedbackNotice(
                                    FeedbackNoticeKind.ERROR,
                                    "Das Video konnte nicht gespeichert werden.",
                                )
                                onClose()
                            }
                        }
                    }
                }
        }.onFailure {
            pendingVideo = null
            video.delete()
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Die Videoaufnahme konnte nicht gestartet werden.",
            )
        }
    }

    val video = capturedVideo
    if (video == null) {
        VideoRecordingSurface(
            previewView = previewView,
            isRecording = recording != null,
            isFinalizing = isFinalizing,
            recordedDurationMillis = recordedDurationMillis,
            canRecord = videoCapture != null,
            selfie = isSelfieLens(lensFacing),
            landscape = landscape,
            onClose = ::discardAndClose,
            onSwitchCamera = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            onRecord = {
                if (recording == null) {
                    startRecording()
                } else {
                    isFinalizing = true
                    recording?.stop()
                }
            },
        )
    } else {
        VideoConfirmationSurface(
            video = video,
            selfie = isSelfieLens(lensFacing),
            landscape = landscape,
            onDiscard = {
                video.delete()
                capturedVideo = null
            },
            onAccept = {
                accepted.set(true)
                onVideoAccepted(video)
            },
        )
    }
}

@Composable
private fun VideoRecordingSurface(
    previewView: PreviewView,
    isRecording: Boolean,
    isFinalizing: Boolean,
    recordedDurationMillis: Long,
    canRecord: Boolean,
    selfie: Boolean,
    landscape: Boolean,
    onClose: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRecord: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = if (selfie) {
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (selfie) Modifier.padding(5.dp).clip(CircleShape) else Modifier)
                    .semantics {
                        contentDescription = if (selfie) {
                            "Runde Selfie-Videovorschau"
                        } else {
                            "Videokameravorschau"
                        }
                    },
            )
        }
        CameraCloseButton(
            contentDescription = "Videokamera schließen",
            onClick = onClose,
        )
        if (isRecording || isFinalizing) {
            Text(
                text = if (isFinalizing) {
                    "Wird gespeichert …"
                } else {
                    formatPlayerDuration(recordedDurationMillis)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 28.dp)
                    .background(CameraChrome, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!isRecording && !isFinalizing) {
            CameraSwitchButton(
                contentDescription = "Videokamera wechseln",
                landscape = landscape,
                onClick = onSwitchCamera,
            )
        }
        CameraCaptureButton(
            enabled = canRecord && !isFinalizing,
            contentDescription = when {
                isFinalizing -> "Video wird gespeichert"
                isRecording -> "Videoaufnahme beenden"
                else -> "Videoaufnahme starten"
            },
            color = if (isFinalizing) CameraChrome else StopRed,
            landscape = landscape,
            shape = if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
            innerSize = if (isRecording) 34.dp else 64.dp,
            onClick = onRecord,
        )
    }
}
