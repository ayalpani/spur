package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolZOrder
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
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
private val Mist = Color(0xFFE8EEE9)
private const val DefaultMapZoom = 17.5
private val MapRotationOptionGap = 16.dp
private val FilterChipVisualInset = 8.dp
private const val MapRotationAnimationMillis = 350L
private const val PhotoDetailEnterMillis = 320
private const val PhotoDetailExitMillis = 240
private const val TourRouteWidthPixels = 6f
private const val TourRouteBorderPerSidePixels = 2f
private const val TourRouteBorderWidthPixels =
    TourRouteWidthPixels + TourRouteBorderPerSidePixels * 2f
internal const val LucideBoldStrokeWidth = 3f
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
    BLACK("Schwarz", Color.Black),
    WHITE("Weiß", Color.White),
    GRAY("Grau", Color(0xFF64748B)),
    RED("Rot", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFF97316)),
    YELLOW("Gelb", Color(0xFFF4C430)),
    GREEN("Grün", Color(0xFF23614A)),
    BLUE("Blau", Color(0xFF2563EB)),
    VIOLET("Violett", Color(0xFF7C3AED)),
    ;

    val contrastColor: Color
        get() = if (color.luminance() > 0.3f) Ink else Color.White
}

private val LocalMapControlColors = staticCompositionLocalOf {
    MapControlColors(background = Color.White, foreground = Ink)
}
private val LocalLucideStrokeWidth = staticCompositionLocalOf { 2f }

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
    const val TOUR_ID = "tourId"
    const val EDITOR = "tour/{$TOUR_ID}"

    fun editor(tourId: Long): String = "tour/$tourId"
}
private const val StreetMapStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val SatelliteMapStyleJson =
    """{"version":8,"sources":{"satellite-source":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Esri, Maxar, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-layer","type":"raster","source":"satellite-source"}]}"""
private const val MomentMarkerWidth = 62
private const val MomentMarkerHeight = 58
private const val MomentMarkerStroke = 3f
private const val MapPreviewPixels = 180
private const val LocationPulseDurationMillis = 2_300
private const val TourRouteSource = "tour-route-source"
private const val TourRouteBorderLayer = "tour-route-border-layer"
private const val TourRouteLayer = "tour-route-layer"
private const val SelectedTrackPointSource = "selected-track-point-source"
private const val SelectedTrackPointLayer = "selected-track-point-layer"
private const val MapMomentSource = "map-moment-source"
private const val MapMomentLayer = "map-moment-layer"
private const val MapMomentClusterLayer = "map-moment-cluster-layer"
private const val MapMomentIdProperty = "moment-id"
private const val MapMomentImageProperty = "moment-image"
private const val MapMomentRepresentativeProperty = "moment-representative"
private const val MapMomentImagePrefix = "map-moment-"
private const val MapMomentClusterImagePrefix = "map-moment-cluster-"
private const val MapMomentClusterMaxZoom = 18
private const val MapMomentClusterRadius = 54

internal enum class MapRotation(val label: String, val bearing: Double) {
    NORTH("Norden", 0.0),
    EAST("Osten", 90.0),
    SOUTH("Süden", 180.0),
    WEST("Westen", 270.0),
}

internal fun mapRotationFromStored(value: String?): MapRotation =
    MapRotation.entries.firstOrNull { it.name == value } ?: MapRotation.NORTH

internal fun mapControlColorFromStored(value: String?): MapControlColor =
    MapControlColor.entries.firstOrNull { it.name == value } ?: MapControlColor.BLACK

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
    pointCount: Int,
): Boolean = tourId != null && tourId != fittedTourId && pointCount > 0

internal fun shouldShowTourOverview(
    isFollowingLocation: Boolean,
    isTourActive: Boolean,
    routePointCount: Int,
): Boolean = isFollowingLocation && isTourActive && routePointCount > 0

internal fun shouldStopFollowing(cameraMoveReason: Int): Boolean =
    cameraMoveReason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldShowMapPreviewLoading(
    isFollowingLocation: Boolean,
    cameraMoveReason: Int,
): Boolean = !isFollowingLocation || shouldStopFollowing(cameraMoveReason)

internal fun mapPreviewZoom(
    mapZoom: Double,
    mapWidthPixels: Int,
    density: Float,
    previewWidthPixels: Int,
): Double = mapZoom - log2(mapWidthPixels / density / previewWidthPixels)

internal fun shouldCompleteStopSwipe(offset: Float, maximum: Float): Boolean =
    maximum > 0f && offset >= maximum * 0.82f

internal fun stopSwipePromptAlpha(offset: Float, maximum: Float): Float {
    if (maximum <= 0f) return 1f
    val progress = (offset / maximum).coerceIn(0f, 1f)
    return ((0.55f - progress) / 0.4f).coerceIn(0f, 1f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpurApp() }
    }
}

@Composable
private fun SpurApp() {
    val context = LocalContext.current
    val store = remember { TourStore(context) }
    val scope = rememberCoroutineScope()
    var activeTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTourId by rememberSaveable { mutableStateOf<Long?>(null) }
    var routePoints by remember { mutableStateOf(emptyList<TrackPoint>()) }
    var historyRevision by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasLocationPermission())
    }
    val navController = rememberNavController()
    val historyDrawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
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
        val id = activeTour?.id ?: displayedTourId
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
                ModalNavigationDrawer(
                    drawerState = historyDrawerState,
                    gesturesEnabled = false,
                    drawerContent = {
                        ModalDrawerSheet {
                            HistoryScreen(
                                store = store,
                                revision = historyRevision,
                                isVisible = historyDrawerState.isOpen,
                                onBack = {
                                    scope.launch { historyDrawerState.close() }
                                },
                                onOpenTour = { id ->
                                    displayedTourId = id
                                    scope.launch { historyDrawerState.close() }
                                },
                                onEditTour = { id ->
                                    scope.launch {
                                        historyDrawerState.close()
                                        navController.navigate(SpurRoute.editor(id))
                                    }
                                },
                                onDeleteTour = { id ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            store.deleteTour(id)
                                        }
                                        if (displayedTourId == id) {
                                            displayedTour = null
                                            displayedTourId = null
                                            routePoints = emptyList()
                                        }
                                        historyRevision++
                                    }
                                },
                            )
                        }
                    },
                ) {
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
                            MapScreen(
                                tour = displayedTour,
                                isTourActive = activeTour != null,
                                routePoints = routePoints,
                                now = now,
                                onStartTour = {
                                    scope.launch {
                                        val id = withContext(Dispatchers.IO) {
                                            store.startTour().also { startedId ->
                                                context.loadManualLocation()?.let { coordinate ->
                                                    store.appendSimulatedLocation(
                                                        startedId,
                                                        coordinate,
                                                    )
                                                }
                                            }
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
                                        routePoints = emptyList()
                                        historyRevision++
                                    }
                                },
                                onSimulatedLocation = { coordinate ->
                                    val id = activeTour?.id ?: return@MapScreen
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            store.appendSimulatedLocation(id, coordinate)
                                        }
                                        historyRevision++
                                    }
                                },
                                onEndTour = {
                                    val id = activeTour?.id ?: return@MapScreen
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
                                        routePoints = result.second
                                        now = System.currentTimeMillis()
                                        historyRevision++
                                    }
                                },
                                onOpenHistory = {
                                    scope.launch { historyDrawerState.open() }
                                },
                            )
                        }
                        composable(
                            route = SpurRoute.EDITOR,
                            arguments = listOf(
                                navArgument(SpurRoute.TOUR_ID) { type = NavType.LongType },
                            ),
                        ) { entry ->
                            val tourId = entry.arguments?.getLong(SpurRoute.TOUR_ID)
                                ?: return@composable
                            TourEditorScreen(
                                store = store,
                                tourId = tourId,
                                onBack = { navController.popBackStack() },
                                onSaved = {
                                    historyRevision++
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
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
private fun MapScreen(
    tour: Tour?,
    isTourActive: Boolean,
    routePoints: List<TrackPoint>,
    now: Long,
    onStartTour: () -> Unit,
    onSimulatedLocation: (SpurCoordinate) -> Unit,
    onEndTour: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    var followRequest by rememberSaveable { mutableStateOf(0) }
    var tourOverviewRequest by rememberSaveable { mutableStateOf(0) }
    var isFollowingLocation by rememberSaveable { mutableStateOf(false) }
    var isTourOverview by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    var alternateMapPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isAlternateMapPreviewLoading by remember { mutableStateOf(true) }
    var showMomentSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var photoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var mapMoments by remember { mutableStateOf(context.loadMapMoments()) }
    var manualLocation by remember { mutableStateOf(context.loadManualLocation()) }
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
    var isMapGestureActive by remember { mutableStateOf(false) }
    val momentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val followOwnLocation: () -> Unit = {
        isTourOverview = false
        isFollowingLocation = true
        followRequest++
    }
    BackHandler(
        enabled = isTourOverview &&
            drawerState.isClosed &&
            !showMomentSheet &&
            !showSettingsSheet &&
            !showAboutSheet &&
            !showCamera &&
            photoDetail == null,
        onBack = followOwnLocation,
    )
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(
                context,
                "Für Fotos braucht Spur Zugriff auf die Kamera.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val mapControlColors = MapControlColors(
        background = mapControlBackground.color,
        foreground = mapControlForeground.color,
    )
    CompositionLocalProvider(LocalMapControlColors provides mapControlColors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Spur",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    NavigationDrawerItem(
                        label = { Text("Einstellungen") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showSettingsSheet = true
                            }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("Über Spur") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showAboutSheet = true
                            }
                        },
                    )
                }
            }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            MapSurface(
                modifier = Modifier.zIndex(if (isMapGestureActive) 1f else 0f),
                tourId = tour?.id,
                followRequest = followRequest,
                tourOverviewRequest = tourOverviewRequest,
                isFollowingLocation = isFollowingLocation,
                isSatelliteView = isSatelliteView,
                manualLocation = manualLocation,
                initialMapZoom = initialMapZoom,
                defaultMapBearing = defaultMapRotation.bearing,
                mapSettingsVisible = showSettingsSheet,
                mapMoments = mapMoments,
                routePoints = routePoints,
                photoToPlace = pendingPhoto,
                onAlternateMapPreviewChanged = { alternateMapPreview = it },
                onAlternateMapPreviewLoadingChanged = {
                    isAlternateMapPreviewLoading = it
                },
                onMomentPlaced = { moment ->
                    val updatedMoments = mapMoments + moment.copy(tourId = tour?.id)
                    context.saveMapMoments(updatedMoments)
                    mapMoments = updatedMoments
                    pendingPhoto = null
                    Toast.makeText(context, "Foto auf der Karte abgelegt.", Toast.LENGTH_SHORT)
                        .show()
                },
                onPhotoPlacementFailed = { photo ->
                    photo.delete()
                    pendingPhoto = null
                    Toast.makeText(
                        context,
                        "Der Standort ist noch nicht verfügbar.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onMomentClick = { moment ->
                    if (moment.type == MomentType.PHOTO) photoDetail = moment
                },
                onManualLocationChanged = { location ->
                    context.saveManualLocation(location)
                    manualLocation = location
                    onSimulatedLocation(location)
                    Toast.makeText(
                        context,
                        "Simulierter Standort gesetzt.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFollowingInterrupted = {
                    isFollowingLocation = false
                    isTourOverview = false
                },
                onMapGestureActiveChanged = { isMapGestureActive = it },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapIconButton(
                    contentDescription = "Hauptmenü öffnen",
                    onClick = { scope.launch { drawerState.open() } },
                ) {
                    MenuIcon()
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isTourActive) {
                    MapIconButton(
                        contentDescription = "Tour teilen",
                        onClick = { shareActiveTour(context) },
                    ) {
                        ShareIcon()
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                MapIconButton(
                    contentDescription = "Tour-History öffnen",
                    onClick = onOpenHistory,
                ) {
                    HistoryIcon()
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, bottom = 86.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapStyleButton(
                    contentDescription = if (isSatelliteView) {
                        "Schematische Kartenansicht anzeigen"
                    } else {
                        "Satellitenansicht anzeigen"
                    },
                    onClick = {
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
                if (manualLocation != null) {
                    MapIconButton(
                        contentDescription = "Simulierten Standort zurücksetzen",
                        onClick = {
                            context.saveManualLocation(null)
                            manualLocation = null
                            Toast.makeText(
                                context,
                                "GPS-Standort wieder aktiv.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        LucideLocateOffIcon()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                MapIconButton(
                    contentDescription = "Moment hinzufügen",
                    onClick = { showMomentSheet = true },
                ) {
                    PlusIcon()
                }
                if (isTourActive && tour != null) {
                    ActiveTourStopControl(
                        tour = tour,
                        now = now,
                        onStop = onEndTour,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val controlColors = LocalMapControlColors.current.inverted
                    Button(
                        onClick = onStartTour,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
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
                    FollowLocationIcon(selected = isFollowingLocation)
                }
            }
            }
        }
    }

    if (showMomentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMomentSheet = false },
            sheetState = momentSheetState,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LucideIcon(
                    paths = listOf(
                        "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
                        "M15 10a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
                    ),
                    color = Ink,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(64.dp),
                )
                Text(
                    text = "Auf der Karte ablegen",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MomentOption(
                        label = "Sprache",
                        modifier = Modifier.weight(1f),
                    ) {
                        Toast.makeText(
                            context,
                            "Sprachaufnahme kommt als Nächstes.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    MomentOption(
                        label = "Emoji",
                        modifier = Modifier.weight(1f),
                    ) {
                        Toast.makeText(
                            context,
                            "Emojimarker kommt als Nächstes.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MomentOption(
                        label = "Video",
                        modifier = Modifier.weight(1f),
                    ) {
                        Toast.makeText(
                            context,
                            "Videomarker kommt als Nächstes.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    MomentOption(
                        label = "Foto",
                        modifier = Modifier.weight(1f),
                    ) {
                        showMomentSheet = false
                        if (context.hasCameraPermission()) {
                            showCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
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
        val selectMapControlBackground: (MapControlColor) -> Unit = {
            mapControlBackground = it
            context.saveMapControlColor(it)
        }
        val selectMapControlForeground: (MapControlColor) -> Unit = {
            mapControlForeground = it
            context.saveMapControlForegroundColor(it)
        }
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
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
                Spacer(modifier = Modifier.height(28.dp))
                MapRotationPicker(
                    compassRotation = compassRotation.value,
                    selectedRotation = defaultMapRotation,
                    onSelect = selectMapRotation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Spur",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Spur hält deine Wege und Erinnerungen privat auf deinem Gerät fest.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Entwickelt von Arash.",
                    color = Ink.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showCamera) {
        CameraScreen(
            onClose = { showCamera = false },
            onPhotoAccepted = { photo ->
                showCamera = false
                pendingPhoto = photo
            },
        )
    }

    photoDetail?.let { moment ->
        PhotoDetailDialog(
            photoPath = moment.payload,
            onDismiss = { photoDetail = null },
        )
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
        MapControlColor.entries.chunked(3).forEach { options ->
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        border = BorderStroke(1.dp, Ink),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun MapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val controlColors = LocalMapControlColors.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(60.dp)
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
    val controlColors = LocalMapControlColors.current
    val screen = LocalConfiguration.current
    val aspectRatio = preview?.let { it.width.toFloat() / it.height }
        ?: screen.screenWidthDp.toFloat() / screen.screenHeightDp
    val previewShape = RoundedCornerShape(18.dp)
    val blurRadius by animateDpAsState(
        targetValue = if (isLoading) 7.dp else 0.dp,
        animationSpec = tween(180),
        label = "Map preview blur",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(60.dp)
            .aspectRatio(aspectRatio)
            .mapControlShadow(previewShape)
            .semantics { this.contentDescription = contentDescription },
        shape = previewShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, controlColors.background),
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
    followRequest: Int,
    tourOverviewRequest: Int,
    isFollowingLocation: Boolean,
    isSatelliteView: Boolean,
    manualLocation: SpurCoordinate?,
    initialMapZoom: Double,
    defaultMapBearing: Double,
    mapSettingsVisible: Boolean,
    mapMoments: List<MapMoment>,
    routePoints: List<TrackPoint>,
    photoToPlace: File?,
    onAlternateMapPreviewChanged: (ImageBitmap) -> Unit,
    onAlternateMapPreviewLoadingChanged: (Boolean) -> Unit,
    onMomentPlaced: (MapMoment) -> Unit,
    onPhotoPlacementFailed: (File) -> Unit,
    onMomentClick: (MapMoment) -> Unit,
    onManualLocationChanged: (SpurCoordinate) -> Unit,
    onFollowingInterrupted: () -> Unit,
    onMapGestureActiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMomentPlaced by rememberUpdatedState(onMomentPlaced)
    val currentOnPhotoPlacementFailed by rememberUpdatedState(onPhotoPlacementFailed)
    val currentOnMomentClick by rememberUpdatedState(onMomentClick)
    val currentOnManualLocationChanged by rememberUpdatedState(onManualLocationChanged)
    val currentOnFollowingInterrupted by rememberUpdatedState(onFollowingInterrupted)
    val currentOnMapGestureActiveChanged by rememberUpdatedState(onMapGestureActiveChanged)
    val currentOnAlternateMapPreviewChanged by rememberUpdatedState(
        onAlternateMapPreviewChanged,
    )
    val currentOnAlternateMapPreviewLoadingChanged by rememberUpdatedState(
        onAlternateMapPreviewLoadingChanged,
    )
    val currentMapMoments by rememberUpdatedState(mapMoments)
    val currentRoutePoints by rememberUpdatedState(routePoints)
    val currentManualLocation by rememberUpdatedState(manualLocation)
    val currentFollowRequest by rememberUpdatedState(followRequest)
    val currentTourOverviewRequest by rememberUpdatedState(tourOverviewRequest)
    val currentIsFollowingLocation by rememberUpdatedState(isFollowingLocation)
    var manualLocationPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var previewCameraPosition by remember {
        mutableStateOf<org.maplibre.android.camera.CameraPosition?>(null)
    }
    var hasLoadedMapStyle by remember { mutableStateOf(false) }
    var fittedTourId by remember { mutableStateOf<Long?>(null) }
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
            if (currentIsFollowingLocation) {
                map.followLocation(
                    context = context,
                    manualLocation = currentManualLocation,
                    transitionDuration = if (animateRotation) {
                        MapRotationAnimationMillis
                    } else {
                        0L
                    },
                    defaultMapBearing = defaultMapBearing,
                )
            } else {
                val update = CameraUpdateFactory.newCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                        .bearing(defaultMapBearing)
                        .build(),
                )
                if (animateRotation) {
                    map.animateCamera(update, MapRotationAnimationMillis.toInt())
                } else {
                    map.moveCamera(update)
                }
            }
        }
    }

    LaunchedEffect(isSatelliteView) {
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
                mapMoments = currentMapMoments,
                onLoaded = {
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

    DisposableEffect(mapView) {
        var map: MapLibreMap? = null
        var isMapTouchActive = false
        var isCameraMoving = false

        fun publishManualLocationPosition() {
            val readyMap = map ?: return
            manualLocationPosition = currentManualLocation?.let { location ->
                readyMap.projection.toScreenLocation(
                    LatLng(location.latitude, location.longitude),
                )
            }
        }

        val moveListener = MapLibreMap.OnCameraMoveListener {
            if (currentManualLocation != null) publishManualLocationPosition()
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
            previewCameraPosition = map?.cameraPosition
            if (shouldStopFollowing(cameraMoveReason)) {
                map?.cameraPosition?.zoom?.let(context::saveDefaultMapZoom)
            }
            cameraMoveReason =
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        }
        val longClickListener = MapLibreMap.OnMapLongClickListener { point ->
            mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            currentOnManualLocationChanged(
                SpurCoordinate(
                    latitude = point.latitude,
                    longitude = point.longitude,
                ),
            )
            true
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
                ?: return@OnMapClickListener false
            currentOnMomentClick(moment)
            true
        }
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMapTouchActive = true
                    currentOnMapGestureActiveChanged(true)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
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
            readyMap.addOnMapLongClickListener(longClickListener)
            readyMap.addOnMapClickListener(clickListener)
            publishManualLocationPosition()
        }
        onDispose {
            currentOnMapGestureActiveChanged(false)
            mapView.setOnTouchListener(null)
            map?.removeOnCameraMoveStartedListener(moveStartedListener)
            map?.removeOnCameraMoveListener(moveListener)
            map?.removeOnCameraIdleListener(idleListener)
            map?.removeOnMapLongClickListener(longClickListener)
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

    LaunchedEffect(photoToPlace) {
        val photo = photoToPlace ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            val location = map.currentSpurCoordinate(
                context = context,
                manual = manualLocation,
            )
            if (location == null) {
                currentOnPhotoPlacementFailed(photo)
            } else {
                currentOnMomentPlaced(
                    MapMoment(
                        id = photo.nameWithoutExtension,
                        type = MomentType.PHOTO,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        payload = photo.absolutePath,
                    ),
                )
            }
        }
    }

    LaunchedEffect(manualLocation) {
        mapView.getMapAsync { map ->
            map.showGpsLocationPuck(
                context = context,
                show = manualLocation == null,
            )
            manualLocationPosition = manualLocation?.let { location ->
                map.projection.toScreenLocation(
                    LatLng(location.latitude, location.longitude),
                )
            }
        }
    }

    LaunchedEffect(mapMoments) {
        mapView.getMapAsync { map ->
            map.style?.showMapMoments(context, mapMoments)
        }
    }

    LaunchedEffect(routePoints) {
        mapView.getMapAsync { map ->
            map.style?.showTourRoute(routePoints)
        }
    }

    LaunchedEffect(tourId, routePoints) {
        if (!shouldFitTourRoute(tourId, fittedTourId, routePoints.size)) return@LaunchedEffect
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
    }
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
private fun PhotoDetailDialog(
    photoPath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageRequest = remember(photoPath) {
        ImageRequest.Builder(context)
            .data(File(photoPath))
            .crossfade(PhotoDetailEnterMillis)
            .build()
    }
    var isVisible by remember(photoPath) { mutableStateOf(false) }
    var isClosing by remember(photoPath) { mutableStateOf(false) }

    fun dismissAnimated() {
        if (isClosing) return
        isClosing = true
        isVisible = false
        scope.launch {
            delay(PhotoDetailExitMillis.toLong())
            onDismiss()
        }
    }

    fun savePhoto() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                context.savePhotoToGallery(File(photoPath))
            }
            Toast.makeText(
                context,
                if (saved) "In Galerie gespeichert." else "Foto konnte nicht gespeichert werden.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) savePhoto() else Toast.makeText(
            context,
            "Zum Speichern braucht Spur Zugriff auf deine Bilder.",
            Toast.LENGTH_LONG,
        ).show()
    }

    LaunchedEffect(photoPath) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = PhotoDetailEnterMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = PhotoDetailEnterMillis,
                    easing = FastOutSlowInEasing,
                ),
                initialOffsetY = { height -> height / 10 },
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = PhotoDetailExitMillis,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = PhotoDetailExitMillis,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetY = { height -> height / 10 },
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                ZoomableAsyncImage(
                    model = imageRequest,
                    contentDescription = "Zoombares Foto in Vollbildansicht",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = ::dismissAnimated,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Ink,
                    ),
                ) {
                    LucideIcon(
                        paths = listOf("M18 6 6 18", "m6 6 12 12"),
                        strokeWidth = LucideBoldStrokeWidth,
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = "Foto schließen" },
                    )
                }
                Button(
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            storagePermissionLauncher.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            )
                        } else {
                            savePhoto()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Ink,
                    ),
                ) {
                    Text("In Galerie speichern")
                }
            }
        }
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
    mapMoments: List<MapMoment>,
    onLoaded: () -> Unit,
) {
    val cameraPosition = map.cameraPosition
    val styleLoaded: (Style) -> Unit = { style ->
        enableLocationTracking(
            context = context,
            map = map,
            style = style,
            centerOnLocation = centerOnLocation,
            manualLocation = manualLocation,
            initialMapZoom = initialMapZoom,
            defaultMapBearing = defaultMapBearing,
        )
        style.showTourRoute(routePoints)
        style.showMapMoments(context, mapMoments)
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

private fun Style.showMapMoments(
    context: Context,
    moments: List<MapMoment>,
) {
    val images = HashMap<String, android.graphics.Bitmap>(moments.size * 2)
    val features = moments.mapIndexed { index, moment ->
        val imageId = MapMomentImagePrefix + moment.id
        val marker = createMomentMarkerBitmap(context, moment, selected = false)
        images[imageId] = marker
        images[MapMomentClusterImagePrefix + moment.id] =
            createMomentClusterBitmap(context, marker)
        Feature.fromGeometry(
            Point.fromLngLat(moment.longitude, moment.latitude),
        ).apply {
            addStringProperty(MapMomentIdProperty, moment.id)
            addStringProperty(MapMomentImageProperty, imageId)
            addNumberProperty(MapMomentRepresentativeProperty, index)
        }
    }
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

    if (getLayer(MapMomentLayer) == null) {
        addLayer(
            SymbolLayer(MapMomentLayer, MapMomentSource)
                .withFilter(
                    Expression.neq(Expression.get("cluster"), true),
                )
                .withProperties(
                    iconImage(Expression.get(MapMomentImageProperty)),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
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
                    textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                    textSize(13f),
                    textColor(android.graphics.Color.WHITE),
                    textHaloColor(android.graphics.Color.rgb(35, 97, 74)),
                    textHaloWidth(5f),
                    textOffset(arrayOf(1.45f, -2.25f)),
                    textAnchor(Property.TEXT_ANCHOR_CENTER),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                    symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y),
                ),
        )
    } else {
        clusterLayer.setProperties(iconImage(clusterImage))
    }
}

private fun clusterMomentImageExpression(moments: List<MapMoment>): Expression {
    val fallback = MapMomentClusterImagePrefix + moments.firstOrNull()?.id.orEmpty()
    val stops = moments.mapIndexed { index, moment ->
        Expression.stop(index, MapMomentClusterImagePrefix + moment.id)
    }.toTypedArray()
    return Expression.match(
        Expression.toNumber(Expression.get(MapMomentRepresentativeProperty)),
        Expression.literal(fallback),
        *stops,
    )
}

private fun Style.showTourRoute(points: List<TrackPoint>) {
    val source = getSourceAs<GeoJsonSource>(TourRouteSource)
        ?: GeoJsonSource(TourRouteSource).also(::addSource)
    if (getLayer(TourRouteBorderLayer) == null) {
        val borderLayer = LineLayer(TourRouteBorderLayer, TourRouteSource).withProperties(
            lineColor(Color.White.toArgb()),
            lineWidth(TourRouteBorderWidthPixels),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (getLayer(TourRouteLayer) == null) {
            addLayer(borderLayer)
        } else {
            addLayerBelow(borderLayer, TourRouteLayer)
        }
    }
    if (getLayer(TourRouteLayer) == null) {
        addLayer(
            LineLayer(TourRouteLayer, TourRouteSource).withProperties(
                lineColor(Ink.toArgb()),
                lineWidth(TourRouteWidthPixels),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }
    if (points.size >= 2) {
        source.setGeoJson(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    points.map { Point.fromLngLat(it.longitude, it.latitude) },
                ),
            ),
        )
    } else {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    }
}

private fun Style.showSelectedTrackPoint(point: TrackPoint?) {
    val source = getSourceAs<GeoJsonSource>(SelectedTrackPointSource)
        ?: GeoJsonSource(SelectedTrackPointSource).also(::addSource)
    if (getLayer(SelectedTrackPointLayer) == null) {
        addLayer(
            CircleLayer(SelectedTrackPointLayer, SelectedTrackPointSource).withProperties(
                circleColor("#18201C"),
                circleRadius(7f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            ),
        )
    }
    if (point == null) {
        source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    } else {
        source.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)),
        )
    }
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
) {
    if (!context.hasLocationPermission()) return

    val locationComponent = map.locationComponent
    val options = LocationComponentOptions.builder(context)
        .foregroundTintColor(Ink.toArgb())
        .backgroundTintColor(Color.White.toArgb())
        .foregroundStaleTintColor(Ink.toArgb())
        .backgroundStaleTintColor(Color.White.toArgb())
        .bearingTintColor(Ink.toArgb())
        .accuracyColor(Ink.toArgb())
        .pulseEnabled(true)
        .pulseColor(Ink.toArgb())
        .pulseSingleDuration(LocationPulseDurationMillis.toFloat())
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

private const val MapMomentPreferences = "map-moments"
private const val MapMomentEntries = "entries"

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

private fun createMomentMarkerBitmap(
    context: Context,
    moment: MapMoment,
    selected: Boolean,
) =
    android.graphics.Bitmap.createBitmap(
            (MomentMarkerWidth * context.resources.displayMetrics.density).toInt(),
            (MomentMarkerHeight * context.resources.displayMetrics.density).toInt(),
            android.graphics.Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            val scale = context.resources.displayMetrics.density
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.rgb(67, 160, 71)
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
            paint.color = android.graphics.Color.WHITE
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawRoundRect(content, 7 * scale, 7 * scale, paint)
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(28 * scale, 47 * scale)
                    lineTo(34 * scale, 47 * scale)
                    lineTo(31 * scale, 54 * scale)
                    close()
                },
                paint,
            )

            val photo = if (moment.type == MomentType.PHOTO) {
                decodeMarkerPhoto(moment.payload)
            } else {
                null
            }
            if (photo != null) {
                val photoContent = android.graphics.RectF(content).apply {
                    inset(2 * scale, 2 * scale)
                }
                drawMarkerPhoto(canvas, paint, photoContent, photo, scale)
                photo.recycle()
            } else {
                paint.color = android.graphics.Color.rgb(24, 32, 28)
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2 * scale
                canvas.save()
                canvas.translate(5 * scale, 5 * scale)
                drawMomentGlyph(canvas, paint, scale, moment.type)
                canvas.restore()
            }
        }

private fun createMomentClusterBitmap(
    context: Context,
    marker: android.graphics.Bitmap,
): android.graphics.Bitmap {
    val scale = context.resources.displayMetrics.density
    return android.graphics.Bitmap.createBitmap(
        (70 * scale).toInt(),
        (66 * scale).toInt(),
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val cardWidth = 50 * scale
        val cardHeight = 50 * scale

        listOf(0f to 5f, 8f to 1f).forEach { (x, y) ->
            paint.color = android.graphics.Color.rgb(67, 160, 71)
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawRoundRect(
                x * scale,
                y * scale,
                x * scale + cardWidth,
                y * scale + cardHeight,
                10 * scale,
                10 * scale,
                paint,
            )
            paint.color = android.graphics.Color.WHITE
            canvas.drawRoundRect(
                (x + 3) * scale,
                (y + 3) * scale,
                x * scale + cardWidth - 3 * scale,
                y * scale + cardHeight - 3 * scale,
                7 * scale,
                7 * scale,
                paint,
            )
        }

        canvas.drawBitmap(marker, 4 * scale, 8 * scale, paint)
    }
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

private fun drawMomentGlyph(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    scale: Float,
    type: MomentType,
) {
    val paths = when (type) {
        MomentType.PHOTO -> listOf(
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            "M15 13a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        )
        MomentType.VIDEO -> listOf(
            "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
            "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2",
        )
        MomentType.VOICE -> listOf(
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3",
        )
        MomentType.EMOJI -> listOf(
            "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0",
            "M8 14s1.5 2 4 2 4-2 4-2",
            "M9 9h.01",
            "M15 9h.01",
        )
    }

    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    canvas.save()
    canvas.translate(12 * scale, 12 * scale)
    canvas.scale(1.17f * scale, 1.17f * scale)
    paths.forEach { pathData ->
        androidx.core.graphics.PathParser.createPathFromPathData(pathData)?.let {
            canvas.drawPath(it, paint)
        }
    }
    canvas.restore()
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun HistoryScreen(
    store: TourStore,
    revision: Long,
    isVisible: Boolean = true,
    onBack: () -> Unit,
    onOpenTour: (Long) -> Unit,
    onEditTour: (Long) -> Unit,
    onDeleteTour: (Long) -> Unit,
) {
    val context = LocalContext.current
    var tours by remember { mutableStateOf(emptyList<Tour>()) }
    var mapMoments by remember { mutableStateOf(emptyList<MapMoment>()) }
    var selectedTour by remember { mutableStateOf<Tour?>(null) }
    var tourToDelete by remember { mutableStateOf<Tour?>(null) }
    var selectedPhoto by remember { mutableStateOf<MapMoment?>(null) }
    BackHandler(enabled = isVisible, onBack = onBack)
    LaunchedEffect(revision) {
        val (loadedTours, loadedMoments) = withContext(Dispatchers.IO) {
            store.tours() to context.loadMapMoments()
        }
        tours = loadedTours
        mapMoments = loadedMoments
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
            IconButton(onClick = onBack) {
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tours, key = { it.id }) { tour ->
                    val photos = photoMomentsForTour(mapMoments, tour)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { selectedTour = tour },
                                onLongClick = { selectedTour = tour },
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                                    .padding(bottom = if (photos.isEmpty()) 0.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatDate(tour.startedAt),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = formatTourTime(tour),
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
                            if (photos.isNotEmpty()) {
                                TourPhotoStrip(
                                    photos = photos,
                                    onOpen = { selectedPhoto = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTour?.let { tour ->
        ModalBottomSheet(
            onDismissRequest = { selectedTour = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = formatDate(tour.startedAt),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${formatTourTime(tour)} · ${formatMeters(tour.distanceMeters)}",
                    color = Ink.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                HistoryAction(label = "Öffnen") {
                    selectedTour = null
                    onOpenTour(tour.id)
                }
                if (tour.endedAt == null) {
                    Text(
                        text = "Eine laufende Tour kannst du nach dem Stoppen bearbeiten.",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Ink.copy(alpha = 0.62f),
                    )
                } else {
                    HistoryAction(label = "Bearbeiten") {
                        selectedTour = null
                        onEditTour(tour.id)
                    }
                    HistoryAction(label = "Tour löschen", destructive = true) {
                        selectedTour = null
                        tourToDelete = tour
                    }
                }
            }
        }
    }

    tourToDelete?.let { tour ->
        AlertDialog(
            onDismissRequest = { tourToDelete = null },
            title = { Text("Tour löschen?") },
            text = { Text("Die Tour und alle gespeicherten Standortpunkte werden entfernt.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        tourToDelete = null
                        onDeleteTour(tour.id)
                    },
                ) {
                    Text("Löschen", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { tourToDelete = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    selectedPhoto?.let { photo ->
        PhotoDetailDialog(
            photoPath = photo.payload,
            onDismiss = { selectedPhoto = null },
        )
    }
}

@Composable
private fun TourPhotoStrip(
    photos: List<MapMoment>,
    onOpen: (MapMoment) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            bottom = 18.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = File(photo.payload),
                contentDescription = "Foto dieser Tour öffnen",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist)
                    .clickable { onOpen(photo) },
            )
        }
    }
}

@Composable
private fun HistoryAction(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            color = if (destructive) Color(0xFFB3261E) else Ink,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TourEditorScreen(
    store: TourStore,
    tourId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var points by remember(tourId) { mutableStateOf(emptyList<TrackPoint>()) }
    var startIndex by remember(tourId) { mutableStateOf(0) }
    var endIndex by remember(tourId) { mutableStateOf(0) }
    var selectedIndex by remember(tourId) { mutableStateOf(0) }
    var isSaving by remember(tourId) { mutableStateOf(false) }

    LaunchedEffect(tourId) {
        val result = withContext(Dispatchers.IO) {
            store.tour(tourId) to store.points(tourId)
        }
        tour = result.first
        points = result.second
        startIndex = 0
        endIndex = result.second.lastIndex.coerceAtLeast(0)
        selectedIndex = 0
    }

    val visiblePoints = if (
        points.isNotEmpty() &&
        startIndex in points.indices &&
        endIndex in points.indices
    ) {
        points.subList(startIndex, endIndex + 1)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TourEditorMap(
                points = visiblePoints,
                selectedPoint = points.getOrNull(selectedIndex),
            )
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapIconButton(
                    contentDescription = "Editor schließen",
                    onClick = onBack,
                ) {
                    BackIcon()
                }
                Surface(
                    modifier = Modifier.padding(start = 10.dp),
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
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Text(
                    text = tour?.let { formatDate(it.startedAt) } ?: "Tour wird geladen …",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${visiblePoints.size} von ${points.size} Standortpunkten",
                    modifier = Modifier.padding(top = 3.dp),
                    color = Ink.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (points.isNotEmpty()) {
                    PointScrubber(
                        points = points,
                        startIndex = startIndex,
                        endIndex = endIndex,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                if (points.size >= 2) {
                    RangeSlider(
                        value = startIndex.toFloat()..endIndex.toFloat(),
                        onValueChange = { range ->
                            val newStart = range.start.roundToInt()
                                .coerceIn(0, points.lastIndex - 1)
                            val newEnd = range.endInclusive.roundToInt()
                                .coerceIn(newStart + 1, points.lastIndex)
                            startIndex = newStart
                            endIndex = newEnd
                            selectedIndex = selectedIndex.coerceIn(newStart, newEnd)
                        },
                        valueRange = 0f..points.lastIndex.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = points.getOrNull(selectedIndex)?.let {
                            "Punkt ${selectedIndex + 1} · ${formatClock(it.recordedAt)}"
                        } ?: "Keine Standortpunkte",
                        modifier = Modifier.weight(1f),
                        color = Ink.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = visiblePoints.size > 2,
                        onClick = {
                            val removeAt = selectedIndex
                            points = points.toMutableList().apply { removeAt(removeAt) }
                            endIndex--
                            selectedIndex = removeAt.coerceAtMost(endIndex)
                                .coerceAtLeast(startIndex)
                        },
                    ) {
                        Text("Punkt löschen")
                    }
                }

                Button(
                    enabled = visiblePoints.size >= 2 && !isSaving,
                    onClick = {
                        isSaving = true
                        val retainedIds = retainedPointIds(points, startIndex, endIndex)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.updateTourPoints(tourId, retainedIds)
                            }
                            onSaved()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    Text(
                        text = if (isSaving) "Wird gespeichert …" else "Änderungen speichern",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PointScrubber(
    points: List<TrackPoint>,
    startIndex: Int,
    endIndex: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (endIndex <= startIndex) return
    Slider(
        value = selectedIndex.toFloat(),
        onValueChange = {
            onSelect(it.roundToInt().coerceIn(startIndex, endIndex))
        },
        valueRange = startIndex.toFloat()..endIndex.toFloat(),
        steps = (endIndex - startIndex - 1).coerceAtLeast(0),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Standortpunkt ${selectedIndex + 1} von ${points.size}"
            }
    )
}

@Composable
private fun TourEditorMap(
    points: List<TrackPoint>,
    selectedPoint: TrackPoint?,
) {
    val context = LocalContext.current
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
                style.showTourRoute(currentPoints)
                style.showSelectedTrackPoint(currentSelectedPoint)
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
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(point.latitude, point.longitude),
                    ),
                    140,
                )
            }
        }
    }

    LaunchedEffect(points) {
        mapView.getMapAsync { map ->
            map.style?.showTourRoute(points)
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
@OptIn(ExperimentalComposeUiApi::class)
private fun ActiveTourStopControl(
    tour: Tour,
    now: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlColors = LocalMapControlColors.current.inverted
    var armed by remember(tour.id) { mutableStateOf(false) }
    var dragOffset by remember(tour.id) { mutableFloatStateOf(0f) }
    var dragStartX by remember(tour.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val mainControlHeight = 60.dp
    val timeCapHeight = 32.dp
    val capOverlap = 8.dp
    val timeCapShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )

    Box(
        modifier = modifier.height(mainControlHeight + timeCapHeight - capOverlap),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .height(timeCapHeight)
                .mapControlShadow(timeCapShape)
                .graphicsLayer { alpha = 0.75f },
            color = controlColors.background,
            contentColor = controlColors.foreground,
            shape = timeCapShape,
        ) {
            Box(
                modifier = Modifier.padding(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = capOverlap,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Seit ${formatClock(tour.startedAt)} · ${
                        formatDuration(now - tour.startedAt)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(mainControlHeight)
                .mapControlShadow(CircleShape),
            color = controlColors.background,
            shape = CircleShape,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val handleSize = 52.dp
                val edgePadding = 4.dp
                val maximum = with(density) {
                    (maxWidth - handleSize - edgePadding * 2).toPx().coerceAtLeast(0f)
                }
                val edgePaddingPixels = with(density) { edgePadding.toPx() }

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
                            modifier = Modifier.graphicsLayer {
                                alpha = stopSwipePromptAlpha(dragOffset, maximum)
                            },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 68.dp, end = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = formatMeters(tour.distanceMeters),
                                color = controlColors.foreground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
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
                                StopRed.copy(alpha = 0.14f)
                            } else {
                                controlColors.foreground.copy(alpha = 0.14f)
                            },
                            CircleShape,
                        )
                        .semantics {
                            contentDescription = if (armed) {
                                "Nach rechts wischen, um die Tour zu beenden"
                            } else {
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
                        color = if (armed) StopRed else controlColors.foreground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeStopPrompt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 68.dp, end = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Swipe right",
            color = StopRed,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
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
    paths = listOf("M4 5h16", "M4 12h16", "M4 19h16"),
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
)

@Composable
private fun PlusIcon() = LucideIcon(
    paths = listOf("M5 12h14", "M12 5v14"),
)

@Composable
private fun FollowLocationIcon(selected: Boolean) {
    val transition = rememberInfiniteTransition(label = "Location following signal")
    val scale by transition.animateFloat(
        initialValue = if (selected) 0.94f else 1f,
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LocationPulseDurationMillis / 2,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Location following signal scale",
    )
    Box(modifier = Modifier.scale(scale)) {
        LucideIcon(
            paths = listOf(
                "M16.247 7.761a6 6 0 0 1 0 8.478",
                "M19.075 4.933a10 10 0 0 1 0 14.134",
                "M4.925 19.067a10 10 0 0 1 0-14.134",
                "M7.753 16.239a6 6 0 0 1 0-8.478",
                "M14 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0",
            ),
            color = LocalContentColor.current,
        )
    }
}

@Composable
private fun HistoryIcon() = LucideIcon(
    paths = listOf(
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    ),
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
private fun MapScreenPreview() {
    MapScreen(
        tour = null,
        isTourActive = false,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ActiveTourScreenPreview() {
    MapScreen(
        tour = Tour(
            id = 1,
            startedAt = System.currentTimeMillis() - 754_000,
            endedAt = null,
            distanceMeters = 1_840.0,
            pointCount = 42,
        ),
        isTourActive = true,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HistoryScreenPreview() {
    HistoryScreen(
        store = TourStore(LocalContext.current),
        revision = 0,
        onBack = {},
        onOpenTour = {},
        onEditTour = {},
        onDeleteTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}
