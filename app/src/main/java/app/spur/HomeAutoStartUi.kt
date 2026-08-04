package app.spur

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun HomeAutoStartBottomSheet(
    onSettingsChanged: (HomeAutoStartSettings) -> Unit,
    onChooseHome: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var settings by remember { mutableStateOf(context.loadHomeAutoStartSettings()) }
    var awaitingBackgroundPermission by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun updateEnabled(enabled: Boolean) {
        if (!enabled) context.removeHomeAutoStart()
        settings = settings.copy(enabled = enabled)
        context.saveHomeAutoStartSettings(settings)
        if (enabled && !context.registerHomeAutoStart()) {
            settings = settings.copy(enabled = false)
            context.saveHomeAutoStartSettings(settings)
            context.removeHomeAutoStart()
            onSettingsChanged(settings)
            message = "Die Startautomatik konnte nicht gestartet werden."
            return
        }
        onSettingsChanged(settings)
        message = if (enabled) {
            "Spur startet deine Tour beim Verlassen deines Zuhauses."
        } else {
            "Die Startautomatik ist ausgeschaltet."
        }
    }

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (settings.enabled) {
                message = if (context.registerHomeAutoStart()) {
                    "Der genaue Tourstart ist aktiv."
                } else {
                    "Die Startautomatik konnte nicht gestartet werden."
                }
            } else {
                updateEnabled(true)
            }
        } else {
            message = if (settings.enabled) {
                "Die Startautomatik bleibt aktiv. Die zusätzliche Schritterkennung ist aus."
            } else {
                "Die Startautomatik bleibt aus, bis du den Bewegungszugriff erlaubst."
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            message = if (settings.enabled) {
                "Die Startautomatik bleibt aktiv, aber Android blendet ihre Meldung aus."
            } else {
                "Die Startautomatik bleibt aus, bis Benachrichtigungen erlaubt sind."
            }
        } else if (context.hasActivityRecognitionPermission()) {
            updateEnabled(true)
        } else {
            activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        awaitingBackgroundPermission = false
        if (granted) {
            if (!context.hasTourNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (context.hasActivityRecognitionPermission()) {
                updateEnabled(true)
            } else {
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            message = "Ohne Hintergrundstandort bleibt die Startautomatik aus."
        }
    }

    fun enable() {
        if (context.hasBackgroundLocationPermission()) {
            if (!context.hasTourNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (context.hasActivityRecognitionPermission()) {
                updateEnabled(true)
            } else {
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            awaitingBackgroundPermission = true
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
    }

    val currentAwaitingPermission by rememberUpdatedState(awaitingBackgroundPermission)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentAwaitingPermission) {
                awaitingBackgroundPermission = false
                if (context.hasBackgroundLocationPermission()) {
                    if (!context.hasTourNotificationPermission()) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    } else if (context.hasActivityRecognitionPermission()) {
                        updateEnabled(true)
                    } else {
                        activityPermissionLauncher.launch(
                            Manifest.permission.ACTIVITY_RECOGNITION,
                        )
                    }
                } else {
                    message = "Ohne Hintergrundstandort bleibt die Startautomatik aus."
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
        BottomSheetHeader(title = "Startautomatik")
        Text(
            text = "Tour automatisch beim Verlassen deines Zuhauses starten.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (settings.homeBuilding == null || settings.home == null) {
            Text(
                text = "Lege zuerst dein Zuhause auf der Karte fest.",
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            SpurPrimaryButton(
                label = "Zuhause festlegen",
                onClick = onChooseHome,
            )
        } else {
            Text(
                text = if (settings.enabled) {
                    if (!context.hasTourNotificationPermission()) {
                        "Die Startautomatik ist aktiv. Android blendet die dauerhafte " +
                            "Startmeldung aus."
                    } else if (context.hasActivityRecognitionPermission()) {
                        "Die Startautomatik und der genaue Tourstart sind aktiv."
                    } else {
                        "Spur wartet sichtbar auf deinen Start. Die zusätzliche " +
                            "Schritterkennung ist nicht freigegeben."
                    }
                } else if (!context.hasBackgroundLocationPermission()) {
                    "Erlaube Spur den Standortzugriff im Hintergrund, damit eine Tour " +
                        "auch bei geschlossener App starten kann."
                } else {
                    "Dein Zuhause ist gespeichert. Die Startautomatik ist aus."
                },
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            SpurPrimaryButton(
                label = if (settings.enabled) {
                    "Startautomatik ausschalten"
                } else if (!context.hasBackgroundLocationPermission()) {
                    "Hintergrundzugriff erlauben"
                } else {
                    "Startautomatik einschalten"
                },
                onClick = {
                    if (settings.enabled) updateEnabled(false) else enable()
                },
            )
            if (settings.enabled && !context.hasActivityRecognitionPermission()) {
                SpurSecondaryButton(
                    label = "Schritterkennung erlauben",
                    onClick = {
                        activityPermissionLauncher.launch(
                            Manifest.permission.ACTIVITY_RECOGNITION,
                        )
                    },
                )
            }
            if (settings.enabled && !context.hasTourNotificationPermission()) {
                SpurSecondaryButton(
                    label = "Startmeldung erlauben",
                    onClick = {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    },
                )
            }
            SpurSecondaryButton(
                label = "Zuhause ändern",
                onClick = onChooseHome,
            )
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

internal enum class HomeSelectionStep {
    BUILDING,
    START_POINT,
}

@Composable
internal fun HomeSelectionPanel(
    step: HomeSelectionStep,
    selectedHome: SelectedBuilding?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SheetBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when {
                    step == HomeSelectionStep.START_POINT ->
                        "Lege das Fadenkreuz vor deinem Zuhause ab."
                    selectedHome == null ->
                        "Tippe auf der Karte auf das Gebäude, in dem du wohnst."
                    else ->
                        "Ist das markierte Gebäude dein Zuhause?"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    step == HomeSelectionStep.START_POINT ->
                        "Verschiebe die Karte, bis das Fadenkreuz am gewünschten " +
                            "Tourstartpunkt liegt."
                    selectedHome == null ->
                        "Du kannst die Karte verschieben und zoomen."
                    else ->
                        "Dein Zuhause bleibt anschließend auf der Karte markiert."
                },
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (selectedHome != null) {
                SpurPrimaryButton(
                    label = if (step == HomeSelectionStep.START_POINT) {
                        "Startpunkt bestätigen"
                    } else {
                        "Das ist mein Zuhause"
                    },
                    onClick = onConfirm,
                )
            }
            SpurSecondaryButton(
                label = "Abbrechen",
                onClick = onCancel,
            )
        }
    }
}

@Composable
internal fun HomeStartPointCrosshair(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = "Fadenkreuz für den Tourstartpunkt" },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val gap = 5.dp.toPx()
        val radius = 16.dp.toPx()
        val stroke = 3.dp.toPx()
        drawCircle(
            color = SheetBackground,
            radius = 7.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Ink,
            radius = 4.dp.toPx(),
            center = center,
        )
        drawLine(Ink, Offset(center.x, center.y - radius), Offset(center.x, center.y - gap), stroke)
        drawLine(Ink, Offset(center.x, center.y + gap), Offset(center.x, center.y + radius), stroke)
        drawLine(Ink, Offset(center.x - radius, center.y), Offset(center.x - gap, center.y), stroke)
        drawLine(Ink, Offset(center.x + gap, center.y), Offset(center.x + radius, center.y), stroke)
    }
}
