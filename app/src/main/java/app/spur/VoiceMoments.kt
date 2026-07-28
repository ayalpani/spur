package app.spur

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun VoiceRecorderBottomSheet(
    startRecordingRequest: Long,
    hasRecordPermission: Boolean,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    onRequestPermission: () -> Unit,
    onRecordingAccepted: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var accepted by remember { mutableStateOf(false) }

    fun stopRecording(keep: Boolean) {
        val activeRecorder = recorder
        recorder = null
        isRecording = false
        val stopped = runCatching { activeRecorder?.stop() }.isSuccess
        runCatching { activeRecorder?.release() }
        if (!keep || !stopped) {
            recording?.delete()
            recording = null
        }
    }

    fun finishRecording() {
        stopRecording(keep = true)
        val completed = recording
        if (completed == null) {
            onDismiss()
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Die Sprachaufnahme konnte nicht gespeichert werden.",
            )
            return
        }
        accepted = true
        onRecordingAccepted(completed)
    }

    @Suppress("DEPRECATION")
    fun startRecording() {
        if (isRecording) return
        recording?.delete()
        val output = context.createMomentFile(MomentType.VOICE)
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        val started = runCatching {
            nextRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            nextRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            nextRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            nextRecorder.setAudioEncodingBitRate(128_000)
            nextRecorder.setAudioSamplingRate(44_100)
            nextRecorder.setOutputFile(output.absolutePath)
            nextRecorder.prepare()
            nextRecorder.start()
        }.isSuccess
        if (started) {
            recorder = nextRecorder
            recording = output
            elapsedSeconds = 0L
            isRecording = true
        } else {
            runCatching { nextRecorder.release() }
            output.delete()
            onDismiss()
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Die Sprachaufnahme konnte nicht gestartet werden.",
            )
        }
    }

    LaunchedEffect(startRecordingRequest) {
        if (startRecordingRequest > 0L && hasRecordPermission) startRecording()
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1_000)
            if (isRecording) elapsedSeconds++
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecording(keep = false)
            if (!accepted) recording?.delete()
        }
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BottomSheetHeader(title = "Sprache aufnehmen")
        Text(
            text = when {
                isRecording -> formatPlayerDuration(elapsedSeconds * 1_000)
                else -> "Spur benötigt das Mikrofon nur während dieser Aufnahme."
            },
            color = Ink,
            style = if (isRecording) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            textAlign = TextAlign.Center,
        )
        if (isRecording) {
            Button(
                onClick = ::finishRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StopRed,
                    contentColor = Color.White,
                ),
                shape = CircleShape,
            ) {
                LucideStopIcon(
                    color = Color.White,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aufnahme abschließen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            SpurPrimaryButton(
                label = "Aufnahme starten",
                onClick = {
                    if (hasRecordPermission) startRecording() else onRequestPermission()
                },
                leadingIcon = { MicrophoneIcon() },
            )
        }
        SpurSecondaryButton(
            label = "Abbrechen",
            onClick = {
                stopRecording(keep = false)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun AudioPlaybackControl(
    file: File,
    foreground: Color = Ink,
    buttonBackground: Color = Ink,
    buttonForeground: Color = Color.White,
) {
    val context = LocalContext.current
    val player = remember(file) {
        runCatching { MediaPlayer.create(context, Uri.fromFile(file)) }.getOrNull()
    }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    DisposableEffect(player) {
        val current = player
        current?.setOnCompletionListener {
            isPlaying = false
            position = 0f
        }
        onDispose {
            runCatching { current?.release() }
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = runCatching { player?.currentPosition?.toFloat() ?: 0f }
                .getOrDefault(0f)
            delay(100)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = {
                val current = player ?: return@IconButton
                if (current.isPlaying) {
                    current.pause()
                    isPlaying = false
                } else {
                    current.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = buttonBackground,
                contentColor = buttonForeground,
            ),
        ) {
            if (isPlaying) PauseIcon() else PlayIcon()
        }
        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = {
                position = it
                runCatching { player?.seekTo(it.roundToInt()) }
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = foreground,
                activeTrackColor = foreground,
                inactiveTrackColor = foreground.copy(alpha = 0.24f),
            ),
        )
        Text(
            text = formatDuration(duration.toLong()),
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
