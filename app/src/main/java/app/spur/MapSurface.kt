package app.spur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@SuppressLint("MissingPermission")
internal fun MapSurface(
    modifier: Modifier = Modifier,
    tourId: Long?,
    activeTourId: Long?,
    isTourActive: Boolean,
    showTourEndpoints: Boolean,
    deferAlternateMapPreview: Boolean,
    isZoomControlInteracting: Boolean,
    tourDisplayRequest: Long,
    followRequest: Int,
    tourOverviewRequest: Int,
    isFollowingLocation: Boolean,
    locationPulseGeneration: Long,
    isSatelliteView: Boolean,
    manualLocation: SpurCoordinate?,
    defaultMapZoom: Double,
    zoomRequest: MapZoomRequest?,
    defaultMapBearing: Double,
    mapSettingsVisible: Boolean,
    mapMoments: List<MapMoment>,
    momentImageRevision: Long,
    routePoints: List<TrackPoint>,
    roadHistoryStore: TourStore?,
    roadHistoryFingerprint: RoadHistoryFingerprint,
    roadTraversalFingerprint: RoadHistoryFingerprint?,
    trailColors: TrailColors,
    homeBuilding: Feature?,
    selectedBuilding: Feature?,
    isBuildingSelectionMode: Boolean,
    isHomeStartPointSelection: Boolean,
    homeStartPointFocus: SpurCoordinate?,
    selectedTrackPoint: TrackPoint?,
    selectedTrackPointRequest: Long,
    momentToPlace: PendingMapMoment?,
    focusedMoment: MapMoment?,
    activeVoiceMoment: MapMoment?,
    voicePlaybackProgress: Float,
    onAlternateMapPreviewChanged: (ImageBitmap) -> Unit,
    onAlternateMapPreviewLoadingChanged: (Boolean) -> Unit,
    onViewportChanged: (MapViewport) -> Unit,
    onMomentPlaced: (MapMoment) -> Unit,
    onMomentPlacementFailed: (PendingMapMoment) -> Unit,
    onMomentClick: (MapMoment, Offset, PhotoOpenPreview?) -> Unit,
    onLocationClick: () -> Unit,
    onBuildingClick: (SelectedBuilding) -> Unit,
    onHomeStartPointChanged: (SpurCoordinate) -> Unit,
    onManualLocationChanged: (SpurCoordinate) -> Unit,
    onFollowingInterrupted: () -> Unit,
    onLocationPulseStarted: (Long) -> Unit,
    onLocationPulseResync: () -> Unit,
    onMovementChanged: (Boolean) -> Unit,
    onMapReadyChanged: (Boolean) -> Unit,
    onMapGestureActiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMomentPlaced by rememberUpdatedState(onMomentPlaced)
    val currentOnMomentPlacementFailed by rememberUpdatedState(onMomentPlacementFailed)
    val currentOnMomentClick by rememberUpdatedState(onMomentClick)
    val currentOnLocationClick by rememberUpdatedState(onLocationClick)
    val currentOnBuildingClick by rememberUpdatedState(onBuildingClick)
    val currentOnHomeStartPointChanged by rememberUpdatedState(onHomeStartPointChanged)
    val currentOnManualLocationChanged by rememberUpdatedState(onManualLocationChanged)
    val currentOnFollowingInterrupted by rememberUpdatedState(onFollowingInterrupted)
    val currentOnLocationPulseStarted by rememberUpdatedState(onLocationPulseStarted)
    val currentOnLocationPulseResync by rememberUpdatedState(onLocationPulseResync)
    val currentOnMovementChanged by rememberUpdatedState(onMovementChanged)
    val currentOnMapReadyChanged by rememberUpdatedState(onMapReadyChanged)
    val currentOnMapGestureActiveChanged by rememberUpdatedState(onMapGestureActiveChanged)
    val currentIsZoomControlInteracting by rememberUpdatedState(isZoomControlInteracting)
    val currentOnAlternateMapPreviewChanged by rememberUpdatedState(
        onAlternateMapPreviewChanged,
    )
    val currentOnAlternateMapPreviewLoadingChanged by rememberUpdatedState(
        onAlternateMapPreviewLoadingChanged,
    )
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentMapMoments by rememberUpdatedState(mapMoments)
    val currentRoutePoints by rememberUpdatedState(routePoints)
    val currentTrailColors by rememberUpdatedState(trailColors)
    val currentHomeBuilding by rememberUpdatedState(homeBuilding)
    val currentSelectedBuilding by rememberUpdatedState(selectedBuilding)
    val currentIsBuildingSelectionMode by rememberUpdatedState(isBuildingSelectionMode)
    val currentIsHomeStartPointSelection by rememberUpdatedState(isHomeStartPointSelection)
    val currentSelectedTrackPoint by rememberUpdatedState(selectedTrackPoint)
    val currentManualLocation by rememberUpdatedState(manualLocation)
    val currentFollowRequest by rememberUpdatedState(followRequest)
    val currentTourOverviewRequest by rememberUpdatedState(tourOverviewRequest)
    val currentZoomRequest by rememberUpdatedState(zoomRequest)
    val currentDefaultMapZoom by rememberUpdatedState(defaultMapZoom)
    val currentLocationPulseGeneration by rememberUpdatedState(locationPulseGeneration)
    val mapControlColors = LocalMapControlColors.current
    val locationMarkerColors = LocalLocationMarkerColors.current
    val currentLocationMarkerColors by rememberUpdatedState(locationMarkerColors)
    val currentIsFollowingLocation by rememberUpdatedState(isFollowingLocation)
    val currentIsSatelliteView by rememberUpdatedState(isSatelliteView)
    val currentDefaultMapBearing by rememberUpdatedState(defaultMapBearing)
    var roadProgressSnapshot by remember { mutableStateOf(RoadProgressSnapshot()) }
    val roadProgressTracker = remember { RoadProgressTracker() }
    var roadProgressTourId by remember { mutableStateOf<Long?>(null) }
    var lastRoadProgressPointId by remember { mutableStateOf<Long?>(null) }
    var roadProgressContextKey by remember { mutableStateOf<String?>(null) }
    var roadTraversalCursor by remember { mutableStateOf(RoadTraversalCursor()) }
    var roadTraversalContext by remember { mutableStateOf<RoadTraversalContext?>(null) }
    var roadHistoryMapRevision by remember { mutableLongStateOf(0L) }
    var roadNetworkLoadRevision by remember { mutableLongStateOf(0L) }
    var roadNetworkReadyCameraKey by remember {
        mutableStateOf<RoadNetworkCameraKey?>(null)
    }
    var loadedRoads by remember { mutableStateOf(emptyList<RenderedRoadSegment>()) }
    var loadedRoadsCameraKey by remember { mutableStateOf<RoadNetworkCameraKey?>(null) }
    var loadedRoadsRevision by remember { mutableLongStateOf(0L) }
    var appliedRoadTraversalLoad by remember {
        mutableStateOf<Pair<String, Long>?>(null)
    }
    var appliedRoadCoverageLoad by remember {
        mutableStateOf<Pair<String, Long>?>(null)
    }
    var roadHistoryCameraKey by remember { mutableStateOf<RoadNetworkCameraKey?>(null) }
    val currentRoadHistoryCameraKey by rememberUpdatedState(roadHistoryCameraKey)
    var renderedRoadCoverageContextKey by remember { mutableStateOf<String?>(null) }
    var renderedRoadCoverageSegments by remember {
        mutableStateOf(emptyList<List<SpurCoordinate>>())
    }
    var reconciledRoadHistoryBaseline by remember(roadHistoryStore) {
        mutableStateOf<RoadHistoryFingerprint?>(null)
    }
    var manualLocationPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var previewCameraPosition by remember {
        mutableStateOf<org.maplibre.android.camera.CameraPosition?>(null)
    }
    var pendingMapMoment by remember { mutableStateOf<MapMoment?>(null) }
    var pendingMomentPosition by remember { mutableStateOf<android.graphics.PointF?>(null) }
    var preparedMapMoments by remember { mutableStateOf<PreparedMapMoments?>(null) }
    var currentLocation by remember { mutableStateOf<SpurCoordinate?>(null) }
    var isAtHome by remember { mutableStateOf(false) }
    var renderedVoicePlaybackId by remember { mutableStateOf<String?>(null) }
    var mapStyleRevision by remember { mutableStateOf(0) }
    var hasLoadedMapStyle by remember { mutableStateOf(false) }
    var isSelectedTrackPointVisible by remember { mutableStateOf(false) }
    var fittedTourId by remember { mutableStateOf<Long?>(null) }
    var fittedTourDisplayRequest by remember { mutableLongStateOf(-1L) }
    var lastMapSettingsBearing by remember { mutableStateOf(defaultMapBearing) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }
    val roadCoverageStore = remember(roadHistoryStore) {
        roadHistoryStore?.let { RoadCoverageStore(context) }
    }
    val roadTraversalStore = remember(roadHistoryStore) {
        roadHistoryStore?.let { RoadTraversalStore(context) }
    }

    DisposableEffect(roadCoverageStore) {
        onDispose { roadCoverageStore?.close() }
    }

    DisposableEffect(roadTraversalStore) {
        onDispose { roadTraversalStore?.close() }
    }

    DisposableEffect(context, lifecycle, manualLocation) {
        if (!context.hasLocationPermission() || manualLocation != null) {
            currentLocation = null
            isAtHome = false
            currentOnMovementChanged(false)
            onDispose {}
        } else {
            var updatesRequested = false
            var isMoving = false
            val client = LocationServices.getFusedLocationProviderClient(context)
            fun publishLocation(location: android.location.Location) {
                val settings = context.loadHomeAutoStartSettings()
                val coordinate = SpurCoordinate(location.latitude, location.longitude)
                val normalizedCoordinate = normalizedHomeCoordinate(settings, coordinate)
                currentLocation = normalizedCoordinate
                val locationIsAtHome = isWithinHomeZone(settings, coordinate)
                isAtHome = locationIsAtHome
                val nextIsMoving = movingForMapSignal(
                    isAtHome = locationIsAtHome,
                    speedKilometersPerHour = location.speed
                        .takeIf { location.hasSpeed() }
                        ?.times(3.6f)
                        ?.toDouble(),
                    wasMoving = isMoving,
                )
                if (nextIsMoving != isMoving) {
                    isMoving = nextIsMoving
                    currentOnMovementChanged(isMoving)
                }
                mapView.getMapAsync { map ->
                    if (map.locationComponent.isLocationComponentActivated) {
                        map.locationComponent.forceLocationUpdate(
                            android.location.Location(location).apply {
                                latitude = normalizedCoordinate.latitude
                                longitude = normalizedCoordinate.longitude
                            },
                        )
                    }
                }
            }
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!updatesRequested) return
                    val location = result.lastLocation ?: return
                    publishLocation(location)
                }
            }

            fun startLocationUpdates() {
                if (updatesRequested) return
                updatesRequested = true
                client.lastLocation.addOnSuccessListener { location ->
                    if (updatesRequested && location != null) {
                        publishLocation(location)
                    }
                }
                client.requestLocationUpdates(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                        .setMinUpdateIntervalMillis(1_000L)
                        .build(),
                    callback,
                    Looper.getMainLooper(),
                )
            }

            fun stopLocationUpdates() {
                if (!updatesRequested) return
                updatesRequested = false
                client.removeLocationUpdates(callback)
                isMoving = false
                currentOnMovementChanged(false)
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> startLocationUpdates()
                    Lifecycle.Event.ON_PAUSE -> stopLocationUpdates()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                startLocationUpdates()
            }
            onDispose {
                lifecycle.removeObserver(observer)
                stopLocationUpdates()
            }
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
                        targetZoom = map.cameraPosition.zoom,
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

    LaunchedEffect(zoomRequest?.id) {
        val request = zoomRequest ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (currentZoomRequest?.id != request.id) return@getMapAsync
            if (currentIsFollowingLocation) {
                if (map.locationComponent.isLocationComponentActivated) {
                    map.locationComponent.cameraMode = CameraMode.NONE
                }
                currentOnFollowingInterrupted()
            }
            val update = CameraUpdateFactory.zoomTo(request.zoom)
            if (request.animated) {
                map.animateCamera(update, MapZoomButtonAnimationMillis)
            } else {
                map.moveCamera(update)
            }
        }
    }

    LaunchedEffect(isSatelliteView) {
        currentOnAlternateMapPreviewLoadingChanged(true)
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            if (hasLoadedMapStyle) {
                map.style?.showSatelliteBaseMap(
                    satellite = isSatelliteView,
                )
                previewCameraPosition = map.cameraPosition
                map.mapViewport(isSatelliteView)?.let(currentOnViewportChanged)
                return@getMapAsync
            }
            setMapStyle(
                context = context,
                map = map,
                satellite = isSatelliteView,
                centerOnLocation = !hasLoadedMapStyle,
                manualLocation = manualLocation,
                initialMapZoom = defaultMapZoom,
                defaultMapBearing = defaultMapBearing,
                routePoints = currentRoutePoints,
                trailColors = currentTrailColors,
                locationMarkerColors = currentLocationMarkerColors,
                onLoaded = {
                    mapStyleRevision++
                    hasLoadedMapStyle = true
                    if (currentManualLocation == null) {
                        currentLocation = map.currentSpurCoordinate(
                            context = context,
                            manual = null,
                        )
                    }
                    previewCameraPosition = map.cameraPosition
                    map.mapViewport(currentIsSatelliteView)?.let(currentOnViewportChanged)
                    if (currentIsFollowingLocation) {
                        map.followLocation(
                            context = context,
                            manualLocation = currentManualLocation,
                            transitionDuration = 0L,
                            targetZoom = currentDefaultMapZoom,
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

    DisposableEffect(
        previewCameraPosition,
        isSatelliteView,
        deferAlternateMapPreview,
    ) {
        val cameraPosition = previewCameraPosition
        if (cameraPosition == null || deferAlternateMapPreview) {
            onDispose {}
        } else {
            var disposed = false
            var snapshotter: MapSnapshotter? = null
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
            val startSnapshot = Runnable {
                if (disposed) return@Runnable
                snapshotter = MapSnapshotter(context, options).also { startedSnapshotter ->
                    startedSnapshotter.start(
                        { snapshot ->
                            if (!disposed) {
                                currentOnAlternateMapPreviewChanged(
                                    snapshot.bitmap.asImageBitmap(),
                                )
                                currentOnAlternateMapPreviewLoadingChanged(false)
                            }
                        },
                        { _ ->
                            if (!disposed) {
                                currentOnAlternateMapPreviewLoadingChanged(false)
                            }
                        },
                    )
                }
            }
            mapView.postDelayed(startSnapshot, MapPreviewIdleDelayMillis)
            onDispose {
                disposed = true
                mapView.removeCallbacks(startSnapshot)
                snapshotter?.cancel()
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
        var roadNetworkNeedsLoad = true
        var roadSourceChanged = false
        var isMapTouchActive = false
        var isCameraMoving = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var holdStart = PointF()
        var isTapCandidate = false
        var immediatePhotoId: String? = null
        var immediatePhotoExpiresAt = 0L
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

        fun publishHomeStartPoint() {
            if (!currentIsHomeStartPointSelection) return
            val target = map?.cameraPosition?.target ?: return
            currentOnHomeStartPointChanged(
                SpurCoordinate(
                    latitude = target.latitude,
                    longitude = target.longitude,
                ),
            )
        }

        fun momentAt(readyMap: MapLibreMap, screenPoint: PointF): MapMoment? {
            val momentId = readyMap.queryRenderedFeatures(
                screenPoint,
                MapMomentLayer,
            ).firstOrNull()?.getStringProperty(MapMomentIdProperty)
            return currentMapMoments.firstOrNull { it.id == momentId }
        }

        fun dispatchMomentClick(moment: MapMoment, screenPoint: PointF) {
            currentOnMomentClick(
                moment,
                Offset(screenPoint.x, screenPoint.y),
                preparedMapMoments
                    ?.photoPreviews
                    ?.get(moment.id)
                    ?.let {
                        PhotoOpenPreview(
                            image = it.bitmap.asImageBitmap(),
                            aspectRatio = it.aspectRatio,
                        )
                    },
            )
        }

        val moveListener = MapLibreMap.OnCameraMoveListener {
            if (currentManualLocation != null) publishManualLocationPosition()
            if (pendingMapMoment != null) publishPendingMomentPosition()
        }
        var cameraMoveReason =
            MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            isCameraMoving = true
            roadNetworkNeedsLoad = true
            roadNetworkReadyCameraKey = null
            cameraMoveReason = reason
            if (shouldStopFollowing(reason)) {
                isSelectedTrackPointVisible = false
                currentOnMapGestureActiveChanged(true)
            }
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
            publishHomeStartPoint()
            map?.takeUnless { currentIsZoomControlInteracting }?.let { readyMap ->
                val shouldRefreshRoadHistory =
                    readyMap.roadNetworkCameraKey() != currentRoadHistoryCameraKey
                if (shouldRefreshRoadHistory) roadHistoryMapRevision++
            }
            previewCameraPosition = map?.cameraPosition
            map?.mapViewport(currentIsSatelliteView)?.let(currentOnViewportChanged)
            cameraMoveReason =
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        }
        val clickListener = MapLibreMap.OnMapClickListener { point ->
            val readyMap = map ?: return@OnMapClickListener false
            val screenPoint = readyMap.projection.toScreenLocation(point)
            if (currentIsBuildingSelectionMode) {
                if (!canSelectHomeBuilding(readyMap.cameraPosition.zoom)) {
                    return@OnMapClickListener false
                }
                val building = readyMap.queryRenderedFeatures(
                    screenPoint,
                    MapBuildingLayer,
                ).firstOrNull() ?: return@OnMapClickListener false
                val coordinate = SpurCoordinate(
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
                val selectedFeature = buildingFeatureAt(building, coordinate)
                    ?: return@OnMapClickListener false
                currentOnBuildingClick(
                    SelectedBuilding(
                        coordinate = homeCoordinate(selectedFeature) ?: coordinate,
                        feature = selectedFeature,
                    ),
                )
                return@OnMapClickListener true
            }
            if (currentIsHomeStartPointSelection) return@OnMapClickListener true
            val cluster = readyMap.queryRenderedFeatures(
                screenPoint,
                MapMomentClusterLayer,
            ).firstOrNull()
            if (cluster != null) {
                val source = readyMap.style?.getSourceAs<GeoJsonSource>(MapMomentSource)
                    ?: return@OnMapClickListener false
                val expansionZoom = mapMomentClusterExpansionZoom(
                    source.getClusterExpansionZoom(cluster),
                )
                val clusterPoint = (cluster.geometry() as? org.maplibre.geojson.Point)
                    ?.let { LatLng(it.latitude(), it.longitude()) }
                    ?: point
                readyMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(clusterPoint, expansionZoom),
                    MapRotationAnimationMillis.toInt(),
                )
                return@OnMapClickListener true
            }
            val location = if (
                currentManualLocation == null &&
                currentSelectedTrackPoint == null
            ) {
                readyMap.currentSpurCoordinate(context, manual = null)
            } else {
                null
            }
            val locationPoint = location?.let {
                readyMap.projection.toScreenLocation(
                    LatLng(it.latitude, it.longitude),
                )
            }
            if (
                locationPoint != null &&
                isWithinLocationHitTarget(
                    clickX = screenPoint.x,
                    clickY = screenPoint.y,
                    locationX = locationPoint.x,
                    locationY = locationPoint.y,
                    hitTargetSize = LocationPuckHitTargetDp *
                        context.resources.displayMetrics.density,
                )
            ) {
                currentOnLocationClick()
                return@OnMapClickListener true
            }
            val moment = momentAt(readyMap, screenPoint)
            if (moment != null) {
                val now = android.os.SystemClock.uptimeMillis()
                if (moment.id != immediatePhotoId || now > immediatePhotoExpiresAt) {
                    dispatchMomentClick(moment, screenPoint)
                }
                return@OnMapClickListener true
            }
            val building = readyMap.queryRenderedFeatures(
                screenPoint,
                MapBuildingLayer,
            ).firstOrNull()
            if (building != null) {
                val coordinate = SpurCoordinate(
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
                val selectedFeature = buildingFeatureAt(building, coordinate)
                    ?: return@OnMapClickListener false
                currentOnBuildingClick(
                    SelectedBuilding(
                        coordinate = coordinate,
                        feature = selectedFeature,
                    ),
                )
                return@OnMapClickListener true
            }
            false
        }
        val sourceChangedListener = MapView.OnSourceChangedListener { sourceId ->
            if (map?.isOsmRoadSource(sourceId) == true) {
                roadNetworkNeedsLoad = true
                roadSourceChanged = true
            }
        }
        val mapIdleListener = MapView.OnDidBecomeIdleListener {
            if (!roadNetworkNeedsLoad) return@OnDidBecomeIdleListener
            roadNetworkNeedsLoad = false
            val readyMap = map ?: return@OnDidBecomeIdleListener
            val cameraKey = readyMap.roadNetworkCameraKey()
            roadNetworkReadyCameraKey = cameraKey
            if (cameraKey != null && cameraKey != loadedRoadsCameraKey) {
                roadTraversalContext = null
            }
            val shouldLoad = cameraKey != null &&
                (cameraKey != loadedRoadsCameraKey || roadSourceChanged)
            roadSourceChanged = false
            if (!shouldLoad) return@OnDidBecomeIdleListener
            roadNetworkLoadRevision++
        }
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMapTouchActive = true
                    isTapCandidate = true
                    cancelManualLocationHold()
                    holdStart = PointF(event.x, event.y)
                    manualLocationHold = Runnable {
                        if (currentIsBuildingSelectionMode) return@Runnable
                        isTapCandidate = false
                        val point = map?.projection?.fromScreenLocation(holdStart)
                            ?: return@Runnable
                        context.vibrateManualWaypoint()
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
                        isTapCandidate = false
                        cancelManualLocationHold()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isTapCandidate = false
                    cancelManualLocationHold()
                }
                MotionEvent.ACTION_UP -> {
                    cancelManualLocationHold()
                    if (
                        isTapCandidate &&
                        !currentIsBuildingSelectionMode &&
                        !currentIsHomeStartPointSelection
                    ) {
                        val screenPoint = PointF(event.x, event.y)
                        val photo = map?.let { momentAt(it, screenPoint) }
                            ?.takeIf { it.type == MomentType.PHOTO }
                        if (photo != null) {
                            immediatePhotoId = photo.id
                            immediatePhotoExpiresAt =
                                android.os.SystemClock.uptimeMillis() +
                                    ViewConfiguration.getDoubleTapTimeout() * 2L
                            dispatchMomentClick(photo, screenPoint)
                        }
                    }
                    isTapCandidate = false
                    isMapTouchActive = false
                    if (!isCameraMoving) currentOnMapGestureActiveChanged(false)
                }
                MotionEvent.ACTION_CANCEL -> {
                    isTapCandidate = false
                    cancelManualLocationHold()
                    isMapTouchActive = false
                    if (!isCameraMoving) currentOnMapGestureActiveChanged(false)
                }
            }
            false
        }
        mapView.addOnSourceChangedListener(sourceChangedListener)
        mapView.addOnDidBecomeIdleListener(mapIdleListener)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.addOnCameraMoveStartedListener(moveStartedListener)
            readyMap.addOnCameraMoveListener(moveListener)
            readyMap.addOnCameraIdleListener(idleListener)
            readyMap.addOnMapClickListener(clickListener)
            publishManualLocationPosition()
            publishPendingMomentPosition()
        }
        onDispose {
            cancelManualLocationHold()
            currentOnMapGestureActiveChanged(false)
            mapView.setOnTouchListener(null)
            mapView.removeOnSourceChangedListener(sourceChangedListener)
            mapView.removeOnDidBecomeIdleListener(mapIdleListener)
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
                targetZoom = currentDefaultMapZoom,
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
                    pointZoom = defaultMapZoom,
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
        showTourEndpoints,
        mapStyleRevision,
    ) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                style.showSelectedTrackPoint(null)
                style.showTourEndpoints(
                    currentRoutePoints.takeIf { showTourEndpoints }.orEmpty(),
                    currentTrailColors,
                )
            }
            currentSelectedTrackPoint?.let { point ->
                map.locationComponent.cameraMode = CameraMode.NONE
                val update = CameraUpdateFactory.newCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                        .target(LatLng(point.latitude, point.longitude))
                        .padding(0.0, 0.0, 0.0, 0.0)
                        .build(),
                )
                if (selectedTrackPointRequest == 0L) {
                    map.moveCamera(update)
                } else {
                    map.animateCamera(update, EditorPointTransitionDurationMillis)
                }
            }
        }
    }

    LaunchedEffect(selectedTrackPoint?.id, selectedTrackPointRequest) {
        isSelectedTrackPointVisible = selectedTrackPoint != null
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

    val visibleHomeStatusLocation = currentLocation?.takeIf {
        isAtHome && manualLocation == null && selectedTrackPoint == null
    }
    LaunchedEffect(
        visibleHomeStatusLocation,
        mapStyleRevision,
        mapControlColors,
    ) {
        mapView.getMapAsync { map ->
            map.style?.showHomeStatus(
                context = context.applicationContext,
                colors = mapControlColors,
                coordinate = visibleHomeStatusLocation,
            )
        }
    }

    LaunchedEffect(mapMoments, momentImageRevision) {
        preparedMapMoments = withContext(Dispatchers.IO) {
            prepareMapMoments(
                context = context.applicationContext,
                moments = mapMoments,
            )
        }
    }

    LaunchedEffect(
        preparedMapMoments,
        mapStyleRevision,
    ) {
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

    LaunchedEffect(homeBuilding, selectedBuilding, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showHighlightedBuildings(
                home = currentHomeBuilding,
                selected = currentSelectedBuilding,
            )
        }
    }

    LaunchedEffect(isBuildingSelectionMode, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showSelectableHomeBuildings(isBuildingSelectionMode)
        }
    }

    LaunchedEffect(isHomeStartPointSelection, homeStartPointFocus) {
        if (!isHomeStartPointSelection) return@LaunchedEffect
        val focus = homeStartPointFocus ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(focus.latitude, focus.longitude),
                    HomeBuildingSelectionZoom,
                ),
                MapRotationAnimationMillis.toInt(),
            )
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

    LaunchedEffect(routePoints, trailColors, showTourEndpoints, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        val points = routePoints
        val routeFeatures = withContext(Dispatchers.Default) {
            tourRouteFeatures(points)
        }
        mapView.getMapAsync { map ->
            if (points !== currentRoutePoints) return@getMapAsync
            map.style?.let { style ->
                style.showTourRoute(routeFeatures, currentTrailColors)
                style.showTourEndpoints(
                    points.takeIf { showTourEndpoints }.orEmpty(),
                    currentTrailColors,
                )
            }
        }
    }

    LaunchedEffect(
        mapStyleRevision,
        roadNetworkLoadRevision,
        isZoomControlInteracting,
    ) {
        if (mapStyleRevision == 0 || isZoomControlInteracting) return@LaunchedEffect
        val expectedCameraKey = roadNetworkReadyCameraKey ?: return@LaunchedEffect
        val map = suspendCancellableCoroutine<MapLibreMap> { continuation ->
            mapView.getMapAsync { readyMap ->
                if (continuation.isActive) continuation.resume(readyMap)
            }
        }
        if (map.roadNetworkCameraKey() != expectedCameraKey) return@LaunchedEffect
        val target = map.cameraPosition.target ?: return@LaunchedEffect
        val center = SpurCoordinate(target.latitude, target.longitude)
        val polylines = map.osmRoadPolylines(
            viewport = mapView.roadQueryViewport(),
            near = center,
        )
        val workJob = kotlin.coroutines.coroutineContext[Job]
        val roads = withContext(Dispatchers.Default) {
            if (workJob?.isActive == false) {
                emptyList()
            } else {
                intersectionRoadEdges(polylines)
            }
        }
        if (
            workJob?.isActive == false ||
            roadNetworkReadyCameraKey != expectedCameraKey ||
            map.roadNetworkCameraKey() != expectedCameraKey
        ) {
            return@LaunchedEffect
        }
        loadedRoads = roads
        loadedRoadsCameraKey = expectedCameraKey
        loadedRoadsRevision++
    }

    LaunchedEffect(
        mapStyleRevision,
        roadHistoryMapRevision,
        loadedRoadsRevision,
        roadTraversalFingerprint,
        isTourActive,
        tourId,
        roadHistoryStore,
        roadTraversalStore,
    ) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        val traversalFingerprint = roadTraversalFingerprint ?: return@LaunchedEffect
        val map = suspendCancellableCoroutine<MapLibreMap> { continuation ->
            mapView.getMapAsync { readyMap ->
                if (continuation.isActive) continuation.resume(readyMap)
            }
        }
        if (map.cameraPosition.zoom < RoadHistoryDetailZoom) return@LaunchedEffect
        val cameraKey = map.roadNetworkCameraKey()
        val target = map.cameraPosition.target
        val historyStore = roadHistoryStore
        val traversalStore = roadTraversalStore
        if (
            cameraKey == null ||
            target == null ||
            historyStore == null ||
            traversalStore == null
        ) {
            roadTraversalContext = RoadTraversalContext(
                key = "unavailable:${traversalFingerprint.cacheIdentity()}",
                completedRoads = emptyMap(),
            )
            return@LaunchedEffect
        }

        val cacheKey = cameraKey.roadTraversalCacheKey()
        val contextKey = buildString {
            append(cacheKey)
            append(':')
            append(traversalFingerprint.cacheIdentity())
            append(':')
            append(tourId.takeIf { isTourActive } ?: "none")
        }
        val cached = withContext(Dispatchers.IO) {
            traversalStore.traversals(cacheKey)
        }
        val cacheIsCurrent = cached?.fingerprint == traversalFingerprint
        val roadLoad = cacheKey to loadedRoadsRevision
        val roadLoadIsApplied = appliedRoadTraversalLoad == roadLoad
        val needsRoads = isTourActive || !cacheIsCurrent || !roadLoadIsApplied
        val center = SpurCoordinate(target.latitude, target.longitude)
        if (needsRoads && loadedRoadsCameraKey != cameraKey) {
            roadTraversalContext = null
            return@LaunchedEffect
        }
        val roads = loadedRoads.takeIf { needsRoads }.orEmpty()
        val workJob = kotlin.coroutines.coroutineContext[Job]
        if (workJob?.isActive == false) return@LaunchedEffect

        val refreshesRoadGeometry = cacheIsCurrent && !roadLoadIsApplied
        val completedRoads = when {
            cacheIsCurrent && !refreshesRoadGeometry -> cached.completedRoads
            roads.isEmpty() -> cached?.completedRoads
                ?.takeIf { cacheIsCurrent }
                .orEmpty()
            else -> {
                val routes = withContext(Dispatchers.IO) {
                    historyStore.roadHistoryRoutes(
                        bounds = roadHistoryBounds(
                            center = center,
                            radiusMeters = RoadNetworkRadiusMeters +
                                RoadHistoryQueryPaddingMeters,
                        ),
                        excludingTourId = tourId.takeIf { isTourActive },
                    )
                }
                val completed = withContext(Dispatchers.Default) {
                    historicalRoadTraversals(
                        routes = routes,
                        roads = roads,
                        shouldContinue = { workJob?.isActive != false },
                    )
                }
                if (workJob?.isActive == false) return@LaunchedEffect
                val combined = if (refreshesRoadGeometry) {
                    cached?.completedRoads.orEmpty() + completed
                } else {
                    completed
                }
                withContext(Dispatchers.IO) {
                    traversalStore.replace(
                        cacheKey = cacheKey,
                        traversals = CachedRoadTraversals(
                            fingerprint = traversalFingerprint,
                            completedRoads = combined,
                        ),
                    )
                }
                combined
            }
        }
        if (needsRoads) appliedRoadTraversalLoad = roadLoad
        val analyzer = if (isTourActive && roads.isNotEmpty()) {
            withContext(Dispatchers.Default) { RoadTraversalAnalyzer(roads) }
        } else {
            null
        }
        if (workJob?.isActive == false) return@LaunchedEffect
        roadTraversalContext = RoadTraversalContext(
            key = "$contextKey:${loadedRoadsRevision.takeIf { needsRoads } ?: "cached"}",
            completedRoads = completedRoads,
            analyzer = analyzer,
        )
    }

    LaunchedEffect(
        roadTraversalContext,
        tourId,
        isTourActive,
        routePoints.lastOrNull()?.id,
        mapStyleRevision,
    ) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        val contextForRoads = roadTraversalContext ?: return@LaunchedEffect
        val contextChanged = roadProgressContextKey != contextForRoads.key
        val tourChanged = roadProgressTourId != tourId
        val latestPointId = routePoints.lastOrNull()?.id
        val lastProcessedIndex = lastRoadProgressPointId?.let { pointId ->
            routePoints.binarySearchBy(pointId) { it.id }
        } ?: -1
        val requiresReplay = contextChanged || tourChanged || lastProcessedIndex < 0

        if (!isTourActive || contextForRoads.analyzer == null) {
            val snapshot = roadProgressTracker.replaceCompleted(
                contextForRoads.completedRoads.values,
            )
            roadProgressTourId = tourId
            roadProgressContextKey = contextForRoads.key
            lastRoadProgressPointId = null
            roadTraversalCursor = RoadTraversalCursor()
            roadProgressSnapshot = snapshot
            return@LaunchedEffect
        }
        if (!requiresReplay && latestPointId == lastRoadProgressPointId) {
            return@LaunchedEffect
        }

        val routeToProcess = if (requiresReplay) {
            routePoints
        } else {
            routePoints.subList(lastProcessedIndex, routePoints.size)
        }.map { point ->
            SpurCoordinate(point.latitude, point.longitude)
        }
        val workJob = kotlin.coroutines.coroutineContext[Job]
        val update = withContext(Dispatchers.Default) {
            if (requiresReplay) {
                roadProgressTracker.replaceCompleted(contextForRoads.completedRoads.values)
            }
            contextForRoads.analyzer.updateRoute(
                route = routeToProcess,
                tracker = roadProgressTracker,
                initialCursor = roadTraversalCursor.takeUnless { requiresReplay }
                    ?: RoadTraversalCursor(),
                resetTraversal = requiresReplay,
                shouldContinue = { workJob?.isActive != false },
            )
        }
        if (workJob?.isActive == false) return@LaunchedEffect
        val snapshot = update.snapshot.copy(
            completion = update.completions.lastOrNull().takeUnless { requiresReplay },
        )
        roadProgressTourId = tourId
        roadProgressContextKey = contextForRoads.key
        lastRoadProgressPointId = latestPointId
        roadTraversalCursor = update.cursor
        roadProgressSnapshot = snapshot
    }

    LaunchedEffect(roadProgressSnapshot.completedRoads, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showRoadCounts(roadProgressSnapshot.completedRoads.values)
        }
    }

    val hasRoadHistory = roadHistoryFingerprint.pointCount >= 2
    LaunchedEffect(
        mapStyleRevision,
        roadHistoryMapRevision,
        loadedRoadsRevision,
        roadHistoryFingerprint,
        roadTraversalFingerprint,
        activeTourId,
        isTourActive,
        roadHistoryStore,
    ) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        val map = suspendCancellableCoroutine<MapLibreMap> { continuation ->
            mapView.getMapAsync { readyMap ->
                if (continuation.isActive) continuation.resume(readyMap)
            }
        }
        val coverageStore = roadCoverageStore ?: return@LaunchedEffect
        val historyStore = roadHistoryStore ?: return@LaunchedEffect
        val renderedCoverageContextKey = buildString {
            append(mapStyleRevision)
            append(':')
            if (isTourActive) {
                append("active:")
                append(tourId)
            } else {
                append(roadHistoryFingerprint.cacheIdentity())
            }
        }

        suspend fun renderCoverage(
            additions: List<List<SpurCoordinate>>,
            refresh: Boolean = false,
        ) {
            val contextChanged =
                renderedRoadCoverageContextKey != renderedCoverageContextKey
            if (!refresh && !contextChanged) return
            val segments = if (contextChanged) {
                withContext(Dispatchers.IO) { coverageStore.overviewSegments() }
            } else {
                withContext(Dispatchers.Default) {
                    appendOverviewRoadCoverageSegments(
                        existing = renderedRoadCoverageSegments,
                        additions = additions,
                    )
                }
            }
            val features = withContext(Dispatchers.Default) {
                roadProgressOverviewFeatureCollection(segments)
            }
            renderedRoadCoverageSegments = segments
            renderedRoadCoverageContextKey = renderedCoverageContextKey
            map.style?.showRoadProgressFeatures(features)
        }

        val roadHistoryBaseline = roadTraversalFingerprint
        if (
            roadHistoryBaseline != null &&
            reconciledRoadHistoryBaseline != roadHistoryBaseline
        ) {
            val cachedBaseline = withContext(Dispatchers.IO) {
                coverageStore.historyBaseline()
            }
            if (
                cachedBaseline != null &&
                cachedBaseline != roadHistoryBaseline
            ) {
                val addedFingerprint = withContext(Dispatchers.IO) {
                    historyStore.roadHistoryFingerprint(
                        afterPointId = cachedBaseline.maximumPointId,
                        excludingTourId = activeTourId,
                    )
                }
                if (
                    !preservesRoadCoverage(
                        cached = cachedBaseline,
                        current = roadHistoryBaseline,
                        added = addedFingerprint,
                    )
                ) {
                    withContext(Dispatchers.IO) { coverageStore.clear() }
                    renderedRoadCoverageSegments = emptyList()
                    renderedRoadCoverageContextKey = null
                }
            }
            withContext(Dispatchers.IO) {
                coverageStore.replaceHistoryBaseline(roadHistoryBaseline)
            }
            reconciledRoadHistoryBaseline = roadHistoryBaseline
        }
        renderCoverage(emptyList())
        val cameraKey = map.roadNetworkCameraKey()
        val target = map.cameraPosition.target
        if (!hasRoadHistory || cameraKey == null || target == null) {
            roadHistoryCameraKey = null
            if (!hasRoadHistory) {
                renderedRoadCoverageSegments = emptyList()
                val features = roadProgressOverviewFeatureCollection(emptyList())
                renderedRoadCoverageContextKey = renderedCoverageContextKey
                map.style?.showRoadProgressFeatures(features)
            }
            return@LaunchedEffect
        }
        val cacheKey = cameraKey.roadCoverageCacheKey()
        val cached = withContext(Dispatchers.IO) {
            coverageStore.coverage(cacheKey)
        }
        val cacheIsCurrent = cached?.fingerprint == roadHistoryFingerprint
        val needsCompaction = cacheIsCurrent &&
            cached?.needsCompaction == true &&
            !isTourActive
        val roadLoad = cacheKey to loadedRoadsRevision
        val roadLoadIsApplied = appliedRoadCoverageLoad == roadLoad
        val roadsAreReady = loadedRoadsCameraKey == cameraKey
        if (cacheIsCurrent && !roadsAreReady) {
            roadHistoryCameraKey = cameraKey
            renderCoverage(cached.segments)
            return@LaunchedEffect
        }
        if (cacheIsCurrent && !needsCompaction && roadLoadIsApplied) {
            roadHistoryCameraKey = cameraKey
            renderCoverage(cached.segments)
            return@LaunchedEffect
        }
        if (!roadsAreReady) {
            roadHistoryCameraKey = cameraKey
            renderCoverage(cached?.segments.orEmpty())
            return@LaunchedEffect
        }

        val historyCanAppend = !cacheIsCurrent &&
            cached != null &&
            isTourActive && withContext(Dispatchers.IO) {
                cached.fingerprint + historyStore.roadHistoryFingerprint(
                    afterPointId = cached.fingerprint.maximumPointId,
                ) == roadHistoryFingerprint
            }
        val refreshesRoadGeometry = shouldRefreshRoadCoverageGeometry(
            hasCachedCoverage = cached != null,
            cacheIsCurrent = cacheIsCurrent,
            historyCanAppend = historyCanAppend,
            needsCompaction = needsCompaction,
            roadLoadIsApplied = roadLoadIsApplied,
        )
        val canAppend = historyCanAppend && !refreshesRoadGeometry
        if (cached != null && !cacheIsCurrent && !historyCanAppend) {
            withContext(Dispatchers.IO) { coverageStore.clear() }
            renderedRoadCoverageSegments = emptyList()
            renderedRoadCoverageContextKey = null
        }
        val center = SpurCoordinate(target.latitude, target.longitude)
        val routes = withContext(Dispatchers.IO) {
            historyStore.roadHistoryRoutes(
                bounds = roadHistoryBounds(
                    roads = loadedRoads,
                    paddingMeters = RoadHistoryQueryPaddingMeters,
                ) ?: roadHistoryBounds(
                    center = center,
                    radiusMeters = RoadNetworkRadiusMeters + RoadHistoryQueryPaddingMeters,
                ),
                afterPointId = cached?.fingerprint?.maximumPointId.takeIf { canAppend },
            )
        }
        if (routes.isEmpty()) {
            val coverage = CachedRoadCoverage(
                fingerprint = roadHistoryFingerprint,
                segments = cached?.segments.takeIf { canAppend }.orEmpty(),
                needsCompaction = cached?.needsCompaction == true && canAppend,
            )
            withContext(Dispatchers.IO) {
                coverageStore.replace(cacheKey, coverage)
            }
            appliedRoadCoverageLoad = roadLoad
            roadHistoryCameraKey = cameraKey
            renderCoverage(coverage.segments, refresh = true)
            return@LaunchedEffect
        }

        val roads = loadedRoads
        val workJob = kotlin.coroutines.coroutineContext[Job]
        if (roads.isEmpty()) {
            appliedRoadCoverageLoad = roadLoad
            renderCoverage(cached?.segments.orEmpty())
            return@LaunchedEffect
        }
        val addedSegments = withContext(Dispatchers.Default) {
            normalizedRoadSegments(
                routes = routes,
                roads = roads,
                shouldContinue = { workJob?.isActive != false },
            )
        }
        if (workJob?.isActive == false) return@LaunchedEffect
        val segments = if (canAppend || refreshesRoadGeometry) {
            combineRoadCoverageSegments(cached?.segments.orEmpty(), addedSegments)
        } else {
            addedSegments
        }
        withContext(Dispatchers.IO) {
            coverageStore.replace(
                cacheKey = cacheKey,
                coverage = CachedRoadCoverage(
                    fingerprint = roadHistoryFingerprint,
                    segments = segments,
                    needsCompaction = canAppend &&
                        (cached?.needsCompaction == true || addedSegments.isNotEmpty()),
                ),
            )
        }
        appliedRoadCoverageLoad = roadLoad
        roadHistoryCameraKey = cameraKey
        renderCoverage(segments, refresh = true)
    }

    LaunchedEffect(roadProgressSnapshot.completion?.generation) {
        val completion = roadProgressSnapshot.completion ?: return@LaunchedEffect
        val map = suspendCancellableCoroutine<MapLibreMap> { continuation ->
            mapView.getMapAsync { readyMap ->
                if (continuation.isActive) continuation.resume(readyMap)
            }
        }
        delay(220L)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(360),
        ) { value, _ ->
            map.style?.showRoadCompletionPulse(
                road = completion.road,
                progress = value,
            )
        }
        mapView.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            },
        )
        delay(480L)
        map.style?.showRoadCompletionPulse(null, 0f)
    }

    LaunchedEffect(
        mapStyleRevision,
        isFollowingLocation,
        locationMarkerColors,
        trailColors,
    ) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.restartLocationPulse(
                currentLocationMarkerColors,
                currentTrailColors.stroke,
            )
        }
    }

    LaunchedEffect(
        mapStyleRevision,
        locationPulseGeneration,
        trailColors,
    ) {
        val generation = locationPulseGeneration
        if (
            mapStyleRevision == 0 ||
            generation == 0L ||
            currentManualLocation != null
        ) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (generation != currentLocationPulseGeneration) return@getMapAsync
            map.restartLocationPulse(
                currentLocationMarkerColors,
                currentTrailColors.stroke,
            )
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
                    pointZoom = defaultMapZoom,
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
                .semantics {
                    contentDescription = if (isAtHome) {
                        "Interaktive Kartenansicht. Du bist zu Hause."
                    } else {
                        "Interaktive Kartenansicht"
                    }
                },
        )

        val density = LocalDensity.current
        selectedTrackPoint?.let {
            AnimatedVisibility(
                visible = isSelectedTrackPointVisible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                SelectedTrackPointPuck()
            }
        }

        if (isHomeStartPointSelection) {
            HomeStartPointCrosshair(modifier = Modifier.align(Alignment.Center))
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

private fun MapLibreMap.mapViewport(satellite: Boolean): MapViewport? {
    val target = cameraPosition.target ?: return null
    return MapViewport(
        center = SpurCoordinate(target.latitude, target.longitude),
        zoom = cameraPosition.zoom,
        satellite = satellite,
    )
}

internal data class RoadNetworkCameraKey(
    val zoom: Int,
    val latitudeCell: Int,
    val longitudeCell: Int,
)

private data class RoadTraversalContext(
    val key: String,
    val completedRoads: Map<String, CompletedRoad>,
    val analyzer: RoadTraversalAnalyzer? = null,
)

private fun RoadNetworkCameraKey.roadCoverageCacheKey(): String =
    "$RoadCoverageAlgorithmVersion:$zoom:$latitudeCell:$longitudeCell"

private fun RoadNetworkCameraKey.roadTraversalCacheKey(): String =
    "$RoadTraversalAlgorithmVersion:$zoom:$latitudeCell:$longitudeCell"

private fun RoadHistoryFingerprint.cacheIdentity(): String =
    "$maximumPointId:$pointCount:$signature"

private fun MapLibreMap.roadNetworkCameraKey(): RoadNetworkCameraKey? {
    val target = cameraPosition.target ?: return null
    return roadNetworkCameraKey(
        zoom = cameraPosition.zoom,
        target = SpurCoordinate(target.latitude, target.longitude),
    )
}

internal fun roadNetworkCameraKey(
    zoom: Double,
    target: SpurCoordinate,
): RoadNetworkCameraKey? {
    if (!shouldShowRoadHistory(zoom)) return null
    val zoomLevel = floor(zoom).toInt()
    val cellDegrees = when {
        zoomLevel >= 20 -> 0.00025
        zoomLevel >= 19 -> 0.0005
        zoomLevel >= 18 -> 0.001
        zoomLevel >= 16 -> 0.002
        zoomLevel >= 15 -> 0.005
        zoomLevel >= 14 -> 0.005
        else -> 0.01
    }
    return RoadNetworkCameraKey(
        zoom = zoomLevel,
        latitudeCell = floor(target.latitude / cellDegrees).toInt(),
        longitudeCell = floor(target.longitude / cellDegrees).toInt(),
    )
}

private fun MapView.roadQueryViewport(): RectF = RectF(
    0f,
    0f,
    width.coerceAtLeast(1).toFloat(),
    height.coerceAtLeast(1).toFloat(),
)

private fun Context.vibrateManualWaypoint() {
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(
            ManualWaypointVibrationMillis,
            VibrationEffect.DEFAULT_AMPLITUDE,
        ),
    )
}

private const val RoadHistoryQueryPaddingMeters = 100.0
private const val MapPreviewIdleDelayMillis = 500L
