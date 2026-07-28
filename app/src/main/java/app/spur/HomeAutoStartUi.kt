package app.spur

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun HomeAutoStartBottomSheet(
    onSettingsChanged: (HomeAutoStartSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var settings by remember { mutableStateOf(context.loadHomeAutoStartSettings()) }
    var setupRequested by rememberSaveable { mutableStateOf(false) }
    var homeSearchOrigin by remember { mutableStateOf<SpurCoordinate?>(settings.home) }
    var candidateHome by remember { mutableStateOf<SelectedBuilding?>(null) }
    var candidateStartPoint by remember { mutableStateOf(settings.startPoint) }
    var selectingStartPoint by rememberSaveable { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var needsBackgroundPermission by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun disable() {
        context.removeHomeExitGeofence()
        settings = HomeAutoStartSettings(enabled = false, home = null)
        context.saveHomeAutoStartSettings(settings)
        onSettingsChanged(settings)
        setupRequested = false
        homeSearchOrigin = null
        candidateHome = null
        candidateStartPoint = null
        selectingStartPoint = false
        needsBackgroundPermission = false
        message = null
    }

    fun activate(home: SelectedBuilding, startPoint: SpurCoordinate) {
        settings = HomeAutoStartSettings(
            enabled = true,
            home = home.coordinate,
            homeBuilding = home.feature,
            startPoint = startPoint,
        )
        context.saveHomeAutoStartSettings(settings)
        onSettingsChanged(settings)
        context.registerHomeExitGeofence()
        setupRequested = false
        selectingStartPoint = false
        needsBackgroundPermission = false
        message = "Startautomatik ist aktiv."
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val home = candidateHome
            val startPoint = candidateStartPoint
            if (home != null && startPoint != null) activate(home, startPoint)
        } else {
            locating = false
            setupRequested = false
            homeSearchOrigin = null
            candidateHome = null
            candidateStartPoint = null
            selectingStartPoint = false
            needsBackgroundPermission = false
            message = "Ohne Hintergrundstandort bleibt die Einstellung aus."
        }
    }

    fun requestBackgroundLocation() {
        needsBackgroundPermission = true
    }

    LaunchedEffect(needsBackgroundPermission) {
        if (!needsBackgroundPermission) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            backgroundPermissionLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
    }

    val currentNeedsBackgroundPermission by rememberUpdatedState(needsBackgroundPermission)
    val currentCandidateHome by rememberUpdatedState(candidateHome)
    val currentCandidateStartPoint by rememberUpdatedState(candidateStartPoint)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentNeedsBackgroundPermission) {
                if (context.hasBackgroundLocationPermission()) {
                    val home = currentCandidateHome
                    val startPoint = currentCandidateStartPoint
                    if (home != null && startPoint != null) activate(home, startPoint)
                } else {
                    locating = false
                    setupRequested = false
                    homeSearchOrigin = null
                    candidateHome = null
                    candidateStartPoint = null
                    selectingStartPoint = false
                    needsBackgroundPermission = false
                    message = "Ohne Hintergrundstandort bleibt die Einstellung aus."
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BottomSheetHeader(
            title = "Startautomatik",
            onBack = onBack,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Startautomatik",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Switch(
                checked = settings.enabled || setupRequested,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        disable()
                    } else {
                        setupRequested = true
                        locating = true
                        message = null
                        scope.launch {
                            val currentLocation = context.currentSpurLocation()
                            homeSearchOrigin = currentLocation
                            candidateHome = null
                            candidateStartPoint = null
                            selectingStartPoint = false
                            locating = false
                            if (currentLocation == null) {
                                setupRequested = false
                                message = "Dein aktueller Standort konnte nicht bestimmt werden."
                            }
                        }
                    }
                },
            )
        }

        val shownHomeOrigin = settings.home ?: homeSearchOrigin
        if (shownHomeOrigin != null) {
            HomeBuildingSelector(
                origin = shownHomeOrigin,
                initialBuilding = settings.homeBuilding,
                initialStartPoint = candidateStartPoint,
                selectionEnabled = !settings.enabled && !selectingStartPoint,
                startPointSelection = selectingStartPoint,
                onBuildingSelected = { selected ->
                    candidateHome = selected
                    candidateStartPoint = selected.coordinate
                },
                onStartPointChanged = { candidateStartPoint = it },
            )
        }

        when {
            locating -> Text("Aktueller Standort wird bestimmt.")
            settings.enabled -> Text("Spur startet eine Tour, wenn du diesen Bereich verlässt.")
            selectingStartPoint && candidateHome != null -> {
                Text(
                    text = "Startpunkt vor dem Haus festlegen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Verschiebe die Karte, bis das Fadenkreuz auf dem Startpunkt liegt.")
                if (!context.hasBackgroundLocationPermission()) {
                    Text(
                        text = "Danach öffnen sich die Android-Einstellungen. " +
                            "Wähle dort Berechtigungen → Standort → Immer zulassen.",
                    )
                }
                Button(
                    onClick = {
                        val home = candidateHome ?: return@Button
                        val startPoint = candidateStartPoint ?: return@Button
                        if (context.hasBackgroundLocationPermission()) {
                            activate(home, startPoint)
                        } else {
                            requestBackgroundLocation()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    enabled = candidateStartPoint != null,
                ) {
                    Text("Startpunkt übernehmen", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        selectingStartPoint = false
                        candidateStartPoint = candidateHome?.coordinate
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Gebäude ändern")
                }
            }
            candidateHome != null -> {
                Text(
                    text = "Bist du gerade zu Hause?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = {
                        val home = candidateHome ?: return@Button
                        candidateStartPoint = home.coordinate
                        selectingStartPoint = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Ja, hier ist mein Zuhause", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        setupRequested = false
                        homeSearchOrigin = null
                        candidateHome = null
                        candidateStartPoint = null
                        selectingStartPoint = false
                        message = "Komm später wieder, wenn du zu Hause bist."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Nein")
                }
            }
            homeSearchOrigin != null -> Text("Passendes Gebäude wird gesucht.")
        }

        message?.let {
            Text(
                text = it,
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
