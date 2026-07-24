package app.spur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

private val Sand = Color(0xFFF7F5F0)
private val Ink = Color(0xFF18201C)
private val Moss = Color(0xFF23614A)
private val Mist = Color(0xFFE8EEE9)

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
    var activeTourId by remember { mutableStateOf<Long?>(null) }
    var selectedTourId by remember { mutableStateOf<Long?>(null) }
    var homeRevision by remember { mutableLongStateOf(0L) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    fun beginTour() {
        scope.launch {
            val id = withContext(Dispatchers.IO) { store.startTour() }
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TOUR_ID, id),
            )
            activeTourId = id
            selectedTourId = null
        }
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            permissionMessage = null
            beginTour()
        } else {
            permissionMessage = "Ohne genauen Standort kann Spur keine Tour aufzeichnen."
        }
    }

    LaunchedEffect(Unit) {
        activeTourId = withContext(Dispatchers.IO) { store.activeTour()?.id }
        activeTourId?.let { id ->
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TOUR_ID, id),
            )
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
            when {
                activeTourId != null -> ActiveTourScreen(
                    tourId = activeTourId!!,
                    store = store,
                    onEndTour = {
                        val finishedId = activeTourId ?: return@ActiveTourScreen
                        scope.launch {
                            withContext(Dispatchers.IO) { store.finishTour(finishedId) }
                            context.startService(
                                Intent(context, TrackingService::class.java)
                                    .setAction(TrackingService.ACTION_STOP),
                            )
                            activeTourId = null
                            selectedTourId = finishedId
                            homeRevision++
                        }
                    },
                )

                selectedTourId != null -> TourDetailScreen(
                    tourId = selectedTourId!!,
                    store = store,
                    onBack = { selectedTourId = null },
                )

                else -> HomeScreen(
                    store = store,
                    revision = homeRevision,
                    permissionMessage = permissionMessage,
                    onStartTour = {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            beginTour()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    onOpenTour = { selectedTourId = it },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    store: TourStore,
    revision: Long,
    permissionMessage: String?,
    onStartTour: () -> Unit,
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Spur",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Wege, die dir gehören.",
            modifier = Modifier.padding(top = 4.dp),
            color = Ink.copy(alpha = 0.64f),
            style = MaterialTheme.typography.bodyLarge,
        )
        permissionMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 20.dp),
                color = Ink.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (tours.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Noch keine Touren",
                modifier = Modifier.fillMaxWidth(),
                color = Ink.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tours, key = { it.id }) { tour ->
                    TourCard(tour = tour, onClick = { onOpenTour(tour.id) })
                }
            }
        }

        Button(
            onClick = onStartTour,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(vertical = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Moss),
        ) {
            Text("Neue Tour starten", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TourCard(tour: Tour, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
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
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ActiveTourScreen(
    tourId: Long,
    store: TourStore,
    onEndTour: () -> Unit,
) {
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tourId) {
        while (true) {
            tour = withContext(Dispatchers.IO) { store.tour(tourId) }
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val currentTour = tour

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Tour läuft",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = currentTour?.let { "Seit ${formatClock(it.startedAt)}" } ?: "Standort wird gesucht …",
            modifier = Modifier.padding(top = 4.dp),
            color = Ink.copy(alpha = 0.64f),
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.weight(1f))

        currentTour?.let {
            Text(
                text = formatKilometers(it.distanceMeters),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = formatDuration(now - it.startedAt),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 28.dp),
                textAlign = TextAlign.Center,
                color = Ink.copy(alpha = 0.52f),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .semantics {
                    role = Role.Button
                    contentDescription = "Tour beenden"
                }
                .clickable(role = Role.Button, onClick = onEndTour),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Mist, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Moss, RoundedCornerShape(3.dp)),
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = currentTour?.let { formatKilometers(it.distanceMeters) } ?: "0,00 km",
                        color = Moss,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = currentTour?.let {
                            "Seit ${formatClock(it.startedAt)} · ${formatDuration(now - it.startedAt)}"
                        } ?: "Tour stoppen",
                        color = Ink.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TourDetailScreen(
    tourId: Long,
    store: TourStore,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var points by remember(tourId) { mutableStateOf(emptyList<TrackPoint>()) }
    LaunchedEffect(tourId) {
        val result = withContext(Dispatchers.IO) {
            store.tour(tourId) to store.points(tourId)
        }
        tour = result.first
        points = result.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("← Touren")
        }
        Text(
            text = tour?.let { formatDate(it.startedAt) } ?: "Tour",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = tour?.let { "${formatKilometers(it.distanceMeters)} · ${formatTourTime(it)}" }
                ?: "Wird geladen …",
            modifier = Modifier.padding(top = 4.dp),
            color = Ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .aspectRatio(0.86f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
        ) {
            if (points.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Für diese Tour wurde noch keine Spur gefunden.",
                        modifier = Modifier.padding(28.dp),
                        textAlign = TextAlign.Center,
                        color = Ink.copy(alpha = 0.5f),
                    )
                }
            } else {
                Trail(points)
            }
        }
    }
}

@Composable
private fun Trail(points: List<TrackPoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        val averageLatitude = points.map { it.latitude }.average()
        val longitudeScale = cos(Math.toRadians(averageLatitude)).coerceAtLeast(0.2)
        val xs = points.map { it.longitude * longitudeScale }
        val ys = points.map { it.latitude }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val rangeX = (maxX - minX).coerceAtLeast(0.00002)
        val rangeY = (maxY - minY).coerceAtLeast(0.00002)
        val scale = minOf(size.width / rangeX, size.height / rangeY)
        val contentWidth = rangeX * scale
        val contentHeight = rangeY * scale
        val offsetX = (size.width - contentWidth) / 2f
        val offsetY = (size.height - contentHeight) / 2f

        fun position(index: Int) = Offset(
            x = (offsetX + (xs[index] - minX) * scale).toFloat(),
            y = (offsetY + (maxY - ys[index]) * scale).toFloat(),
        )

        if (points.size == 1) {
            drawCircle(Moss, radius = 7.dp.toPx(), center = position(0))
        } else {
            val path = Path().apply {
                val first = position(0)
                moveTo(first.x, first.y)
                for (index in 1 until points.size) {
                    val point = position(index)
                    lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = path,
                color = Moss,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(Color.White, radius = 7.dp.toPx(), center = position(0))
            drawCircle(Moss, radius = 4.dp.toPx(), center = position(0))
            drawCircle(Moss, radius = 8.dp.toPx(), center = position(points.lastIndex))
        }
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
