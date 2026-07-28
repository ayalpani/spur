package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ContextThemeWrapper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser as ComposePathParser
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleTranslate
import org.maplibre.android.style.layers.PropertyFactory.circleTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconTranslate
import org.maplibre.android.style.layers.PropertyFactory.iconTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolZOrder
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textTranslate
import org.maplibre.android.style.layers.PropertyFactory.textTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

private val Sand = Color(0xFFF7F5F0)
private val Ink = Color(0xFF18201C)
private val Moss = Color(0xFF23614A)
private val FollowGreen = Color(0xFF43A873)
private val StopRed = Color(0xFFE53935)
private val MapPinRed = Color(0xFFEA4335)
private val MomentMarkerGreen = Color(0xFF43A047)
private val TourMomentSelectionYellow = Color(0xFFCCCC00)
private val Mist = Color(0xFFE8EEE9)
private val SheetBackground = Color.White
private val ImageDetailControlBackground = Color.White.copy(alpha = 0.1f)
private val ImageDetailControlForeground = Color.White
private const val DefaultMapZoom = 17.5
private const val MapControlGapDp = 10
private const val MapControlSizeDp = 60
private const val MapControlHorizontalPaddingDp = 18
private const val MapControlVerticalPaddingDp = 16
private const val MapPlayerMinimumWidthDp = 180
private val MapControlGap = MapControlGapDp.dp
private val MapControlSize = MapControlSizeDp.dp
private val MapControlHorizontalPadding = MapControlHorizontalPaddingDp.dp
private val MapControlVerticalPadding = MapControlVerticalPaddingDp.dp
private val MomentSheetHeaderGap = 24.dp
private val MomentSheetGridGap = 10.dp
private val SheetMenuTextSize = 24.sp
private val StopSwipeHandleSize = 52.dp
private val MapRotationOptionGap = 16.dp
private val FilterChipVisualInset = 8.dp
private const val MotionDurationDefaultMillis = 200
private const val EditorPointTransitionDurationMillis = 10
private const val FeedbackNoticeDurationMillis = 2_500L
private const val PendingPhotoRevealDelayMillis = 1_000L
private const val MinimumSystemSplashDurationMillis = 3_000L
private const val MinimumMapLoadingDurationMillis = 3_000L
private const val InitialManualLocationHoldDurationMillis = 5_000L
private const val ActiveManualLocationHoldDurationMillis = 1_000L
private const val PanelMotionDurationMillis = 300
private const val MapRotationAnimationMillis = 350L
private const val AsteriskRotationDurationMillis = 900
private const val LoaderAsteriskAccelerationDurationMillis = 1_000
private const val LoaderAsteriskAccelerationDegrees =
    180f * LoaderAsteriskAccelerationDurationMillis / AsteriskRotationDurationMillis
private val PhotoMapPreviewSize = 96.dp
private val LoaderAsteriskSize = 128.dp
private val LoaderTextGap = 20.dp
private val EditorLocationRailHeight = 96.dp
private val EditorMetricBarHeight = 68.dp
private const val PhotoMapPreviewZoom = 17.5
private const val TourRouteWidthPixels = 6f
private const val TourRouteBorderPerSidePixels = 4f
private const val TourRouteBorderWidthPixels =
    TourRouteWidthPixels + TourRouteBorderPerSidePixels * 2f
private const val TrailStrokeAlpha = 0.5f
private const val LocationPulseAlpha = 0.48f
private const val LocationPulseDurationMillis = 3_000
private const val LocationPulseMaxRadius = 35f
private const val LocationPulseScale = 1.15f
private const val MapPinTipY = 21.799f
private val MapPinIconPaths = listOf(
    "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
    "M15 10a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
)
private const val EmojiPickerColumns = 8
private const val MaxRecentEmojis = 18
private const val EmojiPreferences = "emoji-picker"
private const val RecentEmojiPreference = "recent-emojis"
private const val EmojiPreferenceSeparator = "\n"
private val DefaultProminentEmojis = listOf(
    "❤️",
    "😂",
    "🔥",
    "✨",
    "👍",
    "😍",
    "🎉",
    "😎",
    "🙏",
)
internal const val LucideBoldStrokeWidth = 3f
private const val LucideRegularStrokeWidth = 2f
private const val ArashLinkedInUrl = "https://www.linkedin.com/in/arash-yalpani-3367258"
private val MapControlElevation = 16.dp
private val MapControlShadowColor = Color.Black
private const val MapControlShadowLayers = 3

internal data class MapControlColors(
    val background: Color,
    val foreground: Color,
) {
    val inverted: MapControlColors
        get() = MapControlColors(
            background = foreground,
            foreground = background,
        )
}

internal enum class MapControlColor(
    val label: String,
    val color: Color,
) {
    RED("Rot", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFF97316)),
    AMBER("Bernstein", Color(0xFFF59E0B)),
    YELLOW("Gelb", Color(0xFFF4C430)),
    LIME("Limette", Color(0xFF84CC16)),
    GREEN("Grün", Color(0xFF23614A)),
    TEAL("Türkisgrün", Color(0xFF0D9488)),
    CYAN("Cyan", Color(0xFF06B6D4)),
    BLUE("Blau", Color(0xFF2563EB)),
    INDIGO("Indigo", Color(0xFF4F46E5)),
    VIOLET("Violett", Color(0xFF7C3AED)),
    PINK("Pink", Color(0xFFDB2777)),
    BROWN("Braun", Color(0xFF795548)),
    GRAY("Grau", Color(0xFF64748B)),
    BLACK("Schwarz", Color.Black),
    WHITE("Weiß", Color.White),
    ;

    val contrastColor: Color
        get() = if (color.luminance() > 0.3f) Ink else Color.White
}

private fun momentMarkerColor(type: MomentType): Color = when (type) {
    MomentType.PHOTO -> MomentMarkerGreen
    MomentType.VIDEO -> MapControlColor.BLUE.color
    MomentType.VOICE -> MapControlColor.ORANGE.color
    MomentType.EMOJI -> Ink
}

private fun momentMarkerContentColor(type: MomentType): Color =
    if (
        type == MomentType.VOICE ||
        momentMarkerColor(type).luminance() <= 0.3f
    ) {
        Color.White
    } else {
        Ink
    }

private val MomentPhotoIconPaths = listOf(
    "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
    "M15 13a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
)
private val MomentVideoIconPaths = listOf(
    "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
    "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2",
)
private val MomentVoicePlaybackIconPaths = listOf(
    "M11 5 6 9H2v6h4l5 4z",
    "M15.54 8.46a5 5 0 0 1 0 7.07",
    "M19.07 4.93a10 10 0 0 1 0 14.14",
)

internal enum class FeedbackNoticeKind(
    val background: Color,
    val foreground: Color,
) {
    PERMISSION(FollowGreen, Ink),
    ERROR(StopRed, Color.White),
    PLACEHOLDER(Mist, Ink),
}

internal typealias ShowFeedbackNotice = (FeedbackNoticeKind, String) -> Unit

internal data class TrailColors(
    val fill: Color,
    val stroke: Color,
)

private val LocalMapControlColors = staticCompositionLocalOf {
    MapControlColors(background = Color.White, foreground = Ink)
}
private val LocalTrailColors = staticCompositionLocalOf {
    TrailColors(
        fill = MapControlColor.YELLOW.color,
        stroke = MapControlColor.BLACK.color.copy(alpha = TrailStrokeAlpha),
    )
}
private val LocalLucideStrokeWidth = staticCompositionLocalOf { LucideRegularStrokeWidth }

private data class PendingMapMoment(
    val type: MomentType,
    val id: String,
    val payload: String,
) {
    constructor(type: MomentType, file: File) : this(
        type = type,
        id = file.nameWithoutExtension,
        payload = file.absolutePath,
    )

    fun deletePayload() {
        if (type != MomentType.EMOJI) {
            File(payload).delete()
        }
    }

    companion object {
        fun emoji(emoji: String) = PendingMapMoment(
            type = MomentType.EMOJI,
            id = "emoji-${System.currentTimeMillis()}",
            payload = emoji,
        )
    }
}

private sealed interface MomentPlacementTarget {
    data object CurrentLocation : MomentPlacementTarget

    data class RecordedLocation(
        val tourId: Long,
        val trackPointId: Long,
        val coordinate: SpurCoordinate,
    ) : MomentPlacementTarget
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MomentComposer(
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
    var showVoiceRecorder by remember(target) { mutableStateOf(false) }
    var pendingVideoCapturePath by rememberSaveable(target) { mutableStateOf<String?>(null) }
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
    val videoCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { saved ->
        val video = pendingVideoCapturePath?.let(::File)
        pendingVideoCapturePath = null
        if (saved && video?.isFile == true && video.length() > 0L) {
            scope.launch {
                withContext(Dispatchers.IO) { ensureVideoThumbnail(video) }
                accept(PendingMapMoment(MomentType.VIDEO, video))
            }
        } else {
            video?.delete()
            onDismiss()
        }
    }
    val startVideoCapture: () -> Unit = {
        val video = context.createMomentFile(MomentType.VIDEO)
        pendingVideoCapturePath = video.absolutePath
        runCatching {
            videoCaptureLauncher.launch(context.momentContentUri(video))
        }.onFailure {
            pendingVideoCapturePath = null
            video.delete()
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Auf diesem Gerät ist keine Videoaufnahme verfügbar.",
            )
            onDismiss()
        }
    }
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVideoCapture()
        } else {
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Für Videos braucht Spur Zugriff auf die Kamera.",
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
                            if (context.hasCameraPermission()) {
                                startVideoCapture()
                            } else {
                                videoPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MapPinIcon(
                color = MapPinRed,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Auf der Karte ablegen",
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(MomentSheetHeaderGap))
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

private class SpurRecentEmojiProvider(context: Context) : RecentEmojiProvider {
    private val preferences = context.getSharedPreferences(
        EmojiPreferences,
        Context.MODE_PRIVATE,
    )

    override suspend fun getRecentEmojiList(): List<String> =
        synchronized(preferences) {
            preferences.getString(RecentEmojiPreference, null)
                ?.split(EmojiPreferenceSeparator)
                ?.filter(String::isNotBlank)
                ?.take(MaxRecentEmojis)
                ?.ifEmpty { DefaultProminentEmojis }
                ?: DefaultProminentEmojis
        }

    override fun recordSelection(emoji: String) {
        synchronized(preferences) {
            val recent = preferences.getString(RecentEmojiPreference, null)
                ?.split(EmojiPreferenceSeparator)
                ?.filter(String::isNotBlank)
                .orEmpty()
            val updated = (listOf(emoji) + recent)
                .distinct()
                .take(MaxRecentEmojis)
            preferences.edit()
                .putString(
                    RecentEmojiPreference,
                    updated.joinToString(EmojiPreferenceSeparator),
                )
                .apply()
        }
    }
}

@Composable
private fun EmojiPickerBottomSheet(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onEmojiPicked: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LightSheetNavigationBar()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            val sheetHeight = constraints.maxHeight.toFloat()
            val partialOffset = sheetHeight / 2f
            var sheetOffset by remember(sheetHeight) {
                mutableFloatStateOf(partialOffset)
            }
            var animationJob by remember {
                mutableStateOf<kotlinx.coroutines.Job?>(null)
            }

            val animateTo: (Float, () -> Unit) -> Unit = { target, onFinished ->
                animationJob?.cancel()
                animationJob = scope.launch {
                    animate(
                        initialValue = sheetOffset,
                        targetValue = target,
                        animationSpec = tween(MotionDurationDefaultMillis),
                    ) { value, _ ->
                        sheetOffset = value
                    }
                    onFinished()
                }
            }
            val settleSheet: () -> Unit = {
                val target = listOf(0f, partialOffset, sheetHeight)
                    .minBy { abs(it - sheetOffset) }
                animateTo(target) {
                    if (target == sheetHeight) onDismiss()
                }
            }
            val hideTo: (() -> Unit) -> Unit = { onHidden ->
                animateTo(sheetHeight, onHidden)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { sheetOffset.toDp() })
                    .semantics {
                        contentDescription = "Emoji-Auswahl schließen"
                    }
                    .clickable { hideTo(onDismiss) },
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(
                        with(density) {
                            (sheetHeight - sheetOffset).coerceAtLeast(0f).toDp()
                        },
                    ),
                color = SheetBackground,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                EmojiPickerSheet(
                    headerModifier = Modifier.pointerInput(sheetHeight) {
                        detectVerticalDragGestures(
                            onDragStart = { animationJob?.cancel() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                sheetOffset =
                                    (sheetOffset + dragAmount).coerceIn(0f, sheetHeight)
                            },
                            onDragEnd = settleSheet,
                            onDragCancel = settleSheet,
                        )
                    },
                    onBack = { hideTo(onBack) },
                    onEmojiPicked = { emoji ->
                        hideTo { onEmojiPicked(emoji) }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SpurModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color = SheetBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
    ) {
        LightSheetNavigationBar(containerColor)
        content()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmojiPickerSheet(
    headerModifier: Modifier,
    onBack: () -> Unit,
    onEmojiPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    val recentEmojiProvider = remember(context) {
        SpurRecentEmojiProvider(context.applicationContext)
    }
    val currentOnEmojiPicked by rememberUpdatedState(onEmojiPicked)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = headerModifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
            BottomSheetHeader(
                title = "Emoji wählen",
                onBack = onBack,
            )
        }
        AndroidView(
            factory = { viewContext ->
                val configuration = Configuration(viewContext.resources.configuration)
                    .apply { setLocale(Locale.GERMAN) }
                val localizedContext = viewContext.createConfigurationContext(configuration)
                val pickerContext = ContextThemeWrapper(
                    localizedContext,
                    R.style.Theme_Spur,
                )
                EmojiPickerView(pickerContext).apply {
                    emojiGridColumns = EmojiPickerColumns
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setRecentEmojiProvider(recentEmojiProvider)
                    setOnEmojiPickedListener { item ->
                        currentOnEmojiPicked(item.emoji)
                    }
                }
            },
            update = { picker ->
                picker.setOnEmojiPickedListener { item ->
                    currentOnEmojiPicked(item.emoji)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

private data class FeedbackNotice(
    val id: Long,
    val kind: FeedbackNoticeKind,
    val message: String,
)

@Composable
private fun FeedbackNoticeHost(
    notice: FeedbackNotice?,
    modifier: Modifier = Modifier,
) {
    var displayedNotice by remember { mutableStateOf(notice) }
    LaunchedEffect(notice) {
        if (notice != null) displayedNotice = notice
    }

    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(MotionDurationDefaultMillis),
        ) + fadeIn(tween(MotionDurationDefaultMillis)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(MotionDurationDefaultMillis),
        ) + fadeOut(tween(MotionDurationDefaultMillis)),
    ) {
        displayedNotice?.let { current ->
            Surface(
                color = current.kind.background,
                contentColor = current.kind.foreground,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = MapControlElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = current.message,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Modifier.mapControlShadow(shape: Shape): Modifier {
    var result = this
    repeat(MapControlShadowLayers) {
        result = result.shadow(
            elevation = MapControlElevation,
            shape = shape,
            ambientColor = MapControlShadowColor,
            spotColor = Color.Transparent,
        )
    }
    return result
}

private object SpurRoute {
    const val MAP = "map"
}
private const val StreetMapStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val SatelliteMapStyleJson =
    """{"version":8,"glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf","sources":{"satellite-source":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Esri, Maxar, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-layer","type":"raster","source":"satellite-source"}]}"""
internal const val MomentMarkerWidth = 62
private const val MomentMarkerHeight = 58
private const val MomentMarkerStroke = 3f
private const val MomentMarkerEdgeWidth = 1f
private const val MapPreviewPixels = 180
private const val LocationPulseWatchdogMillis = LocationPulseDurationMillis * 10L
private const val CurrentLocationFootprintsImage = "current-location-footprints-image"
private const val CurrentLocationFootprintsLayer = "current-location-footprints-layer"
private const val CurrentLocationFootprintsLiftPixels = 16f
private val LocationPulseEasing = Easing { fraction ->
    (cos((fraction + 1f) * PI) / 2f + 0.5f).toFloat()
}
private const val TourRouteSource = "tour-route-source"
private const val TourRouteBorderLayer = "tour-route-border-layer"
private const val TourRouteLayer = "tour-route-layer"
private const val TourEndpointSource = "tour-endpoint-source"
private const val TourEndpointRingLayer = "tour-endpoint-ring-layer"
private const val TourEndpointEndLayer = "tour-endpoint-end-layer"
private const val TourEndpointTypeProperty = "endpoint-type"
private const val TourEndpointEnd = "end"
private const val TourEndpointScale = 1.25f * 1.5f
private const val TourEndpointRadius = 7f * TourEndpointScale
private const val TourEndpointStrokeWidth = TourRouteBorderPerSidePixels
private const val TourEndpointEndRadius = 3f * TourEndpointScale
private const val SelectedTrackPointSource = "selected-track-point-source"
private const val SelectedTrackPointLayer = "selected-track-point-layer"
private const val MapMomentSource = "map-moment-source"
private const val MapMomentLayer = "map-moment-layer"
private const val MapMomentClusterLayer = "map-moment-cluster-layer"
private const val MapMomentClusterCountBadgeLayer = "map-moment-cluster-count-badge-layer"
private const val MapMomentClusterCountLayer = "map-moment-cluster-count-layer"
private const val MapMomentClusterCountBadgeRadius = 9f
private const val MapMomentClusterCountPositionX = 15f
private const val MapMomentClusterCountPositionY = -42f
private const val MapPoiSourceLayer = "poi"
private const val MapBuildingLayer = "building"
private const val MapBuilding3dLayer = "building-3d"
private const val MapBuildingMaxZoom = 24f
private const val HomeBuildingSource = "home-building-source"
private const val HomeBuildingFillLayer = "home-building-fill-layer"
private const val HomeBuildingOutlineLayer = "home-building-outline-layer"
private const val SelectableHomeBuildingsLayer = "selectable-home-buildings-layer"
private const val HomeBuildingSelectionZoom = 18.5
private const val HomeBuildingSelectionSearchRadiusDp = 64
private const val MapMomentIdProperty = "moment-id"
private const val MapMomentImageProperty = "moment-image"
private const val MapMomentRepresentativeProperty = "moment-representative"
private const val MapMomentImagePrefix = "map-moment-"
private const val MapMomentClusterImagePrefix = "map-moment-cluster-"
private const val MapMomentClusterMaxZoom = 18
private const val MapMomentClusterRadius = MomentMarkerHeight / 2 - 1

internal enum class MapRotation(val label: String, val bearing: Double) {
    NORTH("Norden", 0.0),
    EAST("Osten", 90.0),
    SOUTH("Süden", 180.0),
    WEST("Westen", 270.0),
}

private data class SelectedHomeBuilding(
    val coordinate: SpurCoordinate,
    val feature: Feature,
)

internal fun mapRotationFromStored(value: String?): MapRotation =
    MapRotation.entries.firstOrNull { it.name == value } ?: MapRotation.NORTH

internal fun mapControlColorFromStored(
    value: String?,
    fallback: MapControlColor = MapControlColor.BLACK,
): MapControlColor =
    MapControlColor.entries.firstOrNull { it.name == value } ?: fallback

internal fun defaultMapControlForeground(background: MapControlColor): MapControlColor =
    when {
        background == MapControlColor.BLUE -> MapControlColor.YELLOW
        background.color.luminance() > 0.3f -> MapControlColor.BLACK
        else -> MapControlColor.WHITE
    }

internal fun nearestCompassRotation(current: Float, target: Float): Float {
    val delta = (target - current) % 360f
    return current + when {
        delta > 180f -> delta - 360f
        delta <= -180f -> delta + 360f
        else -> delta
    }
}

internal fun mapRotationOptionCenterDistance(
    circleRadius: Float,
    gap: Float,
    halfWidth: Float,
    halfHeight: Float,
    angleRadians: Double,
): Float = circleRadius +
    gap +
    abs(cos(angleRadians)).toFloat() * halfWidth +
    abs(sin(angleRadians)).toFloat() * halfHeight

internal fun shouldFitTourRoute(
    tourId: Long?,
    fittedTourId: Long?,
    displayRequest: Long,
    fittedDisplayRequest: Long,
    pointCount: Int,
): Boolean = tourId != null &&
    pointCount > 0 &&
    (tourId != fittedTourId || displayRequest != fittedDisplayRequest)

internal fun shouldShowTourOverview(
    isFollowingLocation: Boolean,
    isTourActive: Boolean,
    routePointCount: Int,
): Boolean = isFollowingLocation && isTourActive && routePointCount > 0

internal fun shouldStackMapPlayer(screenWidthDp: Int): Boolean =
    screenWidthDp <
        MapControlHorizontalPaddingDp * 2 +
        MapControlSizeDp * 2 +
        MapControlGapDp * 2 +
        MapPlayerMinimumWidthDp

internal fun shouldStopFollowing(cameraMoveReason: Int): Boolean =
    cameraMoveReason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldShowMapPreviewLoading(
    isFollowingLocation: Boolean,
    cameraMoveReason: Int,
): Boolean = !isFollowingLocation || shouldStopFollowing(cameraMoveReason)

internal fun shouldShowInitialMapLoading(
    initialLoadingComplete: Boolean,
    isMapReady: Boolean,
): Boolean = !initialLoadingComplete && !isMapReady

internal fun manualLocationHoldDurationMillis(isManualLocationActive: Boolean): Long =
    if (isManualLocationActive) {
        ActiveManualLocationHoldDurationMillis
    } else {
        InitialManualLocationHoldDurationMillis
    }

internal fun mapPreviewZoom(
    mapZoom: Double,
    mapWidthPixels: Int,
    density: Float,
    previewWidthPixels: Int,
): Double = mapZoom - log2(mapWidthPixels / density / previewWidthPixels)

internal fun shouldCompleteStopSwipe(offset: Float, maximum: Float): Boolean =
    maximum > 0f && offset >= maximum * 0.82f

internal fun stopSwipeProgress(offset: Float, maximum: Float): Float =
    if (maximum <= 0f) 0f else (offset / maximum).coerceIn(0f, 1f)

internal fun stopSwipePromptAlpha(offset: Float, maximum: Float): Float {
    if (maximum <= 0f) return 1f
    val progress = stopSwipeProgress(offset, maximum)
    return ((0.82f - progress) / 0.22f).coerceIn(0f, 1f)
}

internal fun clusterStackOffsets(pointCount: Int): List<Float> = when {
    pointCount <= 1 -> listOf(8f)
    pointCount == 2 -> listOf(4f, 8f)
    else -> listOf(0f, 4f, 8f)
}

internal fun overlappingMomentOffsets(moments: List<MapMoment>): Map<String, Offset> =
    moments
        .groupBy { it.latitude to it.longitude }
        .values
        .filter { it.size > 1 }
        .flatMap { group ->
            val sorted = group.sortedBy(MapMoment::id)
            val spacing = MomentMarkerWidth + 8f
            val radius = spacing / (2f * sin(PI.toFloat() / sorted.size))
            val startAngle = if (sorted.size == 2) PI.toFloat() else -PI.toFloat() / 2f
            sorted.mapIndexed { index, moment ->
                val angle = startAngle + 2f * PI.toFloat() * index / sorted.size
                moment.id to Offset(
                    x = cos(angle) * radius,
                    y = sin(angle) * radius,
                )
            }
        }
        .toMap()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartedAt = SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        var splashExitComplete by mutableStateOf(false)
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStartedAt <
                MinimumSystemSplashDurationMillis
        }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(LoaderAsteriskAccelerationDurationMillis.toLong())
                .withEndAction {
                    provider.remove()
                    splashExitComplete = true
                }
                .start()
        }
        enableEdgeToEdge()
        setContent { SpurApp(splashExitComplete = splashExitComplete) }
    }
}

@Composable
private fun SpurApp(splashExitComplete: Boolean) {
    val context = LocalContext.current
    val store = remember { TourStore(context) }
    val scope = rememberCoroutineScope()
    var activeTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTourId by rememberSaveable { mutableStateOf<Long?>(null) }
    var displayedTourRequest by rememberSaveable { mutableLongStateOf(0L) }
    var routePoints by remember { mutableStateOf(emptyList<TrackPoint>()) }
    var isTourEditing by rememberSaveable { mutableStateOf(false) }
    var historyRevision by remember { mutableLongStateOf(0L) }
    var photoRevision by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var initialMapLoadingComplete by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasLocationPermission())
    }
    val navController = rememberNavController()
    var isHistoryVisible by rememberSaveable { mutableStateOf(false) }
    var feedbackNotice by remember { mutableStateOf<FeedbackNotice?>(null) }
    var feedbackNoticeId by remember { mutableLongStateOf(0L) }
    val showFeedbackNotice: ShowFeedbackNotice = { kind, message ->
        feedbackNoticeId++
        feedbackNotice = FeedbackNotice(feedbackNoticeId, kind, message)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasLocationPermission()
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        val restored = withContext(Dispatchers.IO) { store.activeTour() }
        activeTour = restored
        if (restored != null) {
            displayedTour = restored
            displayedTourId = restored.id
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TOUR_ID, restored.id),
            )
        }
    }

    LaunchedEffect(activeTour?.id, displayedTourId, historyRevision) {
        val id = displayedTourId ?: activeTour?.id
        if (id == null) {
            displayedTour = null
            routePoints = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            val result = withContext(Dispatchers.IO) {
                store.tour(id) to store.points(id)
            }
            displayedTour = result.first
            routePoints = result.second
            now = System.currentTimeMillis()
            if (activeTour?.id != id) break
            activeTour = result.first?.takeIf { it.endedAt == null }
            delay(1_000L)
        }
    }

    LaunchedEffect(feedbackNotice?.id) {
        if (feedbackNotice == null) return@LaunchedEffect
        delay(FeedbackNoticeDurationMillis)
        feedbackNotice = null
    }

    val deleteTour: (Long) -> Unit = { id ->
        scope.launch {
            if (!context.deleteStoredTour(store, id)) {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Tour konnte nicht gelöscht werden.",
                )
                return@launch
            }
            if (activeTour?.id == id) {
                context.startService(
                    Intent(context, TrackingService::class.java)
                        .setAction(TrackingService.ACTION_STOP),
                )
                activeTour = null
            }
            if (displayedTourId == id) {
                displayedTour = null
                displayedTourId = null
                routePoints = emptyList()
                isTourEditing = false
            }
            historyRevision++
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Moss,
            onPrimary = Color.White,
            background = Sand,
            onBackground = Ink,
            surface = Sand,
            onSurface = Ink,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasLocationPermission) {
                LocationOnboarding(
                    permissionRequested = permissionRequested,
                    onRequestLocation = {
                        permissionRequested = true
                        locationPermissionLauncher.launch(
                            buildList {
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray(),
                        )
                    },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = SpurRoute.MAP,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(340),
                            ) +
                                fadeIn(tween(220))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(340),
                            ) + fadeOut(tween(180))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(340),
                            ) + fadeIn(tween(220))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(340),
                            ) + fadeOut(tween(180))
                        },
                    ) {
                        composable(SpurRoute.MAP) {
                            MapPage(
                                store = store,
                                tour = displayedTour,
                                activeTour = activeTour,
                                tourDisplayRequest = displayedTourRequest,
                                routePoints = routePoints,
                                now = now,
                                onStartTour = {
                                    scope.launch {
                                        val id = withContext(Dispatchers.IO) {
                                            val startedId = store.startTour()
                                            context.loadManualLocation()?.let { coordinate ->
                                                store.appendSimulatedLocation(
                                                    startedId,
                                                    coordinate,
                                                )
                                            }
                                            startedId
                                        }
                                        ContextCompat.startForegroundService(
                                            context,
                                            Intent(context, TrackingService::class.java)
                                                .putExtra(TrackingService.EXTRA_TOUR_ID, id),
                                        )
                                        val started = withContext(Dispatchers.IO) {
                                            store.tour(id)
                                        }
                                        activeTour = started
                                        displayedTour = started
                                        displayedTourId = id
                                        displayedTourRequest++
                                        routePoints = emptyList()
                                        historyRevision++
                                    }
                                },
                                onSimulatedLocation = { coordinate ->
                                    val id = activeTour?.id ?: return@MapPage
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            store.appendSimulatedLocation(id, coordinate)
                                        }
                                        historyRevision++
                                    }
                                },
                                onEndTour = {
                                    val id = activeTour?.id ?: return@MapPage
                                    scope.launch {
                                        withContext(Dispatchers.IO) { store.finishTour(id) }
                                        context.startService(
                                            Intent(context, TrackingService::class.java)
                                                .setAction(TrackingService.ACTION_STOP),
                                        )
                                        val result = withContext(Dispatchers.IO) {
                                            store.tour(id) to store.points(id)
                                        }
                                        activeTour = null
                                        displayedTour = result.first
                                        displayedTourId = id
                                        displayedTourRequest++
                                        routePoints = result.second
                                        now = System.currentTimeMillis()
                                        historyRevision++
                                    }
                                },
                                onOpenHistory = {
                                    isHistoryVisible = true
                                },
                                isTourEditing = isTourEditing,
                                onEditTour = { isTourEditing = true },
                                onCloseTourEditor = { isTourEditing = false },
                                onRoutePointsChanged = {
                                    routePoints = it
                                    historyRevision++
                                },
                                onDeleteTour = deleteTour,
                                showFeedbackNotice = showFeedbackNotice,
                                photoRevision = photoRevision,
                                onPhotoRotated = { photoRevision++ },
                                initialLoadingComplete = initialMapLoadingComplete,
                                splashExitComplete = splashExitComplete,
                                onInitialLoadingComplete = {
                                    initialMapLoadingComplete = true
                                },
                            )
                        }
                    }

                    val historyPanelOffset by animateFloatAsState(
                        targetValue = if (isHistoryVisible) 0f else 1f,
                        animationSpec = tween(
                            durationMillis = PanelMotionDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "History panel offset",
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset {
                                    IntOffset(
                                        x = (constraints.maxWidth * historyPanelOffset)
                                            .roundToInt(),
                                        y = 0,
                                    )
                                },
                        ) {
                            HistoryPage(
                                store = store,
                                revision = historyRevision,
                                isVisible = isHistoryVisible,
                                onBack = { isHistoryVisible = false },
                                onEditTour = { id ->
                                    if (displayedTourId != id) {
                                        displayedTour = null
                                        routePoints = emptyList()
                                    }
                                    displayedTourId = id
                                    displayedTourRequest++
                                    isTourEditing = true
                                    isHistoryVisible = false
                                },
                                showFeedbackNotice = showFeedbackNotice,
                                photoRevision = photoRevision,
                                onPhotoRotated = { photoRevision++ },
                            )
                        }
                    }
                    FeedbackNoticeHost(
                        notice = feedbackNotice,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(100f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationOnboarding(
    permissionRequested: Boolean,
    onRequestLocation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Spur",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (permissionRequested) {
                    "Ohne Standort fehlt deine Spur."
                } else {
                    "Deine Spur beginnt dort, wo du bist."
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Spur nutzt deinen Standort, um die Karte bei dir zu öffnen und deine Tour aufzuzeichnen.",
                modifier = Modifier.padding(top = 14.dp),
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Deine Standortdaten bleiben auf diesem Gerät.",
                modifier = Modifier.padding(top = 10.dp),
                color = Moss,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = onRequestLocation,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Moss),
        ) {
            Text(
                text = if (permissionRequested) "Erneut erlauben" else "Standort erlauben",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MapPage(
    store: TourStore,
    tour: Tour?,
    activeTour: Tour?,
    tourDisplayRequest: Long,
    routePoints: List<TrackPoint>,
    now: Long,
    onStartTour: () -> Unit,
    onSimulatedLocation: (SpurCoordinate) -> Unit,
    onEndTour: () -> Unit,
    onOpenHistory: () -> Unit,
    isTourEditing: Boolean,
    onEditTour: () -> Unit,
    onCloseTourEditor: () -> Unit,
    onRoutePointsChanged: (List<TrackPoint>) -> Unit,
    onDeleteTour: (Long) -> Unit,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    photoRevision: Long = 0L,
    onPhotoRotated: () -> Unit = {},
    initialLoadingComplete: Boolean = false,
    splashExitComplete: Boolean = true,
    onInitialLoadingComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val isTourActive = activeTour != null
    val usesStackedMapPlayer = shouldStackMapPlayer(
        LocalConfiguration.current.screenWidthDp,
    )
    val mapActionsBottomPadding = if (isTourEditing) {
        EditorLocationRailHeight + EditorMetricBarHeight + MapControlVerticalPadding
    } else {
        MapControlVerticalPadding +
            if (usesStackedMapPlayer) MapControlSize + MapControlGap else 0.dp
    }
    val scope = rememberCoroutineScope()
    var followRequest by rememberSaveable { mutableStateOf(0) }
    var tourOverviewRequest by rememberSaveable { mutableStateOf(0) }
    var isFollowingLocation by rememberSaveable { mutableStateOf(false) }
    var requestedLocationPulseGeneration by remember { mutableLongStateOf(0L) }
    var activeLocationPulseGeneration by remember { mutableStateOf<Long?>(null) }
    var isTourOverview by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    var alternateMapPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isAlternateMapPreviewLoading by remember { mutableStateOf(true) }
    var showStartTourBottomSheet by rememberSaveable { mutableStateOf(false) }
    var isStartingTour by rememberSaveable { mutableStateOf(false) }
    var momentTarget by remember { mutableStateOf<MomentPlacementTarget?>(null) }
    var showMainMenu by rememberSaveable { mutableStateOf(false) }
    var showTourMenu by rememberSaveable { mutableStateOf(false) }
    var showHomeAutoStartBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showButtonColorsBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showTrailColorsBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showDirectionBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutBottomSheet by rememberSaveable { mutableStateOf(false) }
    var tourToDelete by remember { mutableStateOf<Tour?>(null) }
    var editorDeleteTarget by remember { mutableStateOf<EditorDeleteTarget?>(null) }
    var selectedEditorPointId by rememberSaveable(tour?.id) {
        mutableStateOf<Long?>(null)
    }
    var editorFocusRequest by remember { mutableLongStateOf(0L) }
    var selectedBuilding by remember { mutableStateOf<SpurCoordinate?>(null) }
    var homeBuilding by remember {
        mutableStateOf(context.loadHomeAutoStartSettings().homeBuilding)
    }
    var pendingMoment by remember { mutableStateOf<PendingMapMoment?>(null) }
    var photoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var mediaDetail by remember { mutableStateOf<MapMoment?>(null) }
    var photoDetailOrigin by remember { mutableStateOf<Offset?>(null) }
    var focusedPhoto by remember { mutableStateOf<MapMoment?>(null) }
    var mapMoments by remember { mutableStateOf(context.loadMapMoments()) }
    val visibleMapMoments = remember(mapMoments, tour) {
        tour?.let { mapMomentsForTour(mapMoments, it) } ?: mapMoments
    }
    val renderedMapMoments = remember(
        visibleMapMoments,
        routePoints,
        tour,
    ) {
        if (tour != null) {
            momentsAttachedToTrackPoints(visibleMapMoments, routePoints)
        } else {
            visibleMapMoments
        }
    }
    val editorLocations = remember(tour, routePoints, visibleMapMoments) {
        tour?.let {
            editorLocations(
                tour = it,
                points = routePoints,
                moments = visibleMapMoments,
            )
        }.orEmpty()
    }
    val selectedEditorLocation = editorLocations.firstOrNull {
        it.point.id == selectedEditorPointId
    } ?: editorLocations.lastOrNull()
    var activeVoiceMoment by remember { mutableStateOf<MapMoment?>(null) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableFloatStateOf(0f) }
    var manualLocation by remember { mutableStateOf(context.loadManualLocation()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val initialMapZoom = remember { context.loadDefaultMapZoom() }
    var defaultMapRotation by remember {
        mutableStateOf(context.loadDefaultMapRotation())
    }
    var mapControlBackground by remember {
        mutableStateOf(context.loadMapControlColor())
    }
    var mapControlForeground by remember {
        mutableStateOf(context.loadMapControlForegroundColor(mapControlBackground))
    }
    var trailFillColor by remember {
        mutableStateOf(context.loadTrailFillColor())
    }
    var trailStrokeColor by remember {
        mutableStateOf(context.loadTrailStrokeColor())
    }
    var isMapRendered by remember { mutableStateOf(false) }
    var systemSplashTimeElapsed by remember(initialLoadingComplete) {
        mutableStateOf(initialLoadingComplete)
    }
    var minimumMapLoadingTimeElapsed by remember(initialLoadingComplete) {
        mutableStateOf(initialLoadingComplete)
    }
    var mapInitializationStarted by remember { mutableStateOf(false) }
    val isMapReady = isMapRendered && minimumMapLoadingTimeElapsed
    var isMapGestureActive by remember { mutableStateOf(false) }
    val startTourBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val mainMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val tourMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val homeAutoStartBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val buttonColorsBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val trailColorsBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val directionBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val buildingDetailsBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val followOwnLocation: () -> Unit = {
        isTourOverview = false
        isFollowingLocation = true
        followRequest++
    }
    LaunchedEffect(isTourEditing, tour?.id, editorLocations.size) {
        if (!isTourEditing) {
            editorDeleteTarget = null
            return@LaunchedEffect
        }
        showStartTourBottomSheet = false
        showMainMenu = false
        isFollowingLocation = false
        isTourOverview = false
        if (editorLocations.none { it.point.id == selectedEditorPointId }) {
            selectedEditorPointId = editorLocations.lastOrNull()?.point?.id
        }
    }
    LaunchedEffect(splashExitComplete, initialLoadingComplete) {
        if (initialLoadingComplete || !splashExitComplete) return@LaunchedEffect
        systemSplashTimeElapsed = true
        delay(MinimumMapLoadingDurationMillis)
        minimumMapLoadingTimeElapsed = true
    }
    LaunchedEffect(isMapReady, initialLoadingComplete) {
        if (isMapReady && !initialLoadingComplete) onInitialLoadingComplete()
    }
    LaunchedEffect(isTourActive) {
        if (isTourActive) isStartingTour = false
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        mapInitializationStarted = true
    }
    LaunchedEffect(
        isMapReady,
        manualLocation,
        trailFillColor,
        isFollowingLocation,
    ) {
        if (!isMapReady || manualLocation != null) {
            activeLocationPulseGeneration = null
            return@LaunchedEffect
        }
        while (true) {
            if (isFollowingLocation) activeLocationPulseGeneration = null
            requestedLocationPulseGeneration++
            delay(LocationPulseWatchdogMillis)
        }
    }
    LaunchedEffect(Unit) {
        if (context.loadHomeAutoStartSettings().enabled) {
            context.registerHomeExitGeofence()
        }
    }
    DisposableEffect(activeVoiceMoment?.id) {
        val moment = activeVoiceMoment
        isVoicePlaying = false
        voiceProgress = 0f
        if (moment == null) {
            voicePlayer = null
            onDispose {}
        } else {
            val player = MediaPlayer()
            voicePlayer = player
            player.setOnPreparedListener {
                if (voicePlayer === player) {
                    player.start()
                    isVoicePlaying = true
                }
            }
            player.setOnCompletionListener {
                if (voicePlayer === player) {
                    isVoicePlaying = false
                    voiceProgress = 1f
                }
            }
            player.setOnErrorListener { _, _, _ ->
                if (voicePlayer === player) {
                    isVoicePlaying = false
                    activeVoiceMoment = null
                    showFeedbackNotice(
                        FeedbackNoticeKind.ERROR,
                        "Die Sprachnachricht konnte nicht abgespielt werden.",
                    )
                }
                true
            }
            runCatching {
                player.setDataSource(moment.payload)
                player.prepareAsync()
            }.onFailure {
                activeVoiceMoment = null
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Die Sprachnachricht konnte nicht abgespielt werden.",
                )
            }
            onDispose {
                if (voicePlayer === player) voicePlayer = null
                runCatching { player.release() }
            }
        }
    }
    LaunchedEffect(voicePlayer, isVoicePlaying) {
        val player = voicePlayer ?: return@LaunchedEffect
        while (isVoicePlaying) {
            val duration = runCatching { player.duration }.getOrDefault(0)
            val position = runCatching { player.currentPosition }.getOrDefault(0)
            voiceProgress = if (duration > 0) {
                position.toFloat() / duration
            } else {
                0f
            }
            delay(100)
        }
    }
    DisposableEffect(lifecycle, voicePlayer) {
        val player = voicePlayer
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && player != null) {
                runCatching {
                    if (player.isPlaying) player.pause()
                }
                isVoicePlaying = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val toggleVoicePlayback: (MapMoment) -> Unit = { moment ->
        if (activeVoiceMoment?.id != moment.id) {
            activeVoiceMoment = moment
        } else {
            voicePlayer?.let { player ->
                runCatching {
                    if (player.isPlaying) {
                        player.pause()
                        isVoicePlaying = false
                    } else {
                        if (player.duration > 0 && player.currentPosition >= player.duration) {
                            player.seekTo(0)
                            voiceProgress = 0f
                        }
                        player.start()
                        isVoicePlaying = true
                    }
                }
            }
        }
    }
    BackHandler(
        enabled = isTourOverview &&
            !showStartTourBottomSheet &&
            !showMainMenu &&
            !showTourMenu &&
            !showHomeAutoStartBottomSheet &&
            !showButtonColorsBottomSheet &&
            !showTrailColorsBottomSheet &&
            !showDirectionBottomSheet &&
            !showAboutBottomSheet &&
            photoDetail == null &&
            mediaDetail == null,
        onBack = followOwnLocation,
    )
    BackHandler(
        enabled = isTourEditing &&
            momentTarget == null &&
            editorDeleteTarget == null,
        onBack = onCloseTourEditor,
    )
    val mapControlColors = MapControlColors(
        background = mapControlBackground.color,
        foreground = mapControlForeground.color,
    )
    val trailColors = TrailColors(
        fill = trailFillColor.color,
        stroke = trailStrokeColor.color.copy(alpha = TrailStrokeAlpha),
    )
    CompositionLocalProvider(
        LocalMapControlColors provides mapControlColors,
        LocalTrailColors provides trailColors,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mapInitializationStarted) {
            MapSurface(
                modifier = Modifier.zIndex(if (isMapGestureActive) 1f else 0f),
                tourId = tour?.id,
                tourDisplayRequest = tourDisplayRequest,
                followRequest = followRequest,
                tourOverviewRequest = tourOverviewRequest,
                isFollowingLocation = isFollowingLocation,
                locationPulseGeneration = requestedLocationPulseGeneration,
                isSatelliteView = isSatelliteView,
                manualLocation = manualLocation,
                initialMapZoom = initialMapZoom,
                defaultMapBearing = defaultMapRotation.bearing,
                mapSettingsVisible = showDirectionBottomSheet,
                mapMoments = renderedMapMoments,
                momentImageRevision = photoRevision,
                routePoints = routePoints,
                trailColors = trailColors,
                homeBuilding = homeBuilding,
                selectedTrackPoint = if (isTourEditing) {
                    selectedEditorLocation?.point
                } else {
                    null
                },
                selectedTrackPointRequest = editorFocusRequest,
                momentToPlace = pendingMoment,
                focusedMoment = focusedPhoto,
                activeVoiceMoment = activeVoiceMoment,
                voicePlaybackProgress = voiceProgress,
                onAlternateMapPreviewChanged = { alternateMapPreview = it },
                onAlternateMapPreviewLoadingChanged = {
                    isAlternateMapPreviewLoading = it
                },
                onMomentPlaced = { moment ->
                    val updatedMoments = mapMoments + moment.copy(
                        tourId = activeTour?.id ?: tour?.id,
                    )
                    context.saveMapMoments(updatedMoments)
                    mapMoments = updatedMoments
                    pendingMoment = null
                },
                onMomentPlacementFailed = { failedMoment ->
                    failedMoment.deletePayload()
                    pendingMoment = null
                    showFeedbackNotice(
                        FeedbackNoticeKind.ERROR,
                        "Der Standort ist noch nicht verfügbar.",
                    )
                },
                onMomentClick = { moment, origin ->
                    isFollowingLocation = false
                    isTourOverview = false
                    when (moment.type) {
                        MomentType.PHOTO -> {
                            activeVoiceMoment = null
                            photoDetailOrigin = origin
                            photoDetail = moment
                        }
                        MomentType.VIDEO -> {
                            activeVoiceMoment = null
                            mediaDetail = moment
                        }
                        MomentType.VOICE -> toggleVoicePlayback(moment)
                        MomentType.EMOJI -> Unit
                    }
                },
                onBuildingClick = { selectedBuilding = it },
                onManualLocationChanged = { location ->
                    context.saveManualLocation(location)
                    manualLocation = location
                    onSimulatedLocation(location)
                },
                onFollowingInterrupted = {
                    isFollowingLocation = false
                    isTourOverview = false
                },
                onLocationPulseStarted = { generation ->
                    activeLocationPulseGeneration = generation
                },
                onLocationPulseResync = {
                    if (isFollowingLocation) activeLocationPulseGeneration = null
                    requestedLocationPulseGeneration++
                },
                onMapReadyChanged = { isMapRendered = it },
                onMapGestureActiveChanged = { isMapGestureActive = it },
            )
            }

            AnimatedVisibility(
                visible = isMapReady,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            end = MapControlHorizontalPadding,
                            bottom = mapActionsBottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MapControlGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isTourEditing) {
                        selectedEditorLocation?.let { location ->
                            MapIconButton(
                                contentDescription = "GPS-Punkt löschen",
                                onClick = {
                                    editorDeleteTarget =
                                        EditorDeleteTarget.Location(location.point)
                                },
                                enabled = routePoints.size > 1,
                            ) {
                                PhotoDeleteIcon()
                            }
                            MapIconButton(
                                contentDescription = "Moment hinzufügen",
                                onClick = {
                                    momentTarget =
                                        MomentPlacementTarget.RecordedLocation(
                                            tourId = tour?.id ?: return@MapIconButton,
                                            trackPointId = location.point.id,
                                            coordinate = SpurCoordinate(
                                                location.point.latitude,
                                                location.point.longitude,
                                            ),
                                        )
                                },
                            ) {
                                PlusIcon()
                            }
                        }
                        MapIconButton(
                            contentDescription = "Editor schließen",
                            onClick = onCloseTourEditor,
                        ) {
                            PhotoCloseIcon()
                        }
                    } else {
                        MapIconButton(
                            contentDescription = "Hauptmenü öffnen",
                            onClick = { showMainMenu = true },
                        ) {
                            MenuIcon()
                        }
                        tour?.takeIf {
                            it.endedAt != null || activeTour?.id == it.id
                        }?.let {
                            MapIconButton(
                                contentDescription = "Tour bearbeiten",
                                onClick = onEditTour,
                            ) {
                                LucideIcon(
                                    paths = listOf(
                                        "M12 20h9",
                                        "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z",
                                    ),
                                    strokeWidth = LucideBoldStrokeWidth,
                                )
                            }
                        }
                        MapIconButton(
                            contentDescription = "Tour-History öffnen",
                            onClick = {
                                activeVoiceMoment = null
                                onOpenHistory()
                            },
                        ) {
                            HistoryIcon()
                        }
                        MapIconButton(
                            contentDescription = "Moment hinzufügen",
                            onClick = {
                                momentTarget = MomentPlacementTarget.CurrentLocation
                            },
                        ) {
                            PlusIcon()
                        }
                        if (manualLocation != null) {
                            MapIconButton(
                                contentDescription = "Simulierten Standort zurücksetzen",
                                onClick = {
                                    context.saveManualLocation(null)
                                    manualLocation = null
                                },
                            ) {
                                LucideLocateOffIcon()
                            }
                        }
                        MapIconButton(
                            contentDescription = when {
                                isTourOverview -> "Zur Standortverfolgung zurückkehren"
                                isFollowingLocation -> "Gesamte Tour anzeigen"
                                else -> "Eigenem Standort folgen"
                            },
                            onClick = {
                                if (
                                    shouldShowTourOverview(
                                        isFollowingLocation = isFollowingLocation,
                                        isTourActive = isTourActive,
                                        routePointCount = routePoints.size,
                                    )
                                ) {
                                    isFollowingLocation = false
                                    isTourOverview = true
                                    tourOverviewRequest++
                                } else {
                                    followOwnLocation()
                                }
                            },
                        ) {
                            FollowLocationIcon(
                                selected = isFollowingLocation,
                                pulseGeneration = activeLocationPulseGeneration,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isMapReady && !isTourEditing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
            ) {
                val mapStyleControl: @Composable () -> Unit = {
                    MapStyleButton(
                        contentDescription = if (isSatelliteView) {
                            "Schematische Kartenansicht anzeigen"
                        } else {
                            "Satellitenansicht anzeigen"
                        },
                        onClick = {
                            isMapRendered = false
                            isAlternateMapPreviewLoading = true
                            alternateMapPreview = null
                            isSatelliteView = !isSatelliteView
                        },
                        preview = alternateMapPreview,
                        isLoading = isAlternateMapPreviewLoading,
                        fallbackPreview = if (isSatelliteView) {
                            R.drawable.map_preview_street
                        } else {
                            R.drawable.map_preview_satellite
                        },
                    )
                }
                val playerControl: @Composable (Modifier) -> Unit = { modifier ->
                    if (activeTour != null) {
                        TourPlayer(
                            tour = activeTour,
                            now = now,
                            onStop = onEndTour,
                            modifier = modifier,
                        )
                    } else {
                        val controlColors = LocalMapControlColors.current.inverted
                        Button(
                            onClick = { showStartTourBottomSheet = true },
                            modifier = modifier
                                .height(MapControlSize)
                                .mapControlShadow(CircleShape),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = controlColors.background,
                                contentColor = controlColors.foreground,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                        ) {
                            Text(
                                text = "Tour starten",
                                color = controlColors.foreground,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            horizontal = MapControlHorizontalPadding,
                            vertical = MapControlVerticalPadding,
                        )
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(MapControlGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MapControlGap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        mapStyleControl()
                        playerControl(Modifier.weight(1f))
                        if (!usesStackedMapPlayer) {
                            Spacer(modifier = Modifier.size(MapControlSize))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isMapReady &&
                    isTourEditing &&
                    tour != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
            ) {
                tour?.let { editedTour ->
                    selectedEditorLocation?.let { location ->
                        TourSummaryPlayer(
                            tourId = editedTour.id,
                            distanceMeters = location.distanceFromStartMeters,
                            elapsedMillis = location.elapsedMillis,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(
                                    start = MapControlHorizontalPadding,
                                    end = MapControlHorizontalPadding,
                                    bottom = EditorLocationRailHeight + 8.dp,
                                )
                                .fillMaxWidth()
                                .height(EditorMetricBarHeight - 8.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isMapReady && isTourEditing && editorLocations.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(MotionDurationDefaultMillis),
                    initialOffsetY = { it },
                ) + fadeIn(tween(MotionDurationDefaultMillis)),
                exit = slideOutVertically(
                    animationSpec = tween(MotionDurationDefaultMillis),
                    targetOffsetY = { it },
                ) + fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding(),
                ) {
                    selectedEditorLocation?.let { location ->
                        EditorLocationRail(
                            locations = editorLocations,
                            selectedPointId = location.point.id,
                            onSelected = { pointId ->
                                if (pointId != selectedEditorPointId) {
                                    selectedEditorPointId = pointId
                                    editorFocusRequest++
                                }
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = shouldShowInitialMapLoading(
                    initialLoadingComplete = initialLoadingComplete,
                    isMapReady = isMapReady,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                enter = fadeIn(tween(MotionDurationDefaultMillis / 2)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Sand)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val textAlpha = remember { Animatable(0f) }
                        LaunchedEffect(systemSplashTimeElapsed) {
                            textAlpha.snapTo(0f)
                            if (systemSplashTimeElapsed) {
                                withFrameNanos { }
                                textAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis =
                                            LoaderAsteriskAccelerationDurationMillis,
                                        easing = LinearEasing,
                                    ),
                                )
                            }
                        }
                        AcceleratingAsterisk(
                            isRunning = systemSplashTimeElapsed,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(LoaderAsteriskSize),
                            color = Color.Black,
                            contentDescription = "Karte wird geladen",
                        )
                        Text(
                            text = "Spur startet…",
                            color = Ink.copy(alpha = textAlpha.value),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(
                                    y = maxHeight / 2 +
                                        LoaderAsteriskSize / 2 +
                                        LoaderTextGap,
                                ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    if (showStartTourBottomSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showStartTourBottomSheet = false },
            sheetState = startTourBottomSheetState,
        ) {
            StartTourBottomSheet(
                onStartTour = {
                    if (isStartingTour) return@StartTourBottomSheet
                    isStartingTour = true
                    scope.launch {
                        startTourBottomSheetState.hide()
                        showStartTourBottomSheet = false
                        onStartTour()
                    }
                },
            )
        }
    }

    selectedBuilding?.let { building ->
        val closeBuildingDetails: () -> Unit = {
            scope.launch {
                buildingDetailsBottomSheetState.hide()
                selectedBuilding = null
            }
        }
        SpurModalBottomSheet(
            onDismissRequest = { selectedBuilding = null },
            sheetState = buildingDetailsBottomSheetState,
        ) {
            BackHandler(onBack = closeBuildingDetails)
            BuildingDetailsBottomSheet(
                coordinate = building,
                onBack = closeBuildingDetails,
            )
        }
    }

    if (showMainMenu) {
        SpurModalBottomSheet(
            onDismissRequest = { showMainMenu = false },
            sheetState = mainMenuState,
        ) {
            MainMenu(
                onOpenTour = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showTourMenu = true
                    }
                },
                onOpenButtonColors = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showButtonColorsBottomSheet = true
                    }
                },
                onOpenTrailColors = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showTrailColorsBottomSheet = true
                    }
                },
                onOpenDirection = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showDirectionBottomSheet = true
                    }
                },
                onOpenAbout = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showAboutBottomSheet = true
                    }
                },
                onShareTour = if (isTourActive) {
                    {
                        scope.launch {
                            mainMenuState.hide()
                            showMainMenu = false
                            shareActiveTour(context)
                        }
                    }
                } else {
                    null
                },
                onDeleteTour = tour?.let { visibleTour ->
                    {
                        scope.launch {
                            mainMenuState.hide()
                            showMainMenu = false
                            tourToDelete = visibleTour
                        }
                    }
                },
            )
        }
    }

    tourToDelete?.let { selectedTour ->
        EditorDeleteSheet(
            title = "Tour löschen?",
            primaryLabel = "Tour löschen",
            onDismiss = { tourToDelete = null },
            onConfirm = {
                tourToDelete = null
                onDeleteTour(selectedTour.id)
            },
        )
    }

    editorDeleteTarget?.let { target ->
        EditorDeleteSheet(
            title = when (target) {
                is EditorDeleteTarget.Location -> "GPS-Punkt löschen?"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen?"
            },
            primaryLabel = when (target) {
                is EditorDeleteTarget.Location -> "GPS-Punkt löschen"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen"
            },
            onDismiss = { editorDeleteTarget = null },
            onConfirm = {
                editorDeleteTarget = null
                when (target) {
                    is EditorDeleteTarget.Moment -> scope.launch {
                        val updated = context.deleteMapMoment(
                            moment = target.moment,
                            moments = mapMoments,
                        )
                        if (updated == null) {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "${target.moment.type.editorLabel()} konnte nicht gelöscht werden.",
                            )
                        } else {
                            mapMoments = updated
                        }
                    }
                    is EditorDeleteTarget.Location -> scope.launch {
                        val visibleTour = tour ?: return@launch
                        val deletedIndex = routePoints.indexOf(target.point)
                        val retained = routePoints.filterNot {
                            it.id == target.point.id
                        }
                        if (retained.isEmpty()) return@launch
                        withContext(Dispatchers.IO) {
                            store.updateTourPoints(
                                visibleTour.id,
                                retained.mapTo(mutableSetOf(), TrackPoint::id),
                            )
                        }
                        val updatedMoments = mapMoments.map { moment ->
                            if (moment.trackPointId != target.point.id) {
                                moment
                            } else {
                                moment.copy(
                                    trackPointId = nearestTrackPoint(
                                        retained,
                                        moment.latitude,
                                        moment.longitude,
                                    )?.id,
                                )
                            }
                        }
                        context.saveMapMoments(updatedMoments)
                        mapMoments = updatedMoments
                        selectedEditorPointId = retained.getOrNull(
                            deletedIndex.coerceAtMost(retained.lastIndex),
                        )?.id
                        onRoutePointsChanged(retained)
                    }
                }
            },
        )
    }

    if (showTourMenu) {
        val closeTourMenu: () -> Unit = {
            scope.launch {
                tourMenuState.hide()
                showTourMenu = false
                showMainMenu = true
            }
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showTourMenu = false
                showMainMenu = true
            },
            sheetState = tourMenuState,
        ) {
            BackHandler(onBack = closeTourMenu)
            TourMenu(
                onBack = closeTourMenu,
                onOpenHomeAutoStart = {
                    scope.launch {
                        tourMenuState.hide()
                        showTourMenu = false
                        showHomeAutoStartBottomSheet = true
                    }
                },
            )
        }
    }

    if (showHomeAutoStartBottomSheet) {
        SpurModalBottomSheet(
            onDismissRequest = {
                showHomeAutoStartBottomSheet = false
                showTourMenu = true
            },
            sheetState = homeAutoStartBottomSheetState,
        ) {
            HomeAutoStartBottomSheet(
                onSettingsChanged = { homeBuilding = it.homeBuilding },
                onBack = {
                    scope.launch {
                        homeAutoStartBottomSheetState.hide()
                        showHomeAutoStartBottomSheet = false
                        showTourMenu = true
                    }
                },
            )
        }
    }

    if (showButtonColorsBottomSheet) {
        val closeButtonColors: () -> Unit = {
            scope.launch {
                buttonColorsBottomSheetState.hide()
                showButtonColorsBottomSheet = false
                showMainMenu = true
            }
        }
        val selectMapControlBackground: (MapControlColor) -> Unit = {
            mapControlBackground = it
            context.saveMapControlColor(it)
        }
        val selectMapControlForeground: (MapControlColor) -> Unit = {
            mapControlForeground = it
            context.saveMapControlForegroundColor(it)
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showButtonColorsBottomSheet = false
                showMainMenu = true
            },
            sheetState = buttonColorsBottomSheetState,
        ) {
            BackHandler(onBack = closeButtonColors)
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BottomSheetHeader(
                    title = "Buttonfarben wählen",
                    onBack = closeButtonColors,
                )
                MapControlColorPreview(
                    colors = mapControlColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Buttonfarbe",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                MapControlColorPicker(
                    label = "Buttonfarbe",
                    selectedColor = mapControlBackground,
                    onSelect = selectMapControlBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Icon- und Textfarbe",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                MapControlColorPicker(
                    label = "Icon- und Textfarbe",
                    selectedColor = mapControlForeground,
                    onSelect = selectMapControlForeground,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showTrailColorsBottomSheet) {
        val closeTrailColors: () -> Unit = {
            scope.launch {
                trailColorsBottomSheetState.hide()
                showTrailColorsBottomSheet = false
                showMainMenu = true
            }
        }
        val selectTrailFill: (MapControlColor) -> Unit = {
            trailFillColor = it
            context.saveTrailFillColor(it)
        }
        val selectTrailStroke: (MapControlColor) -> Unit = {
            trailStrokeColor = it
            context.saveTrailStrokeColor(it)
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showTrailColorsBottomSheet = false
                showMainMenu = true
            },
            sheetState = trailColorsBottomSheetState,
        ) {
            BackHandler(onBack = closeTrailColors)
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BottomSheetHeader(
                    title = "Trailfarben wählen",
                    onBack = closeTrailColors,
                )
                TrailColorPreview(
                    colors = trailColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Füllfarbe",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                MapControlColorPicker(
                    label = "Trail-Füllfarbe",
                    selectedColor = trailFillColor,
                    onSelect = selectTrailFill,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Randfarbe",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                MapControlColorPicker(
                    label = "Trail-Randfarbe",
                    selectedColor = trailStrokeColor,
                    onSelect = selectTrailStroke,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDirectionBottomSheet) {
        val compassRotation = remember {
            Animatable(-defaultMapRotation.bearing.toFloat())
        }
        LaunchedEffect(defaultMapRotation) {
            compassRotation.animateTo(
                targetValue = nearestCompassRotation(
                    current = compassRotation.value,
                    target = -defaultMapRotation.bearing.toFloat(),
                ),
                animationSpec = tween(MapRotationAnimationMillis.toInt()),
            )
        }
        val selectMapRotation: (MapRotation) -> Unit = {
            defaultMapRotation = it
            context.saveDefaultMapRotation(it)
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showDirectionBottomSheet = false
                showMainMenu = true
            },
            sheetState = directionBottomSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                BottomSheetHeader(
                    title = "Himmelsrichtung wählen",
                    onBack = {
                        scope.launch {
                            directionBottomSheetState.hide()
                            showDirectionBottomSheet = false
                            showMainMenu = true
                        }
                    },
                )
                MapRotationPicker(
                    compassRotation = compassRotation.value,
                    selectedRotation = defaultMapRotation,
                    onSelect = selectMapRotation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showAboutBottomSheet) {
        val uriHandler = LocalUriHandler.current
        SpurModalBottomSheet(
            onDismissRequest = {
                showAboutBottomSheet = false
                showMainMenu = true
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BottomSheetHeader(
                    title = "Über Spur",
                    onBack = {
                        showAboutBottomSheet = false
                        showMainMenu = true
                    },
                )
                Text(
                    text = "Spur hält deine Wege und Erinnerungen privat auf deinem Gerät fest.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Entwickelt von ",
                        color = Ink.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable {
                                runCatching { uriHandler.openUri(ArashLinkedInUrl) }
                                    .onFailure {
                                        showAboutBottomSheet = false
                                        showFeedbackNotice(
                                            FeedbackNoticeKind.ERROR,
                                            "LinkedIn konnte nicht geöffnet werden.",
                                        )
                                    }
                            }
                            .semantics {
                                contentDescription =
                                    "LinkedIn-Profil von Arash Yalpani öffnen"
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Arash Yalpani.",
                            color = Ink,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        ExternalLinkIcon()
                    }
                }
            }
        }
    }

    MomentComposer(
        target = momentTarget,
        showFeedbackNotice = showFeedbackNotice,
        onDismiss = { momentTarget = null },
        onMomentAccepted = { target, moment ->
            when (target) {
                MomentPlacementTarget.CurrentLocation -> pendingMoment = moment
                is MomentPlacementTarget.RecordedLocation -> {
                    val savedMoment = MapMoment(
                        id = moment.id,
                        type = moment.type,
                        latitude = target.coordinate.latitude,
                        longitude = target.coordinate.longitude,
                        payload = moment.payload,
                        tourId = target.tourId,
                        trackPointId = target.trackPointId,
                    )
                    val updatedMoments = mapMoments + savedMoment
                    context.saveMapMoments(updatedMoments)
                    mapMoments = updatedMoments
                }
            }
            momentTarget = null
        },
    )

    photoDetail?.let { moment ->
        val photos = remember(visibleMapMoments) {
            orderedPhotoMoments(visibleMapMoments)
        }
        PhotoDetailPage(
            photos = photos,
            initialPhotoId = moment.id,
            openOrigin = photoDetailOrigin,
            photoRevision = photoRevision,
            onPhotoChanged = {
                photoDetail = it
                focusedPhoto = it
            },
            showFeedbackNotice = showFeedbackNotice,
            onPhotoRotated = onPhotoRotated,
            onPhotoDeleted = { deletedPhoto ->
                scope.launch {
                    val updatedMoments = context.deleteMapMoment(
                        moment = deletedPhoto,
                        moments = mapMoments,
                    )
                    if (updatedMoments == null) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Das Bild konnte nicht gelöscht werden.",
                        )
                    } else {
                        mapMoments = updatedMoments
                        photoDetail = null
                        photoDetailOrigin = null
                        focusedPhoto = null
                    }
                }
            },
            onDismiss = {
                photoDetail = null
                photoDetailOrigin = null
                focusedPhoto = null
            },
        )
    }

    mediaDetail?.let { moment ->
        MediaMomentDetailPage(
            moment = moment,
            onDismiss = { mediaDetail = null },
        )
    }
}

@Composable
private fun VoiceRecorderBottomSheet(
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
                recording != null -> "Aufnahme bereit"
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
        recording?.takeIf { !isRecording }?.let { AudioPlaybackControl(it) }
        Button(
            onClick = {
                when {
                    isRecording -> stopRecording(keep = true)
                    hasRecordPermission -> startRecording()
                    else -> onRequestPermission()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) StopRed else Ink,
                contentColor = Color.White,
            ),
            shape = CircleShape,
        ) {
            if (isRecording) {
                LucideStopIcon(Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Aufnahme beenden", style = MaterialTheme.typography.titleMedium)
            } else {
                MicrophoneIcon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    if (recording == null) "Aufnahme starten" else "Neu aufnehmen",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (recording != null && !isRecording) {
            Button(
                onClick = {
                    recording?.let {
                        accepted = true
                        onRecordingAccepted(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = Color.White,
                ),
                shape = CircleShape,
            ) {
                Text("Auf der Karte ablegen", style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(
            onClick = {
                stopRecording(keep = false)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Abbrechen", color = Ink)
        }
    }
}

@Composable
private fun AudioPlaybackControl(
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

@Composable
private fun MediaMomentDetailPage(
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

@Composable
private fun HomeAutoStartBottomSheet(
    onSettingsChanged: (HomeAutoStartSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var settings by remember { mutableStateOf(context.loadHomeAutoStartSettings()) }
    var setupRequested by rememberSaveable { mutableStateOf(false) }
    var homeSearchOrigin by remember { mutableStateOf<SpurCoordinate?>(settings.home) }
    var candidateHome by remember { mutableStateOf<SelectedHomeBuilding?>(null) }
    var locating by remember { mutableStateOf(false) }
    var needsBackgroundPermission by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun disable() {
        context.removeHomeExitGeofence()
        settings = HomeAutoStartSettings(enabled = false, home = null)
        context.saveHomeAutoStartSettings(settings)
        onSettingsChanged(settings)
        setupRequested = false
        homeSearchOrigin = null
        candidateHome = null
        needsBackgroundPermission = false
        message = null
    }

    fun activate(home: SelectedHomeBuilding) {
        settings = HomeAutoStartSettings(
            enabled = true,
            home = home.coordinate,
            homeBuilding = home.feature,
        )
        context.saveHomeAutoStartSettings(settings)
        onSettingsChanged(settings)
        context.registerHomeExitGeofence()
        setupRequested = false
        needsBackgroundPermission = false
        message = "Startautomatik ist aktiv."
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            candidateHome?.let(::activate)
        } else {
            locating = false
            setupRequested = false
            candidateHome = null
            needsBackgroundPermission = false
            message = "Ohne Hintergrundstandort bleibt die Einstellung aus."
        }
    }

    fun requestBackgroundLocation() {
        needsBackgroundPermission = true
    }

    LaunchedEffect(needsBackgroundPermission) {
        if (!needsBackgroundPermission) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            backgroundPermissionLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
    }

    val currentNeedsBackgroundPermission by rememberUpdatedState(needsBackgroundPermission)
    val currentCandidateHome by rememberUpdatedState(candidateHome)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentNeedsBackgroundPermission) {
                if (context.hasBackgroundLocationPermission()) {
                    currentCandidateHome?.let(::activate)
                } else {
                    locating = false
                    setupRequested = false
                    candidateHome = null
                    needsBackgroundPermission = false
                    message = "Ohne Hintergrundstandort bleibt die Einstellung aus."
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BottomSheetHeader(
            title = "Startautomatik",
            onBack = onBack,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Startautomatik",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Switch(
                checked = settings.enabled || setupRequested,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        disable()
                    } else {
                        setupRequested = true
                        locating = true
                        message = null
                        scope.launch {
                            val currentLocation = context.currentSpurLocation()
                            homeSearchOrigin = currentLocation
                            candidateHome = null
                            locating = false
                            if (currentLocation == null) {
                                setupRequested = false
                                message = "Dein aktueller Standort konnte nicht bestimmt werden."
                            }
                        }
                    }
                },
            )
        }

        val shownHomeOrigin = settings.home ?: homeSearchOrigin
        if (shownHomeOrigin != null) {
            HomeBuildingSelector(
                origin = shownHomeOrigin,
                initialBuilding = settings.homeBuilding,
                selectionEnabled = !settings.enabled,
                onBuildingSelected = { selected ->
                    candidateHome = selected
                    if (settings.enabled && settings.homeBuilding == null) {
                        settings = settings.copy(
                            home = selected.coordinate,
                            homeBuilding = selected.feature,
                        )
                        context.saveHomeAutoStartSettings(settings)
                        context.registerHomeExitGeofence()
                        onSettingsChanged(settings)
                    }
                },
            )
        }

        when {
            locating -> Text("Aktueller Standort wird bestimmt.")
            settings.enabled -> Text("Spur startet eine Tour, wenn du diesen Bereich verlässt.")
            candidateHome != null -> {
                Text(
                    text = "Bist du gerade zu Hause?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!context.hasBackgroundLocationPermission()) {
                    Text(
                        text = "Nach deiner Bestätigung öffnen sich die Android-Einstellungen. " +
                            "Wähle dort Berechtigungen → Standort → Immer zulassen.",
                    )
                }
                Button(
                    onClick = {
                        val home = candidateHome ?: return@Button
                        if (context.hasBackgroundLocationPermission()) {
                            activate(home)
                        } else {
                            requestBackgroundLocation()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Ja, hier ist mein Zuhause", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        setupRequested = false
                        homeSearchOrigin = null
                        candidateHome = null
                        message = "Komm später wieder, wenn du zu Hause bist."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Nein")
                }
            }
            homeSearchOrigin != null -> Text("Passendes Gebäude wird gesucht.")
        }

        message?.let {
            Text(
                text = it,
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HomeBuildingSelector(
    origin: SpurCoordinate,
    initialBuilding: Feature?,
    selectionEnabled: Boolean,
    onBuildingSelected: (SelectedHomeBuilding) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSelectionEnabled by rememberUpdatedState(selectionEnabled)
    val currentOnBuildingSelected by rememberUpdatedState(onBuildingSelected)
    val searchRadiusPixels = with(LocalDensity.current) {
        HomeBuildingSelectionSearchRadiusDp.dp.toPx()
    }
    val mapView = remember(origin) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView, origin, searchRadiusPixels) {
        var map: MapLibreMap? = null
        var selectedBuilding = initialBuilding

        fun selectBuilding(feature: Feature) {
            val readyMap = map ?: return
            val home = homeCoordinate(feature) ?: return
            selectedBuilding = feature
            readyMap.style?.showSelectedHomeBuilding(feature)
            currentOnBuildingSelected(
                SelectedHomeBuilding(
                    coordinate = home,
                    feature = feature,
                ),
            )
        }

        fun selectBuildingAt(screenPoint: PointF, searchNearby: Boolean): Boolean {
            val readyMap = map ?: return false
            val feature = readyMap.homeBuildingAt(
                screenPoint = screenPoint,
                searchRadiusPixels = if (searchNearby) searchRadiusPixels else 0f,
            ) ?: return false
            selectBuilding(feature)
            return true
        }

        val clickListener = MapLibreMap.OnMapClickListener { point ->
            if (!currentSelectionEnabled) return@OnMapClickListener false
            val readyMap = map ?: return@OnMapClickListener false
            selectBuildingAt(
                screenPoint = readyMap.projection.toScreenLocation(point),
                searchNearby = false,
            )
        }
        val renderListener = MapView.OnDidFinishRenderingMapListener { fully ->
            if (!fully || selectedBuilding != null) return@OnDidFinishRenderingMapListener
            val readyMap = map ?: return@OnDidFinishRenderingMapListener
            selectBuildingAt(
                screenPoint = readyMap.projection.toScreenLocation(
                    LatLng(origin.latitude, origin.longitude),
                ),
                searchNearby = true,
            )
        }
        mapView.addOnDidFinishRenderingMapListener(renderListener)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            readyMap.addOnMapClickListener(clickListener)
            readyMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(origin.latitude, origin.longitude),
                    HomeBuildingSelectionZoom,
                ),
            )
            readyMap.setStyle(StreetMapStyle) { style ->
                style.hideDistractingPoiLayers()
                style.showOutlinedBuildings()
                style.showSelectableHomeBuildings()
                style.showSelectedHomeBuilding(selectedBuilding)
                mapView.postInvalidate()
            }
        }

        onDispose {
            map?.removeOnMapClickListener(clickListener)
            mapView.removeOnDidFinishRenderingMapListener(renderListener)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Mist)
                .semantics {
                    contentDescription = "Gebäudeauswahl für dein Zuhause"
                },
        )
        if (selectionEnabled) {
            Text(
                text = "Alle sichtbaren Gebäude können ausgewählt werden.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun MapLibreMap.homeBuildingAt(
    screenPoint: PointF,
    searchRadiusPixels: Float,
): Feature? {
    queryRenderedFeatures(screenPoint, MapBuildingLayer).firstOrNull()?.let { return it }
    if (searchRadiusPixels <= 0f) return null
    val nearby = queryRenderedFeatures(
        RectF(
            screenPoint.x - searchRadiusPixels,
            screenPoint.y - searchRadiusPixels,
            screenPoint.x + searchRadiusPixels,
            screenPoint.y + searchRadiusPixels,
        ),
        MapBuildingLayer,
    )
    return nearby.minByOrNull { feature ->
        val center = homeCoordinate(feature) ?: return@minByOrNull Float.MAX_VALUE
        val renderedCenter = projection.toScreenLocation(
            LatLng(center.latitude, center.longitude),
        )
        val dx = renderedCenter.x - screenPoint.x
        val dy = renderedCenter.y - screenPoint.y
        dx * dx + dy * dy
    }
}

internal fun homeCoordinate(feature: Feature): SpurCoordinate? {
    val points = when (val geometry = feature.geometry()) {
        is Polygon -> geometry.coordinates().flatten()
        is MultiPolygon -> geometry.coordinates().flatten().flatten()
        else -> return null
    }
    if (points.isEmpty()) return null
    return SpurCoordinate(
        latitude = (points.minOf(Point::latitude) + points.maxOf(Point::latitude)) / 2,
        longitude = (points.minOf(Point::longitude) + points.maxOf(Point::longitude)) / 2,
    )
}

private fun Style.showSelectableHomeBuildings() {
    val buildings = getLayerAs<FillLayer>(MapBuildingLayer) ?: return
    val sourceLayer = buildings.sourceLayer ?: return
    if (getLayer(SelectableHomeBuildingsLayer) != null) return
    addLayerAbove(
        LineLayer(SelectableHomeBuildingsLayer, buildings.sourceId)
            .withSourceLayer(sourceLayer)
            .withProperties(
                lineColor(Ink.copy(alpha = 0.28f).toArgb()),
                lineWidth(1.25f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        MapBuildingLayer,
    )
}

private fun Style.showSelectedHomeBuilding(feature: Feature?) {
    val source = getSourceAs<GeoJsonSource>(HomeBuildingSource)
        ?: GeoJsonSource(HomeBuildingSource).also(::addSource)
    if (getLayer(HomeBuildingFillLayer) == null) {
        val layer = FillLayer(HomeBuildingFillLayer, HomeBuildingSource).withProperties(
            fillColor(Ink.toArgb()),
            fillOpacity(0.18f),
        )
        if (getLayer(MapBuildingLayer) == null) addLayer(layer)
        else addLayerAbove(layer, MapBuildingLayer)
    }
    if (getLayer(HomeBuildingOutlineLayer) == null) {
        addLayerAbove(
            LineLayer(HomeBuildingOutlineLayer, HomeBuildingSource).withProperties(
                lineColor(Ink.toArgb()),
                lineWidth(3f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
            HomeBuildingFillLayer,
        )
    }
    if (feature == null) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(feature)
    }
}

@Composable
private fun StartTourBottomSheet(
    onStartTour: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHeader(
            title = "Tour starten",
        )
        Text(
            text = "Spur zeichnet deine Strecke weiter auf, " +
                "auch wenn dein Bildschirm aus ist.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = Ink.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartTour,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
        ) {
            Text(
                text = "Los geht’s",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MainMenu(
    onOpenTour: () -> Unit,
    onOpenButtonColors: () -> Unit,
    onOpenTrailColors: () -> Unit,
    onOpenDirection: () -> Unit,
    onOpenAbout: () -> Unit,
    onShareTour: (() -> Unit)?,
    onDeleteTour: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        if (onDeleteTour != null || onShareTour != null) {
            onDeleteTour?.let {
                SheetMenuItem(
                    label = "Tour löschen",
                    onClick = it,
                    destructive = true,
                    leading = { PhotoDeleteIcon(color = StopRed) },
                    trailing = false,
                )
            }
            onShareTour?.let {
                SheetMenuItem(
                    label = "Tour teilen",
                    onClick = it,
                    leading = { ShareIcon() },
                    trailing = false,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        SheetMenuItem(label = "Tour", onClick = onOpenTour)
        SheetMenuItem(label = "Buttonfarben", onClick = onOpenButtonColors)
        SheetMenuItem(label = "Trail", onClick = onOpenTrailColors)
        SheetMenuItem(label = "Himmelsrichtung", onClick = onOpenDirection)
        SheetMenuItem(label = "Über Spur", onClick = onOpenAbout)
    }
}

@Composable
private fun TourMenu(
    onBack: () -> Unit,
    onOpenHomeAutoStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        BottomSheetHeader(
            title = "Tour",
            modifier = Modifier.padding(horizontal = 24.dp),
            onBack = onBack,
        )
        SheetMenuItem(
            label = "Startautomatik",
            leading = { HomeIcon() },
            onClick = onOpenHomeAutoStart,
        )
    }
}

@Composable
private fun BottomSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                BackIcon()
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BuildingDetailsBottomSheet(
    coordinate: SpurCoordinate,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var address by remember(coordinate) { mutableStateOf<String?>(null) }
    var addressResolved by remember(coordinate) { mutableStateOf(false) }

    LaunchedEffect(coordinate) {
        address = context.reverseGeocode(
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
        )
        addressResolved = true
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        BottomSheetHeader(
            title = "Gebäude",
            onBack = onBack,
        )
        Text(
            text = "Adresse",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                !addressResolved -> "Adresse wird ermittelt…"
                address != null -> address.orEmpty()
                else -> "Für dieses Gebäude ist keine Adresse verfügbar."
            },
            color = Ink.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SheetMenuItem(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (destructive) StopRed else Ink,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = SheetMenuTextSize,
                ),
                fontWeight = FontWeight.Medium,
            )
            if (trailing) {
                LucideIcon(
                    paths = listOf("m9 18 6-6-6-6"),
                    color = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MapControlColorPreview(
    colors: MapControlColors,
    modifier: Modifier = Modifier,
) {
    val playerColors = colors.inverted
    Surface(
        modifier = modifier,
        color = Mist,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                color = colors.background,
                contentColor = colors.foreground,
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(
                        LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
                    ) {
                        MenuIcon()
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                color = playerColors.background,
                contentColor = playerColors.foreground,
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Tour starten",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailColorPreview(
    colors: TrailColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Trail-Vorschau"
        },
        color = Mist,
        shape = RoundedCornerShape(24.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(16.dp),
        ) {
            val previewPath = Path().apply {
                moveTo(0f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.72f,
                    size.width * 0.30f,
                    size.height * 0.22f,
                    size.width * 0.55f,
                    size.height * 0.36f,
                )
                cubicTo(
                    size.width * 0.73f,
                    size.height * 0.46f,
                    size.width * 0.78f,
                    size.height * 0.72f,
                    size.width,
                    size.height * 0.58f,
                )
            }
            drawPath(
                path = previewPath,
                color = colors.stroke,
                style = Stroke(
                    width = TourRouteBorderWidthPixels.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
            drawPath(
                path = previewPath,
                color = colors.fill,
                style = Stroke(
                    width = TourRouteWidthPixels.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun MapControlColorPicker(
    label: String,
    selectedColor: MapControlColor,
    onSelect: (MapControlColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MapControlColor.entries.chunked(4).forEach { options ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                options.forEach { option ->
                    val selected = option == selectedColor
                    Surface(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "$label ${option.label}"
                                this.selected = selected
                            },
                        shape = CircleShape,
                        color = option.color,
                        border = BorderStroke(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                option.contrastColor
                            } else {
                                Ink.copy(alpha = 0.18f)
                            },
                        ),
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LucideIcon(
                                    paths = listOf("M20 6 9 17l-5-5"),
                                    color = option.contrastColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapRotationPicker(
    compassRotation: Float,
    selectedRotation: MapRotation,
    onSelect: (MapRotation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            CompassCircle()
            MapRotation.entries.forEach { rotation ->
                FilterChip(
                    selected = selectedRotation == rotation,
                    onClick = { onSelect(rotation) },
                    label = { Text(rotation.label) },
                )
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val circle = measurables.first().measure(childConstraints)
        val options = measurables.drop(1).map { it.measure(childConstraints) }
        val gap = MapRotationOptionGap.roundToPx()
        val visualInset = FilterChipVisualInset.roundToPx()
        val height = (
            circle.height + 2 * gap + 2 * (options.maxOf { it.height } - visualInset)
        ).coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = constraints.maxWidth
        val centerX = width / 2f
        val centerY = height / 2f

        layout(width, height) {
            circle.placeRelative(
                x = (centerX - circle.width / 2f).roundToInt(),
                y = (centerY - circle.height / 2f).roundToInt(),
            )
            MapRotation.entries.zip(options).forEach { (rotation, option) ->
                val angle = (rotation.bearing - 90.0 + compassRotation) * PI / 180.0
                val radius = mapRotationOptionCenterDistance(
                    circleRadius = circle.width / 2f,
                    gap = gap.toFloat(),
                    halfWidth = option.width / 2f,
                    halfHeight = (option.height / 2f - visualInset).coerceAtLeast(0f),
                    angleRadians = angle,
                )
                option.placeRelative(
                    x = (centerX + cos(angle).toFloat() * radius - option.width / 2f)
                        .roundToInt(),
                    y = (centerY + sin(angle).toFloat() * radius - option.height / 2f)
                        .roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun CompassCircle() {
    Canvas(modifier = Modifier.size(84.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        drawCircle(
            color = Moss.copy(alpha = 0.3f),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = Moss.copy(alpha = 0.22f),
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = Moss.copy(alpha = 0.22f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun MomentOption(
    label: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = accentColor,
        ),
        border = BorderStroke(2.dp, Ink.copy(alpha = 0.18f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val controlColors = LocalMapControlColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(MapControlSize)
            .mapControlShadow(CircleShape)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = controlColors.background,
            contentColor = controlColors.foreground,
        ),
    ) {
        CompositionLocalProvider(
            LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
            content = content,
        )
    }
}

@Composable
private fun MapStyleButton(
    contentDescription: String,
    preview: ImageBitmap?,
    isLoading: Boolean,
    fallbackPreview: Int,
    onClick: () -> Unit,
) {
    val tourControlColors = LocalMapControlColors.current.inverted
    val previewShape = RectangleShape
    val blurRadius by animateDpAsState(
        targetValue = if (isLoading) 7.dp else 0.dp,
        animationSpec = tween(180),
        label = "Map preview blur",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(MapControlSize)
            .mapControlShadow(previewShape)
            .semantics { this.contentDescription = contentDescription },
        shape = previewShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, tourControlColors.background),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val previewModifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
            if (preview == null) {
                Image(
                    painter = painterResource(fallbackPreview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = previewModifier,
                )
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = previewModifier,
                )
            }
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(220)),
            ) {
                MapPreviewLoadingOverlay()
            }
        }
    }
}

@Composable
private fun MapPreviewLoadingOverlay() {
    val transition = rememberInfiniteTransition(label = "Map preview haze")
    val drift by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Map preview haze drift",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.White.copy(alpha = 0.5f))
        drawCircle(
            color = Color.White.copy(alpha = 0.28f),
            radius = size.width * 0.9f,
            center = Offset(
                x = size.width * drift,
                y = size.height * (0.25f + drift * 0.35f),
            ),
        )
        drawCircle(
            color = Color(0xFFD9E9E2).copy(alpha = 0.24f),
            radius = size.width * 0.75f,
            center = Offset(
                x = size.width * (1f - drift),
                y = size.height * (0.75f - drift * 0.3f),
            ),
        )
    }
}

@Composable
private fun MapSurface(
    modifier: Modifier = Modifier,
    tourId: Long?,
    tourDisplayRequest: Long,
    followRequest: Int,
    tourOverviewRequest: Int,
    isFollowingLocation: Boolean,
    locationPulseGeneration: Long,
    isSatelliteView: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    mapSettingsVisible: Boolean,
    mapMoments: List<MapMoment>,
    momentImageRevision: Long,
    routePoints: List<TrackPoint>,
    trailColors: TrailColors,
    homeBuilding: Feature?,
    selectedTrackPoint: TrackPoint?,
    selectedTrackPointRequest: Long,
    momentToPlace: PendingMapMoment?,
    focusedMoment: MapMoment?,
    activeVoiceMoment: MapMoment?,
    voicePlaybackProgress: Float,
    onAlternateMapPreviewChanged: (ImageBitmap) -> Unit,
    onAlternateMapPreviewLoadingChanged: (Boolean) -> Unit,
    onMomentPlaced: (MapMoment) -> Unit,
    onMomentPlacementFailed: (PendingMapMoment) -> Unit,
    onMomentClick: (MapMoment, Offset) -> Unit,
    onBuildingClick: (SpurCoordinate) -> Unit,
    onManualLocationChanged: (SpurCoordinate) -> Unit,
    onFollowingInterrupted: () -> Unit,
    onLocationPulseStarted: (Long) -> Unit,
    onLocationPulseResync: () -> Unit,
    onMapReadyChanged: (Boolean) -> Unit,
    onMapGestureActiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMomentPlaced by rememberUpdatedState(onMomentPlaced)
    val currentOnMomentPlacementFailed by rememberUpdatedState(onMomentPlacementFailed)
    val currentOnMomentClick by rememberUpdatedState(onMomentClick)
    val currentOnBuildingClick by rememberUpdatedState(onBuildingClick)
    val currentOnManualLocationChanged by rememberUpdatedState(onManualLocationChanged)
    val currentOnFollowingInterrupted by rememberUpdatedState(onFollowingInterrupted)
    val currentOnLocationPulseStarted by rememberUpdatedState(onLocationPulseStarted)
    val currentOnLocationPulseResync by rememberUpdatedState(onLocationPulseResync)
    val currentOnMapReadyChanged by rememberUpdatedState(onMapReadyChanged)
    val currentOnMapGestureActiveChanged by rememberUpdatedState(onMapGestureActiveChanged)
    val currentOnAlternateMapPreviewChanged by rememberUpdatedState(
        onAlternateMapPreviewChanged,
    )
    val currentOnAlternateMapPreviewLoadingChanged by rememberUpdatedState(
        onAlternateMapPreviewLoadingChanged,
    )
    val currentMapMoments by rememberUpdatedState(mapMoments)
    val currentRoutePoints by rememberUpdatedState(routePoints)
    val currentTrailColors by rememberUpdatedState(trailColors)
    val currentHomeBuilding by rememberUpdatedState(homeBuilding)
    val currentSelectedTrackPoint by rememberUpdatedState(selectedTrackPoint)
    val currentManualLocation by rememberUpdatedState(manualLocation)
    val currentFollowRequest by rememberUpdatedState(followRequest)
    val currentTourOverviewRequest by rememberUpdatedState(tourOverviewRequest)
    val currentLocationPulseGeneration by rememberUpdatedState(locationPulseGeneration)
    val currentIsFollowingLocation by rememberUpdatedState(isFollowingLocation)
    val currentDefaultMapBearing by rememberUpdatedState(defaultMapBearing)
    var manualLocationPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var selectedTrackPointPosition by remember {
        mutableStateOf<android.graphics.PointF?>(null)
    }
    var previewCameraPosition by remember {
        mutableStateOf<org.maplibre.android.camera.CameraPosition?>(null)
    }
    var pendingMapMoment by remember { mutableStateOf<MapMoment?>(null) }
    var pendingMomentPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var preparedMapMoments by remember { mutableStateOf<PreparedMapMoments?>(null) }
    var renderedVoicePlaybackId by remember { mutableStateOf<String?>(null) }
    var mapStyleRevision by remember { mutableStateOf(0) }
    var hasLoadedMapStyle by remember { mutableStateOf(false) }
    var fittedTourId by remember { mutableStateOf<Long?>(null) }
    var fittedTourDisplayRequest by remember { mutableLongStateOf(-1L) }
    var lastMapSettingsBearing by remember { mutableStateOf(defaultMapBearing) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }

    LaunchedEffect(isFollowingLocation) {
        if (isFollowingLocation) currentOnAlternateMapPreviewLoadingChanged(false)
    }

    LaunchedEffect(
        mapSettingsVisible,
        defaultMapBearing,
    ) {
        val animateRotation = defaultMapBearing != lastMapSettingsBearing
        lastMapSettingsBearing = defaultMapBearing
        if (!mapSettingsVisible) return@LaunchedEffect
        mapView.getMapAsync { map ->
            val targetBearing = defaultMapBearing
            val resumeTracking =
                currentIsFollowingLocation &&
                    currentManualLocation == null &&
                    map.locationComponent.isLocationComponentActivated
            if (resumeTracking) map.locationComponent.cameraMode = CameraMode.NONE
            val update = CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                    .bearing(targetBearing)
                    .build(),
            )
            val resumeTrackingIfCurrent = {
                if (
                    resumeTracking &&
                    currentIsFollowingLocation &&
                    currentDefaultMapBearing == targetBearing
                ) {
                    map.followLocation(
                        context = context,
                        manualLocation = null,
                        transitionDuration = 0L,
                        defaultMapBearing = targetBearing,
                    )
                }
            }
            if (animateRotation) {
                map.animateCamera(
                    update,
                    MapRotationAnimationMillis.toInt(),
                    object : MapLibreMap.CancelableCallback {
                        override fun onCancel() = Unit

                        override fun onFinish() = resumeTrackingIfCurrent()
                    },
                )
            } else {
                map.moveCamera(update)
                resumeTrackingIfCurrent()
            }
        }
    }

    LaunchedEffect(isSatelliteView) {
        currentOnMapReadyChanged(false)
        currentOnAlternateMapPreviewLoadingChanged(true)
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            setMapStyle(
                context = context,
                map = map,
                satellite = isSatelliteView,
                centerOnLocation = !hasLoadedMapStyle,
                manualLocation = manualLocation,
                initialMapZoom = initialMapZoom,
                defaultMapBearing = defaultMapBearing,
                routePoints = currentRoutePoints,
                trailColors = currentTrailColors,
                onLoaded = {
                    mapStyleRevision++
                    hasLoadedMapStyle = true
                    previewCameraPosition = map.cameraPosition
                    if (currentIsFollowingLocation) {
                        map.followLocation(
                            context = context,
                            manualLocation = currentManualLocation,
                            transitionDuration = 0L,
                            defaultMapBearing = defaultMapBearing,
                        )
                    }
                    mapView.postOnAnimation {
                        currentOnMapReadyChanged(true)
                    }
                },
            )
        }
    }

    DisposableEffect(previewCameraPosition, isSatelliteView) {
        val cameraPosition = previewCameraPosition
        if (cameraPosition == null) {
            onDispose {}
        } else {
            var disposed = false
            val hasMapSize = mapView.width > 0 && mapView.height > 0
            val previewHeight = if (hasMapSize) {
                (MapPreviewPixels.toFloat() * mapView.height / mapView.width)
                    .roundToInt()
            } else {
                MapPreviewPixels
            }
            val previewCameraPosition = if (hasMapSize) {
                org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
                    .zoom(
                        mapPreviewZoom(
                            mapZoom = cameraPosition.zoom,
                            mapWidthPixels = mapView.width,
                            density = context.resources.displayMetrics.density,
                            previewWidthPixels = MapPreviewPixels,
                        ),
                    )
                    .build()
            } else {
                cameraPosition
            }
            val options = MapSnapshotter.Options(MapPreviewPixels, previewHeight)
                .withCameraPosition(previewCameraPosition)
                .withPixelRatio(1f)
                .withLogo(false)
                .let { snapshotOptions ->
                    if (isSatelliteView) {
                        snapshotOptions.withStyleBuilder(
                            Style.Builder().fromUri(StreetMapStyle),
                        )
                    } else {
                        snapshotOptions.withStyleBuilder(satelliteStyleBuilder())
                    }
                }
            val snapshotter = MapSnapshotter(context, options)
            snapshotter.start(
                { snapshot ->
                    if (!disposed) {
                        currentOnAlternateMapPreviewChanged(snapshot.bitmap.asImageBitmap())
                        currentOnAlternateMapPreviewLoadingChanged(false)
                    }
                },
                { _ ->
                    if (!disposed) currentOnAlternateMapPreviewLoadingChanged(false)
                },
            )
            onDispose {
                disposed = true
                snapshotter.cancel()
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (currentManualLocation == null) currentOnLocationPulseResync()
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        var map: MapLibreMap? = null
        var isMapTouchActive = false
        var isCameraMoving = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var holdStart = PointF()
        var manualLocationHold: Runnable? = null
        fun cancelManualLocationHold() {
            manualLocationHold?.let(mapView::removeCallbacks)
            manualLocationHold = null
        }
        fun publishManualLocationPosition() {
            val readyMap = map ?: return
            manualLocationPosition = currentManualLocation?.let { location ->
                readyMap.projection.toScreenLocation(
                    LatLng(location.latitude, location.longitude),
                )
            }
        }

        fun publishPendingMomentPosition() {
            val readyMap = map ?: return
            pendingMomentPosition = pendingMapMoment?.let { moment ->
                readyMap.projection.toScreenLocation(
                    LatLng(moment.latitude, moment.longitude),
                )
            }
        }

        fun publishSelectedTrackPointPosition() {
            val readyMap = map ?: return
            selectedTrackPointPosition = currentSelectedTrackPoint?.let { point ->
                readyMap.projection.toScreenLocation(
                    LatLng(point.latitude, point.longitude),
                )
            }
        }

        val moveListener = MapLibreMap.OnCameraMoveListener {
            if (currentManualLocation != null) publishManualLocationPosition()
            if (pendingMapMoment != null) publishPendingMomentPosition()
            if (currentSelectedTrackPoint != null) publishSelectedTrackPointPosition()
        }
        var cameraMoveReason =
            MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            isCameraMoving = true
            cameraMoveReason = reason
            if (shouldStopFollowing(reason)) currentOnMapGestureActiveChanged(true)
            if (shouldShowMapPreviewLoading(currentIsFollowingLocation, reason)) {
                currentOnAlternateMapPreviewLoadingChanged(true)
            }
            if (currentIsFollowingLocation && shouldStopFollowing(reason)) {
                map?.locationComponent?.cameraMode = CameraMode.NONE
                currentOnFollowingInterrupted()
            }
        }
        val idleListener = MapLibreMap.OnCameraIdleListener {
            isCameraMoving = false
            if (!isMapTouchActive) currentOnMapGestureActiveChanged(false)
            publishManualLocationPosition()
            publishPendingMomentPosition()
            publishSelectedTrackPointPosition()
            previewCameraPosition = map?.cameraPosition
            if (shouldStopFollowing(cameraMoveReason)) {
                map?.cameraPosition?.zoom?.let(context::saveDefaultMapZoom)
            }
            cameraMoveReason =
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        }
        val clickListener = MapLibreMap.OnMapClickListener { point ->
            val readyMap = map ?: return@OnMapClickListener false
            val screenPoint = readyMap.projection.toScreenLocation(point)
            val cluster = readyMap.queryRenderedFeatures(
                screenPoint,
                MapMomentClusterLayer,
            ).firstOrNull()
            if (cluster != null) {
                val source = readyMap.style?.getSourceAs<GeoJsonSource>(MapMomentSource)
                    ?: return@OnMapClickListener false
                val expansionZoom = source.getClusterExpansionZoom(cluster).toDouble()
                readyMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(point, expansionZoom),
                    MapRotationAnimationMillis.toInt(),
                )
                return@OnMapClickListener true
            }
            val momentId = readyMap.queryRenderedFeatures(
                screenPoint,
                MapMomentLayer,
            ).firstOrNull()?.getStringProperty(MapMomentIdProperty)
            val moment = currentMapMoments.firstOrNull { it.id == momentId }
            if (moment != null) {
                currentOnMomentClick(
                    moment,
                    Offset(screenPoint.x, screenPoint.y),
                )
                return@OnMapClickListener true
            }
            val building = readyMap.queryRenderedFeatures(
                screenPoint,
                MapBuildingLayer,
            ).firstOrNull()
            if (building != null) {
                currentOnBuildingClick(
                    SpurCoordinate(
                        latitude = point.latitude,
                        longitude = point.longitude,
                    ),
                )
                return@OnMapClickListener true
            }
            false
        }
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMapTouchActive = true
                    cancelManualLocationHold()
                    holdStart = PointF(event.x, event.y)
                    manualLocationHold = Runnable {
                        val point = map?.projection?.fromScreenLocation(holdStart)
                            ?: return@Runnable
                        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnManualLocationChanged(
                            SpurCoordinate(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            ),
                        )
                        manualLocationHold = null
                    }.also { hold ->
                        mapView.postDelayed(
                            hold,
                            manualLocationHoldDurationMillis(
                                isManualLocationActive = currentManualLocation != null,
                            ),
                        )
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - holdStart.x
                    val deltaY = event.y - holdStart.y
                    if (deltaX * deltaX + deltaY * deltaY > touchSlop * touchSlop) {
                        cancelManualLocationHold()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelManualLocationHold()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    cancelManualLocationHold()
                    isMapTouchActive = false
                    if (!isCameraMoving) currentOnMapGestureActiveChanged(false)
                }
            }
            false
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.addOnCameraMoveStartedListener(moveStartedListener)
            readyMap.addOnCameraMoveListener(moveListener)
            readyMap.addOnCameraIdleListener(idleListener)
            readyMap.addOnMapClickListener(clickListener)
            publishManualLocationPosition()
            publishPendingMomentPosition()
            publishSelectedTrackPointPosition()
        }
        onDispose {
            cancelManualLocationHold()
            currentOnMapGestureActiveChanged(false)
            mapView.setOnTouchListener(null)
            map?.removeOnCameraMoveStartedListener(moveStartedListener)
            map?.removeOnCameraMoveListener(moveListener)
            map?.removeOnCameraIdleListener(idleListener)
            map?.removeOnMapClickListener(clickListener)
        }
    }

    LaunchedEffect(followRequest, manualLocation, isFollowingLocation) {
        if (followRequest == 0 || !isFollowingLocation) return@LaunchedEffect
        val request = followRequest
        mapView.getMapAsync { map ->
            if (
                request != currentFollowRequest ||
                !currentIsFollowingLocation
            ) return@getMapAsync
            map.followLocation(
                context = context,
                manualLocation = manualLocation,
                transitionDuration = 500L,
                defaultMapBearing = defaultMapBearing,
            )
        }
    }

    LaunchedEffect(tourOverviewRequest) {
        if (tourOverviewRequest == 0) return@LaunchedEffect
        val request = tourOverviewRequest
        mapView.getMapAsync { map ->
            mapView.post {
                if (
                    request != currentTourOverviewRequest ||
                    currentIsFollowingLocation ||
                    currentRoutePoints.isEmpty()
                ) return@post
                map.locationComponent.cameraMode = CameraMode.NONE
                map.fitMapScreenTourRoute(
                    points = currentRoutePoints,
                    density = context.resources.displayMetrics.density,
                    pointZoom = initialMapZoom,
                    animated = true,
                )
            }
        }
    }

    LaunchedEffect(momentToPlace) {
        val pending = momentToPlace ?: return@LaunchedEffect
        val map = suspendCancellableCoroutine<MapLibreMap> { continuation ->
            mapView.getMapAsync { readyMap ->
                if (continuation.isActive) continuation.resume(readyMap)
            }
        }
        val location = map.currentSpurCoordinate(
            context = context,
            manual = manualLocation,
        )
        if (location == null) {
            pendingMapMoment = null
            pendingMomentPosition = null
            currentOnMomentPlacementFailed(pending)
            return@LaunchedEffect
        }
        val moment = MapMoment(
            id = pending.id,
            type = pending.type,
            latitude = location.latitude,
            longitude = location.longitude,
            payload = pending.payload,
        )
        pendingMapMoment = moment
        pendingMomentPosition = map.projection.toScreenLocation(
            LatLng(location.latitude, location.longitude),
        )
        map.locationComponent.cameraMode = CameraMode.NONE
        currentOnFollowingInterrupted()
        map.animateCamera(
            CameraUpdateFactory.newLatLng(
                LatLng(location.latitude, location.longitude),
            ),
            MotionDurationDefaultMillis,
        )
        delay(PendingPhotoRevealDelayMillis)
        currentOnMomentPlaced(moment)
    }

    LaunchedEffect(focusedMoment?.id) {
        val moment = focusedMoment ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.locationComponent.cameraMode = CameraMode.NONE
            map.moveCamera(
                CameraUpdateFactory.newLatLng(
                    LatLng(moment.latitude, moment.longitude),
                ),
            )
        }
    }

    LaunchedEffect(
        selectedTrackPoint?.id,
        selectedTrackPointRequest,
        mapStyleRevision,
    ) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                style.showSelectedTrackPoint(null)
                style.showTourEndpoints(
                    if (currentSelectedTrackPoint == null) {
                        emptyList()
                    } else {
                        currentRoutePoints
                    },
                    currentTrailColors,
                )
            }
            selectedTrackPointPosition = currentSelectedTrackPoint?.let { point ->
                map.projection.toScreenLocation(
                    LatLng(point.latitude, point.longitude),
                )
            }
            if (selectedTrackPointRequest == 0L) return@getMapAsync
            currentSelectedTrackPoint?.let { point ->
                map.locationComponent.cameraMode = CameraMode.NONE
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(point.latitude, point.longitude),
                    ),
                    EditorPointTransitionDurationMillis,
                )
            }
            selectedTrackPointPosition = currentSelectedTrackPoint?.let { point ->
                map.projection.toScreenLocation(
                    LatLng(point.latitude, point.longitude),
                )
            }
        }
    }

    LaunchedEffect(manualLocation, selectedTrackPoint == null) {
        mapView.getMapAsync { map ->
            map.showGpsLocationPuck(
                context = context,
                show = manualLocation == null && currentSelectedTrackPoint == null,
            )
            manualLocationPosition = manualLocation?.let { location ->
                map.projection.toScreenLocation(
                    LatLng(location.latitude, location.longitude),
                )
            }
        }
    }

    LaunchedEffect(mapMoments, momentImageRevision) {
        preparedMapMoments = withContext(Dispatchers.IO) {
            prepareMapMoments(context.applicationContext, mapMoments)
        }
    }

    LaunchedEffect(preparedMapMoments, mapStyleRevision) {
        val prepared = preparedMapMoments ?: return@LaunchedEffect
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showMapMoments(prepared)
            val pending = pendingMapMoment
            if (pending != null && prepared.moments.any { it.id == pending.id }) {
                pendingMapMoment = null
                pendingMomentPosition = null
            }
        }
    }

    LaunchedEffect(homeBuilding, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showSelectedHomeBuilding(currentHomeBuilding)
        }
    }

    val voiceProgressFrame =
        (voicePlaybackProgress.coerceIn(0f, 1f) * 100f).roundToInt() / 100f
    LaunchedEffect(
        activeVoiceMoment?.id,
        voiceProgressFrame,
        preparedMapMoments,
        mapStyleRevision,
    ) {
        val prepared = preparedMapMoments ?: return@LaunchedEffect
        if (mapStyleRevision == 0) return@LaunchedEffect
        val activeVoice = activeVoiceMoment
        val playbackMarker = activeVoice?.let {
            createMomentMarkerBitmap(
                context = context.applicationContext,
                moment = it,
                selected = false,
                voiceProgress = voiceProgressFrame,
            )
        }
        mapView.getMapAsync { map ->
            val style = map.style ?: return@getMapAsync
            val previousId = renderedVoicePlaybackId
            if (previousId != null && previousId != activeVoice?.id) {
                prepared.images[MapMomentImagePrefix + previousId]?.let { marker ->
                    style.addImage(MapMomentImagePrefix + previousId, marker)
                }
            }
            if (activeVoice != null && playbackMarker != null) {
                style.addImage(MapMomentImagePrefix + activeVoice.id, playbackMarker)
            }
            renderedVoicePlaybackId = activeVoice?.id
        }
    }

    LaunchedEffect(routePoints, trailColors) {
        val points = routePoints
        val routeFeature = withContext(Dispatchers.Default) {
            tourRouteFeature(points)
        }
        mapView.getMapAsync { map ->
            if (points !== currentRoutePoints) return@getMapAsync
            map.style?.let { style ->
                style.showTourRoute(routeFeature, currentTrailColors)
                style.showTourEndpoints(
                    if (currentSelectedTrackPoint == null) emptyList() else points,
                    currentTrailColors,
                )
            }
        }
    }

    LaunchedEffect(
        mapStyleRevision,
        locationPulseGeneration,
    ) {
        val generation = locationPulseGeneration
        if (
            mapStyleRevision == 0 ||
            generation == 0L ||
            currentManualLocation != null
        ) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (generation != currentLocationPulseGeneration) return@getMapAsync
            map.restartLocationPulse(currentTrailColors.fill)
            currentOnLocationPulseStarted(generation)
        }
    }

    LaunchedEffect(tourId, tourDisplayRequest, routePoints) {
        if (
            !shouldFitTourRoute(
                tourId = tourId,
                fittedTourId = fittedTourId,
                displayRequest = tourDisplayRequest,
                fittedDisplayRequest = fittedTourDisplayRequest,
                pointCount = routePoints.size,
            )
        ) return@LaunchedEffect
        val id = tourId ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            mapView.post {
                if (currentIsFollowingLocation) {
                    map.locationComponent.cameraMode = CameraMode.NONE
                    currentOnFollowingInterrupted()
                }
                map.fitMapScreenTourRoute(
                    points = routePoints,
                    density = context.resources.displayMetrics.density,
                    pointZoom = initialMapZoom,
                    animated = true,
                )
                fittedTourId = id
                fittedTourDisplayRequest = tourDisplayRequest
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Interaktive Kartenansicht" },
        )

        val density = LocalDensity.current
        val selectedPointSizePx = with(density) { 20.dp.roundToPx() }
        selectedTrackPointPosition
            ?.takeUnless {
                selectedTrackPoint?.id == routePoints.firstOrNull()?.id ||
                    selectedTrackPoint?.id == routePoints.lastOrNull()?.id
            }
            ?.let { position ->
                SelectedTrackPointPuck(
                    modifier = Modifier.offset {
                        IntOffset(
                            x = position.x.roundToInt() - selectedPointSizePx / 2,
                            y = position.y.roundToInt() - selectedPointSizePx / 2,
                        )
                    },
                )
            }

        val manualPuckSizePx = with(density) { 52.dp.roundToPx() }
        manualLocationPosition?.let { position ->
            SimulatedLocationPuck(
                modifier = Modifier.offset {
                    IntOffset(
                        x = position.x.roundToInt() - manualPuckSizePx / 2,
                        y = position.y.roundToInt() - manualPuckSizePx / 2,
                    )
                },
            )
        }

        val pendingMarkerWidthPx = with(density) { MomentMarkerWidth.dp.roundToPx() }
        val pendingMarkerHeightPx = with(density) { MomentMarkerHeight.dp.roundToPx() }
        pendingMomentPosition?.let { position ->
            pendingMapMoment?.let { moment ->
                PendingMomentMarker(
                    moment = moment,
                    modifier = Modifier.offset {
                        IntOffset(
                            x = position.x.roundToInt() - pendingMarkerWidthPx / 2,
                            y = position.y.roundToInt() - pendingMarkerHeightPx,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedTrackPointPuck(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        drawCircle(Color.White)
        drawCircle(Ink, radius = 7.dp.toPx())
    }
}

@Composable
private fun PendingMomentMarker(
    moment: MapMoment,
    modifier: Modifier = Modifier,
) {
    val markerColor = momentMarkerColor(moment.type)
    val bounceScale = remember(moment.id) {
        Animatable(if (moment.type == MomentType.EMOJI) 0.25f else 1f)
    }
    LaunchedEffect(moment.id) {
        if (moment.type == MomentType.EMOJI) {
            bounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Box(
        modifier = modifier
            .size(MomentMarkerWidth.dp, MomentMarkerHeight.dp)
            .graphicsLayer {
                scaleX = bounceScale.value
                scaleY = bounceScale.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / MomentMarkerWidth
            drawRoundRect(
                color = markerColor,
                topLeft = Offset(6f * scale, 2f * scale),
                size = Size(50f * scale, 50f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    10f * scale,
                    10f * scale,
                ),
            )
            drawPath(
                path = Path().apply {
                    moveTo(26f * scale, 50f * scale)
                    lineTo(36f * scale, 50f * scale)
                    lineTo(31f * scale, 57f * scale)
                    close()
                },
                color = markerColor,
            )
            drawPath(
                path = Path().apply {
                    moveTo(16f * scale, 2f * scale)
                    lineTo(46f * scale, 2f * scale)
                    cubicTo(
                        51.5f * scale,
                        2f * scale,
                        56f * scale,
                        6.5f * scale,
                        56f * scale,
                        12f * scale,
                    )
                    lineTo(56f * scale, 42f * scale)
                    cubicTo(
                        56f * scale,
                        47.5f * scale,
                        51.5f * scale,
                        52f * scale,
                        46f * scale,
                        52f * scale,
                    )
                    lineTo(34.5f * scale, 52f * scale)
                    lineTo(31f * scale, 57f * scale)
                    lineTo(27.5f * scale, 52f * scale)
                    lineTo(16f * scale, 52f * scale)
                    cubicTo(
                        10.5f * scale,
                        52f * scale,
                        6f * scale,
                        47.5f * scale,
                        6f * scale,
                        42f * scale,
                    )
                    lineTo(6f * scale, 12f * scale)
                    cubicTo(
                        6f * scale,
                        6.5f * scale,
                        10.5f * scale,
                        2f * scale,
                        16f * scale,
                        2f * scale,
                    )
                    close()
                },
                color = Color.White,
                style = Stroke(
                    width = MomentMarkerEdgeWidth * scale,
                    cap = StrokeCap.Round,
                ),
            )
        }
        if (moment.type == MomentType.EMOJI) {
            Text(
                text = moment.payload,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .semantics {
                        contentDescription = "Emoji ${moment.payload} wird abgelegt"
                    },
                fontSize = 28.sp,
            )
        } else {
            RotatingAsterisk(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .size(24.dp),
                color = momentMarkerContentColor(moment.type),
                contentDescription = when (moment.type) {
                    MomentType.PHOTO -> "Foto wird geladen"
                    MomentType.VIDEO -> "Video wird geladen"
                    MomentType.VOICE -> "Sprache wird geladen"
                    MomentType.EMOJI -> "Moment wird geladen"
                },
            )
        }
    }
}

@Composable
private fun RotatingAsterisk(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "Rotating asterisk")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AsteriskRotationDurationMillis,
                easing = LinearEasing,
            ),
        ),
        label = "Asterisk rotation",
    )
    AsteriskIcon(
        rotation = rotation,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
private fun AcceleratingAsterisk(
    isRunning: Boolean,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        rotation.animateTo(
            targetValue = LoaderAsteriskAccelerationDegrees,
            animationSpec = tween(
                durationMillis = LoaderAsteriskAccelerationDurationMillis,
                easing = Easing(::loaderAsteriskAcceleration),
            ),
        )
        while (true) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(
                    durationMillis = AsteriskRotationDurationMillis,
                    easing = LinearEasing,
                ),
            )
        }
    }
    AsteriskIcon(
        rotation = rotation.value,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

internal fun loaderAsteriskAcceleration(fraction: Float): Float = fraction * fraction

@Composable
private fun AsteriskIcon(
    rotation: Float,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    LucideIcon(
        paths = listOf(
            "M12 6v12",
            "M17.196 9 6.804 15",
            "m6.804 9 10.392 6",
        ),
        color = color,
        strokeWidth = LucideBoldStrokeWidth,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
private fun SimulatedLocationPuck(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = "Simulierter Standort" },
    ) {
        drawCircle(Ink.copy(alpha = 0.2f), radius = size.minDimension / 2)
        drawCircle(Color.White, radius = 10.dp.toPx())
        drawCircle(Ink, radius = 6.dp.toPx())
        drawCircle(
            color = Ink,
            radius = 10.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
@Suppress("DEPRECATION")
private fun LightSheetNavigationBar(backgroundColor: Color = SheetBackground) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousColor = window?.navigationBarColor
        val previousContrastEnforced =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window?.isNavigationBarContrastEnforced
            } else {
                null
            }
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        val previousLightIcons = insetsController?.isAppearanceLightNavigationBars

        window?.navigationBarColor = backgroundColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        insetsController?.isAppearanceLightNavigationBars =
            backgroundColor.luminance() > 0.5f

        onDispose {
            if (previousColor != null) {
                window.navigationBarColor = previousColor
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                previousContrastEnforced != null
            ) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousLightIcons != null) {
                insetsController?.isAppearanceLightNavigationBars = previousLightIcons
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun DarkMediaSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let {
            it.statusBarColor = Color.Black.toArgb()
            it.navigationBarColor = Color.Black.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose {}
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PhotoDetailPage(
    photos: List<MapMoment>,
    initialPhotoId: String,
    openOrigin: Offset? = null,
    photoRevision: Long = 0L,
    showFeedbackNotice: ShowFeedbackNotice,
    onPhotoChanged: (MapMoment) -> Unit = {},
    onPhotoRotated: () -> Unit = {},
    onPhotoDeleted: (MapMoment) -> Unit,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialPage = remember(photos, initialPhotoId) {
        photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size },
    )
    val selectedPhoto = photos[pagerState.currentPage.coerceIn(photos.indices)]
    val currentPhoto by rememberUpdatedState(selectedPhoto)
    val currentOnPhotoChanged by rememberUpdatedState(onPhotoChanged)
    val currentOnPhotoRotated by rememberUpdatedState(onPhotoRotated)
    val currentOnPhotoDeleted by rememberUpdatedState(onPhotoDeleted)
    val rotationMutex = remember { Mutex() }
    var imageRevision by remember(photoRevision) { mutableLongStateOf(photoRevision) }
    var hasRotatedPhoto by remember { mutableStateOf(false) }
    val openProgress = remember(openOrigin) {
        Animatable(if (openOrigin == null) 1f else 0f)
    }
    var openingPhotoAspectRatio by remember(openOrigin) { mutableFloatStateOf(1f) }
    var openingThumbnail by remember(openOrigin) { mutableStateOf<ImageBitmap?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var showPhotoActionsSheet by remember { mutableStateOf(false) }
    var showDeletePhotoSheet by remember { mutableStateOf(false) }
    val photoActionsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val deletePhotoSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var openingAnimationFinished by remember(openOrigin) {
        mutableStateOf(false)
    }
    val resolvedImageKeys = remember { mutableStateMapOf<String, Boolean>() }

    fun dismissAnimated() {
        if (isClosing) return
        isClosing = true
        isVisible = false
        scope.launch {
            delay(MotionDurationDefaultMillis.toLong())
            if (hasRotatedPhoto) currentOnPhotoRotated()
            onDismiss()
        }
    }

    fun deleteAnimated(photo: MapMoment) {
        if (isClosing) return
        isClosing = true
        isVisible = false
        scope.launch {
            deletePhotoSheetState.hide()
            showDeletePhotoSheet = false
            delay(MotionDurationDefaultMillis.toLong())
            currentOnPhotoDeleted(photo)
            if (hasRotatedPhoto) currentOnPhotoRotated()
            onDismiss()
        }
    }

    fun savePhoto() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                context.savePhotoToGallery(File(currentPhoto.payload))
            }
            if (!saved) {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Foto konnte nicht gespeichert werden.",
                )
            }
        }
    }

    fun sharePhoto() {
        val shared = context.sharePhoto(File(currentPhoto.payload))
        if (!shared) {
            showFeedbackNotice(
                FeedbackNoticeKind.ERROR,
                "Das Foto konnte nicht geteilt werden.",
            )
        }
    }

    fun rotatePhotoLeft() {
        val photo = File(currentPhoto.payload)
        scope.launch {
            val rotated = withContext(Dispatchers.IO) {
                rotationMutex.withLock { rotatePhotoLeftAndSave(photo) }
            }
            if (rotated) {
                imageRevision++
                hasRotatedPhoto = true
            } else {
                showFeedbackNotice(
                    FeedbackNoticeKind.ERROR,
                    "Das Foto konnte nicht gedreht werden.",
                )
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            savePhoto()
        } else {
            showFeedbackNotice(
                FeedbackNoticeKind.PERMISSION,
                "Zum Speichern braucht Spur Zugriff auf deine Bilder.",
            )
        }
    }
    val requestSavePhoto: () -> Unit = {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            savePhoto()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
        if (openOrigin == null) {
            delay(MotionDurationDefaultMillis.toLong())
            openingAnimationFinished = true
        }
    }

    LaunchedEffect(openOrigin) {
        if (openOrigin == null) return@LaunchedEffect
        val photo = File(photos[initialPage].payload)
        val (aspectRatio, thumbnail) = withContext(Dispatchers.IO) {
            photoAspectRatio(photo) to decodeMarkerPhoto(photo.absolutePath)?.asImageBitmap()
        }
        openingPhotoAspectRatio = aspectRatio
        openingThumbnail = thumbnail
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MotionDurationDefaultMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        openingAnimationFinished = true
    }

    LaunchedEffect(pagerState, photos) {
        var lastPage = initialPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastPage) {
                photos.getOrNull(page)?.let(currentOnPhotoChanged)
                lastPage = page
            }
        }
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(
            enabled = !isClosing && !showPhotoActionsSheet && !showDeletePhotoSheet,
            onBack = ::dismissAnimated,
        )
        DarkMediaSystemBars()
        AnimatedVisibility(
            visible = isVisible,
            enter = if (openOrigin == null) {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = MotionDurationDefaultMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = MotionDurationDefaultMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { height -> height / 10 },
                )
            } else {
                EnterTransition.None
            },
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = MotionDurationDefaultMillis,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = MotionDurationDefaultMillis,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetY = { height -> height / 10 },
            ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val progress = openProgress.value
                val detailAlpha = if (openOrigin == null) {
                    1f
                } else {
                    if (progress >= 1f) 1f else 0f
                }
                val selectedImageKey = "${selectedPhoto.id}:$imageRevision"
                val controlsVisible =
                    openingAnimationFinished &&
                        resolvedImageKeys[selectedImageKey] == true &&
                        !isClosing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = progress)),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = detailAlpha },
                    key = { photos[it].id },
                    beyondViewportPageCount = 1,
                ) { page ->
                    val photo = photos[page]
                    val imageKey = "${photo.id}:$imageRevision"
                    val imageRequest = remember(photo.payload, imageRevision, openOrigin) {
                        ImageRequest.Builder(context)
                            .data(File(photo.payload))
                            .memoryCacheKey("${photo.payload}:$imageRevision")
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .listener(
                                onError = { _, _ -> resolvedImageKeys[imageKey] = true },
                                onSuccess = { _, _ -> resolvedImageKeys[imageKey] = true },
                            )
                            .let { builder ->
                                if (openOrigin == null) {
                                    builder.crossfade(MotionDurationDefaultMillis)
                                } else {
                                    builder
                                }
                            }
                            .build()
                    }
                    ZoomableAsyncImage(
                        model = imageRequest,
                        contentDescription = if (page == pagerState.currentPage) {
                            "Foto ${page + 1} von ${photos.size}"
                        } else {
                            null
                        },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val thumbnail = openingThumbnail
                if (openOrigin != null && thumbnail != null && progress < 1f) {
                    val density = LocalDensity.current
                    val availableWidth = constraints.maxWidth.toFloat()
                    val availableHeight = constraints.maxHeight.toFloat()
                    val targetWidth = minOf(
                        availableWidth,
                        availableHeight * openingPhotoAspectRatio,
                    )
                    val targetHeight = targetWidth / openingPhotoAspectRatio
                    val sourceSize = with(density) { 40.dp.toPx() }
                    val targetLeft = (availableWidth - targetWidth) / 2f
                    val targetTop = (availableHeight - targetHeight) / 2f
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    targetLeft.roundToInt(),
                                    targetTop.roundToInt(),
                                )
                            }
                            .size(
                                with(density) { targetWidth.toDp() },
                                with(density) { targetHeight.toDp() },
                            )
                            .graphicsLayer {
                                scaleX = sourceSize / targetWidth +
                                    (1f - sourceSize / targetWidth) * progress
                                scaleY = sourceSize / targetHeight +
                                    (1f - sourceSize / targetHeight) * progress
                                translationX =
                                    (openOrigin.x - availableWidth / 2f) * (1f - progress)
                                translationY =
                                    (openOrigin.y - availableHeight / 2f) * (1f - progress)
                            }
                            .clip(RoundedCornerShape(7.dp)),
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier
                        .fillMaxSize(),
                    enter = fadeIn(tween(MotionDurationDefaultMillis)),
                    exit = fadeOut(tween(MotionDurationDefaultMillis / 2)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PhotoLocationMetadata(
                            photo = selectedPhoto,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 18.dp, end = 18.dp),
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(MapControlGap),
                        ) {
                            PhotoActionButton(
                                contentDescription = "Bildaktionen öffnen",
                                onClick = { showPhotoActionsSheet = true },
                            ) {
                                PhotoMoreIcon()
                            }
                            PhotoActionButton(
                                contentDescription = "Foto 90 Grad nach links drehen",
                                onClick = ::rotatePhotoLeft,
                            ) {
                                PhotoRotateLeftIcon()
                            }
                        }
                        PhotoActionButton(
                            contentDescription = "Foto schließen",
                            onClick = ::dismissAnimated,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(16.dp),
                        ) {
                            PhotoCloseIcon()
                        }
                    }
                }
            }
        }
    }

    if (showPhotoActionsSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showPhotoActionsSheet = false },
            sheetState = photoActionsSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                BottomSheetHeader(
                    title = "Bildaktionen",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SheetMenuItem(
                    label = "Teilen",
                    trailing = false,
                    leading = { ShareIcon() },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            sharePhoto()
                        }
                    },
                )
                SheetMenuItem(
                    label = "In Galerie speichern",
                    trailing = false,
                    leading = { PhotoDownloadIcon() },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            requestSavePhoto()
                        }
                    },
                )
                SheetMenuItem(
                    label = "Bild löschen",
                    destructive = true,
                    trailing = false,
                    leading = { PhotoDeleteIcon(color = StopRed) },
                    onClick = {
                        scope.launch {
                            photoActionsSheetState.hide()
                            showPhotoActionsSheet = false
                            showDeletePhotoSheet = true
                        }
                    },
                )
            }
        }
    }

    if (showDeletePhotoSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showDeletePhotoSheet = false },
            sheetState = deletePhotoSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomSheetHeader(title = "Bild löschen")
                Text(
                    text = "Das Bild wird dauerhaft aus Spur und vom Gerät entfernt.",
                    color = Ink.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { deleteAnimated(currentPhoto) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StopRed,
                        contentColor = Color.White,
                    ),
                    shape = CircleShape,
                ) {
                    Text(
                        text = "Bild endgültig löschen",
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = { showDeletePhotoSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abbrechen", color = Ink)
                }
            }
        }
    }
}

@Composable
private fun PhotoActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = ImageDetailControlBackground,
            contentColor = ImageDetailControlForeground,
        ),
        content = {
            CompositionLocalProvider(
                LocalLucideStrokeWidth provides LucideBoldStrokeWidth,
                content = content,
            )
        },
    )
}

@Composable
private fun PhotoDownloadIcon() = LucideIcon(
    paths = listOf(
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
        "M12 15V3",
    ),
)

@Composable
private fun PhotoMoreIcon() = LucideIcon(
    paths = listOf(
        "M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
        "M19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
        "M5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2",
    ),
)

@Composable
private fun PhotoDeleteIcon(color: Color = LocalContentColor.current) = LucideIcon(
    paths = listOf(
        "M3 6h18",
        "M8 6V4h8v2",
        "M19 6l-1 14H6L5 6",
        "M10 11v5",
        "M14 11v5",
    ),
    color = color,
)

@Composable
private fun PhotoRotateLeftIcon() = LucideIcon(
    paths = listOf(
        "M3 12a9 9 0 1 0 3-6.7L3 8",
        "M3 3v5h5",
    ),
)

@Composable
private fun PhotoCloseIcon() = LucideIcon(
    paths = listOf("M18 6 6 18", "m6 6 12 12"),
)

private fun photoCaptureLabel(photo: MapMoment): String {
    val capturedAt = photo.captureTimeMillis()
        ?: File(photo.payload).lastModified().takeIf { it > 0L }
        ?: return "Aufnahmezeit unbekannt"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(capturedAt))
}

@Composable
private fun PhotoLocationMetadata(
    photo: MapMoment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var place by remember(photo.id) {
        mutableStateOf(context.loadPhotoPlace(photo.id))
    }
    LaunchedEffect(photo.id) {
        if (place != null) return@LaunchedEffect
        context.reverseGeocode(photo.latitude, photo.longitude)?.let { resolved ->
            context.savePhotoPlace(photo.id, resolved)
            place = resolved
        }
    }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoMapPreview(photo = photo)
        Column(
            modifier = Modifier
                .widthIn(max = 228.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = photoCaptureLabel(photo),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            place?.let { description ->
                Text(
                    text = description.replaceFirst(", ", "\n"),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun PhotoMapPreview(
    photo: MapMoment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewPixels = with(LocalDensity.current) {
        PhotoMapPreviewSize.roundToPx()
    }
    var preview by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(photo.id, previewPixels) {
        MapLibre.getInstance(context)
        var disposed = false
        val options = MapSnapshotter.Options(previewPixels, previewPixels)
            .withCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(photo.latitude, photo.longitude))
                    .zoom(PhotoMapPreviewZoom)
                    .build(),
            )
            .withPixelRatio(1f)
            .withLogo(false)
            .withStyleBuilder(Style.Builder().fromUri(StreetMapStyle))
        val snapshotter = MapSnapshotter(context, options)
        snapshotter.start(
            { snapshot ->
                if (!disposed) preview = snapshot.bitmap.asImageBitmap()
            },
            { _ -> },
        )
        onDispose {
            disposed = true
            snapshotter.cancel()
        }
    }

    Box(
        modifier = modifier
            .size(PhotoMapPreviewSize)
            .background(Mist)
            .semantics { contentDescription = "Karte des Aufnahmeorts" },
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        FilledMapPinAtCenter(
            color = MapPinRed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private suspend fun Context.reverseGeocode(
    latitude: Double,
    longitude: Double,
): String? {
    if (
        !Geocoder.isPresent() ||
        latitude !in -90.0..90.0 ||
        longitude !in -180.0..180.0
    ) {
        return null
    }
    val geocoder = Geocoder(applicationContext, Locale.getDefault())
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses.firstOrNull())
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    } else {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.IO) {
            runCatching {
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }.getOrNull()
        }
    }
    return address?.let {
        shortPlaceDescription(
            thoroughfare = it.thoroughfare,
            streetNumber = it.subThoroughfare,
            district = it.subLocality,
            locality = it.locality,
            region = it.adminArea,
            featureName = it.featureName,
        )
    }
}

internal fun shortPlaceDescription(
    thoroughfare: String?,
    streetNumber: String?,
    district: String?,
    locality: String?,
    region: String?,
    featureName: String?,
): String? {
    val street = listOfNotNull(thoroughfare, streetNumber)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" ")
        .ifEmpty { null }
    val area = listOf(district, locality, region)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
    val fallback = featureName?.trim()?.takeIf(String::isNotEmpty)
    return listOfNotNull(street ?: fallback, area)
        .distinct()
        .take(2)
        .joinToString(", ")
        .ifEmpty { null }
}

private fun Context.sharePhoto(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val uri = FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        source,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Spur Foto", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, "Foto teilen"))
    true
}.getOrDefault(false)

private fun rotatePhotoLeftAndSave(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val originalLastModified = source.lastModified()
    val exif = android.media.ExifInterface(source.absolutePath)
    val orientation = exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL,
    )
    exif.setAttribute(
        android.media.ExifInterface.TAG_ORIENTATION,
        exifOrientationAfterLeftRotation(orientation).toString(),
    )
    exif.saveAttributes()
    if (originalLastModified > 0L) source.setLastModified(originalLastModified)
    true
}.getOrDefault(false)

internal fun exifOrientationAfterLeftRotation(orientation: Int): Int =
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_NORMAL ->
            android.media.ExifInterface.ORIENTATION_ROTATE_270
        android.media.ExifInterface.ORIENTATION_ROTATE_270 ->
            android.media.ExifInterface.ORIENTATION_ROTATE_180
        android.media.ExifInterface.ORIENTATION_ROTATE_180 ->
            android.media.ExifInterface.ORIENTATION_ROTATE_90
        android.media.ExifInterface.ORIENTATION_ROTATE_90 ->
            android.media.ExifInterface.ORIENTATION_NORMAL
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            android.media.ExifInterface.ORIENTATION_TRANSPOSE
        android.media.ExifInterface.ORIENTATION_TRANSPOSE ->
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            android.media.ExifInterface.ORIENTATION_TRANSVERSE
        android.media.ExifInterface.ORIENTATION_TRANSVERSE ->
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        else -> android.media.ExifInterface.ORIENTATION_ROTATE_270
    }

private fun photoAspectRatio(photo: File): Float {
    val options = android.graphics.BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    android.graphics.BitmapFactory.decodeFile(photo.absolutePath, options)
    val orientation = runCatching {
        android.media.ExifInterface(photo.absolutePath).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
    return orientedPhotoAspectRatio(options.outWidth, options.outHeight, orientation)
}

internal fun orientedPhotoAspectRatio(width: Int, height: Int, orientation: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val swapsDimensions = when (orientation) {
        android.media.ExifInterface.ORIENTATION_TRANSPOSE,
        android.media.ExifInterface.ORIENTATION_ROTATE_90,
        android.media.ExifInterface.ORIENTATION_TRANSVERSE,
        android.media.ExifInterface.ORIENTATION_ROTATE_270,
        -> true
        else -> false
    }
    return if (swapsDimensions) {
        height.toFloat() / width
    } else {
        width.toFloat() / height
    }
}

@Suppress("DEPRECATION")
private fun Context.savePhotoToGallery(source: File): Boolean = runCatching {
    if (!source.isFile) return false
    val resolver = contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Spur",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open gallery entry")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    } else {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Spur",
        ).apply { mkdirs() }
        val destination = File(directory, source.name)
        source.copyTo(destination, overwrite = true)
        resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, destination.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, destination.absolutePath)
            },
        )
    }
    true
}.getOrDefault(false)

private fun shareActiveTour(context: Context) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Meine Tour mit Spur läuft gerade.")
    }
    context.startActivity(Intent.createChooser(share, "Tour teilen"))
}

private fun setMapStyle(
    context: Context,
    map: MapLibreMap,
    satellite: Boolean,
    centerOnLocation: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    routePoints: List<TrackPoint>,
    trailColors: TrailColors,
    onLoaded: () -> Unit,
) {
    val cameraPosition = map.cameraPosition
    val styleLoaded: (Style) -> Unit = { style ->
        style.hideDistractingPoiLayers()
        style.showOutlinedBuildings()
        enableLocationTracking(
            context = context,
            map = map,
            style = style,
            centerOnLocation = centerOnLocation,
            manualLocation = manualLocation,
            initialMapZoom = initialMapZoom,
            defaultMapBearing = defaultMapBearing,
            pulseColor = trailColors.fill,
        )
        style.showTourRoute(routePoints, trailColors)
        if (!centerOnLocation) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
        onLoaded()
    }

    if (satellite) {
        map.setStyle(
            satelliteStyleBuilder(),
            styleLoaded,
        )
    } else {
        map.setStyle(StreetMapStyle, styleLoaded)
    }
}

private fun Style.hideDistractingPoiLayers() {
    layers
        .filterIsInstance<SymbolLayer>()
        .filter { it.sourceLayer == MapPoiSourceLayer }
        .forEach { it.setProperties(visibility(Property.NONE)) }
}

private fun Style.showOutlinedBuildings() {
    getLayerAs<FillLayer>(MapBuildingLayer)?.setMaxZoom(MapBuildingMaxZoom)
    getLayer(MapBuilding3dLayer)?.setProperties(visibility(Property.NONE))
}

private data class PreparedMapMoments(
    val moments: List<MapMoment>,
    val images: HashMap<String, android.graphics.Bitmap>,
    val features: List<Feature>,
)

private fun prepareMapMoments(
    context: Context,
    moments: List<MapMoment>,
): PreparedMapMoments {
    val images = HashMap<String, android.graphics.Bitmap>(moments.size * 3)
    val features = moments.mapIndexed { index, moment ->
        val imageId = MapMomentImagePrefix + moment.id
        val marker = createMomentMarkerBitmap(context, moment, selected = false)
        images[imageId] = marker
        listOf(2, 3).forEach { stackSize ->
            images[clusterMomentImageId(moment, stackSize)] =
                createMomentClusterBitmap(context, marker, stackSize)
        }
        Feature.fromGeometry(
            Point.fromLngLat(moment.longitude, moment.latitude),
        ).apply {
            addStringProperty(MapMomentIdProperty, moment.id)
            addStringProperty(MapMomentImageProperty, imageId)
            addNumberProperty(MapMomentRepresentativeProperty, index)
        }
    }
    return PreparedMapMoments(
        moments = moments,
        images = images,
        features = features,
    )
}

private fun Style.showMapMoments(prepared: PreparedMapMoments) {
    val moments = prepared.moments
    val images = prepared.images
    val features = prepared.features
    if (images.isNotEmpty()) addImages(images)

    val source = getSourceAs<GeoJsonSource>(MapMomentSource)
        ?: GeoJsonSource(
            MapMomentSource,
            GeoJsonOptions()
                .withMaxZoom(20)
                .withCluster(true)
                .withClusterMaxZoom(MapMomentClusterMaxZoom)
                .withClusterRadius(MapMomentClusterRadius)
                .withClusterProperty(
                    MapMomentRepresentativeProperty,
                    Expression.max(
                        Expression.accumulated(),
                        Expression.get(MapMomentRepresentativeProperty),
                    ),
                    Expression.get(MapMomentRepresentativeProperty),
                ),
        ).also(::addSource)
    source.setGeoJson(FeatureCollection.fromFeatures(features))

    val momentOffset = momentOffsetExpression(moments)
    val momentLayer = getLayerAs<SymbolLayer>(MapMomentLayer)
    if (momentLayer == null) {
        addLayer(
            SymbolLayer(MapMomentLayer, MapMomentSource)
                .withFilter(
                    Expression.neq(Expression.get("cluster"), true),
                )
                .withProperties(
                    iconImage(Expression.get(MapMomentImageProperty)),
                    iconOffset(momentOffset),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        momentLayer.setProperties(iconOffset(momentOffset))
    }

    val clusterImage = clusterMomentImageExpression(moments)
    val clusterLayer = getLayerAs<SymbolLayer>(MapMomentClusterLayer)
    if (clusterLayer == null) {
        addLayer(
            SymbolLayer(MapMomentClusterLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    iconImage(clusterImage),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        clusterLayer.setProperties(iconImage(clusterImage))
    }

    if (getLayer(MapMomentClusterCountBadgeLayer) == null) {
        val countBadgeLayer =
            CircleLayer(MapMomentClusterCountBadgeLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    circleRadius(MapMomentClusterCountBadgeRadius),
                    circleColor(Ink.toArgb()),
                    circleTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX,
                            MapMomentClusterCountPositionY,
                        ),
                    ),
                    circleTranslateAnchor(Property.CIRCLE_TRANSLATE_ANCHOR_VIEWPORT),
                )
        if (getLayer(MapMomentClusterCountLayer) == null) {
            addLayer(countBadgeLayer)
        } else {
            addLayerBelow(countBadgeLayer, MapMomentClusterCountLayer)
        }
    }

    if (getLayer(MapMomentClusterCountLayer) == null) {
        addLayer(
            SymbolLayer(MapMomentClusterCountLayer, MapMomentSource)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(13f),
                    textColor(android.graphics.Color.WHITE),
                    textTranslate(
                        arrayOf(
                            MapMomentClusterCountPositionX,
                            MapMomentClusterCountPositionY,
                        ),
                    ),
                    textTranslateAnchor(Property.TEXT_TRANSLATE_ANCHOR_VIEWPORT),
                    textAnchor(Property.TEXT_ANCHOR_CENTER),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    }
}

private fun clusterMomentImageExpression(moments: List<MapMoment>): Expression =
    Expression.switchCase(
        Expression.eq(
            Expression.toNumber(Expression.get("point_count")),
            Expression.literal(2),
        ),
        representativeClusterImageExpression(moments, 2),
        representativeClusterImageExpression(moments, 3),
    )

private fun representativeClusterImageExpression(
    moments: List<MapMoment>,
    stackSize: Int,
): Expression {
    val fallback = moments.firstOrNull()?.let {
        clusterMomentImageId(it, stackSize)
    }.orEmpty()
    val stops = moments.mapIndexed { index, moment ->
        Expression.stop(index, clusterMomentImageId(moment, stackSize))
    }.toTypedArray()
    return Expression.match(
        Expression.toNumber(Expression.get(MapMomentRepresentativeProperty)),
        Expression.literal(fallback),
        *stops,
    )
}

private fun clusterMomentImageId(moment: MapMoment, stackSize: Int): String =
    "$MapMomentClusterImagePrefix$stackSize-${moment.id}"

private fun momentOffsetExpression(moments: List<MapMoment>): Expression {
    val offsets = overlappingMomentOffsets(moments)
    val center = Expression.literal(arrayOf(0f, 0f))
    if (offsets.isEmpty()) return center
    val stops = offsets.map { (momentId, offset) ->
        Expression.stop(momentId, Expression.literal(arrayOf(offset.x, offset.y)))
    }.toTypedArray()
    return Expression.match(
        Expression.get(MapMomentIdProperty),
        center,
        *stops,
    )
}

private fun Style.showTourRoute(
    points: List<TrackPoint>,
    colors: TrailColors,
) {
    showTourRoute(tourRouteFeature(points), colors)
}

internal fun tourRouteFeature(points: List<TrackPoint>): Feature? =
    if (points.size >= 2) {
        Feature.fromGeometry(
            LineString.fromLngLats(
                points.map { Point.fromLngLat(it.longitude, it.latitude) },
            ),
        )
    } else {
        null
    }

private fun Style.showTourRoute(
    route: Feature?,
    colors: TrailColors,
) {
    val source = getSourceAs<GeoJsonSource>(TourRouteSource)
        ?: GeoJsonSource(TourRouteSource).also(::addSource)
    val borderLayer = getLayerAs<LineLayer>(TourRouteBorderLayer)
    if (borderLayer == null) {
        val borderLayer = LineLayer(TourRouteBorderLayer, TourRouteSource).withProperties(
            lineColor(colors.stroke.toArgb()),
            lineWidth(TourRouteBorderWidthPixels),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (getLayer(TourRouteLayer) == null) {
            addLayer(borderLayer)
        } else {
            addLayerBelow(borderLayer, TourRouteLayer)
        }
    } else {
        borderLayer.setProperties(lineColor(colors.stroke.toArgb()))
    }
    val routeLayer = getLayerAs<LineLayer>(TourRouteLayer)
    if (routeLayer == null) {
        addLayer(
            LineLayer(TourRouteLayer, TourRouteSource).withProperties(
                lineColor(colors.fill.toArgb()),
                lineWidth(TourRouteWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    } else {
        routeLayer.setProperties(lineColor(colors.fill.toArgb()))
    }
    if (route != null) {
        source.setGeoJson(route)
    } else {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    }
}

private fun Style.showSelectedTrackPoint(point: TrackPoint?) {
    val source = getSourceAs<GeoJsonSource>(SelectedTrackPointSource)
        ?: GeoJsonSource(SelectedTrackPointSource).also(::addSource)
    if (getLayer(SelectedTrackPointLayer) == null) {
        val layer =
            CircleLayer(SelectedTrackPointLayer, SelectedTrackPointSource).withProperties(
                circleColor("#18201C"),
                circleRadius(7f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            )
        if (getLayer(TourEndpointRingLayer) == null) {
            addLayer(layer)
        } else {
            addLayerBelow(layer, TourEndpointRingLayer)
        }
    }
    if (point == null) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)),
        )
    }
}

private fun Style.showTourEndpoints(
    points: List<TrackPoint>,
    colors: TrailColors,
) {
    val source = getSourceAs<GeoJsonSource>(TourEndpointSource)
        ?: GeoJsonSource(TourEndpointSource).also(::addSource)
    val ringLayer = getLayerAs<CircleLayer>(TourEndpointRingLayer)
    if (ringLayer == null) {
        addLayer(
            CircleLayer(TourEndpointRingLayer, TourEndpointSource).withProperties(
                circleColor(colors.fill.toArgb()),
                circleRadius(TourEndpointRadius),
                circleStrokeColor(colors.stroke.toArgb()),
                circleStrokeWidth(TourEndpointStrokeWidth),
            ),
        )
    } else {
        ringLayer.setProperties(
            circleColor(colors.fill.toArgb()),
            circleRadius(TourEndpointRadius),
            circleStrokeColor(colors.stroke.toArgb()),
            circleStrokeWidth(TourEndpointStrokeWidth),
        )
    }
    val endLayer = getLayerAs<CircleLayer>(TourEndpointEndLayer)
    if (endLayer == null) {
        addLayer(
            CircleLayer(TourEndpointEndLayer, TourEndpointSource)
                .withFilter(
                    Expression.eq(
                        Expression.get(TourEndpointTypeProperty),
                        Expression.literal(TourEndpointEnd),
                    ),
                )
                .withProperties(
                    circleColor(colors.stroke.toArgb()),
                    circleRadius(TourEndpointEndRadius),
                ),
        )
    } else {
        endLayer.setProperties(
            circleColor(colors.stroke.toArgb()),
            circleRadius(TourEndpointEndRadius),
        )
    }
    source.setGeoJson(tourEndpointFeatures(points))
}

internal fun tourEndpointFeatures(points: List<TrackPoint>): FeatureCollection {
    val endpoints = buildList {
        points.firstOrNull()?.let { point ->
            add(
                Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude),
                ),
            )
        }
        points.lastOrNull()?.let { point ->
            add(
                Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude),
                ).apply {
                    addStringProperty(TourEndpointTypeProperty, TourEndpointEnd)
                },
            )
        }
    }
    return FeatureCollection.fromFeatures(endpoints)
}

private fun satelliteStyleBuilder(): Style.Builder {
    return Style.Builder().fromJson(SatelliteMapStyleJson)
}

@SuppressLint("MissingPermission")
private fun enableLocationTracking(
    context: Context,
    map: MapLibreMap,
    style: Style,
    centerOnLocation: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    pulseColor: Color,
) {
    if (!context.hasLocationPermission()) return

    val locationComponent = map.locationComponent
    val options = LocationComponentOptions.builder(context)
        .spurLocationAppearance(pulseColor)
        .build()
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build(),
    )
    locationComponent.isLocationComponentEnabled = manualLocation == null
    locationComponent.renderMode = RenderMode.NORMAL
    locationComponent.cameraMode = CameraMode.NONE
    style.showCurrentLocationFootprints(
        context = context,
        visible = manualLocation == null,
    )

    val location = map.currentSpurCoordinate(
        context = context,
        manual = manualLocation,
    )
    if (centerOnLocation && location != null) {
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(initialMapZoom)
                    .bearing(defaultMapBearing)
                    .build(),
            ),
        )
    }
}

private fun MapLibreMap.restartLocationPulse(color: Color) {
    val component = locationComponent
    if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
    component.applyStyle(
        component.locationComponentOptions
            .toBuilder()
            .spurLocationAppearance(color)
            .build(),
    )
}

private fun LocationComponentOptions.Builder.spurLocationAppearance(
    color: Color,
): LocationComponentOptions.Builder =
    foregroundTintColor(color.toArgb())
        .backgroundTintColor(color.toArgb())
        .foregroundStaleTintColor(color.toArgb())
        .backgroundStaleTintColor(color.toArgb())
        .bearingTintColor(color.toArgb())
        .accuracyColor(color.toArgb())
        .pulseEnabled(true)
        .pulseFadeEnabled(true)
        .pulseColor(Color.White.toArgb())
        .pulseSingleDuration(LocationPulseDurationMillis.toFloat())
        .pulseMaxRadius(LocationPulseMaxRadius)
        .pulseAlpha(LocationPulseAlpha)
        .pulseInterpolator(AccelerateDecelerateInterpolator())

private fun Style.showCurrentLocationFootprints(
    context: Context,
    visible: Boolean,
) {
    context.currentLocationFootprintsBitmap()?.let {
        addImage(CurrentLocationFootprintsImage, it)
    }
    val layer = getLayerAs<SymbolLayer>(CurrentLocationFootprintsLayer)
    if (layer == null) {
        addLayerAbove(
            SymbolLayer(
                CurrentLocationFootprintsLayer,
                LocationComponentConstants.LOCATION_SOURCE,
            ).withProperties(
                iconImage(CurrentLocationFootprintsImage),
                iconAnchor(Property.ICON_ANCHOR_CENTER),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                iconTranslate(arrayOf(0f, -CurrentLocationFootprintsLiftPixels)),
                iconTranslateAnchor(Property.ICON_TRANSLATE_ANCHOR_VIEWPORT),
                visibility(
                    if (visible) Property.VISIBLE else Property.NONE,
                ),
            ),
            LocationComponentConstants.FOREGROUND_LAYER,
        )
    } else {
        layer.setProperties(
            visibility(
                if (visible) Property.VISIBLE else Property.NONE,
            ),
        )
    }
}

private fun Context.currentLocationFootprintsBitmap(): android.graphics.Bitmap? {
    val halo = ContextCompat.getDrawable(
        this,
        R.drawable.ic_footprints_location_halo,
    )?.mutate() ?: return null
    val footprints = ContextCompat.getDrawable(
        this,
        R.drawable.ic_footprints_location,
    )?.mutate() ?: return null
    val width = maxOf(halo.intrinsicWidth, footprints.intrinsicWidth)
    val height = maxOf(halo.intrinsicHeight, footprints.intrinsicHeight)
    return android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        halo.setTint(Color.White.toArgb())
        halo.setBounds(0, 0, width, height)
        halo.draw(canvas)
        footprints.setTint(Ink.toArgb())
        footprints.setBounds(0, 0, width, height)
        footprints.draw(canvas)
    }
}

private fun MapLibreMap.followLocation(
    context: Context,
    manualLocation: SpurCoordinate?,
    transitionDuration: Long,
    defaultMapBearing: Double,
) {
    if (manualLocation == null && locationComponent.isLocationComponentActivated) {
        locationComponent.setCameraMode(
            CameraMode.TRACKING,
            transitionDuration,
            cameraPosition.zoom,
            defaultMapBearing,
            null,
            null,
        )
        return
    }

    val location = currentSpurCoordinate(context = context, manual = manualLocation) ?: return
    val update = CameraUpdateFactory.newCameraPosition(
        org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
            .target(LatLng(location.latitude, location.longitude))
            .bearing(defaultMapBearing)
            .build(),
    )
    if (transitionDuration == 0L) {
        moveCamera(update)
    } else {
        animateCamera(update, transitionDuration.toInt())
    }
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.hasAudioRecordingPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Context.createMomentFile(type: MomentType): File {
    val (directoryName, extension) = when (type) {
        MomentType.PHOTO -> "photos" to "jpg"
        MomentType.VIDEO -> "videos" to "mp4"
        MomentType.VOICE -> "voice" to "m4a"
        MomentType.EMOJI -> error("Emoji moments do not use files")
    }
    val directory = File(filesDir, "moments/$directoryName").apply { mkdirs() }
    return File(directory, "${type.name.lowercase()}-${System.currentTimeMillis()}.$extension")
}

private fun Context.momentContentUri(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

private fun Location.toSpurCoordinate() =
    SpurCoordinate(latitude = latitude, longitude = longitude)

@SuppressLint("MissingPermission")
private fun MapLibreMap.currentSpurCoordinate(
    context: Context,
    manual: SpurCoordinate?,
): SpurCoordinate? {
    if (manual != null) return manual
    val gps = if (locationComponent.isLocationComponentActivated) {
        locationComponent.lastKnownLocation
    } else {
        null
    } ?: context.bestLastKnownLocation()
    return resolveSpurCoordinate(
        manual = null,
        gps = gps?.toSpurCoordinate(),
    )
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.showGpsLocationPuck(
    context: Context,
    show: Boolean,
) {
    if (!context.hasLocationPermission() || !locationComponent.isLocationComponentActivated) return
    locationComponent.isLocationComponentEnabled = show
    style?.getLayer(CurrentLocationFootprintsLayer)?.setProperties(
        visibility(
            if (show) Property.VISIBLE else Property.NONE,
        ),
    )
}

@SuppressLint("MissingPermission")
private fun Context.bestLastKnownLocation(): Location? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)
}

private const val ManualLocationPreferences = "manual-location"
private const val ManualLatitude = "latitude"
private const val ManualLongitude = "longitude"

internal fun Context.loadManualLocation(): SpurCoordinate? {
    val preferences = getSharedPreferences(ManualLocationPreferences, Context.MODE_PRIVATE)
    if (!preferences.contains(ManualLatitude) || !preferences.contains(ManualLongitude)) {
        return null
    }
    return SpurCoordinate(
        latitude = Double.fromBits(preferences.getLong(ManualLatitude, 0L)),
        longitude = Double.fromBits(preferences.getLong(ManualLongitude, 0L)),
    )
}

private fun Context.saveManualLocation(location: SpurCoordinate?) {
    getSharedPreferences(ManualLocationPreferences, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (location == null) {
                remove(ManualLatitude)
                remove(ManualLongitude)
            } else {
                putLong(ManualLatitude, location.latitude.toBits())
                putLong(ManualLongitude, location.longitude.toBits())
            }
        }
        .apply()
}

private const val MapSettingsPreferences = "map-settings"
private const val DefaultZoomPreference = "default-zoom"
private const val DefaultRotationPreference = "default-rotation"
private const val MapControlColorPreference = "map-control-color"
private const val MapControlForegroundColorPreference = "map-control-foreground-color"
private const val TrailFillColorPreference = "trail-fill-color"
private const val TrailStrokeColorPreference = "trail-stroke-color"

private fun Context.loadDefaultMapZoom(): Double =
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .getFloat(DefaultZoomPreference, DefaultMapZoom.toFloat())
        .toDouble()

private fun Context.saveDefaultMapZoom(zoom: Double) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putFloat(DefaultZoomPreference, zoom.toFloat())
        .apply()
}

private fun Context.loadDefaultMapRotation(): MapRotation =
    mapRotationFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(DefaultRotationPreference, null),
    )

private fun Context.saveDefaultMapRotation(rotation: MapRotation) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(DefaultRotationPreference, rotation.name)
        .apply()
}

private fun Context.loadMapControlColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(MapControlColorPreference, null),
    )

private fun Context.saveMapControlColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(MapControlColorPreference, color.name)
        .apply()
}

private fun Context.loadMapControlForegroundColor(
    background: MapControlColor,
): MapControlColor {
    val stored = getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .getString(MapControlForegroundColorPreference, null)
    return stored
        ?.let(::mapControlColorFromStored)
        ?: defaultMapControlForeground(background)
}

private fun Context.saveMapControlForegroundColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(MapControlForegroundColorPreference, color.name)
        .apply()
}

private fun Context.loadTrailFillColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(TrailFillColorPreference, null),
        fallback = MapControlColor.YELLOW,
    )

private fun Context.saveTrailFillColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(TrailFillColorPreference, color.name)
        .apply()
}

private fun Context.loadTrailStrokeColor(): MapControlColor =
    mapControlColorFromStored(
        getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
            .getString(TrailStrokeColorPreference, null),
        fallback = MapControlColor.BLACK,
    )

private fun Context.saveTrailStrokeColor(color: MapControlColor) {
    getSharedPreferences(MapSettingsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(TrailStrokeColorPreference, color.name)
        .apply()
}

private fun Context.loadTrailColors(): TrailColors =
    TrailColors(
        fill = loadTrailFillColor().color,
        stroke = loadTrailStrokeColor().color.copy(alpha = TrailStrokeAlpha),
    )

private const val MapMomentPreferences = "map-moments"
private const val MapMomentEntries = "entries"
private const val PhotoPlacePreferences = "photo-places"

private fun Context.loadMapMoments(): List<MapMoment> =
    getSharedPreferences(MapMomentPreferences, Context.MODE_PRIVATE)
        .getStringSet(MapMomentEntries, emptySet())
        .orEmpty()
        .mapNotNull(::decodeMapMoment)
        .filter { it.type == MomentType.EMOJI || File(it.payload).isFile }

private fun Context.saveMapMoments(moments: List<MapMoment>) {
    getSharedPreferences(MapMomentPreferences, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(MapMomentEntries, moments.map(::encodeMapMoment).toSet())
        .apply()
}

private suspend fun Context.deleteMapMoment(
    moment: MapMoment,
    moments: List<MapMoment>,
): List<MapMoment>? = withContext(Dispatchers.IO) {
    val allowedDirectory = when (moment.type) {
        MomentType.PHOTO -> "photos"
        MomentType.VIDEO -> "videos"
        MomentType.VOICE -> "voice"
        MomentType.EMOJI -> null
    }?.let { File(filesDir, "moments/$it").canonicalFile }
    if (allowedDirectory != null) {
        val mediaFile = File(moment.payload).canonicalFile
        if (mediaFile.parentFile != allowedDirectory) return@withContext null
        if (mediaFile.exists() && !mediaFile.delete()) return@withContext null
        if (moment.type == MomentType.VIDEO) {
            videoThumbnailFile(mediaFile).takeIf(File::exists)?.delete()
        }
    }
    val updatedMoments = moments.filterNot { it.id == moment.id }
    saveMapMoments(updatedMoments)
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .edit()
        .remove(moment.id)
        .apply()
    updatedMoments
}

private suspend fun Context.deleteStoredTour(
    store: TourStore,
    tourId: Long,
): Boolean = withContext(Dispatchers.IO) {
    val tour = store.tour(tourId) ?: return@withContext true
    var remainingMoments = loadMapMoments()
    for (moment in mapMomentsForTour(remainingMoments, tour)) {
        remainingMoments = deleteMapMoment(moment, remainingMoments)
            ?: return@withContext false
    }
    store.deleteTour(tourId)
    true
}

private fun Context.loadPhotoPlace(photoId: String): String? =
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .getString(photoId, null)

private fun Context.savePhotoPlace(photoId: String, place: String) {
    getSharedPreferences(PhotoPlacePreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(photoId, place)
        .apply()
}

private fun createMomentMarkerBitmap(
    context: Context,
    moment: MapMoment,
    selected: Boolean,
    voiceProgress: Float? = null,
) =
    android.graphics.Bitmap.createBitmap(
            (MomentMarkerWidth * context.resources.displayMetrics.density).toInt(),
            (MomentMarkerHeight * context.resources.displayMetrics.density).toInt(),
            android.graphics.Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            val scale = context.resources.displayMetrics.density
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = momentMarkerColor(moment.type).toArgb()
            paint.style = android.graphics.Paint.Style.FILL

            if (selected) {
                canvas.drawRoundRect(
                    3 * scale,
                    0f,
                    59 * scale,
                    57 * scale,
                    12 * scale,
                    12 * scale,
                    paint,
                )
            }

            val flag = android.graphics.RectF(
                6 * scale,
                2 * scale,
                56 * scale,
                52 * scale,
            )
            canvas.drawRoundRect(flag, 10 * scale, 10 * scale, paint)
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(26 * scale, 50 * scale)
                    lineTo(36 * scale, 50 * scale)
                    lineTo(31 * scale, 57 * scale)
                    close()
                },
                paint,
            )
            val content = android.graphics.RectF(flag).apply {
                inset(MomentMarkerStroke * scale, MomentMarkerStroke * scale)
            }
            if (moment.type == MomentType.VOICE && voiceProgress != null) {
                canvas.save()
                canvas.clipPath(
                    android.graphics.Path().apply {
                        addRoundRect(
                            flag,
                            10 * scale,
                            10 * scale,
                            android.graphics.Path.Direction.CW,
                        )
                    },
                )
                paint.color = Color.White.copy(alpha = 0.24f).toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(
                    flag.left,
                    flag.top,
                    flag.left + flag.width() * voiceProgress.coerceIn(0f, 1f),
                    flag.bottom,
                    paint,
                )
                canvas.restore()
            }

            val preview = when (moment.type) {
                MomentType.PHOTO -> decodeMarkerPhoto(moment.payload)
                MomentType.VIDEO -> ensureVideoThumbnail(File(moment.payload))
                    ?.let { decodeMarkerPhoto(it.absolutePath) }
                else -> null
            }
            if (preview != null) {
                val photoSide = (40 * scale).roundToInt().toFloat()
                val photoContentLeft =
                    (flag.centerX() - photoSide / 2f).roundToInt().toFloat()
                val photoContentTop =
                    (flag.centerY() - photoSide / 2f).roundToInt().toFloat()
                val photoContent = android.graphics.RectF(
                    photoContentLeft,
                    photoContentTop,
                    photoContentLeft + photoSide,
                    photoContentTop + photoSide,
                )
                drawMarkerPhoto(canvas, paint, photoContent, preview, scale)
                preview.recycle()
                if (moment.type == MomentType.VIDEO) {
                    drawVideoPlayOverlay(canvas, paint, photoContent, scale)
                }
            } else if (moment.type == MomentType.EMOJI) {
                paint.color = momentMarkerContentColor(moment.type).toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = 28 * scale
                canvas.drawText(moment.payload.ifBlank { "🙂" }, 31 * scale, 38 * scale, paint)
            } else {
                paint.color = momentMarkerContentColor(moment.type).toArgb()
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2 * scale
                drawMomentGlyph(canvas, paint, content, scale, moment.type)
            }

            val edge = createMomentMarkerEdgeBitmap(
                bitmap,
                MomentMarkerEdgeWidth * scale,
            )
            canvas.drawBitmap(edge, 0f, 0f, null)
            edge.recycle()
        }

private fun createMomentClusterBitmap(
    context: Context,
    marker: android.graphics.Bitmap,
    stackSize: Int,
): android.graphics.Bitmap {
    val scale = context.resources.displayMetrics.density
    return android.graphics.Bitmap.createBitmap(
        (70 * scale).toInt(),
        (66 * scale).toInt(),
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        clusterStackOffsets(stackSize).forEach { offset ->
            canvas.drawBitmap(marker, offset * scale, offset * scale, paint)
        }
    }
}

private fun createMomentMarkerEdgeBitmap(
    marker: android.graphics.Bitmap,
    edgeWidth: Float,
): android.graphics.Bitmap =
    android.graphics.Bitmap.createBitmap(
        marker.width,
        marker.height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { edge ->
        val canvas = android.graphics.Canvas(edge)
        val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(
                Color.White.toArgb(),
                android.graphics.PorterDuff.Mode.SRC_IN,
            )
        }
        val erasePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }
        for (horizontalDirection in -1..1) {
            for (verticalDirection in -1..1) {
                if (horizontalDirection != 0 || verticalDirection != 0) {
                    canvas.drawBitmap(
                        marker,
                        horizontalDirection * edgeWidth,
                        verticalDirection * edgeWidth,
                        whitePaint,
                    )
                }
            }
        }
        canvas.drawBitmap(marker, 0f, 0f, erasePaint)
    }

private fun decodeMarkerPhoto(path: String): android.graphics.Bitmap? =
    runCatching {
        val file = File(path)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(file),
            ) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val side = minOf(info.size.width, info.size.height)
                val scale = minOf(1f, 240f / side)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
            )
        }
    }.getOrNull()

private fun drawMarkerPhoto(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    photo: android.graphics.Bitmap,
    scale: Float,
) {
    val side = minOf(photo.width, photo.height)
    val source = android.graphics.Rect(
        (photo.width - side) / 2,
        (photo.height - side) / 2,
        (photo.width + side) / 2,
        (photo.height + side) / 2,
    )
    val clip = android.graphics.Path().apply {
        addRoundRect(destination, 5 * scale, 5 * scale, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clip)
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawBitmap(photo, source, destination, paint)
    canvas.restore()
}

private fun drawVideoPlayOverlay(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    scale: Float,
) {
    val centerX = destination.centerX()
    val centerY = destination.centerY()
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = Ink.copy(alpha = 0.62f).toArgb()
    canvas.drawCircle(centerX, centerY, 11 * scale, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(
        android.graphics.Path().apply {
            moveTo(centerX - 3.5f * scale, centerY - 6f * scale)
            lineTo(centerX + 6f * scale, centerY)
            lineTo(centerX - 3.5f * scale, centerY + 6f * scale)
            close()
        },
        paint,
    )
}

private fun drawMomentGlyph(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    destination: android.graphics.RectF,
    scale: Float,
    type: MomentType,
) {
    val paths = when (type) {
        MomentType.PHOTO -> MomentPhotoIconPaths
        MomentType.VIDEO -> MomentVideoIconPaths
        MomentType.VOICE -> MomentVoicePlaybackIconPaths
        MomentType.EMOJI -> emptyList()
    }

    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    val glyphScale = 1.17f * scale
    canvas.save()
    canvas.translate(
        destination.centerX() - 12f * glyphScale,
        destination.centerY() - 12f * glyphScale,
    )
    canvas.scale(glyphScale, glyphScale)
    paths.forEach { pathData ->
        androidx.core.graphics.PathParser.createPathFromPathData(pathData)?.let {
            canvas.drawPath(it, paint)
        }
    }
    canvas.restore()
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun HistoryPage(
    store: TourStore,
    revision: Long,
    isVisible: Boolean = true,
    onBack: () -> Unit,
    onEditTour: (Long) -> Unit,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    photoRevision: Long = 0L,
    onPhotoRotated: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tourListState = rememberLazyListState()
    var tours by remember { mutableStateOf(emptyList<Tour>()) }
    var mapMoments by remember { mutableStateOf(emptyList<MapMoment>()) }
    var selectedMoment by remember { mutableStateOf<MapMoment?>(null) }
    var isPhotoDetailVisible by remember { mutableStateOf(false) }
    var isMediaDetailVisible by remember { mutableStateOf(false) }
    val visualsByTour = remember(tours, mapMoments) {
        tours.associate { tour ->
            tour.id to visualMomentsForTour(mapMoments, tour)
        }
    }
    val historyPhotos = remember(visualsByTour) {
        orderedPhotoMoments(
            visualsByTour.values
                .flatten()
                .distinctBy(MapMoment::id),
        )
    }
    val selectedTourIndex = remember(selectedMoment?.id, tours, visualsByTour) {
        val selectedId = selectedMoment?.id
        tours.indexOfFirst { tour ->
            visualsByTour[tour.id].orEmpty().any { it.id == selectedId }
        }
    }
    BackHandler(
        enabled = isVisible &&
            !isPhotoDetailVisible &&
            !isMediaDetailVisible,
        onBack = onBack,
    )
    LaunchedEffect(revision) {
        val (loadedTours, loadedMoments) = withContext(Dispatchers.IO) {
            val moments = context.loadMapMoments()
            moments
                .filter { it.type == MomentType.VIDEO }
                .forEach { ensureVideoThumbnail(File(it.payload)) }
            store.tours() to moments
        }
        tours = loadedTours
        mapMoments = loadedMoments
    }
    LaunchedEffect(historyPhotos, selectedMoment?.id) {
        val selected = selectedMoment ?: return@LaunchedEffect
        if (
            selected.type == MomentType.PHOTO &&
            historyPhotos.none { it.id == selected.id }
        ) {
            isPhotoDetailVisible = false
            selectedMoment = null
        }
    }
    LaunchedEffect(selectedMoment?.id, selectedTourIndex) {
        if (selectedTourIndex < 0) return@LaunchedEffect
        val margin = with(context.resources.displayMetrics) { (24 * density).roundToInt() }
        val item = tourListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == selectedTourIndex }
        val viewportStart = tourListState.layoutInfo.viewportStartOffset + margin
        val viewportEnd = tourListState.layoutInfo.viewportEndOffset - margin
        if (
            item == null ||
            item.offset < viewportStart ||
            item.offset + item.size > viewportEnd
        ) {
            tourListState.animateScrollToItem(
                index = selectedTourIndex,
                scrollOffset = -margin,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
            ) {
                BackIcon()
            }
            Text(
                text = "Deine Touren",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (tours.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Noch keine Touren",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Deine aufgezeichneten Wege erscheinen hier.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = Ink.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = tourListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                items(tours, key = { it.id }) { tour ->
                    val visuals = visualsByTour[tour.id].orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditTour(tour.id) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp)
                                        .padding(
                                            bottom = if (visuals.isEmpty()) 0.dp else 4.dp,
                                        ),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tour.activity ?: formatDate(tour.startedAt),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (tour.activity == null) {
                                                formatTourTime(tour)
                                            } else {
                                                "${formatDate(tour.startedAt)} · " +
                                                    formatTourTime(tour)
                                            },
                                            modifier = Modifier.padding(top = 3.dp),
                                            color = Ink.copy(alpha = 0.56f),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        text = formatMeters(tour.distanceMeters),
                                        color = Moss,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (visuals.isNotEmpty()) {
                                    TourMomentStrip(
                                        moments = visuals,
                                        photoRevision = photoRevision,
                                        selectedMomentId = selectedMoment?.id,
                                        onOpen = {
                                            selectedMoment = it
                                            if (it.type == MomentType.PHOTO) {
                                                isPhotoDetailVisible = true
                                            } else if (it.type == MomentType.VIDEO) {
                                                isMediaDetailVisible = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isPhotoDetailVisible) selectedMoment
        ?.takeIf { it.type == MomentType.PHOTO }
        ?.let { photo ->
        PhotoDetailPage(
            photos = historyPhotos,
            initialPhotoId = photo.id,
            showFeedbackNotice = showFeedbackNotice,
            photoRevision = photoRevision,
            onPhotoChanged = { selectedMoment = it },
            onPhotoRotated = onPhotoRotated,
            onPhotoDeleted = { deletedPhoto ->
                scope.launch {
                    val updatedMoments = context.deleteMapMoment(
                        moment = deletedPhoto,
                        moments = mapMoments,
                    )
                    if (updatedMoments == null) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Das Bild konnte nicht gelöscht werden.",
                        )
                    } else {
                        mapMoments = updatedMoments
                        selectedMoment = null
                        isPhotoDetailVisible = false
                    }
                }
            },
            onDismiss = { isPhotoDetailVisible = false },
        )
    }

    if (isMediaDetailVisible) selectedMoment
        ?.takeIf { it.type == MomentType.VIDEO }
        ?.let { video ->
            MediaMomentDetailPage(
                moment = video,
                onDismiss = { isMediaDetailVisible = false },
            )
        }
}

@Composable
private fun TourMomentStrip(
    moments: List<MapMoment>,
    photoRevision: Long,
    selectedMomentId: String?,
    onOpen: (MapMoment) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedIndex = remember(moments, selectedMomentId) {
        moments.indexOfFirst { it.id == selectedMomentId }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val margin = with(context.resources.displayMetrics) { (18 * density).roundToInt() }
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == selectedIndex }
        val viewportStart = listState.layoutInfo.viewportStartOffset + margin
        val viewportEnd = listState.layoutInfo.viewportEndOffset - margin
        if (
            item == null ||
            item.offset < viewportStart ||
            item.offset + item.size > viewportEnd
        ) {
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -margin,
            )
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            bottom = 18.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(moments, key = { it.id }) { moment ->
            val isSelected = moment.id == selectedMomentId
            val previewFile = remember(moment.payload, moment.type) {
                if (moment.type == MomentType.VIDEO) {
                    videoThumbnailFile(File(moment.payload))
                } else {
                    File(moment.payload)
                }
            }
            val imageRequest = remember(previewFile, photoRevision) {
                ImageRequest.Builder(context)
                    .data(previewFile)
                    .memoryCacheKey("${previewFile.absolutePath}:$photoRevision")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = 3.dp,
                        color = if (isSelected) {
                            TourMomentSelectionYellow
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist)
                    .clickable { onOpen(moment) }
                    .semantics {
                        selected = isSelected
                        contentDescription = if (moment.type == MomentType.VIDEO) {
                            "Video dieser Tour öffnen"
                        } else {
                            "Foto dieser Tour öffnen"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (moment.type == MomentType.VIDEO) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Ink.copy(alpha = 0.62f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayIcon(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TourEditorScreen(
    store: TourStore,
    tourId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    showFeedbackNotice: ShowFeedbackNotice,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var points by remember(tourId) { mutableStateOf(emptyList<TrackPoint>()) }
    var moments by remember(tourId) { mutableStateOf(emptyList<MapMoment>()) }
    var selectedPointId by rememberSaveable(tourId) { mutableStateOf<Long?>(null) }
    var placementTarget by remember { mutableStateOf<MomentPlacementTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<EditorDeleteTarget?>(null) }

    LaunchedEffect(tourId) {
        while (true) {
            val result = withContext(Dispatchers.IO) {
                val loadedTour = store.tour(tourId) ?: return@withContext null
                Triple(
                    loadedTour,
                    store.points(tourId),
                    mapMomentsForTour(context.loadMapMoments(), loadedTour),
                )
            } ?: return@LaunchedEffect
            val isInitialLoad = tour == null
            tour = result.first
            points = result.second
            moments = result.third
            if (isInitialLoad) selectedPointId = result.second.lastOrNull()?.id
            if (result.first.endedAt != null) return@LaunchedEffect
            delay(1_000)
        }
    }

    val currentTour = tour
    val locations = remember(currentTour, points, moments) {
        currentTour?.let {
            editorLocations(
                tour = it,
                points = points,
                moments = moments,
            )
        }.orEmpty()
    }
    val selectedLocation = locations.firstOrNull { it.point.id == selectedPointId }
        ?: locations.lastOrNull()

    suspend fun saveMoments(updated: List<MapMoment>) {
        withContext(Dispatchers.IO) {
            val editorMomentIds = (moments + updated).mapTo(mutableSetOf(), MapMoment::id)
            context.saveMapMoments(
                context.loadMapMoments().filterNot { it.id in editorMomentIds } + updated,
            )
        }
        moments = updated
        onChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TourEditorMap(
                points = points,
                selectedPoint = selectedLocation?.point,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 18.dp, top = 14.dp),
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = "Tour bearbeiten",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = MapControlHorizontalPadding, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(MapControlGap),
            ) {
                MapIconButton(
                    contentDescription = "Editor schließen",
                    onClick = onBack,
                ) {
                    BackIcon()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Sand),
        ) {
            if (selectedLocation == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Keine GPS-Punkte",
                        color = Ink.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatClock(selectedLocation.point.recordedAt),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Uhrzeit",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    enabled = points.size > 1,
                                    onClick = {
                                        deleteTarget =
                                            EditorDeleteTarget.Location(
                                                selectedLocation.point,
                                            )
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .semantics {
                                            contentDescription = "GPS-Punkt löschen"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.dp,
                                        Ink.copy(alpha = 0.18f),
                                    ),
                                ) {
                                    PhotoDeleteIcon(color = Ink)
                                }
                                Button(
                                    onClick = {
                                        placementTarget =
                                            MomentPlacementTarget.RecordedLocation(
                                                tourId = tourId,
                                                trackPointId = selectedLocation.point.id,
                                                coordinate = SpurCoordinate(
                                                    selectedLocation.point.latitude,
                                                    selectedLocation.point.longitude,
                                                ),
                                            )
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .semantics {
                                            contentDescription =
                                                "Moment an diesem GPS-Punkt hinzufügen"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Ink,
                                    ),
                                ) {
                                    PlusIcon()
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatEditorElapsed(
                                        selectedLocation.elapsedMillis,
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "seit Start",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatKilometers(
                                        selectedLocation.distanceFromStartMeters,
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "vom Start",
                                    color = Ink.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                    }

                    selectedLocation.moments.forEach { moment ->
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = moment.type.editorLabel(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                IconButton(
                                    onClick = {
                                        deleteTarget = EditorDeleteTarget.Moment(moment)
                                    },
                                ) {
                                    PhotoDeleteIcon(color = Ink)
                                }
                            }
                        }
                    }
                }
                EditorLocationRail(
                    locations = locations,
                    selectedPointId = selectedLocation.point.id,
                    onSelected = { selectedPointId = it },
                )
            }
        }
    }

    MomentComposer(
        target = placementTarget,
        showFeedbackNotice = showFeedbackNotice,
        onDismiss = { placementTarget = null },
        onMomentAccepted = { target, pending ->
            val location = target as? MomentPlacementTarget.RecordedLocation
            if (location == null) {
                pending.deletePayload()
            } else {
                val moment = MapMoment(
                    id = pending.id,
                    type = pending.type,
                    latitude = location.coordinate.latitude,
                    longitude = location.coordinate.longitude,
                    payload = pending.payload,
                    tourId = location.tourId,
                    trackPointId = location.trackPointId,
                )
                scope.launch { saveMoments(moments + moment) }
            }
            placementTarget = null
        },
    )

    deleteTarget?.let { target ->
        EditorDeleteSheet(
            title = when (target) {
                is EditorDeleteTarget.Location -> "GPS-Punkt löschen?"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen?"
            },
            primaryLabel = when (target) {
                is EditorDeleteTarget.Location -> "GPS-Punkt löschen"
                is EditorDeleteTarget.Moment -> "${target.moment.type.editorLabel()} löschen"
            },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                when (target) {
                    is EditorDeleteTarget.Moment -> scope.launch {
                        val updated = context.deleteMapMoment(
                            moment = target.moment,
                            moments = context.loadMapMoments(),
                        )
                        if (updated == null) {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "${target.moment.type.editorLabel()} konnte nicht gelöscht werden.",
                            )
                        } else {
                            moments = mapMomentsForTour(updated, currentTour ?: return@launch)
                            onChanged()
                        }
                    }
                    is EditorDeleteTarget.Location -> scope.launch {
                        val deletedIndex = points.indexOf(target.point)
                        val retained = points.filterNot { it.id == target.point.id }
                        val retainedIds = retained.mapTo(mutableSetOf(), TrackPoint::id)
                        withContext(Dispatchers.IO) {
                            store.updateTourPoints(tourId, retainedIds)
                        }
                        points = retained
                        saveMoments(
                            moments.map { moment ->
                                if (moment.trackPointId != target.point.id) {
                                    moment
                                } else {
                                    moment.copy(
                                        trackPointId = nearestTrackPoint(
                                            retained,
                                            moment.latitude,
                                            moment.longitude,
                                        )?.id,
                                    )
                                }
                            },
                        )
                        selectedPointId = retained.getOrNull(
                            deletedIndex.coerceAtMost(retained.lastIndex),
                        )?.id
                        tour = withContext(Dispatchers.IO) { store.tour(tourId) }
                    }
                }
            },
        )
    }
}

private sealed interface EditorDeleteTarget {
    data class Location(val point: TrackPoint) : EditorDeleteTarget
    data class Moment(val moment: MapMoment) : EditorDeleteTarget
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditorDeleteSheet(
    title: String,
    primaryLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SpurModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BottomSheetHeader(
                title = title,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Text("Abbrechen", color = Ink)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = Color.White,
                ),
            ) {
                Text(primaryLabel)
            }
        }
    }
}

@Composable
private fun EditorLocationRail(
    locations: List<EditorLocation>,
    selectedPointId: Long,
    onSelected: (Long) -> Unit,
) {
    val initialIndex = locations.indexOfFirst { it.point.id == selectedPointId }
        .coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(
        lazyListState = state,
        snapPosition = SnapPosition.Center,
    )

    suspend fun centerVisibleItem(index: Int, animated: Boolean) {
        var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            state.scrollToItem(index)
            withFrameNanos { }
            item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }
        item?.let {
            val layout = state.layoutInfo
            val viewportCenter =
                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val itemCenter = it.offset + it.size / 2
            val distance = (itemCenter - viewportCenter).toFloat()
            if (abs(distance) > 0.5f) {
                if (animated) {
                    state.animateScrollBy(
                        value = distance,
                        animationSpec = tween(MotionDurationDefaultMillis),
                    )
                } else {
                    state.scrollBy(distance)
                }
            }
        }
    }

    LaunchedEffect(state, locations.size) {
        centerVisibleItem(initialIndex, animated = false)
    }

    LaunchedEffect(state, locations) {
        snapshotFlow {
            val layout = state.layoutInfo
            val center =
                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - center)
            }?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                index?.let {
                    locations.getOrNull(it)?.point?.id?.let(onSelected)
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorLocationRailHeight)
            .background(Color.White),
    ) {
        val selectedIndex = locations.indexOfFirst {
            it.point.id == selectedPointId
        }.coerceAtLeast(0)
        val itemWidth = 10.dp
        val edgePadding = (maxWidth - itemWidth) / 2
        Text(
            text = "${selectedIndex + 1} von ${locations.size}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = edgePadding),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            items(
                count = locations.size,
                key = { locations[it].point.id },
            ) { index ->
                val location = locations[index]
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable {
                            scope.launch { centerVisibleItem(index, animated = true) }
                        }
                        .semantics {
                            contentDescription =
                                "GPS-Punkt ${index + 1} von ${locations.size}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (index == 0) {
                        Text(
                            text = "Start",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = (-26).dp, y = (-10).dp)
                                .requiredWidth(40.dp),
                            color = Ink.copy(alpha = 0.28f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index == locations.lastIndex) {
                        Text(
                            text = "Ende",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = 26.dp, y = (-10).dp)
                                .requiredWidth(40.dp),
                            color = Ink.copy(alpha = 0.28f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (location.moments.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Moss, CircleShape),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(Ink.copy(alpha = 0.16f), CircleShape),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .width(2.dp)
                .height(32.dp)
                .background(Ink, CircleShape),
        )
    }
}

private fun MomentType.editorLabel(): String = when (this) {
    MomentType.PHOTO -> "Foto"
    MomentType.VIDEO -> "Video"
    MomentType.VOICE -> "Sprachnachricht"
    MomentType.EMOJI -> "Emoji"
}

private fun formatEditorElapsed(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000
    return String.format(Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}

@Composable
private fun TourEditorMap(
    points: List<TrackPoint>,
    selectedPoint: TrackPoint?,
) {
    val context = LocalContext.current
    val trailColors = remember { context.loadTrailColors() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPoints by rememberUpdatedState(points)
    val currentSelectedPoint by rememberUpdatedState(selectedPoint)
    val density = LocalDensity.current
    val cameraPadding = with(density) { 52.dp.roundToPx() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            map.setStyle(StreetMapStyle) { style ->
                style.showTourRoute(currentPoints, trailColors)
                style.showSelectedTrackPoint(currentSelectedPoint)
                style.showTourEndpoints(currentPoints, trailColors)
                mapView.post {
                    map.fitTourRoute(currentPoints, cameraPadding, animated = false)
                }
            }
        }
    }

    LaunchedEffect(selectedPoint) {
        mapView.getMapAsync { map ->
            map.style?.showSelectedTrackPoint(selectedPoint)
            selectedPoint?.let { point ->
                map.moveCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(point.latitude, point.longitude),
                    ),
                )
            }
        }
    }

    LaunchedEffect(points) {
        mapView.getMapAsync { map ->
            map.style?.showTourRoute(points, trailColors)
            map.style?.showTourEndpoints(points, trailColors)
            mapView.post { map.fitTourRoute(points, cameraPadding, animated = true) }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Kartenvorschau der bearbeiteten Tour" },
    )
}

private fun MapLibreMap.fitTourRoute(
    points: List<TrackPoint>,
    paddingPixels: Int,
    animated: Boolean,
) = fitTourRoute(
    points = points,
    leftPaddingPixels = paddingPixels,
    topPaddingPixels = paddingPixels,
    rightPaddingPixels = paddingPixels,
    bottomPaddingPixels = paddingPixels,
    pointZoom = DefaultMapZoom,
    animated = animated,
)

private fun MapLibreMap.fitMapScreenTourRoute(
    points: List<TrackPoint>,
    density: Float,
    pointZoom: Double,
    animated: Boolean,
) = fitTourRoute(
    points = points,
    leftPaddingPixels = (40 * density).roundToInt(),
    topPaddingPixels = (104 * density).roundToInt(),
    rightPaddingPixels = (40 * density).roundToInt(),
    bottomPaddingPixels = (184 * density).roundToInt(),
    pointZoom = pointZoom,
    animated = animated,
)

private fun MapLibreMap.fitTourRoute(
    points: List<TrackPoint>,
    leftPaddingPixels: Int,
    topPaddingPixels: Int,
    rightPaddingPixels: Int,
    bottomPaddingPixels: Int,
    pointZoom: Double,
    animated: Boolean,
) {
    if (points.isEmpty()) return
    val update = if (points.size == 1) {
        CameraUpdateFactory.newCameraPosition(
            org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
                .target(LatLng(points.first().latitude, points.first().longitude))
                .zoom(pointZoom)
                .build(),
        )
    } else {
        val bounds = LatLngBounds.Builder()
            .includes(points.map { LatLng(it.latitude, it.longitude) })
            .build()
        val camera = getCameraForLatLngBounds(
            bounds,
            intArrayOf(
                leftPaddingPixels,
                topPaddingPixels,
                rightPaddingPixels,
                bottomPaddingPixels,
            ),
            cameraPosition.bearing,
            cameraPosition.tilt,
        )
        camera?.let(CameraUpdateFactory::newCameraPosition)
            ?: CameraUpdateFactory.newLatLngBounds(
                bounds,
                leftPaddingPixels,
                topPaddingPixels,
                rightPaddingPixels,
                bottomPaddingPixels,
            )
    }
    if (animated) animateCamera(update, 220) else moveCamera(update)
}

@Composable
private fun TourSummaryPlayer(
    tourId: Long,
    distanceMeters: Double,
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
) {
    val controlColors = LocalMapControlColors.current.inverted
    var showTrackingTime by rememberSaveable(tourId) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .mapControlShadow(CircleShape)
            .clickable(
                onClickLabel = if (showTrackingTime) {
                    "Distanz anzeigen"
                } else {
                    "Tour-Dauer anzeigen"
                },
            ) {
                showTrackingTime = !showTrackingTime
            },
        color = controlColors.background,
        contentColor = controlColors.foreground,
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tourProgressPlayerText(
                    distanceMeters = distanceMeters,
                    elapsedMillis = elapsedMillis,
                    showTrackingTime = showTrackingTime,
                ),
                color = controlColors.foreground,
                fontSize = if (showTrackingTime) 18.sp else 28.sp,
                fontWeight = if (showTrackingTime) {
                    FontWeight.Normal
                } else {
                    FontWeight.SemiBold
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TourPlayer(
    tour: Tour,
    now: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlColors = LocalMapControlColors.current.inverted
    var armed by remember(tour.id) { mutableStateOf(false) }
    var showTrackingTime by rememberSaveable(tour.id) { mutableStateOf(false) }
    var dragOffset by remember(tour.id) { mutableFloatStateOf(0f) }
    var dragStartX by remember(tour.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val mainControlHeight = 60.dp

    Box(
        modifier = modifier.height(mainControlHeight),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(mainControlHeight)
                .mapControlShadow(CircleShape),
            color = Color.Transparent,
            shape = CircleShape,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val handleSize = StopSwipeHandleSize
                val edgePadding = 4.dp
                val maximum = with(density) {
                    (maxWidth - handleSize - edgePadding * 2).toPx().coerceAtLeast(0f)
                }
                val edgePaddingPixels = with(density) { edgePadding.toPx() }
                val swipeProgress = stopSwipeProgress(dragOffset, maximum)
                val stopThresholdReached = shouldCompleteStopSwipe(dragOffset, maximum)
                val swipeColor = lerp(controlColors.background, StopRed, swipeProgress)
                val swipeForeground = lerp(StopRed, Color.White, swipeProgress)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(swipeColor),
                )

                AnimatedContent(
                    targetState = armed,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                    },
                    label = "Stop confirmation",
                    modifier = Modifier.fillMaxSize(),
                ) { confirmationVisible ->
                    if (confirmationVisible) {
                        SwipeStopPrompt(
                            color = swipeForeground,
                            stopThresholdReached = stopThresholdReached,
                            swipePromptAlpha = stopSwipePromptAlpha(dragOffset, maximum),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 68.dp, end = 12.dp)
                                .clickable(
                                    onClickLabel = if (showTrackingTime) {
                                        "Distanz anzeigen"
                                    } else {
                                        "Trackingzeit anzeigen"
                                    },
                                ) {
                                    showTrackingTime = !showTrackingTime
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = activeTourPlayerText(
                                    tour = tour,
                                    now = now,
                                    showTrackingTime = showTrackingTime,
                                ),
                                color = controlColors.foreground,
                                fontSize = if (showTrackingTime) 18.sp else 28.sp,
                                fontWeight = if (showTrackingTime) {
                                    FontWeight.Normal
                                } else {
                                    FontWeight.SemiBold
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (edgePaddingPixels + dragOffset).roundToInt(),
                                y = 0,
                            )
                        }
                        .size(handleSize)
                        .background(
                            if (armed) {
                                swipeForeground.copy(alpha = 0.14f)
                            } else {
                                controlColors.foreground.copy(alpha = 0.14f)
                            },
                            CircleShape,
                        )
                        .semantics {
                            contentDescription = when {
                                stopThresholdReached ->
                                    "Loslassen, um die Tour zu beenden"
                                armed ->
                                    "Nach rechts wischen, um die Tour zu beenden"
                                else ->
                                    "Tour beenden vorbereiten"
                            }
                        }
                        .pointerInteropFilter { event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    armed = true
                                    dragOffset = 0f
                                    dragStartX = event.rawX
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    dragOffset = (event.rawX - dragStartX)
                                        .coerceIn(0f, maximum)
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (shouldCompleteStopSwipe(dragOffset, maximum)) {
                                        dragOffset = maximum
                                        onStop()
                                    } else {
                                        armed = false
                                        dragOffset = 0f
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    armed = false
                                    dragOffset = 0f
                                }
                            }
                            true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LucideStopIcon(
                        color = if (armed) swipeForeground else controlColors.foreground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeStopPrompt(
    color: Color,
    stopThresholdReached: Boolean,
    swipePromptAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!stopThresholdReached) Spacer(modifier = Modifier.width(StopSwipeHandleSize))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (stopThresholdReached) "Stop Tour" else "Swipe right",
                modifier = Modifier.graphicsLayer {
                    alpha = if (stopThresholdReached) 1f else swipePromptAlpha
                },
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (stopThresholdReached) Spacer(modifier = Modifier.width(StopSwipeHandleSize))
    }
}

@Composable
private fun LucideStopIcon(color: Color) = LucideIcon(
    paths = listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2",
    ),
    color = color,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier
        .size(24.dp)
        .semantics { contentDescription = "Tour beenden" },
)

internal fun formatKilometers(distanceMeters: Double): String =
    String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1_000.0)

internal fun formatMeters(distanceMeters: Double): String =
    String.format(Locale.GERMANY, "%,.0f m", distanceMeters.coerceAtLeast(0.0))

internal fun activeTourPlayerText(
    tour: Tour,
    now: Long,
    showTrackingTime: Boolean,
): String =
    tourProgressPlayerText(
        distanceMeters = tour.distanceMeters,
        elapsedMillis = now - tour.startedAt,
        showTrackingTime = showTrackingTime,
    )

internal fun tourProgressPlayerText(
    distanceMeters: Double,
    elapsedMillis: Long,
    showTrackingTime: Boolean,
): String =
    if (showTrackingTime) formatPlayerDuration(elapsedMillis)
    else formatMeters(distanceMeters)

internal fun formatPlayerDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}

private fun formatClock(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatTourTime(tour: Tour): String {
    val end = tour.endedAt ?: System.currentTimeMillis()
    return "${formatClock(tour.startedAt)}–${formatClock(end)} · ${
        formatDuration(end - tour.startedAt)
    }"
}

@Composable
private fun MenuIcon() = LucideIcon(
    paths = listOf("M4 12h.01", "M12 12h.01", "M20 12h.01"),
)

@Composable
private fun ShareIcon() = LucideIcon(
    paths = listOf(
        "M21 5a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M9 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M21 19a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M8.59 13.51 15.42 17.49",
        "M15.41 6.51 8.59 10.49",
    ),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
private fun ExternalLinkIcon() = LucideIcon(
    paths = listOf(
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    ),
    modifier = Modifier.size(18.dp),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
private fun MapPinIcon(
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.size(32.dp),
) = LucideIcon(
    paths = MapPinIconPaths,
    color = color,
    modifier = modifier,
)

@Composable
private fun FilledMapPinAtCenter(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pinPath = remember {
        ComposePathParser().parsePathString(MapPinIconPaths.first()).toPath()
    }
    Canvas(modifier = modifier) {
        val pinSize = 28.dp.toPx()
        val scale = pinSize / 24f
        withTransform({
            translate(
                left = (size.width - pinSize) / 2f,
                top = size.height / 2f - MapPinTipY * scale,
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(
                path = pinPath,
                color = color,
            )
        }
    }
}

@Composable
private fun MomentPhotoIcon() = LucideIcon(
    paths = MomentPhotoIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
private fun MomentVideoIcon() = LucideIcon(
    paths = MomentVideoIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
private fun MomentVoiceIcon() = LucideIcon(
    paths = MomentVoicePlaybackIconPaths,
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier.size(30.dp),
)

@Composable
private fun PlusIcon() = LucideIcon(
    paths = listOf("M5 12h14", "M12 5v14"),
)

@Composable
private fun MicrophoneIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf(
        "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12 19v3",
    ),
    modifier = modifier,
    color = color,
)

@Composable
private fun PlayIcon(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf("m6 3 14 9-14 9z"),
    modifier = modifier,
    color = color,
)

@Composable
private fun PauseIcon() = LucideIcon(
    paths = listOf("M8 5v14", "M16 5v14"),
)

@Composable
private fun FollowLocationIcon(
    selected: Boolean,
    pulseGeneration: Long?,
) {
    val trailColor = LocalTrailColors.current.fill
    val rippleProgress = if (selected && pulseGeneration != null) {
        key(pulseGeneration) {
            val transition = rememberInfiniteTransition(label = "Location following signal")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = LocationPulseDurationMillis,
                        easing = LocationPulseEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "Location following white ripple",
            )
        }
    } else {
        null
    }
    Box(
        modifier = Modifier
            .size(MapControlSize)
            .clip(CircleShape)
            .background(if (selected) trailColor else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (rippleProgress != null) {
            Box(
                modifier = Modifier
                    .size(MapControlSize)
                    .graphicsLayer {
                        val phase = rippleProgress.value
                        val rippleScale = phase * LocationPulseScale
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha = (1f - phase) * LocationPulseAlpha
                    }
                    .background(Color.White, CircleShape),
            )
        }
        FootprintsIcon(
            color = if (selected) Color.White else LocalContentColor.current,
        )
    }
}

@Composable
private fun FootprintsIcon(
    color: Color = LocalContentColor.current,
) = LucideIcon(
    paths = listOf(
        "M4 16v-2.38C4 11.5 2.97 10.5 3 8c.03-2.72 1.49-6 4.5-6C9.37 2 10 3.8 10 5.5c0 3.11-2 5.66-2 8.68V16a2 2 0 1 1-4 0Z",
        "M20 20v-2.38c0-2.12 1.03-3.12 1-5.62-.03-2.72-1.49-6-4.5-6C14.63 6 14 7.8 14 9.5c0 3.11 2 5.66 2 8.68V20a2 2 0 1 0 4 0Z",
        "M16 17h4",
        "M4 13h4",
    ),
    color = color,
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
private fun HistoryIcon() = LucideIcon(
    paths = listOf(
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    ),
    strokeWidth = LucideRegularStrokeWidth,
)

@Composable
private fun HomeIcon(
    modifier: Modifier = Modifier.size(28.dp),
) = LucideIcon(
    paths = listOf(
        "M3 9.5 12 2l9 7.5",
        "M5 10v10h14V10",
        "M9 20v-6h6v6",
    ),
    modifier = modifier,
)

@Composable
private fun LucideLocateOffIcon() = LucideIcon(
    paths = listOf(
        "M12 19v3",
        "M12 2v3",
        "M18.89 13.24a7 7 0 0 0-8.13-8.13",
        "M19 12h3",
        "M2 12h3",
        "m2 2 20 20",
        "M7.05 7.05a7 7 0 0 0 9.9 9.9",
    ),
)

@Composable
internal fun LucideIcon(
    paths: List<String>,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.size(32.dp),
    strokeWidth: Float = LocalLucideStrokeWidth.current,
) {
    val parsedPaths = paths.map { path ->
        remember(path) { ComposePathParser().parsePathString(path).toPath() }
    }
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        withTransform({
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            parsedPaths.forEach { path ->
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BackIcon() = LucideIcon(
    paths = listOf("m12 19-7-7 7-7", "M19 12H5"),
    strokeWidth = LucideBoldStrokeWidth,
    modifier = Modifier
        .size(24.dp)
        .semantics { contentDescription = "Zurück zur Karte" },
)

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun MapPagePreview() {
    MapPage(
        store = TourStore(LocalContext.current),
        tour = null,
        activeTour = null,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        isTourEditing = false,
        onEditTour = {},
        onCloseTourEditor = {},
        onRoutePointsChanged = {},
        onDeleteTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ActiveTourPagePreview() {
    val tour = Tour(
        id = 1,
        startedAt = System.currentTimeMillis() - 754_000,
        endedAt = null,
        distanceMeters = 1_840.0,
        pointCount = 42,
    )
    MapPage(
        store = TourStore(LocalContext.current),
        tour = tour,
        activeTour = tour,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        isTourEditing = false,
        onEditTour = {},
        onCloseTourEditor = {},
        onRoutePointsChanged = {},
        onDeleteTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HistoryPagePreview() {
    HistoryPage(
        store = TourStore(LocalContext.current),
        revision = 0,
        onBack = {},
        onEditTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}
