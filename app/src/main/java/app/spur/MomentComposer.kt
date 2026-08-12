package app.spur

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class MomentPickerAction {
    PHOTO,
    VIDEO,
    ROUND_SELFIE_VIDEO,
    VOICE,
    EMOJI,
    LANDMARK,
}

private enum class VideoCaptureMode { STANDARD, ROUND_SELFIE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MomentComposer(
    target: MomentPlacementTarget?,
    showFeedbackNotice: ShowFeedbackNotice,
    onDismiss: () -> Unit,
    onMomentAccepted: (MomentPlacementTarget, PendingMapMoment) -> Unit,
    onLandmarkAccepted: ((MomentPlacementTarget, String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember(target) { mutableStateOf(target != null) }
    var showEmojiPicker by remember(target) { mutableStateOf(false) }
    var showCamera by remember(target) { mutableStateOf(false) }
    var videoCameraMode by remember(target) { mutableStateOf<VideoCaptureMode?>(null) }
    var requestedVideoCameraMode by remember(target) {
        mutableStateOf<VideoCaptureMode?>(null)
    }
    var showVoiceRecorder by remember(target) { mutableStateOf(false) }
    var showLandmarkCreator by remember(target) { mutableStateOf(false) }
    var audioPermissionGranted by remember(target) {
        mutableStateOf(context.hasAudioRecordingPermission())
    }
    var voiceRecordingStartRequest by remember(target) { mutableLongStateOf(0L) }
    val momentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val emojiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val voiceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val landmarkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placementTarget = target

    val accept: (PendingMapMoment) -> Unit = { pending ->
        placementTarget?.let { onMomentAccepted(it, pending) } ?: pending.deletePayload()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Für Fotos braucht Spur Zugriff auf die Kamera.",
            )
            onDismiss()
        }
    }
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (context.hasCameraPermission() && context.hasAudioRecordingPermission()) {
            videoCameraMode = requestedVideoCameraMode ?: VideoCaptureMode.STANDARD
            requestedVideoCameraMode = null
        } else {
            requestedVideoCameraMode = null
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Für Videos braucht Spur Zugriff auf Kamera und Mikrofon.",
            )
            onDismiss()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        audioPermissionGranted = granted
        if (granted) {
            voiceRecordingStartRequest++
        } else {
            showVoiceRecorder = false
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Für Sprache braucht Spur Zugriff auf das Mikrofon.",
            )
            onDismiss()
        }
    }

    if (placementTarget != null && showPicker) {
        SpurModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = momentSheetState,
        ) {
            MomentPickerSheetContent(
                onDismiss = onDismiss,
                showLandmark = onLandmarkAccepted != null,
                onSelect = { action ->
                    when (action) {
                        MomentPickerAction.PHOTO -> {
                            showPicker = false
                            if (context.hasCameraPermission()) {
                                showCamera = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        MomentPickerAction.VIDEO,
                        MomentPickerAction.ROUND_SELFIE_VIDEO,
                        -> {
                            showPicker = false
                            val mode = if (action == MomentPickerAction.ROUND_SELFIE_VIDEO) {
                                VideoCaptureMode.ROUND_SELFIE
                            } else {
                                VideoCaptureMode.STANDARD
                            }
                            if (
                                context.hasCameraPermission() &&
                                context.hasAudioRecordingPermission()
                            ) {
                                videoCameraMode = mode
                            } else {
                                requestedVideoCameraMode = mode
                                videoPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO,
                                    ),
                                )
                            }
                        }
                        MomentPickerAction.VOICE -> {
                            voiceRecordingStartRequest = 0L
                            scope.swapBottomSheets(
                                currentState = momentSheetState,
                                nextState = voiceSheetState,
                                showNext = { showVoiceRecorder = true },
                                hideCurrent = { showPicker = false },
                            )
                        }
                        MomentPickerAction.EMOJI -> {
                            scope.swapBottomSheets(
                                currentState = momentSheetState,
                                nextState = emojiSheetState,
                                showNext = { showEmojiPicker = true },
                                hideCurrent = { showPicker = false },
                            )
                        }
                        MomentPickerAction.LANDMARK -> {
                            scope.swapBottomSheets(
                                currentState = momentSheetState,
                                nextState = landmarkSheetState,
                                showNext = { showLandmarkCreator = true },
                                hideCurrent = { showPicker = false },
                            )
                        }
                    }
                },
            )
        }
    }

    if (showLandmarkCreator) {
        val closeLandmarkCreator: () -> Unit = {
            scope.swapBottomSheets(
                currentState = landmarkSheetState,
                nextState = momentSheetState,
                showNext = { showPicker = true },
                hideCurrent = { showLandmarkCreator = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = landmarkSheetState,
        ) {
            LandmarkCreateBottomSheet(
                onSave = { title ->
                    showLandmarkCreator = false
                    placementTarget?.let { onLandmarkAccepted?.invoke(it, title) }
                    onDismiss()
                },
                onBack = closeLandmarkCreator,
            )
        }
    }

    if (showEmojiPicker) {
        val closeEmojiPicker: () -> Unit = {
            scope.swapBottomSheets(
                currentState = emojiSheetState,
                nextState = momentSheetState,
                showNext = { showPicker = true },
                hideCurrent = { showEmojiPicker = false },
            )
        }
        EmojiPickerBottomSheet(
            onDismiss = onDismiss,
            onBack = closeEmojiPicker,
            sheetState = emojiSheetState,
            onEmojiPicked = { emoji ->
                scope.launch {
                    emojiSheetState.hide()
                    showEmojiPicker = false
                    accept(PendingMapMoment.emoji(emoji))
                }
            },
        )
    }

    if (showCamera) {
        CameraScreen(
            showFeedbackNotice = showFeedbackNotice,
            onClose = onDismiss,
            onPhotoAccepted = { photo ->
                showCamera = false
                accept(PendingMapMoment(MomentType.PHOTO, photo))
            },
        )
    }

    videoCameraMode?.let { mode ->
        VideoCameraScreen(
            roundSelfie = mode == VideoCaptureMode.ROUND_SELFIE,
            showFeedbackNotice = showFeedbackNotice,
            onClose = onDismiss,
            onVideoAccepted = { video ->
                videoCameraMode = null
                scope.launch {
                    withContext(Dispatchers.IO) { ensureVideoThumbnail(video) }
                    accept(PendingMapMoment(MomentType.VIDEO, video))
                }
            },
        )
    }

    if (showVoiceRecorder) {
        SpurModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = voiceSheetState,
        ) {
            VoiceRecorderBottomSheet(
                startRecordingRequest = voiceRecordingStartRequest,
                hasRecordPermission = audioPermissionGranted,
                showFeedbackNotice = showFeedbackNotice,
                onRequestPermission = {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRecordingAccepted = { recording ->
                    showVoiceRecorder = false
                    accept(PendingMapMoment(MomentType.VOICE, recording))
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun MomentPickerSheetContent(
    onSelect: (MomentPickerAction) -> Unit,
    onDismiss: () -> Unit,
    showLandmark: Boolean,
) {
    CompositionLocalProvider(
        LocalMapControlColors provides MapControlColors(
            background = MapControlColor.BLACK.color,
            foreground = MapControlColor.WHITE.color,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(MomentSheetGridGap),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MomentPickerGridHeight),
                horizontalArrangement = Arrangement.spacedBy(MomentSheetGridGap),
                verticalArrangement = Arrangement.spacedBy(MomentSheetGridGap),
            ) {
                item {
                    SpurSecondaryButton(
                        label = "Selfie",
                        leadingIcon = { SelfieButtonPreview() },
                        compactContent = true,
                        onClick = { onSelect(MomentPickerAction.ROUND_SELFIE_VIDEO) },
                    )
                }
                item {
                    SpurSecondaryButton(
                        label = "Foto",
                        leadingIcon = { MomentPhotoIcon() },
                        compactContent = true,
                        onClick = { onSelect(MomentPickerAction.PHOTO) },
                    )
                }
                item {
                    SpurSecondaryButton(
                        label = "Video",
                        leadingIcon = { MomentVideoIcon() },
                        compactContent = true,
                        onClick = { onSelect(MomentPickerAction.VIDEO) },
                    )
                }
                item {
                    SpurSecondaryButton(
                        label = "Sprache",
                        leadingIcon = { MomentVoiceIcon() },
                        compactContent = true,
                        onClick = { onSelect(MomentPickerAction.VOICE) },
                    )
                }
                item {
                    SpurSecondaryButton(
                        label = "Emoji",
                        leadingIcon = { MomentEmojiIcon() },
                        compactContent = true,
                        onClick = { onSelect(MomentPickerAction.EMOJI) },
                    )
                }
                if (showLandmark) {
                    item {
                        SpurSecondaryButton(
                            label = "Landmark",
                            leadingIcon = { MapPinIcon(modifier = Modifier.size(24.dp)) },
                            compactContent = true,
                            onClick = { onSelect(MomentPickerAction.LANDMARK) },
                        )
                    }
                }
            }
            SpurPrimaryButton(
                label = "Abbrechen",
                onClick = onDismiss,
            )
        }
    }
}

private val MomentPickerGridHeight = 164.dp
