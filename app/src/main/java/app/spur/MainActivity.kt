package app.spur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

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
    var isTourActive by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

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

@Composable
private fun MapScreen(
    isTourActive: Boolean,
    onTourAction: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapSurface()

        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = Sand.copy(alpha = 0.94f),
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "Spur",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isTourActive) {
                    Text(
                        text = "Tour läuft",
                        color = Moss,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
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
            Button(
                onClick = onTourAction,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTourActive) Ink else Moss,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Text(
                    text = if (isTourActive) "Tour beenden" else "Tour starten",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Sand,
                    contentColor = Ink,
                ),
            ) {
                HistoryIcon()
            }
        }
    }
}

@Composable
private fun MapSurface() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle("https://tiles.openfreemap.org/styles/liberty")
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(52.52, 13.405))
                    .zoom(13.0)
                    .build()
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

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Interaktive Kartenansicht" },
    )
}

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
