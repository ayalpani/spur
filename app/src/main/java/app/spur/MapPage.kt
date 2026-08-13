package app.spur

import android.location.Location
import android.media.MediaPlayer
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    animateTourEntry: Boolean = false,
    tourEntryPreparationRequest: Long? = null,
    routePoints: List<TrackPoint>,
    preparedTourRoute: PreparedTourRoute? = null,
    pendingDeparturePreview: PendingDeparturePreview? = null,
    roadHistoryStore: TourStore? = null,
    roadTraversalFingerprint: RoadHistoryFingerprint? = null,
    onStartTour: () -> Unit,
    onSimulatedLocation: (SpurCoordinate) -> Unit,
    onEndTour: () -> Unit,
    onRenameTour: suspend (Long, String) -> Boolean,
    onOpenHome: () -> Unit,
    onCloseDisplayedTour: () -> Unit,
    onDeleteTour: (Long) -> Unit,
    onDeleteWaypoint: suspend (Long, Set<Long>) -> Boolean,
    showFeedbackNotice: ShowFeedbackNotice = { _, _ -> },
    photoRevision: Long = 0L,
    onPhotoRotated: () -> Unit = {},
    initialLoadingComplete: Boolean = false,
    splashExitComplete: Boolean = true,
    onInitialLoadingComplete: () -> Unit = {},
    onTourEntryPrepared: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val itemsApi = remember { ItemsApi() }
    val itemRepositoryResult = remember {
        runCatching { ItemRepository(ItemInventoryStore(context.applicationContext), itemsApi) }
    }
    val itemRepository = itemRepositoryResult.getOrNull()
    LaunchedEffect(itemRepositoryResult) {
        itemRepositoryResult.exceptionOrNull()?.let {
            Log.e("SpurItems", "Encrypted inventory unavailable", it)
        }
    }
    val inventory by itemRepository?.inventory?.collectAsState()
        ?: remember { mutableStateOf(ItemInventory()) }
    var worldMode by rememberSaveable { mutableStateOf(WorldMode.MAP) }
    var arClosing by remember { mutableStateOf(false) }
    var latestItemLocation by remember { mutableStateOf<Location?>(null) }
    var itemMapBounds by remember { mutableStateOf<ItemMapBounds?>(null) }
    var publicItemsPage by remember { mutableStateOf(PublicItemsPage(emptyList(), emptyList())) }
    var nearbyItems by remember { mutableStateOf(emptyList<PublicItem>()) }
    var selectedPublicItem by remember { mutableStateOf<PublicItem?>(null) }
    var itemTarget by remember { mutableStateOf<PublicItem?>(null) }
    var showInventoryPage by rememberSaveable { mutableStateOf(false) }
    var isHomeSelectionMode by rememberSaveable { mutableStateOf(false) }
    var homeSelectionStep by rememberSaveable {
        mutableStateOf(HomeSelectionStep.BUILDING)
    }
    var homeSelectionCandidate by remember { mutableStateOf<SelectedBuilding?>(null) }
    var homeStartPoint by remember { mutableStateOf<SpurCoordinate?>(null) }
    val isTourActive = activeTour != null
    val isDisplayedActiveTour = isDisplayedActiveTour(tour, activeTour)
    val playerActiveTour = activeTourForPlayer(tour, activeTour)
    val showsPendingDeparture =
        pendingDeparturePreview != null && tour == null && activeTour == null
    val mapRoutePoints = if (showsPendingDeparture) {
        pendingDeparturePreview?.points.orEmpty()
    } else {
        routePoints
    }
    val archivedTour = tour?.takeIf { it.endedAt != null }
    var isMapGestureActive by remember { mutableStateOf(false) }
    val usesStackedMapPlayer = shouldStackMapPlayer(
        LocalConfiguration.current.screenWidthDp,
    )
    val hasWaypointRail = !isHomeSelectionMode &&
        (tour != null || activeTour != null)
    val mapActionsBottomPadding =
        (if (hasWaypointRail) WaypointRailHeight else 0.dp) +
            MapControlVerticalPadding +
            if (usesStackedMapPlayer) MapControlSize + MapControlGap else 0.dp
    val scope = rememberCoroutineScope()
    val closeAr: () -> Unit = {
        if (worldMode == WorldMode.AR) {
            worldMode = closeArMode(worldMode)
            arClosing = true
            scope.launch {
                delay(3_000)
                arClosing = false
            }
        }
    }
    val openAr: () -> Unit = {
        worldMode = openArMode(worldMode, arClosing)
    }
    val landmarkStore = remember(context) { LandmarkStore(context) }
    var landmarks by remember { mutableStateOf(emptyList<Landmark>()) }
    LaunchedEffect(landmarkStore) {
        landmarks = withContext(Dispatchers.IO) { landmarkStore.landmarks() }
    }
    DisposableEffect(landmarkStore) {
        onDispose { landmarkStore.close() }
    }
    var followRequest by rememberSaveable { mutableStateOf(0) }
    var tourOverviewRequest by rememberSaveable { mutableStateOf(0) }
    var isFollowingLocation by rememberSaveable { mutableStateOf(false) }
    var isUserMoving by remember { mutableStateOf(false) }
    var requestedLocationPulseGeneration by remember { mutableLongStateOf(0L) }
    var activeLocationPulseGeneration by remember { mutableStateOf<Long?>(null) }
    var dismissedActiveTourHeaderId by rememberSaveable(activeTour?.id) {
        mutableStateOf<Long?>(null)
    }
    var isTourOverview by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    var mapViewport by remember { mutableStateOf<MapViewport?>(null) }
    var alternateMapPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isWaypointRailScrolling by remember { mutableStateOf(false) }
    var isZoomControlInteracting by remember { mutableStateOf(false) }
    var showStartTourBottomSheet by rememberSaveable { mutableStateOf(false) }
    var isStartingTour by rememberSaveable { mutableStateOf(false) }
    var momentTarget by remember { mutableStateOf<MomentPlacementTarget?>(null) }
    ActiveTourNavigationBar(active = isDisplayedActiveTour)
    var showMainMenu by rememberSaveable { mutableStateOf(false) }
    var showSettingsMenu by rememberSaveable { mutableStateOf(false) }
    var showLandmarkSettingsBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showHomeAutoStartBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showBackupBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showDirectionBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showTourEndConfirmation by rememberSaveable { mutableStateOf(false) }
    var tourTitleEditor by remember(tour?.id) {
        mutableStateOf<TextFieldValue?>(null)
    }
    var isSavingTourTitle by remember(tour?.id) { mutableStateOf(false) }
    var tourToDelete by remember { mutableStateOf<Tour?>(null) }
    var waypointToDelete by remember { mutableStateOf<TrackPoint?>(null) }
    var landmarkToDelete by remember { mutableStateOf<Landmark?>(null) }
    var selectedEditorPointId by rememberSaveable(tour?.id) {
        mutableStateOf<Long?>(null)
    }
    var editorFocusRequest by remember { mutableLongStateOf(0L) }
    var waypointRailFocusRequest by remember { mutableLongStateOf(0L) }
    var selectedBuilding by remember { mutableStateOf<SelectedBuilding?>(null) }
    var homeSettings by remember {
        mutableStateOf(context.loadHomeAutoStartSettings())
    }
    var pendingMoment by remember { mutableStateOf<PendingMapMoment?>(null) }
    var photoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var mediaDetail by remember { mutableStateOf<MapMoment?>(null) }
    var photoDetailOrigin by remember { mutableStateOf<Offset?>(null) }
    var photoDetailPreview by remember { mutableStateOf<PhotoOpenPreview?>(null) }
    var focusedPhoto by remember { mutableStateOf<MapMoment?>(null) }
    var mapMoments by remember { mutableStateOf(context.loadMapMoments()) }
    LaunchedEffect(photoRevision) {
        mapMoments = context.loadMapMoments()
    }
    val visibleMapMoments = remember(mapMoments, tour) {
        tour?.let { mapMomentsForTour(mapMoments, it) } ?: mapMoments
    }
    var presentation by remember { mutableStateOf(TourPresentation.Empty) }
    var presentationGeneration by remember { mutableLongStateOf(0L) }
    var presentedTourId by remember { mutableStateOf<Long?>(null) }
    var presentedRoutePoints by remember { mutableStateOf<List<TrackPoint>?>(null) }
    var presentedMapMoments by remember { mutableStateOf<List<MapMoment>?>(null) }
    LaunchedEffect(tour, routePoints, visibleMapMoments) {
        val generation = ++presentationGeneration
        val result = tour?.let { displayedTour ->
            withContext(Dispatchers.Default) {
                tourPresentation(displayedTour, routePoints, visibleMapMoments)
            }
        } ?: TourPresentation(
            mapMoments = visibleMapMoments,
            editorLocations = emptyList(),
            editorLocationsByPointId = emptyMap(),
        )
        if (generation == presentationGeneration) {
            presentation = result
            presentedTourId = tour?.id
            presentedRoutePoints = routePoints
            presentedMapMoments = visibleMapMoments
        }
    }
    val isTourPresentationReady = isTourPresentationReadyForEntry(
        presentedTourId = presentedTourId,
        tourId = tour?.id,
        presentedRoutePoints = presentedRoutePoints,
        routePoints = routePoints,
        presentedMapMoments = presentedMapMoments,
        mapMoments = visibleMapMoments,
    )
    val renderedMapMoments = remember(presentation.mapMoments, homeSettings) {
        normalizedHomeMoments(presentation.mapMoments, homeSettings)
    }
    val editorLocations = presentation.editorLocations
    val selectedEditorLocation = selectedEditorPointId
        ?.let(presentation.editorLocationsByPointId::get)
        ?: editorLocations.lastOrNull()
    var activeVoiceMoment by remember { mutableStateOf<MapMoment?>(null) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableFloatStateOf(0f) }
    var manualLocation by remember { mutableStateOf(context.loadManualLocation()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var defaultMapZoom by remember {
        mutableStateOf(
            context.loadDefaultMapZoom().coerceIn(MapZoomMinimum, MapZoomMaximum),
        )
    }
    var displayedMapZoom by remember {
        mutableStateOf(defaultMapZoom)
    }
    var mapZoomRequestId by remember { mutableLongStateOf(0L) }
    var mapZoomRequest by remember { mutableStateOf<MapZoomRequest?>(null) }
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
    val areMapControlsVisible = worldMode == WorldMode.MAP &&
        shouldShowTourChrome(isMapGestureActive) &&
        isMapReady &&
        !isHomeSelectionMode
    val hasTourModeHeader = tour != null &&
        (!isDisplayedActiveTour || dismissedActiveTourHeaderId != tour?.id)
    val isTourModeHeaderVisible = areMapControlsVisible && hasTourModeHeader
    val isWaypointRailVisible = areMapControlsVisible && hasWaypointRail
    val startTourBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val mainMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val settingsMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val landmarkSettingsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val landmarkDeleteSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val homeAutoStartBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val backupBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val directionBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val aboutBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val publicItemSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tourDeleteSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tourEndSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(itemRepository) {
        runCatching { itemRepository?.resumeTransfers() }
    }
    LaunchedEffect(itemMapBounds, mapViewport?.zoom) {
        val bounds = itemMapBounds ?: return@LaunchedEffect
        delay(300)
        runCatching { itemsApi.list(bounds, mapViewport?.zoom ?: defaultMapZoom) }
            .onSuccess { publicItemsPage = it }
    }
    LaunchedEffect(worldMode, latestItemLocation?.latitude, latestItemLocation?.longitude) {
        if (worldMode != WorldMode.AR) return@LaunchedEffect
        val location = latestItemLocation ?: return@LaunchedEffect
        runCatching { itemsApi.nearby(location.toItemLocation()) }
            .onSuccess { nearbyItems = it.items }
    }
    LaunchedEffect(isDisplayedActiveTour) {
        if (!isDisplayedActiveTour) showTourEndConfirmation = false
    }
    val followOwnLocation: () -> Unit = {
        isTourOverview = false
        isFollowingLocation = true
        editorFocusRequest = 0L
        if (isDisplayedActiveTour) {
            selectedEditorPointId = editorLocations.lastOrNull()?.point?.id
            waypointRailFocusRequest++
        }
        followRequest++
    }
    val requestMapZoom: (Double, Boolean) -> Unit = { zoom, animated ->
        val target = normalizedMapZoom(zoom, isSatelliteView)
        displayedMapZoom = target
        mapZoomRequestId++
        mapZoomRequest = MapZoomRequest(
            id = mapZoomRequestId,
            zoom = target,
            animated = animated,
        )
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
    BackHandler(enabled = worldMode == WorldMode.AR && !showInventoryPage) {
        closeAr()
    }
    LaunchedEffect(tour?.id, editorLocations.size) {
        editorFocusRequest = 0L
        if (
            isFollowingLocation ||
            editorLocations.none { it.point.id == selectedEditorPointId }
        ) {
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
            context.registerHomeAutoStart()
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
    val isTourBackNavigationAvailable =
        !showStartTourBottomSheet &&
        !showMainMenu &&
        !showSettingsMenu &&
        !showHomeAutoStartBottomSheet &&
        !showThemePicker &&
        !showDirectionBottomSheet &&
        !showAboutBottomSheet &&
        photoDetail == null &&
        mediaDetail == null
    BackHandler(
        enabled = archivedTour != null && isTourBackNavigationAvailable,
        onBack = onCloseDisplayedTour,
    )
    BackHandler(
        enabled = isTourOverview &&
            archivedTour == null &&
            isTourBackNavigationAvailable,
        onBack = followOwnLocation,
    )
    val mapControlColors = colorTheme.mapControlColors
    val trailColors = colorTheme.trailColors
    CompositionLocalProvider(
        LocalMapControlColors provides mapControlColors,
        LocalAccentColor provides colorTheme.accent.color,
        LocalLocationMarkerColors provides colorTheme.locationMarkerColors,
        LocalTrailColors provides trailColors,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (mapInitializationStarted) {
            MapSurface(
                isWorldVisible = worldMode == WorldMode.MAP,
                tourId = tour?.id,
                activeTourId = activeTour?.id,
                isTourActive = isTourActive,
                showTourEndpoints = !isDisplayedActiveTour && !showsPendingDeparture,
                departureCheckActive = showsPendingDeparture,
                deferAlternateMapPreview =
                    tourEntryPreparationRequest != null ||
                    (isWaypointRailScrolling && !isFollowingLocation) ||
                    isMapGestureActive ||
                    isZoomControlInteracting,
                isZoomControlInteracting = isZoomControlInteracting,
                tourDisplayRequest = tourDisplayRequest,
                animateTourEntry = animateTourEntry,
                tourEntryPreparationRequest = tourEntryPreparationRequest,
                tourEntryContentReady = isTourPresentationReady,
                followRequest = followRequest,
                tourOverviewRequest = tourOverviewRequest,
                isFollowingLocation = isFollowingLocation,
                locationPulseGeneration = requestedLocationPulseGeneration,
                isSatelliteView = isSatelliteView,
                manualLocation = manualLocation,
                defaultMapZoom = defaultMapZoom,
                zoomRequest = mapZoomRequest,
                defaultMapRotation = defaultMapRotation,
                mapSettingsVisible = showDirectionBottomSheet,
                landmarks = landmarks,
                mapMoments = renderedMapMoments,
                publicItemsPage = publicItemsPage,
                selectedPublicItemId = itemTarget?.id,
                momentImageRevision = photoRevision,
                routePoints = mapRoutePoints,
                preparedTourRoute = preparedTourRoute,
                roadHistoryStore = roadHistoryStore,
                roadTraversalFingerprint = roadTraversalFingerprint,
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
                onViewportChanged = {
                    mapViewport = it
                    displayedMapZoom = normalizedMapZoom(it.zoom, it.satellite)
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
                onMomentClick = { moment, origin, preview ->
                    isFollowingLocation = false
                    isTourOverview = false
                    when (moment.type) {
                        MomentType.PHOTO -> {
                            activeVoiceMoment = null
                            photoDetailOrigin = origin
                            photoDetailPreview = preview
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
                onPublicItemClick = { item ->
                    itemTarget = item
                    selectedPublicItem = item
                },
                onItemBoundsChanged = { itemMapBounds = it },
                onItemLocationChanged = { latestItemLocation = it },
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
                onMovementChanged = { isUserMoving = it },
                onMapReadyChanged = { isMapRendered = it },
                onMapGestureActiveChanged = { isMapGestureActive = it },
                onTourEntryPrepared = onTourEntryPrepared,
            )
            }

            if (worldMode == WorldMode.AR) {
                ItemArView(
                    inventory = inventory,
                    inventoryAvailable = itemRepository != null,
                    deviceLocation = latestItemLocation,
                    nearbyItems = nearbyItems,
                    selectedTarget = itemTarget,
                    onDrop = { item, location ->
                        val repository = itemRepository
                            ?: return@ItemArView DropOutcome.FAILED
                        val result = runCatching { repository.drop(item, location) }
                            .onSuccess { dropped ->
                                publicItemsPage = publicItemsPage.copy(
                                    items = publicItemsPage.items.filterNot { it.id == dropped.id } + dropped,
                                )
                                itemTarget = dropped
                                showFeedbackNotice(
                                    FeedbackNoticeKind.PLACEHOLDER,
                                    "${item.kind.displayName} abgelegt",
                                )
                            }
                            .onFailure {
                                showFeedbackNotice(
                                    FeedbackNoticeKind.ERROR,
                                    if (repository.inventory.value.pendingDrops.any { pending -> pending.itemId == item.id }) {
                                        "Offline – Ablage wird später fortgesetzt."
                                    } else {
                                        "Item konnte nicht abgelegt werden."
                                    },
                                )
                            }
                        when {
                            result.isSuccess -> DropOutcome.DROPPED
                            repository.inventory.value.pendingDrops.any { it.itemId == item.id } ->
                                DropOutcome.PENDING
                            else -> DropOutcome.FAILED
                        }
                    },
                    onClaim = { item, location ->
                        val currentLocation = latestItemLocation
                        val repository = itemRepository
                        if (
                            currentLocation == null ||
                            !currentLocation.isPublishableItemLocation(System.currentTimeMillis()) ||
                            repository == null
                        ) {
                            ClaimOutcome.FAILED
                        } else {
                            try {
                                repository.claim(item, location)
                                publicItemsPage = publicItemsPage.copy(
                                    items = publicItemsPage.items.filterNot { it.id == item.id },
                                )
                                nearbyItems = nearbyItems.filterNot { it.id == item.id }
                                if (itemTarget?.id == item.id) itemTarget = null
                                ClaimOutcome.CLAIMED
                            } catch (failure: ItemsApiException) {
                                if (failure.code == "already_claimed") {
                                    publicItemsPage = publicItemsPage.copy(
                                        items = publicItemsPage.items.filterNot { it.id == item.id },
                                    )
                                    nearbyItems = nearbyItems.filterNot { it.id == item.id }
                                    ClaimOutcome.ALREADY_CLAIMED
                                } else {
                                    ClaimOutcome.FAILED
                                }
                            } catch (_: Exception) {
                                ClaimOutcome.FAILED
                            }
                        }
                    },
                    onNotice = showFeedbackNotice,
                    onExitAr = closeAr,
                    modifier = Modifier.zIndex(0.5f),
                )
            }

            TourModeHeader(
                tour = tour,
                active = isDisplayedActiveTour,
                visible = isTourModeHeaderVisible,
                titleEditor = tourTitleEditor,
                titleSaving = isSavingTourTitle,
                onClose = {
                    if (isDisplayedActiveTour) {
                        dismissedActiveTourHeaderId = tour?.id
                    } else {
                        onCloseDisplayedTour()
                    }
                },
                onOpenMenu = { showMainMenu = true },
                onTitleChange = { tourTitleEditor = it },
                onSaveTitle = {
                    val displayed = tour ?: return@TourModeHeader
                    val draft = tourTitleEditor ?: return@TourModeHeader
                    if (isSavingTourTitle) return@TourModeHeader
                    isSavingTourTitle = true
                    scope.launch {
                        val saved = onRenameTour(displayed.id, draft.text)
                        isSavingTourTitle = false
                        if (saved) {
                            tourTitleEditor = null
                        } else {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "Tourname konnte nicht gespeichert werden.",
                            )
                        }
                    }
                },
                onCancelTitleEdit = {
                    isSavingTourTitle = false
                    tourTitleEditor = null
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )

            AnimatedVisibility(
                visible = areMapControlsVisible && tour == null && activeTour == null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        top = MapControlVerticalPadding,
                        end = MapControlHorizontalPadding,
                    ),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                MapIconButton(
                    contentDescription = "Hauptmenü öffnen",
                    onClick = { showMainMenu = true },
                    secondary = true,
                ) {
                    MenuIcon()
                }
            }

            AnimatedVisibility(
                visible = areMapControlsVisible && archivedTour == null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        start = MapControlHorizontalPadding,
                        top = MapControlVerticalPadding +
                            if (hasTourModeHeader) 60.dp else 0.dp,
                    ),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                MapIconButton(
                    contentDescription = "Home öffnen",
                    onClick = {
                        activeVoiceMoment = null
                        onOpenHome()
                    },
                    secondary = true,
                ) {
                    HomeAsteriskIcon(isMoving = isUserMoving)
                }
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
                    if (selectedEditorLocation != null && routePoints.size > 1) {
                        MapIconButton(
                            contentDescription = "Wegpunkt löschen",
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
                                isFollowingLocation = false
                                isTourOverview = false
                                editorFocusRequest++
                                MomentPlacementTarget.RecordedLocation(
                                    tourId = tour.id,
                                    trackPointId = focusedWaypoint.point.id,
                                    coordinate = SpurCoordinate(
                                        focusedWaypoint.point.latitude,
                                        focusedWaypoint.point.longitude,
                                    ),
                                )
                            } else {
                                followOwnLocation()
                                MomentPlacementTarget.CurrentLocation
                            }
                        },
                        secondary = true,
                        contentColor = MapControlColor.BLACK.color,
                    ) {
                        PlusIcon()
                    }
                    MapZoomControl(
                        zoom = displayedMapZoom,
                        defaultZoom = defaultMapZoom,
                        maximumZoom = mapZoomMaximum(isSatelliteView),
                        isInteractionActive = isZoomControlInteracting,
                        onZoomChange = requestMapZoom,
                        onDefaultZoomSelected = { zoom ->
                            defaultMapZoom = zoom
                            context.saveDefaultMapZoom(zoom)
                            showFeedbackNotice(
                                FeedbackNoticeKind.PLACEHOLDER,
                                "Default Zoom gespeichert",
                            )
                        },
                        onInteractionActiveChanged = {
                            isZoomControlInteracting = it
                        },
                    )
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
                                    hasDisplayedTour = tour != null,
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
                        FollowLocationIcon(
                            selected = isFollowingLocation,
                        )
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
                            alternateMapPreview = null
                            isSatelliteView = !isSatelliteView
                            displayedMapZoom = normalizedMapZoom(
                                displayedMapZoom,
                                isSatelliteView,
                            )
                        },
                        preview = alternateMapPreview,
                        fallbackPreview = if (isSatelliteView) {
                            R.drawable.map_preview_street
                        } else {
                            R.drawable.map_preview_satellite
                        },
                    )
                }
                val playerControl: @Composable (Modifier) -> Unit = { modifier ->
                    if (playerActiveTour != null) {
                        TourPlayer(
                            tour = playerActiveTour,
                            routePoints = routePoints,
                            onStop = { showTourEndConfirmation = true },
                            modifier = modifier,
                        )
                    } else if (tour != null) {
                        TourSummaryPlayer(
                            tourId = tour.id,
                            distanceMeters = tour.distanceMeters,
                            elapsedMillis =
                                (tour.endedAt ?: System.currentTimeMillis()) - tour.startedAt,
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
                                if (hasWaypointRail) WaypointRailHeight else 0.dp,
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
                            WorldModeToggle(
                                mode = WorldMode.MAP,
                                enabled = !arClosing,
                                onClick = openAr,
                            )
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

            if (worldMode == WorldMode.AR) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(
                            start = MapControlHorizontalPadding,
                            bottom = MapControlVerticalPadding,
                        )
                        .zIndex(2f),
                    verticalArrangement = Arrangement.spacedBy(MapControlGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WorldModeToggle(mode = WorldMode.AR, onClick = closeAr)
                    if (manualLocation != null) Spacer(modifier = Modifier.size(MapControlSize))
                    Spacer(modifier = Modifier.size(MapControlSize))
                }
            }

            AnimatedVisibility(
                visible = isWaypointRailVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(MotionDurationDefaultMillis)),
                exit = fadeOut(tween(MotionDurationDefaultMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDisplayedActiveTour) GameRoadSurface else Color.White,
                        )
                        .navigationBarsPadding(),
                ) {
                    WaypointRail(
                        locations = editorLocations,
                        selectedPointId = selectedEditorPointId,
                        focusRequest = waypointRailFocusRequest,
                        followLatest = isFollowingLocation,
                        emptyText = waypointEmptyText(
                            hasActiveTour = activeTour != null,
                            hasDisplayedTour = tour != null,
                        ),
                        backgroundColor = if (isDisplayedActiveTour) {
                            GameRoadSurface
                        } else {
                            Color.White
                        },
                        onScrollInProgressChanged = { isScrolling ->
                            isWaypointRailScrolling = isScrolling
                        },
                        onSelected = { pointId ->
                            if (pointId != selectedEditorPointId) {
                                selectedEditorPointId = pointId
                                if (
                                    isFollowingLocation &&
                                    pointId == editorLocations.lastOrNull()?.point?.id
                                ) {
                                    return@WaypointRail
                                }
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
                exit = fadeOut(tween(InitialLoaderExitDurationMillis)),
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
                            context.removeHomeAutoStart()
                            context.registerHomeAutoStart()
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
            if (showInventoryPage) {
                InventoryPage(
                    inventory = inventory,
                    onBack = { showInventoryPage = false },
                    modifier = Modifier.zIndex(10f),
                    available = itemRepository != null,
                )
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
                onOpenInventory = {
                    scope.launch {
                        mainMenuState.hide()
                        showMainMenu = false
                        showInventoryPage = true
                    }
                },
                onOpenSettings = {
                    scope.swapBottomSheets(
                        currentState = mainMenuState,
                        nextState = settingsMenuState,
                        showNext = { showSettingsMenu = true },
                        hideCurrent = { showMainMenu = false },
                    )
                },
                onOpenGoogleMaps = {
                    val viewport = mapViewport
                    if (viewport == null) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Der Kartenausschnitt ist noch nicht bereit.",
                        )
                    } else {
                        showMainMenu = false
                        runCatching { uriHandler.openUri(googleMapsViewUrl(viewport)) }
                            .onFailure {
                                showFeedbackNotice(
                                    FeedbackNoticeKind.ERROR,
                                    "Google Maps konnte nicht geöffnet werden.",
                                )
                            }
                    }
                },
                onRenameTour = archivedTour?.let { visibleTour ->
                    {
                        scope.launch {
                            mainMenuState.hide()
                            showMainMenu = false
                            val initialTitle = visibleTour.title ?: "Archiv-Tour"
                            tourTitleEditor = TextFieldValue(
                                text = initialTitle,
                                selection = TextRange(initialTitle.length),
                            )
                        }
                    }
                },
                onOpenAbout = {
                    scope.swapBottomSheets(
                        currentState = mainMenuState,
                        nextState = aboutBottomSheetState,
                        showNext = { showAboutBottomSheet = true },
                        hideCurrent = { showMainMenu = false },
                    )
                },
                onShareTour = if (isDisplayedActiveTour) {
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
                onStopTour = if (isDisplayedActiveTour) {
                    {
                        scope.swapBottomSheets(
                            currentState = mainMenuState,
                            nextState = tourEndSheetState,
                            showNext = { showTourEndConfirmation = true },
                            hideCurrent = { showMainMenu = false },
                        )
                    }
                } else {
                    null
                },
                onDeleteTour = tour?.let { visibleTour ->
                    {
                        scope.swapBottomSheets(
                            currentState = mainMenuState,
                            nextState = tourDeleteSheetState,
                            showNext = { tourToDelete = visibleTour },
                            hideCurrent = { showMainMenu = false },
                        )
                    }
                },
            )
        }
    }

    selectedPublicItem?.let { item ->
        SpurModalBottomSheet(
            onDismissRequest = { selectedPublicItem = null },
            sheetState = publicItemSheetState,
        ) {
            PublicItemDetails(
                item = item,
                onOpenInAr = {
                    scope.launch {
                        publicItemSheetState.hide()
                        selectedPublicItem = null
                        openAr()
                    }
                },
            )
        }
    }

    if (showSettingsMenu) {
        val closeSettingsMenu: () -> Unit = {
            scope.swapBottomSheets(
                currentState = settingsMenuState,
                nextState = mainMenuState,
                showNext = { showMainMenu = true },
                hideCurrent = { showSettingsMenu = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = { showSettingsMenu = false },
            sheetState = settingsMenuState,
            scrimColor = if (showThemePicker) {
                Color.Transparent
            } else {
                BottomSheetDefaults.ScrimColor
            },
        ) {
            BackHandler(onBack = closeSettingsMenu)
            SettingsMenu(
                onOpenHome = {
                    scope.launch {
                        settingsMenuState.hide()
                        showSettingsMenu = false
                        openHomeSelection()
                    }
                },
                onOpenLandmarks = {
                    scope.swapBottomSheets(
                        currentState = settingsMenuState,
                        nextState = landmarkSettingsSheetState,
                        showNext = { showLandmarkSettingsBottomSheet = true },
                        hideCurrent = { showSettingsMenu = false },
                    )
                },
                onOpenHomeAutoStart = {
                    scope.swapBottomSheets(
                        currentState = settingsMenuState,
                        nextState = homeAutoStartBottomSheetState,
                        showNext = { showHomeAutoStartBottomSheet = true },
                        hideCurrent = { showSettingsMenu = false },
                    )
                },
                onOpenBackup = {
                    scope.swapBottomSheets(
                        currentState = settingsMenuState,
                        nextState = backupBottomSheetState,
                        showNext = { showBackupBottomSheet = true },
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
                    scope.swapBottomSheets(
                        currentState = settingsMenuState,
                        nextState = directionBottomSheetState,
                        showNext = { showDirectionBottomSheet = true },
                        hideCurrent = { showSettingsMenu = false },
                    )
                },
            )
        }
    }

    if (showLandmarkSettingsBottomSheet) {
        val closeLandmarkSettings: () -> Unit = {
            scope.swapBottomSheets(
                currentState = landmarkSettingsSheetState,
                nextState = settingsMenuState,
                showNext = { showSettingsMenu = true },
                hideCurrent = { showLandmarkSettingsBottomSheet = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = { showLandmarkSettingsBottomSheet = false },
            sheetState = landmarkSettingsSheetState,
        ) {
            BackHandler(onBack = closeLandmarkSettings)
            LandmarkSettingsBottomSheet(
                landmarks = landmarks,
                onRename = { landmark, title ->
                    scope.launch {
                        val renamed = withContext(Dispatchers.IO) {
                            landmarkStore.rename(landmark.id, title)
                        }
                        if (renamed) {
                            landmarks = landmarks.map {
                                if (it.id == landmark.id) it.copy(title = title) else it
                            }
                        } else {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "Der Ort konnte nicht umbenannt werden.",
                            )
                        }
                    }
                },
                onDelete = { landmark ->
                    scope.swapBottomSheets(
                        currentState = landmarkSettingsSheetState,
                        nextState = landmarkDeleteSheetState,
                        showNext = { landmarkToDelete = landmark },
                        hideCurrent = { showLandmarkSettingsBottomSheet = false },
                    )
                },
            )
        }
    }

    landmarkToDelete?.let { landmark ->
        val closeLandmarkDelete: () -> Unit = {
            scope.swapBottomSheets(
                currentState = landmarkDeleteSheetState,
                nextState = landmarkSettingsSheetState,
                showNext = { showLandmarkSettingsBottomSheet = true },
                hideCurrent = { landmarkToDelete = null },
            )
        }
        EditorDeleteSheet(
            title = "${landmark.title} löschen?",
            primaryLabel = "Ort löschen",
            sheetState = landmarkDeleteSheetState,
            onDismiss = closeLandmarkDelete,
            onConfirm = {
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        landmarkStore.delete(landmark.id)
                    }
                    if (deleted) {
                        landmarks = landmarks.filterNot { it.id == landmark.id }
                    } else {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Der Ort konnte nicht gelöscht werden.",
                        )
                    }
                    closeLandmarkDelete()
                }
            },
        )
    }

    if (showTourEndConfirmation) {
        EditorDeleteSheet(
            title = "Tour beenden?",
            primaryLabel = "Tour beenden",
            sheetState = tourEndSheetState,
            onDismiss = { showTourEndConfirmation = false },
            onConfirm = {
                showTourEndConfirmation = false
                onEndTour()
            },
        )
    }

    tourToDelete?.let { selectedTour ->
        EditorDeleteSheet(
            title = "Tour löschen?",
            primaryLabel = "Tour löschen",
            sheetState = tourDeleteSheetState,
            onDismiss = { tourToDelete = null },
            onConfirm = {
                tourToDelete = null
                onDeleteTour(selectedTour.id)
            },
        )
    }

    waypointToDelete?.let { selectedPoint ->
        EditorDeleteSheet(
            title = "Wegpunkt löschen?",
            primaryLabel = "Wegpunkt löschen",
            onDismiss = { waypointToDelete = null },
            onConfirm = {
                waypointToDelete = null
                val deletion = trackPointDeletion(
                    points = routePoints,
                    moments = mapMoments,
                    deletedPointId = selectedPoint.id,
                ) ?: return@EditorDeleteSheet
                if (tour == null) return@EditorDeleteSheet
                scope.launch {
                    if (!onDeleteWaypoint(tour.id, deletion.retainedPointIds)) {
                        showFeedbackNotice(
                            FeedbackNoticeKind.ERROR,
                            "Wegpunkt konnte nicht gelöscht werden.",
                        )
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        context.saveMapMoments(deletion.updatedMoments)
                    }
                    mapMoments = deletion.updatedMoments
                    selectedEditorPointId = deletion.selectedPointId
                }
            },
        )
    }

    if (showHomeAutoStartBottomSheet) {
        val closeHomeAutoStart: () -> Unit = {
            scope.swapBottomSheets(
                currentState = homeAutoStartBottomSheetState,
                nextState = settingsMenuState,
                showNext = { showSettingsMenu = true },
                hideCurrent = { showHomeAutoStartBottomSheet = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = { showHomeAutoStartBottomSheet = false },
            sheetState = homeAutoStartBottomSheetState,
        ) {
            BackHandler(onBack = closeHomeAutoStart)
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

    if (showBackupBottomSheet) {
        val closeBackup: () -> Unit = {
            scope.swapBottomSheets(
                currentState = backupBottomSheetState,
                nextState = settingsMenuState,
                showNext = { showSettingsMenu = true },
                hideCurrent = { showBackupBottomSheet = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = { showBackupBottomSheet = false },
            sheetState = backupBottomSheetState,
        ) {
            BackupBottomSheet(
                hasActiveTour = activeTour != null,
                onBack = closeBackup,
            )
        }
    }

    if (showDirectionBottomSheet) {
        val closeDirection: () -> Unit = {
            scope.swapBottomSheets(
                currentState = directionBottomSheetState,
                nextState = settingsMenuState,
                showNext = { showSettingsMenu = true },
                hideCurrent = { showDirectionBottomSheet = false },
            )
        }
        val compassRotation = remember {
            Animatable(-(defaultMapRotation.bearing ?: 0.0).toFloat())
        }
        LaunchedEffect(defaultMapRotation) {
            val bearing = defaultMapRotation.bearing ?: return@LaunchedEffect
            compassRotation.animateTo(
                targetValue = nearestCompassRotation(
                    current = compassRotation.value,
                    target = -bearing.toFloat(),
                ),
                animationSpec = tween(MapRotationAnimationMillis.toInt()),
            )
        }
        val selectMapRotation: (MapRotation) -> Unit = {
            defaultMapRotation = it
            context.saveDefaultMapRotation(it)
        }
        SpurModalBottomSheet(
            onDismissRequest = { showDirectionBottomSheet = false },
            sheetState = directionBottomSheetState,
        ) {
            BackHandler(onBack = closeDirection)
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
        val closeAbout: () -> Unit = {
            scope.swapBottomSheets(
                currentState = aboutBottomSheetState,
                nextState = mainMenuState,
                showNext = { showMainMenu = true },
                hideCurrent = { showAboutBottomSheet = false },
            )
        }
        SpurModalBottomSheet(
            onDismissRequest = { showAboutBottomSheet = false },
            sheetState = aboutBottomSheetState,
        ) {
            BackHandler(onBack = closeAbout)
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
                            color = Ink.copy(alpha = IconTextLabelAlpha),
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
            loadLandmarkTitleSuggestion = { target ->
                val coordinate = when (target) {
                    MomentPlacementTarget.CurrentLocation -> mapViewport?.center
                    is MomentPlacementTarget.RecordedLocation -> target.coordinate
                }
                coordinate?.let { context.fetchNearbyLandmarkTitle(it) }
            },
            onLandmarkAccepted = { target, title ->
                val coordinate = when (target) {
                    MomentPlacementTarget.CurrentLocation -> mapViewport?.center
                    is MomentPlacementTarget.RecordedLocation -> target.coordinate
                }
                if (coordinate == null) {
                    showFeedbackNotice(
                        FeedbackNoticeKind.ERROR,
                        "Der Standort ist noch nicht verfügbar.",
                    )
                } else {
                    scope.launch {
                        val landmark = withContext(Dispatchers.IO) {
                            landmarkStore.add(title, coordinate)
                        }
                        if (landmark == null) {
                            showFeedbackNotice(
                                FeedbackNoticeKind.ERROR,
                                "Die Landmark konnte nicht gespeichert werden.",
                            )
                        } else {
                            landmarks = landmarks + landmark
                        }
                    }
                }
            },
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
            openPreview = photoDetailPreview,
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
                        photoDetailPreview = null
                        focusedPhoto = null
                    }
                }
            },
            onDismiss = {
                photoDetail = null
                photoDetailOrigin = null
                photoDetailPreview = null
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

internal fun isTourPresentationReadyForEntry(
    presentedTourId: Long?,
    tourId: Long?,
    presentedRoutePoints: List<TrackPoint>?,
    routePoints: List<TrackPoint>,
    presentedMapMoments: List<MapMoment>?,
    mapMoments: List<MapMoment>,
): Boolean =
    presentedTourId == tourId &&
        presentedRoutePoints === routePoints &&
        presentedMapMoments === mapMoments

@Composable
private fun ActiveTourNavigationBar(active: Boolean) {
    if (active) ActivityNavigationBar(backgroundColor = GameRoadSurface)
}
