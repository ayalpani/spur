package app.spur

import android.location.Location
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MapPage(
    tour: Tour?,
    activeTour: Tour?,
    tourDisplayRequest: Long,
    routePoints: List<TrackPoint>,
    now: Long,
    onStartTour: () -> Unit,
    onSimulatedLocation: (SpurCoordinate) -> Unit,
    onEndTour: () -> Unit,
    onOpenHistory: () -> Unit,
    onDeleteTour: (Long) -> Unit,
    onDeleteWaypoint: suspend (Long, Set<Long>) -> Boolean,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    photoRevision: Long = 0L,
    onPhotoRotated: () -> Unit = {},
    initialLoadingComplete: Boolean = false,
    splashExitComplete: Boolean = true,
    onInitialLoadingComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    var isHomeSelectionMode by rememberSaveable { mutableStateOf(false) }
    var homeSelectionStep by rememberSaveable {
        mutableStateOf(HomeSelectionStep.BUILDING)
    }
    var homeSelectionCandidate by remember { mutableStateOf<SelectedBuilding?>(null) }
    var homeStartPoint by remember { mutableStateOf<SpurCoordinate?>(null) }
    val isTourActive = activeTour != null
    val usesStackedMapPlayer = shouldStackMapPlayer(
        LocalConfiguration.current.screenWidthDp,
    )
    val isWaypointRailVisible =
        !isHomeSelectionMode && (tour != null || activeTour != null)
    val mapActionsBottomPadding =
        (if (isWaypointRailVisible) WaypointRailHeight else 0.dp) +
            MapControlVerticalPadding +
            if (usesStackedMapPlayer) MapControlSize + MapControlGap else 0.dp
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
    var showSettingsMenu by rememberSaveable { mutableStateOf(false) }
    var showTourMenu by rememberSaveable { mutableStateOf(false) }
    var showHomeAutoStartBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showDirectionBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutBottomSheet by rememberSaveable { mutableStateOf(false) }
    var tourToDelete by remember { mutableStateOf<Tour?>(null) }
    var waypointToDelete by remember { mutableStateOf<TrackPoint?>(null) }
    var selectedEditorPointId by rememberSaveable(tour?.id) {
        mutableStateOf<Long?>(null)
    }
    var editorFocusRequest by remember { mutableLongStateOf(0L) }
    var selectedBuilding by remember { mutableStateOf<SelectedBuilding?>(null) }
    var homeSettings by remember {
        mutableStateOf(context.loadHomeAutoStartSettings())
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
    var colorTheme by remember {
        mutableStateOf(context.loadColorTheme())
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
    val areMapControlsVisible =
        isMapReady && !isMapGestureActive && !isHomeSelectionMode
    val startTourBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val mainMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val settingsMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val tourMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val homeAutoStartBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val directionBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val aboutBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun swapBottomSheets(
        currentState: SheetState,
        showNext: () -> Unit,
        hideCurrent: () -> Unit,
    ) {
        showNext()
        scope.launch {
            currentState.hide()
            hideCurrent()
        }
    }
    val followOwnLocation: () -> Unit = {
        isTourOverview = false
        isFollowingLocation = true
        editorFocusRequest = 0L
        followRequest++
    }
    val closeHomeSelection: () -> Unit = {
        isHomeSelectionMode = false
        homeSelectionStep = HomeSelectionStep.BUILDING
        homeSelectionCandidate = null
        homeStartPoint = null
    }
    val openHomeSelection: () -> Unit = {
        selectedBuilding = null
        homeSelectionStep = HomeSelectionStep.BUILDING
        homeSelectionCandidate = null
        homeStartPoint = null
        isHomeSelectionMode = true
        followOwnLocation()
    }
    BackHandler(enabled = isHomeSelectionMode) {
        if (homeSelectionStep == HomeSelectionStep.START_POINT) {
            homeSelectionStep = HomeSelectionStep.BUILDING
            homeStartPoint = null
        } else {
            closeHomeSelection()
        }
    }
    LaunchedEffect(tour?.id, editorLocations.size) {
        editorFocusRequest = 0L
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
        colorTheme,
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
            !showSettingsMenu &&
            !showTourMenu &&
            !showHomeAutoStartBottomSheet &&
            !showThemePicker &&
            !showDirectionBottomSheet &&
            !showAboutBottomSheet &&
            photoDetail == null &&
            mediaDetail == null,
        onBack = followOwnLocation,
    )
    val mapControlColors = colorTheme.mapControlColors
    val trailColors = colorTheme.trailColors
    CompositionLocalProvider(
        LocalMapControlColors provides mapControlColors,
        LocalAccentColor provides colorTheme.accent.color,
        LocalSignalColor provides trailColors.fill,
        LocalTrailColors provides trailColors,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mapInitializationStarted) {
            MapSurface(
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
                homeBuilding = homeSettings.homeBuilding,
                selectedBuilding = if (isHomeSelectionMode) {
                    homeSelectionCandidate?.feature
                } else {
                    selectedBuilding?.feature
                },
                isBuildingSelectionMode =
                    isHomeSelectionMode &&
                        homeSelectionStep == HomeSelectionStep.BUILDING,
                isHomeStartPointSelection =
                    isHomeSelectionMode &&
                        homeSelectionStep == HomeSelectionStep.START_POINT,
                homeStartPointFocus = homeSelectionCandidate?.coordinate,
                selectedTrackPoint = selectedEditorLocation?.point?.takeIf {
                    editorFocusRequest > 0L
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
                onLocationClick = followOwnLocation,
                onBuildingClick = {
                    if (
                        isHomeSelectionMode &&
                        homeSelectionStep == HomeSelectionStep.BUILDING
                    ) {
                        homeSelectionCandidate = it
                    } else {
                        selectedBuilding = it
                    }
                },
                onHomeStartPointChanged = { homeStartPoint = it },
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
                visible = areMapControlsVisible,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
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
                    val signalButtonAlpha = locationSignalButtonAlpha(
                        selected = isFollowingLocation,
                        pulseGeneration = activeLocationPulseGeneration,
                    )
                    MapIconButton(
                        contentDescription = "Hauptmenü öffnen",
                        onClick = { showMainMenu = true },
                        secondary = true,
                    ) {
                        MenuIcon()
                    }
                    if (selectedEditorLocation != null && routePoints.size > 1) {
                        MapIconButton(
                            contentDescription = "GPS-Punkt löschen",
                            onClick = { waypointToDelete = selectedEditorLocation.point },
                            secondary = true,
                        ) {
                            PhotoDeleteIcon()
                        }
                    }
                    val focusedWaypoint = selectedEditorLocation?.takeIf {
                        editorFocusRequest > 0L
                    }
                    MapIconButton(
                        contentDescription = "Moment hinzufügen",
                        onClick = {
                            momentTarget = if (focusedWaypoint != null && tour != null) {
                                MomentPlacementTarget.RecordedLocation(
                                    tourId = tour.id,
                                    trackPointId = focusedWaypoint.point.id,
                                    coordinate = SpurCoordinate(
                                        focusedWaypoint.point.latitude,
                                        focusedWaypoint.point.longitude,
                                    ),
                                )
                            } else {
                                MomentPlacementTarget.CurrentLocation
                            }
                        },
                        secondary = true,
                        contentColor = LocalAccentColor.current,
                    ) {
                        PlusIcon()
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
                        modifier = Modifier.graphicsLayer {
                            alpha = signalButtonAlpha
                        },
                    ) {
                        FollowLocationIcon(selected = isFollowingLocation)
                    }
                }
            }

            AnimatedVisibility(
                visible = areMapControlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                val mapStyleControl: @Composable () -> Unit = {
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
                }
                val playerControl: @Composable (Modifier) -> Unit = { modifier ->
                    if (activeTour != null) {
                        TourPlayer(
                            tour = activeTour,
                            now = now,
                            onStop = onEndTour,
                            modifier = modifier,
                        )
                    } else if (tour != null) {
                        TourSummaryPlayer(
                            tourId = tour.id,
                            distanceMeters = tour.distanceMeters,
                            elapsedMillis = (tour.endedAt ?: now) - tour.startedAt,
                            modifier = modifier.height(MapControlSize),
                        )
                    } else {
                        val secondaryStyle =
                            secondaryMapControlStyle(LocalMapControlColors.current)
                        Button(
                            onClick = { showStartTourBottomSheet = true },
                            modifier = modifier
                                .height(MapControlSize)
                                .mapControlShadow(CircleShape),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = secondaryStyle.colors.background,
                                contentColor = secondaryStyle.colors.foreground,
                            ),
                            border = secondaryStyle.border,
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
                                color = secondaryStyle.colors.foreground,
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
                            start = MapControlHorizontalPadding,
                            top = MapControlVerticalPadding,
                            end = MapControlHorizontalPadding,
                            bottom = MapControlVerticalPadding +
                                if (isWaypointRailVisible) WaypointRailHeight else 0.dp,
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MapControlGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (manualLocation != null) {
                                MapIconButton(
                                    contentDescription =
                                        "Simulierten Standort zurücksetzen",
                                    onClick = {
                                        context.saveManualLocation(null)
                                        manualLocation = null
                                    },
                                    secondary = true,
                                ) {
                                    LucideLocateOffIcon()
                                }
                            }
                            MapIconButton(
                                contentDescription = "Letzte Touren öffnen",
                                onClick = {
                                    activeVoiceMoment = null
                                    onOpenHistory()
                                },
                                secondary = true,
                            ) {
                                HistoryIcon()
                            }
                            mapStyleControl()
                        }
                        val focusedWaypoint = selectedEditorLocation?.takeIf {
                            editorFocusRequest > 0L
                        }
                        if (tour != null && focusedWaypoint != null) {
                            TourSummaryPlayer(
                                tourId = tour.id,
                                distanceMeters = focusedWaypoint.distanceFromStartMeters,
                                elapsedMillis = focusedWaypoint.elapsedMillis,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(MapControlSize),
                            )
                        } else {
                            playerControl(Modifier.weight(1f))
                        }
                        if (!usesStackedMapPlayer) {
                            Spacer(modifier = Modifier.size(MapControlSize))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isMapReady && isWaypointRailVisible,
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
                    WaypointRail(
                        locations = editorLocations,
                        selectedPointId = selectedEditorPointId,
                        emptyText = waypointEmptyText(
                            hasActiveTour = activeTour != null,
                            hasDisplayedTour = tour != null,
                        ),
                        onSelected = { pointId ->
                            if (pointId != selectedEditorPointId) {
                                selectedEditorPointId = pointId
                                editorFocusRequest++
                                isFollowingLocation = false
                                isTourOverview = false
                            }
                        },
                    )
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

            if (isHomeSelectionMode) {
                HomeSelectionPanel(
                    step = homeSelectionStep,
                    selectedHome = homeSelectionCandidate,
                    onConfirm = {
                        val selectedHome = homeSelectionCandidate
                            ?: return@HomeSelectionPanel
                        if (homeSelectionStep == HomeSelectionStep.BUILDING) {
                            isFollowingLocation = false
                            homeStartPoint = selectedHome.coordinate
                            homeSelectionStep = HomeSelectionStep.START_POINT
                            return@HomeSelectionPanel
                        }
                        val selectedStartPoint = homeStartPoint
                            ?: return@HomeSelectionPanel
                        val updatedSettings = homeSettings.copy(
                            home = selectedHome.coordinate,
                            homeBuilding = selectedHome.feature,
                            startPoint = selectedStartPoint,
                        )
                        context.saveHomeAutoStartSettings(updatedSettings)
                        if (updatedSettings.enabled) {
                            context.removeHomeExitGeofence()
                            context.registerHomeExitGeofence()
                        }
                        homeSettings = updatedSettings
                        closeHomeSelection()
                        showFeedbackNotice(
                            FeedbackNoticeKind.PLACEHOLDER,
                            "Dein Zuhause wurde festgelegt.",
                        )
                    },
                    onCancel = closeHomeSelection,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(3f),
                )
            }

            selectedBuilding?.takeUnless { isHomeSelectionMode }?.let { building ->
                val closeBuildingDetails: () -> Unit = {
                    selectedBuilding = null
                }
                BackHandler(onBack = closeBuildingDetails)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(3f),
                    shape = RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                    ),
                    color = SheetBackground,
                    shadowElevation = 16.dp,
                ) {
                    BuildingDetailsBottomSheet(
                        coordinate = building.coordinate,
                    )
                }
            }
        }
    }

    if (showStartTourBottomSheet) {
        SpurModalBottomSheet(
            onDismissRequest = { showStartTourBottomSheet = false },
            sheetState = startTourBottomSheetState,
        ) {
            CompositionLocalProvider(LocalMapControlColors provides mapControlColors) {
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
    }

    if (showMainMenu) {
        SpurModalBottomSheet(
            onDismissRequest = { showMainMenu = false },
            sheetState = mainMenuState,
        ) {
            MainMenu(
                onOpenSettings = {
                    swapBottomSheets(
                        currentState = mainMenuState,
                        showNext = { showSettingsMenu = true },
                        hideCurrent = { showMainMenu = false },
                    )
                },
                onOpenAbout = {
                    swapBottomSheets(
                        currentState = mainMenuState,
                        showNext = { showAboutBottomSheet = true },
                        hideCurrent = { showMainMenu = false },
                    )
                },
            )
        }
    }

    if (showSettingsMenu) {
        val closeSettingsMenu: () -> Unit = {
            swapBottomSheets(
                currentState = settingsMenuState,
                showNext = { showMainMenu = true },
                hideCurrent = { showSettingsMenu = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showMainMenu = true
                showSettingsMenu = false
            },
            sheetState = settingsMenuState,
            scrimColor = if (showThemePicker) {
                Color.Transparent
            } else {
                BottomSheetDefaults.ScrimColor
            },
        ) {
            BackHandler(onBack = closeSettingsMenu)
            SettingsMenu(
                onOpenTour = if (isTourActive || tour != null) {
                    {
                        swapBottomSheets(
                            currentState = settingsMenuState,
                            showNext = { showTourMenu = true },
                            hideCurrent = { showSettingsMenu = false },
                        )
                    }
                } else {
                    null
                },
                onOpenHome = {
                    scope.launch {
                        settingsMenuState.hide()
                        showSettingsMenu = false
                        openHomeSelection()
                    }
                },
                onOpenHomeAutoStart = {
                    swapBottomSheets(
                        currentState = settingsMenuState,
                        showNext = { showHomeAutoStartBottomSheet = true },
                        hideCurrent = { showSettingsMenu = false },
                    )
                },
                onOpenTheme = {
                    showThemePicker = true
                    scope.launch {
                        settingsMenuState.hide()
                        showSettingsMenu = false
                    }
                },
                onOpenDirection = {
                    swapBottomSheets(
                        currentState = settingsMenuState,
                        showNext = { showDirectionBottomSheet = true },
                        hideCurrent = { showSettingsMenu = false },
                    )
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

    waypointToDelete?.let { selectedPoint ->
        EditorDeleteSheet(
            title = "GPS-Punkt löschen?",
            primaryLabel = "GPS-Punkt löschen",
            onDismiss = { waypointToDelete = null },
            onConfirm = {
                waypointToDelete = null
                val deletedIndex = routePoints.indexOfFirst { it.id == selectedPoint.id }
                if (deletedIndex < 0 || tour == null) return@EditorDeleteSheet
                val retained = routePoints.filterNot { it.id == selectedPoint.id }
                scope.launch {
                    if (!onDeleteWaypoint(tour.id, retained.mapTo(mutableSetOf(), TrackPoint::id))) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "GPS-Punkt konnte nicht gelöscht werden.",
                        )
                        return@launch
                    }
                    val updatedMoments = mapMoments.map { moment ->
                        if (moment.trackPointId != selectedPoint.id) {
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
                    withContext(Dispatchers.IO) {
                        context.saveMapMoments(updatedMoments)
                    }
                    mapMoments = updatedMoments
                    selectedEditorPointId = retained.getOrNull(
                        deletedIndex.coerceAtMost(retained.lastIndex),
                    )?.id
                }
            },
        )
    }

    if (showTourMenu) {
        val closeTourMenu: () -> Unit = {
            swapBottomSheets(
                currentState = tourMenuState,
                showNext = { showSettingsMenu = true },
                hideCurrent = { showTourMenu = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = {
                showSettingsMenu = true
                showTourMenu = false
            },
            sheetState = tourMenuState,
        ) {
            BackHandler(onBack = closeTourMenu)
            TourMenu(
                onShareTour = if (isTourActive) {
                    {
                        scope.launch {
                            tourMenuState.hide()
                            showTourMenu = false
                            shareActiveTour(context)
                        }
                    }
                } else {
                    null
                },
                onDeleteTour = tour?.let { visibleTour ->
                    {
                        swapBottomSheets(
                            currentState = tourMenuState,
                            showNext = { tourToDelete = visibleTour },
                            hideCurrent = { showTourMenu = false },
                        )
                    }
                },
            )
        }
    }

    if (showHomeAutoStartBottomSheet) {
        SpurModalBottomSheet(
            onDismissRequest = {
                showSettingsMenu = true
                showHomeAutoStartBottomSheet = false
            },
            sheetState = homeAutoStartBottomSheetState,
        ) {
            HomeAutoStartBottomSheet(
                onSettingsChanged = { homeSettings = it },
                onChooseHome = {
                    scope.launch {
                        homeAutoStartBottomSheetState.hide()
                        showHomeAutoStartBottomSheet = false
                        openHomeSelection()
                    }
                },
            )
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
                showSettingsMenu = true
                showDirectionBottomSheet = false
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
                    title = "Himmelsrichtung",
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
                showMainMenu = true
                showAboutBottomSheet = false
            },
            sheetState = aboutBottomSheetState,
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

    ThemePickerOverlay(
        visible = showThemePicker,
        selectedTheme = colorTheme,
        onSelect = { theme ->
            colorTheme = theme
            context.saveColorTheme(theme)
        },
        onDismiss = { showThemePicker = false },
    )

    CompositionLocalProvider(LocalMapControlColors provides mapControlColors) {
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
    }

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
