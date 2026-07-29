package app.spur

import android.graphics.PointF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.Dispatchers
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
import org.maplibre.geojson.Point
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun MapSurface(
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
    selectedBuilding: Feature?,
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
    onBuildingClick: (SelectedBuilding) -> Unit,
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
    val currentSelectedBuilding by rememberUpdatedState(selectedBuilding)
    val currentSelectedTrackPoint by rememberUpdatedState(selectedTrackPoint)
    val currentManualLocation by rememberUpdatedState(manualLocation)
    val currentFollowRequest by rememberUpdatedState(followRequest)
    val currentTourOverviewRequest by rememberUpdatedState(tourOverviewRequest)
    val currentLocationPulseGeneration by rememberUpdatedState(locationPulseGeneration)
    val currentMomentImageRevision by rememberUpdatedState(momentImageRevision)
    val currentLocationPulseColor by rememberUpdatedState(
        if (isFollowingLocation) trailColors.fill else Ink,
    )
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
        currentOnAlternateMapPreviewLoadingChanged(true)
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            if (hasLoadedMapStyle) {
                map.style?.showSatelliteBaseMap(
                    satellite = isSatelliteView,
                )
                previewCameraPosition = map.cameraPosition
                return@getMapAsync
            }
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
                locationPulseColor = currentLocationPulseColor,
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
        var avoidanceRefreshPending = true
        var avoidanceMoments: List<MapMoment>? = null
        var avoidancePersonaVisible: Boolean? = null
        var avoidanceLocation: SpurCoordinate? = null
        var avoidanceImageRevision = -1L
        var avoidanceStyleRevision = -1
        var appliedAvoidanceLayout: MapMomentAvoidanceLayout? = null
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

        fun updateMapMomentAvoidance() {
            val readyMap = map ?: return
            val moments = currentMapMoments
            val personaVisible =
                currentManualLocation == null && currentSelectedTrackPoint == null
            val location = if (
                personaVisible &&
                readyMap.locationComponent.isLocationComponentActivated &&
                readyMap.locationComponent.isLocationComponentEnabled
            ) {
                readyMap.locationComponent.lastKnownLocation?.let {
                    SpurCoordinate(latitude = it.latitude, longitude = it.longitude)
                }
            } else {
                null
            }
            val inputsChanged =
                moments !== avoidanceMoments ||
                    personaVisible != avoidancePersonaVisible ||
                    location != avoidanceLocation ||
                    currentMomentImageRevision != avoidanceImageRevision ||
                    mapStyleRevision != avoidanceStyleRevision
            if (!avoidanceRefreshPending && !inputsChanged) return
            if (moments.isNotEmpty() && readyMap.style?.getLayer(MapMomentLayer) == null) {
                return
            }
            val layout = if (location == null) {
                MapMomentAvoidanceLayout()
            } else {
                readyMap.calculateMapMomentAvoidanceLayout(
                    location = location,
                    moments = moments,
                    density = context.resources.displayMetrics.density,
                    mapWidth = mapView.width,
                    mapHeight = mapView.height,
                )
            }
            if (inputsChanged || layout != appliedAvoidanceLayout) {
                readyMap.style?.showMapMomentAvoidanceLayout(moments, layout)
            }
            avoidanceMoments = moments
            avoidancePersonaVisible = personaVisible
            avoidanceLocation = location
            avoidanceImageRevision = currentMomentImageRevision
            avoidanceStyleRevision = mapStyleRevision
            appliedAvoidanceLayout = layout
            avoidanceRefreshPending = false
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
            avoidanceRefreshPending = true
            if (!isMapTouchActive) currentOnMapGestureActiveChanged(false)
            publishManualLocationPosition()
            publishPendingMomentPosition()
            publishSelectedTrackPointPosition()
            updateMapMomentAvoidance()
            previewCameraPosition = map?.cameraPosition
            if (shouldStopFollowing(cameraMoveReason)) {
                map?.cameraPosition?.zoom?.let(context::saveDefaultMapZoom)
            }
            cameraMoveReason =
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION
        }
        val renderingFrameListener =
            MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
                if (fully) updateMapMomentAvoidance()
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
                val clusterPoint = cluster.geometry() as? Point
                val clusterLocation = clusterPoint?.let {
                    LatLng(it.latitude(), it.longitude())
                } ?: point
                readyMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(clusterLocation, expansionZoom),
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
            mapView.addOnDidFinishRenderingFrameListener(renderingFrameListener)
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
            mapView.removeOnDidFinishRenderingFrameListener(renderingFrameListener)
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

    LaunchedEffect(homeBuilding, selectedBuilding, mapStyleRevision) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.showHighlightedBuildings(
                home = currentHomeBuilding,
                selected = currentSelectedBuilding,
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
        isFollowingLocation,
        trailColors.fill,
    ) {
        if (mapStyleRevision == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.restartLocationPulse(currentLocationPulseColor)
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
            map.restartLocationPulse(currentLocationPulseColor)
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

private fun MapLibreMap.calculateMapMomentAvoidanceLayout(
    location: SpurCoordinate,
    moments: List<MapMoment>,
    density: Float,
    mapWidth: Int,
    mapHeight: Int,
): MapMomentAvoidanceLayout {
    if (density <= 0f || mapWidth <= 0 || mapHeight <= 0) {
        return MapMomentAvoidanceLayout()
    }
    val locationScreen = projection.toScreenLocation(
        LatLng(location.latitude, location.longitude),
    )
    val locationPoint = Offset(locationScreen.x / density, locationScreen.y / density)
    val baseMomentOffsets = overlappingMomentOffsets(moments)
    val momentKeys = mutableMapOf<String, String>()
    val clusterKeys = mutableMapOf<String, Long>()
    val items = queryRenderedFeatures(
        android.graphics.RectF(0f, 0f, mapWidth.toFloat(), mapHeight.toFloat()),
        MapMomentLayer,
        MapMomentClusterLayer,
    ).mapNotNull { feature ->
        val point = feature.geometry() as? Point ?: return@mapNotNull null
        val anchorScreen = projection.toScreenLocation(
            LatLng(point.latitude(), point.longitude()),
        )
        val anchor = Offset(anchorScreen.x / density, anchorScreen.y / density)
        if (feature.hasProperty(MapMomentClusterIdProperty)) {
            val clusterId = feature.getNumberProperty(MapMomentClusterIdProperty).toLong()
            val key = "cluster-$clusterId"
            clusterKeys[key] = clusterId
            PersonaAvoidanceItem(
                key = key,
                anchor = anchor,
                offset = Offset.Zero,
                width = MomentClusterWidth.toFloat(),
                height = MomentClusterHeight.toFloat(),
            )
        } else {
            val momentId = feature.getStringProperty(MapMomentIdProperty)
                ?: return@mapNotNull null
            val key = "moment-$momentId"
            momentKeys[key] = momentId
            PersonaAvoidanceItem(
                key = key,
                anchor = anchor,
                offset = baseMomentOffsets[momentId] ?: Offset.Zero,
                width = MomentMarkerWidth.toFloat(),
                height = MomentMarkerHeight.toFloat(),
            )
        }
    }
    val offsets = avoidPersonaOverlaps(locationPoint, items)
    return MapMomentAvoidanceLayout(
        momentOffsets = offsets.mapNotNull { (key, offset) ->
            momentKeys[key]?.let { it to offset }
        }.toMap(),
        clusterOffsets = offsets.mapNotNull { (key, offset) ->
            clusterKeys[key]?.let { it to offset }
        }.toMap(),
    )
}
