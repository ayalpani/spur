package app.spur

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun VideoConfirmationSurface(
    video: File,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
) {
    val context = LocalContext.current
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }

    DisposableEffect(textureView, video) {
        val view = textureView
        var currentPlayer: MediaPlayer? = null
        var currentSurface: Surface? = null

        fun releasePlayback() {
            runCatching { currentPlayer?.release() }
            currentPlayer = null
            player = null
            currentSurface?.release()
            currentSurface = null
        }

        fun preparePlayback(surfaceTexture: SurfaceTexture) {
            releasePlayback()
            currentSurface = Surface(surfaceTexture)
            currentPlayer = MediaPlayer().apply {
                setDataSource(video.absolutePath)
                setSurface(currentSurface)
                setOnPreparedListener { prepared ->
                    duration = prepared.duration.coerceAtLeast(1).toFloat()
                    if (prepared.videoWidth > 0 && prepared.videoHeight > 0) {
                        videoAspectRatio =
                            prepared.videoWidth.toFloat() / prepared.videoHeight
                    }
                    prepared.seekTo(1)
                    player = prepared
                }
                setOnCompletionListener { completed ->
                    isPlaying = false
                    position = 0f
                    completed.seekTo(1)
                }
                prepareAsync()
            }
        }

        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = preparePlayback(surface)

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releasePlayback()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        view?.surfaceTextureListener = listener
        if (view?.isAvailable == true) {
            view.surfaceTexture?.let(::preparePlayback)
        }

        onDispose {
            view?.surfaceTextureListener = null
            releasePlayback()
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = runCatching {
                player?.currentPosition?.toFloat() ?: 0f
            }.getOrDefault(0f)
            delay(100)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = {
                TextureView(context).also { textureView = it }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(videoAspectRatio)
                .semantics { contentDescription = "Aufgenommenes Video" },
        )
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
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .semantics {
                    contentDescription = if (isPlaying) {
                        "Videowiedergabe pausieren"
                    } else {
                        "Video abspielen"
                    }
                },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = SheetBackground.copy(alpha = 0.88f),
                contentColor = Ink,
            ),
        ) {
            if (isPlaying) PauseIcon() else PlayIcon(modifier = Modifier.size(30.dp))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(SheetBackground)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Slider(
                    value = position.coerceIn(0f, duration),
                    onValueChange = {
                        position = it
                        player?.seekTo(it.roundToInt())
                    },
                    valueRange = 0f..duration,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Ink,
                        activeTrackColor = Ink,
                        inactiveTrackColor = Ink.copy(alpha = 0.24f),
                    ),
                )
                Text(
                    text = formatDuration(duration.toLong()),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SpurSecondaryButton(
                label = "Verwerfen",
                onClick = onDiscard,
            )
            SpurPrimaryButton(
                label = "Bestätigen",
                onClick = onAccept,
            )
        }
    }
}
