package app.spur

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var historyRevision by remember { mutableLongStateOf(0L) }
    var photoRevision by remember { mutableLongStateOf(0L) }
    var historyPhotoDetail by remember { mutableStateOf<MapMoment?>(null) }
    var historyPhotos by remember { mutableStateOf(emptyList<MapMoment>()) }
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

    LaunchedEffect(hasLocationPermission, activeTour?.id, displayedTourId) {
        if (!hasLocationPermission || activeTour != null) return@LaunchedEffect
        while (true) {
            val restored = withContext(Dispatchers.IO) { store.activeTour() }
            if (restored == null) {
                delay(1_000L)
                continue
            }
            activeTour = restored
            if (displayedTourId == null) {
                displayedTour = restored
                displayedTourId = restored.id
                displayedTourRequest++
            }
            historyRevision++
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TOUR_ID, restored.id),
            )
            break
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
                                tour = displayedTour,
                                activeTour = activeTour,
                                tourDisplayRequest = displayedTourRequest,
                                routePoints = routePoints,
                                now = now,
                                onStartTour = {
                                    scope.launch {
                                        val id = withContext(Dispatchers.IO) {
                                            val start = store.activeTourOrStart()
                                            if (start.created) {
                                                context.loadManualLocation()?.let { coordinate ->
                                                    store.appendSimulatedLocation(
                                                        start.id,
                                                        coordinate,
                                                    )
                                                }
                                            }
                                            start.id
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
                                        result.first?.let { finishedTour ->
                                            if (
                                                context.ensureTourHistoryAssets(
                                                    finishedTour,
                                                    result.second,
                                                )
                                            ) {
                                                historyRevision++
                                            }
                                        }
                                    }
                                },
                                onOpenHistory = {
                                    isHistoryVisible = true
                                },
                                onCloseDisplayedTour = {
                                    val currentActiveTour = activeTour
                                    displayedTour = currentActiveTour
                                    displayedTourId = currentActiveTour?.id
                                    displayedTourRequest++
                                    routePoints = emptyList()
                                },
                                onDeleteTour = deleteTour,
                                onDeleteWaypoint = { tourId, retainedIds ->
                                    runCatching {
                                        val result = withContext(Dispatchers.IO) {
                                            store.updateTourPoints(tourId, retainedIds)
                                            store.tour(tourId) to store.points(tourId)
                                        }
                                        if (displayedTourId == tourId) {
                                            displayedTour = result.first
                                            routePoints = result.second
                                        }
                                        if (activeTour?.id == tourId) {
                                            activeTour = result.first
                                        }
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
                        }
                    }

                    if (isHistoryVisible) {
                        val historySheetState =
                            rememberModalBottomSheetState(skipPartiallyExpanded = false)
                        SpurModalBottomSheet(
                            onDismissRequest = { isHistoryVisible = false },
                            sheetState = historySheetState,
                        ) {
                            HistoryBottomSheet(
                                store = store,
                                revision = historyRevision,
                                onOpenTour = { id ->
                                    if (displayedTourId != id) {
                                        displayedTour = null
                                        routePoints = emptyList()
                                    }
                                    displayedTourId = id
                                    displayedTourRequest++
                                    isHistoryVisible = false
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
