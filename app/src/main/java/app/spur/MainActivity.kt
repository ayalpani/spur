package app.spur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.launch

private val Sand = Color(0xFFF7F5F0)
private val Ink = Color(0xFF18201C)
private val Moss = Color(0xFF23614A)

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
    var isTourActive by rememberSaveable { mutableStateOf(false) }
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
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
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
                        HistoryScreen(onBack = { showHistory = false })
                    } else {
                        MapScreen(
                            isTourActive = isTourActive,
                            onTourAction = { isTourActive = !isTourActive },
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
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
private fun MapScreen(
    isTourActive: Boolean,
    onTourAction: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var recenterRequest by rememberSaveable { mutableStateOf(0) }
    var resetNorthRequest by rememberSaveable { mutableStateOf(0) }

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
                    Text(
                        text = if (isTourActive) "Tour läuft" else "Keine Tour aktiv",
                        color = if (isTourActive) Moss else Ink.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodyMedium,
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
                recenterRequest = recenterRequest,
                resetNorthRequest = resetNorthRequest,
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
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    TourStatus(isTourActive = isTourActive)
                }
                if (isTourActive) {
                    MapIconButton(
                        contentDescription = "Tour teilen",
                        onClick = { shareActiveTour(context) },
                    ) {
                        ShareIcon()
                    }
                } else {
                    Spacer(modifier = Modifier.size(60.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapIconButton(
                    contentDescription = "Karte nach Norden ausrichten",
                    onClick = { resetNorthRequest++ },
                ) {
                    CompassIcon()
                }
                MapIconButton(
                    contentDescription = "Auf eigenen Standort zentrieren",
                    onClick = { recenterRequest++ },
                ) {
                    RecenterIcon()
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
                Button(
                    onClick = onTourAction,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    contentPadding = PaddingValues(vertical = 18.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    Text(
                        text = if (isTourActive) "Tour beenden" else "Tour starten",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.size(60.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Ink,
                    ),
                ) {
                    HistoryIcon()
                }
            }
        }
    }
}

@Composable
private fun TourStatus(isTourActive: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Sand.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isTourActive) Moss else Ink.copy(alpha = 0.38f),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = if (isTourActive) "Tour läuft" else "Keine Tour",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
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
private fun MapSurface(
    recenterRequest: Int,
    resetNorthRequest: Int,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.uiSettings.isCompassEnabled = false
                map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                    enableLocationTracking(context, map.locationComponent, style)
                }
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

    LaunchedEffect(recenterRequest) {
        if (recenterRequest == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.setCameraMode(
                    CameraMode.TRACKING_COMPASS,
                    500L,
                    16.0,
                    null,
                    null,
                    null,
                )
            }
        }
    }

    LaunchedEffect(resetNorthRequest) {
        if (resetNorthRequest == 0) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.cameraMode = CameraMode.NONE
            }
            map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 500)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Interaktive Kartenansicht" },
    )
}

private fun shareActiveTour(context: Context) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Meine Tour mit Spur läuft gerade.")
    }
    context.startActivity(Intent.createChooser(share, "Tour teilen"))
}

@SuppressLint("MissingPermission")
private fun enableLocationTracking(
    context: Context,
    locationComponent: org.maplibre.android.location.LocationComponent,
    style: Style,
) {
    if (!context.hasLocationPermission()) return

    val options = LocationComponentOptions.builder(context)
        .pulseEnabled(true)
        .build()
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build(),
    )
    locationComponent.isLocationComponentEnabled = true
    locationComponent.renderMode = RenderMode.COMPASS
    locationComponent.setCameraMode(
        CameraMode.TRACKING_COMPASS,
        750L,
        16.0,
        null,
        null,
        null,
    )
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
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
    }
}

@Composable
private fun MenuIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.2.dp.toPx()
        listOf(6f, 12f, 18f).forEach { y ->
            drawLine(
                color = Ink,
                start = Offset(4.dp.toPx(), y.dp.toPx()),
                end = Offset(20.dp.toPx(), y.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ShareIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val left = Offset(6.dp.toPx(), 12.dp.toPx())
        val upper = Offset(17.dp.toPx(), 6.dp.toPx())
        val lower = Offset(17.dp.toPx(), 18.dp.toPx())
        val stroke = 2.dp.toPx()
        drawLine(Ink, left, upper, stroke, cap = StrokeCap.Round)
        drawLine(Ink, left, lower, stroke, cap = StrokeCap.Round)
        listOf(left, upper, lower).forEach { point ->
            drawCircle(Sand, radius = 3.2.dp.toPx(), center = point)
            drawCircle(Ink, radius = 3.2.dp.toPx(), center = point, style = Stroke(stroke))
        }
    }
}

@Composable
private fun CompassIcon() {
    Canvas(modifier = Modifier.size(25.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(Ink, radius = 10.dp.toPx(), center = center, style = Stroke(stroke))
        val north = Offset(center.x + 3.dp.toPx(), center.y - 7.dp.toPx())
        val south = Offset(center.x - 3.dp.toPx(), center.y + 7.dp.toPx())
        drawLine(Ink, south, north, stroke, cap = StrokeCap.Round)
        drawCircle(Ink, radius = 2.dp.toPx(), center = north)
    }
}

@Composable
private fun RecenterIcon() {
    Canvas(modifier = Modifier.size(25.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(Ink, radius = 6.dp.toPx(), center = center, style = Stroke(stroke))
        drawCircle(Ink, radius = 2.dp.toPx(), center = center)
        drawLine(Ink, Offset(center.x, 1.dp.toPx()), Offset(center.x, 5.dp.toPx()), stroke)
        drawLine(Ink, Offset(center.x, 20.dp.toPx()), Offset(center.x, 24.dp.toPx()), stroke)
        drawLine(Ink, Offset(1.dp.toPx(), center.y), Offset(5.dp.toPx(), center.y), stroke)
        drawLine(Ink, Offset(20.dp.toPx(), center.y), Offset(24.dp.toPx(), center.y), stroke)
    }
}

@Composable
private fun HistoryIcon() {
    Canvas(
        modifier = Modifier
            .size(25.dp)
            .semantics { contentDescription = "Tour-History öffnen" },
    ) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = Ink,
            startAngle = -55f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
            size = Size(19.dp.toPx(), 19.dp.toPx()),
            style = stroke,
        )
        drawLine(Ink, Offset(3.dp.toPx(), 7.dp.toPx()), Offset(3.dp.toPx(), 3.dp.toPx()), stroke.width)
        drawLine(Ink, Offset(3.dp.toPx(), 3.dp.toPx()), Offset(7.dp.toPx(), 3.dp.toPx()), stroke.width)
        drawLine(Ink, center, Offset(center.x, center.y - 5.dp.toPx()), stroke.width)
        drawLine(Ink, center, Offset(center.x + 4.dp.toPx(), center.y + 2.dp.toPx()), stroke.width)
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
    MapScreen(isTourActive = false, onTourAction = {}, onOpenHistory = {})
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ActiveTourScreenPreview() {
    MapScreen(isTourActive = true, onTourAction = {}, onOpenHistory = {})
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HistoryScreenPreview() {
    HistoryScreen(onBack = {})
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}
