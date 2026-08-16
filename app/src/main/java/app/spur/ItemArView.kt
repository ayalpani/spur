package app.spur

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class ClaimOutcome {
    CLAIMED,
    ALREADY_CLAIMED,
    FAILED,
}

internal enum class DropOutcome {
    DROPPED,
    PENDING,
    FAILED,
}

@Composable
internal fun ItemArView(
    inventory: ItemInventory,
    inventoryAvailable: Boolean,
    deviceLocation: Location?,
    nearbyItems: List<PublicItem>,
    selectedTarget: PublicItem?,
    onDrop: suspend (OwnedItem, ItemLocation) -> DropOutcome,
    onClaim: suspend (PublicItem, ItemLocation) -> ClaimOutcome,
    onNotice: ShowFeedbackNotice,
    onExitAr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activityLifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val arLifecycleOwner = remember { ItemArLifecycleOwner() }
    val dormantArLifecycleOwner = remember { ItemArLifecycleOwner() }
    var pauseObserver by remember { mutableStateOf<LifecycleEventObserver?>(null) }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = true)
    val northBearing = rememberNorthBearing(deviceLocation)
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var cameraTracking by remember { mutableStateOf(false) }
    val latestFrame = remember { arrayOfNulls<Frame>(1) }
    var validGroundHit by remember { mutableStateOf(false) }
    var selectedOwnedItem by remember { mutableStateOf<OwnedItem?>(null) }
    var placement by remember { mutableStateOf<FruitAnchor?>(null) }
    var publicAnchors by remember { mutableStateOf<Map<String, FruitAnchor>>(emptyMap()) }
    var showApproximatePlacement by remember { mutableStateOf(false) }
    var waitingForLocation by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    var claimingItemId by remember { mutableStateOf<String?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    fun clearPlacement() {
        placement?.root?.destroy()
        placement = null
    }

    fun cancelPlacement() {
        clearPlacement()
        selectedOwnedItem = null
        showApproximatePlacement = false
        waitingForLocation = false
    }

    fun placePreview(hit: HitResult) {
        val selected = selectedOwnedItem ?: return
        val frame = latestFrame[0] ?: return
        val relativePose = frame.camera.pose.inverse().compose(hit.hitPose)
        val translation = relativePose.translation
        clearPlacement()
        placement = createFruitAnchor(
            engine = engine,
            modelLoader = modelLoader,
            anchor = hit.createAnchor(),
            kind = selected.kind,
            heightMeters = ItemPlacementHeightMeters,
            localOffset = LocalArOffset(
                rightMeters = translation[0].toDouble(),
                forwardMeters = -translation[2].toDouble(),
            ),
        )
    }

    fun placeApproximate() {
        val selected = selectedOwnedItem ?: return
        val activeSession = session ?: return
        val frame = latestFrame[0] ?: return
        if (!cameraTracking || frame.camera.trackingState != TrackingState.TRACKING) {
            onNotice(
                FeedbackNoticeKind.PLACEHOLDER,
                "Bewege dein Handy noch einen Moment",
            )
            return
        }
        clearPlacement()
        val pose = frame.camera.pose.compose(Pose.makeTranslation(0f, 0f, -2f))
        placement = createFruitAnchor(
            engine = engine,
            modelLoader = modelLoader,
            anchor = activeSession.createAnchor(pose),
            kind = selected.kind,
            heightMeters = 0f,
            localOffset = LocalArOffset(rightMeters = 0.0, forwardMeters = 2.0),
        )
    }

    LaunchedEffect(selectedOwnedItem?.id) {
        clearPlacement()
        showApproximatePlacement = false
        waitingForLocation = false
        if (selectedOwnedItem != null) {
            delay(8_000)
            if (placement == null) showApproximatePlacement = true
        }
    }

    val nearbyWithTarget = remember(nearbyItems, selectedTarget) {
        (nearbyItems + listOfNotNull(selectedTarget)).distinctBy { it.id }
    }
    val locationKey = deviceLocation?.let {
        Pair((it.latitude * 100_000).roundToInt(), (it.longitude * 100_000).roundToInt())
    }
    val bearingKey = northBearing?.div(5f)?.roundToInt()
    LaunchedEffect(session, nearbyWithTarget, locationKey, bearingKey, cameraTracking) {
        val activeSession = session ?: return@LaunchedEffect
        if (!cameraTracking) return@LaunchedEffect
        val location = deviceLocation?.toItemLocation() ?: return@LaunchedEffect
        val bearing = northBearing?.toDouble() ?: return@LaunchedEffect
        delay(120)
        val cameraPose = latestFrame[0]?.camera?.pose ?: return@LaunchedEffect
        val next = buildMap {
            nearbyWithTarget.forEach { item ->
                val distance = distanceMeters(location, item.location)
                if (distance > ItemSpatialRadiusMeters) return@forEach
                val offset = localAnchorOffset(location, item.location, bearing)
                val point = cameraPose.transformPoint(
                    floatArrayOf(
                        offset.rightMeters.toFloat(),
                        0f,
                        -offset.forwardMeters.toFloat(),
                    ),
                )
                val root = createFruitAnchor(
                    engine = engine,
                    modelLoader = modelLoader,
                    anchor = activeSession.createAnchor(Pose.makeTranslation(point)),
                    kind = item.kind,
                    heightMeters = 0f,
                    localOffset = offset,
                    onTap = {
                        if (
                            claimingItemId == null &&
                            nearbyItemPresentation(distance, location.accuracyMeters) ==
                            NearbyItemPresentation.CLAIMABLE
                        ) {
                            claimingItemId = item.id
                            scope.launch {
                                when (onClaim(item, deviceLocation.toItemLocation())) {
                                    ClaimOutcome.CLAIMED -> onNotice(
                                        FeedbackNoticeKind.PLACEHOLDER,
                                        "${item.kind.displayName} aufgenommen",
                                    )
                                    ClaimOutcome.ALREADY_CLAIMED -> onNotice(
                                        FeedbackNoticeKind.PLACEHOLDER,
                                        "Schon gefunden",
                                    )
                                    ClaimOutcome.FAILED -> onNotice(
                                        FeedbackNoticeKind.ERROR,
                                        "Item konnte nicht aufgenommen werden.",
                                    )
                                }
                                claimingItemId = null
                            }
                        }
                    },
                )
                put(item.id, root)
            }
        }
        publicAnchors.values.forEach { it.root.destroy() }
        publicAnchors = next
    }

    DisposableEffect(Unit) {
        onDispose {
            placement?.root?.destroy()
            publicAnchors.values.forEach { it.root.destroy() }
        }
    }

    DisposableEffect(activityLifecycle) {
        onDispose {
            pauseObserver?.let(activityLifecycle::removeObserver)
            pauseObserver = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            childNodes = publicAnchors.values.map { it.root } + listOfNotNull(placement?.root),
            sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.depthMode = Config.DepthMode.DISABLED
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onSessionCreated = {
                session = it
                cameraTracking = false
                sessionError = null
            },
            onSessionFailed = {
                cameraTracking = false
                sessionError = "AR ist auf diesem Gerät gerade nicht verfügbar."
            },
            onSessionUpdated = { _, frame ->
                latestFrame[0] = frame
                cameraTracking = frame.camera.trackingState == TrackingState.TRACKING
                val view = sceneView
                val hit = if (view == null || selectedOwnedItem == null) {
                    null
                } else {
                    view.hitTestAR(
                        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
                        predicate = { it.distance in 0.5f..8f },
                    )
                }
                validGroundHit = hit != null
                placement?.model?.let { model ->
                    val bob = sin(System.nanoTime() / 700_000_000.0).toFloat() * 0.015f
                    model.position = Position(
                        model.position.x,
                        (placement?.baseHeightMeters ?: 0f) + bob,
                        model.position.z,
                    )
                }
                publicAnchors.values.forEach { fruit ->
                    val bob = sin(
                        System.nanoTime() / 700_000_000.0 + fruit.root.hashCode(),
                    ).toFloat() * 0.015f
                    fruit.model.position = Position(0f, fruit.baseHeightMeters + bob, 0f)
                }
            },
            onTouchEvent = { event, _ ->
                if (event.actionMasked != MotionEvent.ACTION_UP || selectedOwnedItem == null) {
                    false
                } else {
                    sceneView?.hitTestAR(
                        xPx = event.x,
                        yPx = event.y,
                        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
                        predicate = { it.distance in 0.5f..8f },
                    )?.let(::placePreview) != null
                }
            },
            onViewCreated = {
                sceneView = this
                pauseObserver?.let(activityLifecycle::removeObserver)
                pauseObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        // SceneView 2.3.0 pauses ARCore before its Choreographer callback. A frame
                        // arriving between those steps crashes with SessionPausedException. This
                        // observer is registered last, so it runs first for downward lifecycle
                        // events and stops rendering before the session can be paused.
                        runCatching { destroy() }
                        sceneView = null
                        session = null
                        onExitAr()
                    }
                }.also(activityLifecycle::addObserver)

                runCatching { arLifecycleOwner.moveTo(activityLifecycle.currentState) }
                    .onFailure {
                        sessionError = "AR ist auf diesem Gerät gerade nicht verfügbar."
                        runCatching { destroy() }
                        // AndroidView still attaches the factory result once. Keep a non-null,
                        // dormant lifecycle on the destroyed view so SceneView cannot silently
                        // rebind itself to the resumed Activity lifecycle during that attach.
                        lifecycle = dormantArLifecycleOwner.lifecycle
                        sceneView = null
                        session = null
                        cameraTracking = false
                        onNotice(
                            FeedbackNoticeKind.ERROR,
                            "AR ist auf diesem Gerät gerade nicht verfügbar.",
                        )
                        onExitAr()
                    }
            },
            lifecycle = arLifecycleOwner.lifecycle,
        )

        sessionError?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (selectedOwnedItem != null) {
            PlacementGuidance(
                validGroundHit = validGroundHit,
                waitingForLocation = waitingForLocation,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp, start = 24.dp, end = 24.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .alpha(if (placement == null) 1f else 0.45f)
                    .border(
                        3.dp,
                        if (validGroundHit) LocalAccentColor.current else Color.White,
                        CircleShape,
                    ),
            )
        }

        val nearestGuide = nearbyWithTarget
            .mapNotNull { item ->
                deviceLocation?.toItemLocation()?.let { location ->
                    item to distanceMeters(location, item.location)
                }
            }
            .filter { (_, distance) -> distance > ItemSpatialRadiusMeters }
            .minByOrNull { it.second }
        if (selectedOwnedItem == null && nearestGuide != null) {
            ItemDirectionGuide(
                item = nearestGuide.first,
                distanceMeters = nearestGuide.second,
                deviceLocation = deviceLocation?.toItemLocation(),
                northBearing = northBearing?.toDouble(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp),
            )
        }

        if (selectedOwnedItem == null) {
            ArInventoryRail(
                items = inventory.items,
                pendingItemIds = inventory.pendingDrops.mapTo(mutableSetOf()) { it.itemId },
                selectedItemId = null,
                onSelect = { selectedOwnedItem = it },
                modifier = Modifier.align(Alignment.BottomCenter),
                available = inventoryAvailable,
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (placement != null) {
                    val selectedDropPending = inventory.pendingDrops.any {
                        it.itemId == selectedOwnedItem?.id
                    }
                    SpurPrimaryButton(
                        label = when {
                            dropping -> "Wird veröffentlicht …"
                            selectedDropPending -> "Erneut veröffentlichen"
                            else -> "Hier ablegen"
                        },
                        enabled = !dropping,
                        onClick = {
                            val item = selectedOwnedItem ?: return@SpurPrimaryButton
                            val preview = placement ?: return@SpurPrimaryButton
                            val location = deviceLocation
                            val bearing = northBearing
                            if (
                                location == null ||
                                !location.isPublishableItemLocation(System.currentTimeMillis()) ||
                                bearing == null
                            ) {
                                waitingForLocation = true
                                return@SpurPrimaryButton
                            }
                            dropping = true
                            scope.launch {
                                val destination = projectArOffset(
                                    origin = location.toItemLocation(),
                                    northBearingDegrees = bearing.toDouble(),
                                    offset = preview.localOffset,
                                )
                                when (onDrop(item, destination)) {
                                    DropOutcome.DROPPED -> cancelPlacement()
                                    DropOutcome.PENDING -> Unit
                                    DropOutcome.FAILED -> Unit
                                }
                                dropping = false
                            }
                        },
                    )
                } else if (showApproximatePlacement) {
                    SpurSecondaryButton(
                        label = "Ungefähr platzieren",
                        onClick = ::placeApproximate,
                    )
                }
                SpurSecondaryButton(label = "Abbrechen", onClick = ::cancelPlacement)
            }
        }
    }
}
