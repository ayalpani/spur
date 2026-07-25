package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log2
import kotlin.math.roundToInt

private val Sand = Color(0xFFF7F5F0)
private val Ink = Color(0xFF18201C)
private val Moss = Color(0xFF23614A)
private val FollowGreen = Color(0xFF43A873)
private val Mist = Color(0xFFE8EEE9)
private const val DefaultMapZoom = 17.5
private const val StreetMapStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val SatelliteMapStyleJson =
    """{"version":8,"sources":{"satellite-source":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Esri, Maxar, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-layer","type":"raster","source":"satellite-source"}]}"""
private const val MomentMarkerWidth = 62
private const val MomentMarkerHeight = 58
private const val MomentMarkerStroke = 3f
private const val MapPreviewPixels = 180
private const val LocationPulseDurationMillis = 2_300
private const val TourRouteSource = "tour-route-source"
private const val TourRouteLayer = "tour-route-layer"
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
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasLocationPermission())
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

    BackHandler(enabled = showHistory) { showHistory = false }

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
                AnimatedContent(
                    targetState = showHistory,
                    transitionSpec = {
                        val direction = if (targetState) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(direction, tween(340)) togetherWith
                            slideOutOfContainer(direction, tween(340))
                    },
                    label = "History navigation",
                ) { historyVisible ->
                    if (historyVisible) {
                        HistoryScreen(
                            store = store,
                            revision = historyRevision,
                            onBack = { showHistory = false },
                            onOpenTour = { id ->
                                displayedTourId = id
                                showHistory = false
                            },
                        )
                    } else {
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
                                                store.appendSimulatedLocation(startedId, coordinate)
                                            }
                                        }
                                    }
                                    ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, TrackingService::class.java)
                                            .putExtra(TrackingService.EXTRA_TOUR_ID, id),
                                    )
                                    val started = withContext(Dispatchers.IO) { store.tour(id) }
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
                            onOpenHistory = { showHistory = true },
                        )
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
    var followRequest by rememberSaveable { mutableStateOf(0) }
    var isFollowingLocation by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    var alternateMapPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isAlternateMapPreviewLoading by remember { mutableStateOf(true) }
    var showMomentSheet by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var photoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var mapMoments by remember { mutableStateOf(context.loadMapMoments()) }
    var manualLocation by remember { mutableStateOf(context.loadManualLocation()) }
    val momentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        label = { Text("Karte") },
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                    )
                    NavigationDrawerItem(
                        label = { Text("Touren") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenHistory()
                            }
                        },
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapSurface(
                followRequest = followRequest,
                isFollowingLocation = isFollowingLocation,
                isSatelliteView = isSatelliteView,
                manualLocation = manualLocation,
                mapMoments = mapMoments,
                routePoints = routePoints,
                photoToPlace = pendingPhoto,
                onAlternateMapPreviewChanged = { alternateMapPreview = it },
                onAlternateMapPreviewLoadingChanged = {
                    isAlternateMapPreviewLoading = it
                },
                onMomentPlaced = { moment ->
                    val updatedMoments = mapMoments + moment
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
                onFollowingInterrupted = { isFollowingLocation = false },
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { showMomentSheet = true },
                    modifier = Modifier.size(60.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Ink,
                    ),
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
                    Button(
                        onClick = onStartTour,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ink,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    ) {
                        Text(
                            text = "Tour starten",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                MapIconButton(
                    contentDescription = if (isFollowingLocation) {
                        "Eigenem Standort wird gefolgt"
                    } else {
                        "Eigenem Standort folgen"
                    },
                    onClick = {
                        isFollowingLocation = true
                        followRequest++
                    },
                ) {
                    FollowLocationIcon(selected = isFollowingLocation)
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
                Text(
                    text = "Auf der Karte ablegen",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Was möchtest du an dieser Stelle festhalten?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                MomentOption(label = "Sprachnachricht") {
                    Toast.makeText(
                        context,
                        "Sprachaufnahme kommt als Nächstes.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                MomentOption(label = "Emoji") {
                    Toast.makeText(context, "Emojimarker kommt als Nächstes.", Toast.LENGTH_SHORT)
                        .show()
                }
                MomentOption(label = "Video") {
                    Toast.makeText(context, "Videomarker kommt als Nächstes.", Toast.LENGTH_SHORT)
                        .show()
                }
                MomentOption(label = "Foto") {
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
private fun MomentOption(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(60.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Ink,
        ),
        content = content,
    )
}

@Composable
private fun MapStyleButton(
    contentDescription: String,
    preview: ImageBitmap?,
    isLoading: Boolean,
    fallbackPreview: Int,
    onClick: () -> Unit,
) {
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
            .semantics { this.contentDescription = contentDescription },
        shape = previewShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, Color.White),
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
    followRequest: Int,
    isFollowingLocation: Boolean,
    isSatelliteView: Boolean,
    manualLocation: SpurCoordinate?,
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
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMomentPlaced by rememberUpdatedState(onMomentPlaced)
    val currentOnPhotoPlacementFailed by rememberUpdatedState(onPhotoPlacementFailed)
    val currentOnManualLocationChanged by rememberUpdatedState(onManualLocationChanged)
    val currentOnFollowingInterrupted by rememberUpdatedState(onFollowingInterrupted)
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
    val currentIsFollowingLocation by rememberUpdatedState(isFollowingLocation)
    var markerPositions by remember {
        mutableStateOf<Map<String, android.graphics.PointF>>(emptyMap())
    }
    var manualLocationPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var previewCameraPosition by remember {
        mutableStateOf<org.maplibre.android.camera.CameraPosition?>(null)
    }
    var hasLoadedMapStyle by remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }

    LaunchedEffect(isFollowingLocation) {
        if (isFollowingLocation) currentOnAlternateMapPreviewLoadingChanged(false)
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
                routePoints = currentRoutePoints,
                onLoaded = {
                    hasLoadedMapStyle = true
                    previewCameraPosition = map.cameraPosition
                    if (currentIsFollowingLocation) {
                        map.followLocation(
                            context = context,
                            manualLocation = currentManualLocation,
                            transitionDuration = 0L,
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

        fun publishMarkerPositions() {
            val readyMap = map ?: return
            markerPositions = currentMapMoments.associate { moment ->
                moment.id to readyMap.projection.toScreenLocation(
                    LatLng(moment.latitude, moment.longitude),
                )
            }
            manualLocationPosition = currentManualLocation?.let { location ->
                readyMap.projection.toScreenLocation(
                    LatLng(location.latitude, location.longitude),
                )
            }
        }

        val moveListener = MapLibreMap.OnCameraMoveListener {
            publishMarkerPositions()
        }
        val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (shouldShowMapPreviewLoading(currentIsFollowingLocation, reason)) {
                currentOnAlternateMapPreviewLoadingChanged(true)
            }
            if (currentIsFollowingLocation && shouldStopFollowing(reason)) {
                map?.locationComponent?.cameraMode = CameraMode.NONE
                currentOnFollowingInterrupted()
            }
        }
        val idleListener = MapLibreMap.OnCameraIdleListener {
            publishMarkerPositions()
            previewCameraPosition = map?.cameraPosition
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
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.addOnCameraMoveStartedListener(moveStartedListener)
            readyMap.addOnCameraMoveListener(moveListener)
            readyMap.addOnCameraIdleListener(idleListener)
            readyMap.addOnMapLongClickListener(longClickListener)
            publishMarkerPositions()
        }
        onDispose {
            map?.removeOnCameraMoveStartedListener(moveStartedListener)
            map?.removeOnCameraMoveListener(moveListener)
            map?.removeOnCameraIdleListener(idleListener)
            map?.removeOnMapLongClickListener(longClickListener)
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
            )
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
            markerPositions = mapMoments.associate { moment ->
                moment.id to map.projection.toScreenLocation(
                    LatLng(moment.latitude, moment.longitude),
                )
            }
        }
    }

    LaunchedEffect(routePoints) {
        mapView.getMapAsync { map ->
            map.style?.showTourRoute(routePoints)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Interaktive Kartenansicht" },
        )

        val density = LocalDensity.current
        val manualPuckSizePx = with(density) { 52.dp.roundToPx() }
        val markerWidthPx = with(density) { MomentMarkerWidth.dp.roundToPx() }
        val markerHeightPx = with(density) { MomentMarkerHeight.dp.roundToPx() }
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
        mapMoments.forEach { moment ->
            val position = markerPositions[moment.id] ?: return@forEach
            val marker = remember(moment) {
                createMomentMarkerBitmap(context, moment, selected = false).asImageBitmap()
            }
            Image(
                bitmap = marker,
                contentDescription = "Abgelegtes Foto auf der Karte",
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = position.x.roundToInt() - markerWidthPx / 2,
                            y = position.y.roundToInt() - markerHeightPx,
                        )
                    }
                    .size(
                        width = MomentMarkerWidth.dp,
                        height = MomentMarkerHeight.dp,
                    )
                    .zIndex(position.y)
                    .clickable { onMomentClick(moment) },
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
        val purple = Color(0xFF6D28D9)
        drawCircle(purple.copy(alpha = 0.2f), radius = size.minDimension / 2)
        drawCircle(Color.White, radius = 10.dp.toPx())
        drawCircle(purple, radius = 6.dp.toPx())
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
    val photo = remember(photoPath) { decodePhotoDetail(context, photoPath) }
    DisposableEffect(photo) {
        onDispose { photo?.recycle() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            photo?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Foto in Vollbildansicht",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
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
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Foto schließen",
                )
            }
        }
    }
}

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
    routePoints: List<TrackPoint>,
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
        )
        style.showTourRoute(routePoints)
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

private fun Style.showTourRoute(points: List<TrackPoint>) {
    val source = getSourceAs<GeoJsonSource>(TourRouteSource)
        ?: GeoJsonSource(TourRouteSource).also(::addSource)
    if (getLayer(TourRouteLayer) == null) {
        addLayer(
            LineLayer(TourRouteLayer, TourRouteSource).withProperties(
                lineColor("#23614A"),
                lineWidth(6f),
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
) {
    if (!context.hasLocationPermission()) return

    val locationComponent = map.locationComponent
    val options = LocationComponentOptions.builder(context)
        .pulseEnabled(true)
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
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                DefaultMapZoom,
            ),
        )
    }
}

private fun MapLibreMap.followLocation(
    context: Context,
    manualLocation: SpurCoordinate?,
    transitionDuration: Long,
) {
    if (manualLocation == null && locationComponent.isLocationComponentActivated) {
        locationComponent.setCameraMode(
            CameraMode.TRACKING,
            transitionDuration,
            DefaultMapZoom,
            0.0,
            null,
            null,
        )
        return
    }

    val location = currentSpurCoordinate(context = context, manual = manualLocation) ?: return
    val update = CameraUpdateFactory.newCameraPosition(
        org.maplibre.android.camera.CameraPosition.Builder(cameraPosition)
            .target(LatLng(location.latitude, location.longitude))
            .zoom(DefaultMapZoom)
            .bearing(0.0)
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

private fun decodePhotoDetail(context: Context, path: String): android.graphics.Bitmap? =
    runCatching {
        val metrics = context.resources.displayMetrics
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(File(path)),
            ) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(
                    1f,
                    metrics.widthPixels.toFloat() / info.size.width,
                    metrics.heightPixels.toFloat() / info.size.height,
                )
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * scale).toInt()),
                    maxOf(1, (info.size.height * scale).toInt()),
                )
            }
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > metrics.widthPixels * 2 ||
                bounds.outHeight / sampleSize > metrics.heightPixels * 2
            ) {
                sampleSize *= 2
            }
            android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                },
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
    when (type) {
        MomentType.PHOTO -> {
            canvas.drawRoundRect(
                14 * scale,
                20 * scale,
                38 * scale,
                35 * scale,
                3 * scale,
                3 * scale,
                paint,
            )
            canvas.drawCircle(26 * scale, 27.5f * scale, 4.5f * scale, paint)
            canvas.drawLine(19 * scale, 20 * scale, 22 * scale, 16 * scale, paint)
            canvas.drawLine(22 * scale, 16 * scale, 30 * scale, 16 * scale, paint)
            canvas.drawLine(30 * scale, 16 * scale, 33 * scale, 20 * scale, paint)
        }
        MomentType.VIDEO -> {
            val path = android.graphics.Path().apply {
                moveTo(21 * scale, 18 * scale)
                lineTo(36 * scale, 26 * scale)
                lineTo(21 * scale, 34 * scale)
                close()
            }
            canvas.drawPath(path, paint)
        }
        MomentType.VOICE -> {
            listOf(20f to 5f, 26f to 10f, 32f to 5f).forEach { (x, halfHeight) ->
                canvas.drawLine(
                    x * scale,
                    (26 - halfHeight) * scale,
                    x * scale,
                    (26 + halfHeight) * scale,
                    paint,
                )
            }
        }
        MomentType.EMOJI -> {
            canvas.drawCircle(20 * scale, 22 * scale, 1.5f * scale, paint)
            canvas.drawCircle(32 * scale, 22 * scale, 1.5f * scale, paint)
            canvas.drawArc(
                19 * scale,
                21 * scale,
                33 * scale,
                34 * scale,
                20f,
                140f,
                false,
                paint,
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    store: TourStore,
    revision: Long,
    onBack: () -> Unit,
    onOpenTour: (Long) -> Unit,
) {
    var tours by remember { mutableStateOf(emptyList<Tour>()) }
    LaunchedEffect(revision) {
        tours = withContext(Dispatchers.IO) { store.tours() }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTour(tour.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
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
                                text = formatKilometers(tour.distanceMeters),
                                color = Moss,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTourStopControl(
    tour: Tour,
    now: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var armed by remember(tour.id) { mutableStateOf(false) }
    var dragOffset by remember(tour.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Surface(
        modifier = modifier.height(60.dp),
        color = Color.White,
        shape = CircleShape,
        shadowElevation = 4.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val handleSize = 52.dp
            val edgePadding = 4.dp
            val maximum = with(density) {
                (maxWidth - handleSize - edgePadding * 2).toPx().coerceAtLeast(0f)
            }
            val edgePaddingPixels = with(density) { edgePadding.toPx() }
            val dragState = rememberDraggableState { delta ->
                dragOffset = (dragOffset + delta).coerceIn(0f, maximum)
            }

            AnimatedContent(
                targetState = armed,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                },
                label = "Stop confirmation",
                modifier = Modifier.fillMaxSize(),
            ) { confirmationVisible ->
                if (confirmationVisible) {
                    SwipeStopPrompt()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 68.dp, end = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column {
                            Text(
                                text = formatKilometers(tour.distanceMeters),
                                color = Moss,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Seit ${formatClock(tour.startedAt)} · ${
                                    formatDuration(now - tour.startedAt)
                                }",
                                color = Ink.copy(alpha = 0.58f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                    .background(Mist, CircleShape)
                    .semantics {
                        contentDescription = if (armed) {
                            "Nach rechts wischen, um die Tour zu beenden"
                        } else {
                            "Tour beenden vorbereiten"
                        }
                    }
                    .clickable(enabled = !armed) {
                        armed = true
                        dragOffset = 0f
                    }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            if (!armed) {
                                armed = true
                                dragOffset = 0f
                            }
                        },
                        onDragStopped = {
                            if (shouldCompleteStopSwipe(dragOffset, maximum)) {
                                dragOffset = maximum
                                onStop()
                            } else {
                                Animatable(dragOffset).animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(180),
                                ) {
                                    dragOffset = value
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                StopIcon()
            }
        }
    }
}

@Composable
private fun SwipeStopPrompt() {
    val transition = rememberInfiniteTransition(label = "Stop arrows")
    val arrowAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Stop arrows alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 66.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Zum Stoppen wischen",
            modifier = Modifier.weight(1f),
            color = Ink.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "›››",
            color = Moss.copy(alpha = arrowAlpha),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StopIcon() {
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "Tour beenden" },
    ) {
        val side = 14.dp.toPx()
        drawRoundRect(
            color = Moss,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            size = androidx.compose.ui.geometry.Size(side, side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
    }
}

internal fun formatKilometers(distanceMeters: Double): String =
    String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1_000.0)

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
            color = if (selected) FollowGreen else Ink,
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
private fun LucideIcon(
    paths: List<String>,
    color: Color = Ink,
) {
    val parsedPaths = paths.map { path ->
        remember(path) { PathParser().parsePathString(path).toPath() }
    }
    Canvas(modifier = Modifier.size(32.dp)) {
        val scale = size.minDimension / 24f
        withTransform({
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            parsedPaths.forEach { path ->
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2f,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BackIcon() {
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "Zurück zur Karte" },
    ) {
        val strokeWidth = 2.2.dp.toPx()
        drawLine(Ink, Offset(19.dp.toPx(), 12.dp.toPx()), Offset(5.dp.toPx(), 12.dp.toPx()), strokeWidth)
        drawLine(Ink, Offset(5.dp.toPx(), 12.dp.toPx()), Offset(11.dp.toPx(), 6.dp.toPx()), strokeWidth)
        drawLine(Ink, Offset(5.dp.toPx(), 12.dp.toPx()), Offset(11.dp.toPx(), 18.dp.toPx()), strokeWidth)
    }
}

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
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}
