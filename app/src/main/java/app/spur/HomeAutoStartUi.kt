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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var settings by remember { mutableStateOf(context.loadHomeAutoStartSettings()) }
    var awaitingBackgroundPermission by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun updateEnabled(enabled: Boolean) {
        if (!enabled) context.removeHomeExitGeofence()
        settings = settings.copy(enabled = enabled)
        context.saveHomeAutoStartSettings(settings)
        if (enabled) context.registerHomeExitGeofence()
        onSettingsChanged(settings)
        message = if (enabled) {
            "Spur startet deine Tour beim Verlassen deines Zuhauses."
        } else {
            "Die Startautomatik ist ausgeschaltet."
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        awaitingBackgroundPermission = false
        if (granted) {
            updateEnabled(true)
        } else {
            message = "Ohne Hintergrundstandort bleibt die Startautomatik aus."
        }
    }

    fun enable() {
        if (context.hasBackgroundLocationPermission()) {
            updateEnabled(true)
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
                    updateEnabled(true)
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
        BottomSheetHeader(
            title = "Startautomatik",
            onBack = onBack,
        )
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
                    "Die Startautomatik ist aktiv."
                } else {
                    "Dein Zuhause ist gespeichert. Die Startautomatik ist aus."
                },
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            SpurPrimaryButton(
                label = if (settings.enabled) {
                    "Startautomatik ausschalten"
                } else {
                    "Startautomatik einschalten"
                },
                onClick = {
                    if (settings.enabled) updateEnabled(false) else enable()
                },
            )
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

@Composable
internal fun HomeSelectionPanel(
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
                text = if (selectedHome == null) {
                    "Tippe auf der Karte auf das Gebäude, in dem du wohnst."
                } else {
                    "Ist das markierte Gebäude dein Zuhause?"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (selectedHome == null) {
                    "Du kannst die Karte verschieben und zoomen."
                } else {
                    "Dein Zuhause bleibt anschließend auf der Karte markiert."
                },
                color = Ink.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (selectedHome != null) {
                SpurPrimaryButton(
                    label = "Das ist mein Zuhause",
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
