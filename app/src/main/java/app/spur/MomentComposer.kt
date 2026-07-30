package app.spur

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MomentComposer(
    target: MomentPlacementTarget?,
    showFeedbackNotice: ShowFeedbackNotice,
    onDismiss: () -> Unit,
    onMomentAccepted: (MomentPlacementTarget, PendingMapMoment) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember(target) { mutableStateOf(target != null) }
    var showEmojiPicker by remember(target) { mutableStateOf(false) }
    var showCamera by remember(target) { mutableStateOf(false) }
    var showVideoCamera by remember(target) { mutableStateOf(false) }
    var showVoiceRecorder by remember(target) { mutableStateOf(false) }
    var audioPermissionGranted by remember(target) {
        mutableStateOf(context.hasAudioRecordingPermission())
    }
    var voiceRecordingStartRequest by remember(target) { mutableLongStateOf(0L) }
    val momentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            showVideoCamera = true
        } else {
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
                onSelect = { type ->
                    when (type) {
                        MomentType.PHOTO -> {
                            showPicker = false
                            if (context.hasCameraPermission()) {
                                showCamera = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        MomentType.VIDEO -> {
                            showPicker = false
                            if (
                                context.hasCameraPermission() &&
                                context.hasAudioRecordingPermission()
                            ) {
                                showVideoCamera = true
                            } else {
                                videoPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO,
                                    ),
                                )
                            }
                        }
                        MomentType.VOICE -> {
                            showPicker = false
                            voiceRecordingStartRequest = 0L
                            showVoiceRecorder = true
                        }
                        MomentType.EMOJI -> {
                            scope.launch {
                                momentSheetState.hide()
                                showPicker = false
                                showEmojiPicker = true
                            }
                        }
                    }
                },
            )
        }
    }

    if (showEmojiPicker) {
        val closeEmojiPicker: () -> Unit = {
            showEmojiPicker = false
            showPicker = true
        }
        EmojiPickerBottomSheet(
            onDismiss = onDismiss,
            onBack = closeEmojiPicker,
            onEmojiPicked = { emoji ->
                showEmojiPicker = false
                accept(PendingMapMoment.emoji(emoji))
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

    if (showVideoCamera) {
        VideoCameraScreen(
            showFeedbackNotice = showFeedbackNotice,
            onClose = onDismiss,
            onVideoAccepted = { video ->
                showVideoCamera = false
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
    onSelect: (MomentType) -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MomentSheetGridGap),
        ) {
            MomentOption(
                label = "Foto",
                accentColor = momentMarkerColor(MomentType.PHOTO),
                icon = { MomentPhotoIcon() },
                modifier = Modifier.weight(1f),
                onClick = { onSelect(MomentType.PHOTO) },
            )
            MomentOption(
                label = "Video",
                accentColor = momentMarkerColor(MomentType.VIDEO),
                icon = { MomentVideoIcon() },
                modifier = Modifier.weight(1f),
                onClick = { onSelect(MomentType.VIDEO) },
            )
        }
        Spacer(modifier = Modifier.height(MomentSheetGridGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MomentSheetGridGap),
        ) {
            MomentOption(
                label = "Sprache",
                accentColor = momentMarkerColor(MomentType.VOICE),
                icon = { MomentVoiceIcon() },
                modifier = Modifier.weight(1f),
                onClick = { onSelect(MomentType.VOICE) },
            )
            MomentOption(
                label = "Emoji",
                accentColor = momentMarkerColor(MomentType.EMOJI),
                icon = { Text(text = "🙂", fontSize = 30.sp) },
                modifier = Modifier.weight(1f),
                onClick = { onSelect(MomentType.EMOJI) },
            )
        }
    }
}
