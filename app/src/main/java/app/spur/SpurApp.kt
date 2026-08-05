package app.spur

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SpurApp(splashExitComplete: Boolean) {
    val context = LocalContext.current
    val store = remember { TourStore(context) }
    val scope = rememberCoroutineScope()
    var activeTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTour by remember { mutableStateOf<Tour?>(null) }
    var displayedTourId by rememberSaveable { mutableStateOf<Long?>(null) }
    var displayedTourRequest by rememberSaveable { mutableLongStateOf(0L) }
    var routePoints by remember { mutableStateOf(emptyList<TrackPoint>()) }
    var pendingDeparturePreview by remember {
        mutableStateOf<PendingDeparturePreview?>(null)
    }
    var displayedTourRevision by remember { mutableStateOf<TourRevision?>(null) }
    var roadHistoryRefreshRevision by remember { mutableLongStateOf(0L) }
    var roadTraversalRefreshRevision by remember { mutableLongStateOf(0L) }
    var roadHistoryFingerprint by remember { mutableStateOf(RoadHistoryFingerprint()) }
    var roadTraversalFingerprint by remember { mutableStateOf<RoadHistoryFingerprint?>(null) }
    var historyRevision by remember { mutableLongStateOf(0L) }
    var photoRevision by remember { mutableLongStateOf(0L) }
    var historyPhotoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var historyPhotos by remember { mutableStateOf(emptyList<MapMoment>()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var initialMapLoadingComplete by rememberSaveable { mutableStateOf(false) }
    var completionTour by remember { mutableStateOf<Tour?>(null) }
    var completionPreview by remember { mutableStateOf<File?>(null) }
    var completionPoints by remember { mutableStateOf(emptyList<TrackPoint>()) }
    var completionPreviewLoading by remember { mutableStateOf(false) }
    var completionEligibilityChecked by remember { mutableStateOf(false) }
    var completionRefreshRequest by remember { mutableLongStateOf(0L) }
    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasLocationPermission())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
    }
    val completionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState()
        .value
        ?.destination
        ?.route
    val homeVisible = currentRoute == SpurRoute.HOME
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isAppResumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    isAppResumed = false
                    completionEligibilityChecked = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isAppResumed) {
        if (!isAppResumed) return@LaunchedEffect
        TourCompletionEvents.finishedTourIds.collect {
            completionRefreshRequest++
        }
    }

    LaunchedEffect(isAppResumed, activeTour?.id) {
        if (!isAppResumed || activeTour != null) {
            pendingDeparturePreview = null
            return@LaunchedEffect
        }
        while (true) {
            pendingDeparturePreview = withContext(Dispatchers.IO) {
                context.loadPendingDeparturePreview()
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(isAppResumed, activeTour?.id, completionRefreshRequest) {
        if (!isAppResumed) return@LaunchedEffect
        val candidate = withContext(Dispatchers.IO) {
            val pendingId = context.pendingTourCompletionId()
                ?: return@withContext null
            val active = store.activeTour()
            val pending = store.tour(pendingId)
            eligibleTourCompletion(
                pendingTour = pending,
                activeTour = active,
            ).also {
                if (it == null) context.clearPendingTourCompletion(pendingId)
            }
        }
        completionEligibilityChecked = true
        if (candidate == null && completionTour != null) {
            completionSheetState.hide()
        }
        if (completionTour?.id != candidate?.id) completionPoints = emptyList()
        completionTour = candidate
        completionPreview = candidate?.let { tour ->
            context.tourPreviewFile(tour.id)
                .takeIf { it.isFile && it.length() > 0L }
        }
        completionPreviewLoading = candidate != null && completionPreview == null
    }

    LaunchedEffect(completionTour?.id) {
        val tour = completionTour ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            val points = store.points(tour.id)
            val changed = if (completionPreview == null) {
                context.ensureTourPreview(tour = tour, points = points)
            } else {
                false
            }
            val preview = context.tourPreviewFile(tour.id)
                .takeIf { it.isFile && it.length() > 0L }
            Triple(changed, preview, points)
        }
        if (completionTour?.id != tour.id) return@LaunchedEffect
        completionPreview = result.second
        completionPoints = result.third
        completionPreviewLoading = false
        if (result.first) historyRevision++
    }

    LaunchedEffect(isAppResumed, hasLocationPermission, activeTour?.id, displayedTourId) {
        if (!isAppResumed || !hasLocationPermission || activeTour != null) {
            return@LaunchedEffect
        }
        while (true) {
            val restoredId = withContext(Dispatchers.IO) { store.activeTourId() }
            if (restoredId == null) {
                delay(1_000L)
                continue
            }
            if (displayedTourId == null) {
                displayedTourId = restoredId
                displayedTourRequest++
            }
            historyRevision++
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TOUR_ID, restoredId),
            )
            break
        }
    }

    LaunchedEffect(isAppResumed, activeTour?.id, displayedTourId, historyRevision) {
        if (!isAppResumed) return@LaunchedEffect
        val id = displayedTourId ?: activeTour?.id
        if (id == null) {
            displayedTour = null
            routePoints = emptyList()
            displayedTourRevision = null
            return@LaunchedEffect
        }
        while (true) {
            val revision = withContext(Dispatchers.IO) { store.tourRevision(id) }
            if (shouldReloadTour(displayedTourRevision, revision)) {
                val points = withContext(Dispatchers.IO) {
                    if (revision == null) emptyList() else store.points(id)
                }
                val wasActiveTourId = activeTour?.id
                displayedTourRevision = revision
                displayedTour = revision?.asTour()
                routePoints = points
                roadHistoryRefreshRevision++
                if (wasActiveTourId != id || revision?.endedAt != null) {
                    roadTraversalRefreshRevision++
                }
                if (wasActiveTourId == id || wasActiveTourId == null && revision?.endedAt == null) {
                    activeTour = revision?.asTour()?.takeIf { it.endedAt == null }
                }
            }
            if (activeTour?.id != id) break
            delay(1_000L)
        }
    }

    LaunchedEffect(isAppResumed, roadHistoryRefreshRevision) {
        if (!isAppResumed) return@LaunchedEffect
        roadHistoryFingerprint = withContext(Dispatchers.IO) {
            store.roadHistoryFingerprint()
        }
    }

    LaunchedEffect(isAppResumed, activeTour?.id, roadTraversalRefreshRevision) {
        if (!isAppResumed) return@LaunchedEffect
        roadTraversalFingerprint = withContext(Dispatchers.IO) {
            store.roadHistoryFingerprint(excludingTourId = activeTour?.id)
        }
    }

    LaunchedEffect(feedbackNotice?.id) {
        if (feedbackNotice == null) return@LaunchedEffect
        delay(FeedbackNoticeDurationMillis)
        feedbackNotice = null
    }

    val deleteTour: (Long) -> Unit = { id ->
        val revealHomeBeforeDeletion = shouldRevealHomeBeforeDeletingTour(
            deletedTourId = id,
            displayedTour = displayedTour,
            previousRoute = navController.previousBackStackEntry?.destination?.route,
        )
        scope.launch {
            if (revealHomeBeforeDeletion) {
                displayedTour = activeTour
                displayedTourId = activeTour?.id
                displayedTourRevision = null
                routePoints = emptyList()
                navController.popBackStack()
                delay(HomePanelMotionDurationMillis.toLong())
            }
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
                displayedTourRevision = null
                routePoints = emptyList()
            }
            historyRevision++
            roadHistoryRefreshRevision++
            roadTraversalRefreshRevision++
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
                    val mapTour = displayedTour.takeUnless {
                        pendingDeparturePreview != null && activeTour == null
                    }
                    MapPage(
                        tour = mapTour,
                        activeTour = activeTour,
                        tourDisplayRequest = displayedTourRequest,
                        routePoints = routePoints,
                        pendingDeparturePreview = pendingDeparturePreview,
                        roadHistoryStore = store,
                        roadHistoryFingerprint = roadHistoryFingerprint,
                        roadTraversalFingerprint = roadTraversalFingerprint,
                        onStartTour = {
                            scope.launch {
                                val start = withContext(Dispatchers.IO) {
                                    val start = store.activeTourOrStart()
                                    if (start.created) {
                                        context.loadManualLocation()?.let { coordinate ->
                                            store.appendSimulatedLocation(
                                                start.id,
                                                coordinate,
                                            )
                                        }
                                    }
                                    start
                                }
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, TrackingService::class.java)
                                        .putExtra(TrackingService.EXTRA_TOUR_ID, start.id),
                                )
                                val started = withContext(Dispatchers.IO) {
                                    store.tour(start.id)
                                }
                                if (start.created && started != null) context.vibrateTourStarted()
                                activeTour = started
                                displayedTour = started
                                displayedTourId = start.id
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
                                val finished = withContext(Dispatchers.IO) {
                                    store.finishTour(id).also { finished ->
                                        if (finished) context.markTourCompletionPending(id)
                                    }
                                }
                                if (finished) context.vibrateTourEnded()
                                context.startService(
                                    Intent(context, TrackingService::class.java)
                                        .setAction(TrackingService.ACTION_STOP),
                                )
                                val result = withContext(Dispatchers.IO) {
                                    val revision = store.tourRevision(id)
                                    revision to store.points(id)
                                }
                                activeTour = null
                                displayedTourRevision = result.first
                                displayedTour = result.first?.asTour()
                                displayedTourId = id
                                displayedTourRequest++
                                routePoints = result.second
                                roadHistoryRefreshRevision++
                                roadTraversalRefreshRevision++
                                historyRevision++
                            }
                        },
                        onRenameTour = { id, title ->
                            val renamedRevision = withContext(Dispatchers.IO) {
                                if (!store.updateTourTitle(id, title)) {
                                    null
                                } else {
                                    store.tourRevision(id)
                                }
                            }
                            if (renamedRevision == null) {
                                false
                            } else {
                                val renamedTour = renamedRevision.asTour()
                                if (displayedTourId == id) {
                                    displayedTourRevision = renamedRevision
                                    displayedTour = renamedTour
                                }
                                if (activeTour?.id == id) activeTour = renamedTour
                                historyRevision++
                                true
                            }
                        },
                        onOpenHome = {
                            navController.navigate(SpurRoute.HOME) {
                                launchSingleTop = true
                            }
                        },
                        onCloseDisplayedTour = {
                            val returnToHome =
                                navController.previousBackStackEntry
                                    ?.destination
                                    ?.route == SpurRoute.HOME
                            val currentActiveTour = activeTour
                            displayedTour = currentActiveTour
                            displayedTourId = currentActiveTour?.id
                            displayedTourRequest++
                            routePoints = emptyList()
                            if (returnToHome) navController.popBackStack()
                        },
                        onDeleteTour = deleteTour,
                        onDeleteWaypoint = { tourId, retainedIds ->
                            runCatching {
                                val result = withContext(Dispatchers.IO) {
                                    store.updateTourPoints(tourId, retainedIds)
                                    store.tourRevision(tourId) to store.points(tourId)
                                }
                                if (displayedTourId == tourId) {
                                    displayedTourRevision = result.first
                                    displayedTour = result.first?.asTour()
                                    routePoints = result.second
                                }
                                if (activeTour?.id == tourId) {
                                    activeTour = result.first?.asTour()
                                }
                                roadHistoryRefreshRevision++
                                if (activeTour?.id != tourId) roadTraversalRefreshRevision++
                                historyRevision++
                            }.isSuccess
                        },
                        showFeedbackNotice = showFeedbackNotice,
                        photoRevision = photoRevision,
                        onPhotoRotated = { photoRevision++ },
                        initialLoadingComplete = initialMapLoadingComplete,
                        splashExitComplete = splashExitComplete,
                        onInitialLoadingComplete = {
                            initialMapLoadingComplete = true
                        },
                    )
                    NavHost(
                        navController = navController,
                        startDestination = SpurRoute.MAP,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) {
                        composable(SpurRoute.MAP) {}
                        composable(SpurRoute.HOME) {}
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val panelWidth = with(LocalDensity.current) {
                            maxWidth.roundToPx()
                        }
                        val panelOffset by animateIntAsState(
                            targetValue = if (homeVisible) 0 else -panelWidth,
                            animationSpec = tween(HomePanelMotionDurationMillis),
                            label = "home panel offset",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(panelOffset, 0) },
                        ) {
                            HomeScreen(
                                store = store,
                                revision = historyRevision,
                                loadingEnabled = initialMapLoadingComplete,
                                backEnabled = homeVisible,
                                onBack = { navController.popBackStack() },
                                onOpenTour = { id ->
                                    if (displayedTourId != id) {
                                        displayedTour = null
                                        routePoints = emptyList()
                                    }
                                    displayedTourId = id
                                    displayedTourRequest++
                                    navController.navigate(SpurRoute.MAP)
                                },
                                onOpenPhoto = { photo, photos ->
                                    historyPhotos = photos
                                    historyPhotoDetail = photo
                                },
                            )
                        }
                    }
                    historyPhotoDetail?.let { photo ->
                        PhotoDetailPage(
                            photos = historyPhotos,
                            initialPhotoId = photo.id,
                            photoRevision = photoRevision,
                            showFeedbackNotice = showFeedbackNotice,
                            onPhotoChanged = { historyPhotoDetail = it },
                            onPhotoRotated = {
                                photoRevision++
                                historyRevision++
                            },
                            onPhotoDeleted = { deletedPhoto ->
                                scope.launch {
                                    val updatedMoments = context.deleteMapMoment(
                                        moment = deletedPhoto,
                                        moments = context.loadMapMoments(),
                                    )
                                    if (updatedMoments == null) {
                                        showFeedbackNotice(
                                            FeedbackNoticeKind.ERROR,
                                            "Das Bild konnte nicht gelöscht werden.",
                                        )
                                    } else {
                                        photoRevision++
                                        historyRevision++
                                    }
                                }
                            },
                            onDismiss = {
                                historyPhotoDetail = null
                                historyPhotos = emptyList()
                            },
                        )
                    }
                    if (isAppResumed && completionEligibilityChecked) {
                        completionTour?.let { finishedTour ->
                            val completionColors = context.loadColorTheme().mapControlColors
                            CompositionLocalProvider(
                                LocalMapControlColors provides completionColors,
                            ) {
                                TourCompletionBottomSheet(
                                    tour = finishedTour,
                                    preview = completionPreview,
                                    previewLoading = completionPreviewLoading,
                                    points = completionPoints,
                                    sheetState = completionSheetState,
                                    onDismiss = {
                                        context.clearPendingTourCompletion(finishedTour.id)
                                        scope.launch {
                                            completionSheetState.hide()
                                            completionTour = null
                                            completionPreview = null
                                            completionPoints = emptyList()
                                            completionPreviewLoading = false
                                        }
                                    },
                                )
                            }
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
