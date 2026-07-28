package app.spur

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun MediaMomentDetailPage(
    moment: MapMoment,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DarkMediaSystemBars()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink),
        ) {
            when (moment.type) {
                MomentType.VIDEO -> {
                    val context = LocalContext.current
                    var videoView by remember { mutableStateOf<VideoView?>(null) }
                    AndroidView(
                        factory = {
                            VideoView(context).apply {
                                val controls = MediaController(context)
                                controls.setAnchorView(this)
                                setMediaController(controls)
                                setVideoPath(moment.payload)
                                setOnPreparedListener { player ->
                                    player.isLooping = false
                                    start()
                                }
                                videoView = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Video" },
                    )
                    DisposableEffect(Unit) {
                        onDispose {
                            runCatching { videoView?.stopPlayback() }
                            videoView = null
                        }
                    }
                }
                MomentType.VOICE -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        MicrophoneIcon(
                            modifier = Modifier.size(72.dp),
                            color = Color.White,
                        )
                        AudioPlaybackControl(
                            file = File(moment.payload),
                            foreground = Color.White,
                            buttonBackground = Color.White,
                            buttonForeground = Ink,
                        )
                    }
                }
                else -> Unit
            }
            MapIconButton(
                contentDescription = "Detail schließen",
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.dp),
            ) {
                PhotoCloseIcon()
            }
        }
    }
}
