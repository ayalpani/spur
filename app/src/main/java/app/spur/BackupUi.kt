package app.spur

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class RestoreCandidate(
    val uri: Uri,
    val info: SpurBackupInfo,
)

private enum class BackupOperation {
    SAVING,
    READING,
    RESTORING,
}

@Composable
internal fun BackupBottomSheet(
    hasActiveTour: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(context.loadSpurBackupSettings()) }
    var operation by remember { mutableStateOf<BackupOperation?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var restoreCandidate by remember { mutableStateOf<RestoreCandidate?>(null) }

    fun refreshSettings() {
        settings = context.loadSpurBackupSettings()
    }

    fun saveNow() {
        if (operation != null) return
        operation = BackupOperation.SAVING
        message = null
        scope.launch {
            runCatching { context.createBackupInSelectedFolder() }
                .onSuccess {
                    message = backupStoredMessage(context.loadSpurBackupSettings())
                }
                .onFailure { message = context.backupUiError(it) }
            refreshSettings()
            operation = null
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operation = BackupOperation.SAVING
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { context.selectSpurBackupFolder(uri) }
                context.createBackupInSelectedFolder()
            }
                .onSuccess {
                    message = backupStoredMessage(
                        context.loadSpurBackupSettings(),
                        destinationSelected = true,
                    )
                }
                .onFailure { message = context.backupUiError(it) }
            refreshSettings()
            operation = null
        }
    }
    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (context.loadSpurBackupSettings().waitingForWifi) saveNow()
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operation = BackupOperation.READING
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { inspectSpurBackup(context, uri) }
            }
                .onSuccess { restoreCandidate = RestoreCandidate(uri, it) }
                .onFailure { message = "Diese Datei ist kein gültiges Spur-Backup." }
            operation = null
        }
    }

    fun chooseRestore() {
        if (operation != null) return
        if (hasActiveTour) {
            message = "Beende zuerst die laufende Tour."
        } else {
            restoreLauncher.launch(arrayOf("*/*"))
        }
    }

    BackHandler {
        if (restoreCandidate != null) restoreCandidate = null else onBack()
    }

    val candidate = restoreCandidate
    if (candidate != null) {
        RestoreConfirmation(
            info = candidate.info,
            restoring = operation == BackupOperation.RESTORING,
            message = message,
            onConfirm = {
                if (operation != null) return@RestoreConfirmation
                operation = BackupOperation.RESTORING
                message = null
                scope.launch {
                    runCatching { restoreSpurBackup(context, candidate.uri) }
                        .onSuccess {
                            Toast.makeText(
                                context,
                                "Backup wiederhergestellt.",
                                Toast.LENGTH_LONG,
                            ).show()
                            context.findBackupActivity()?.recreate()
                        }
                        .onFailure {
                            message = context.backupUiError(it)
                            operation = null
                        }
                }
            },
            onCancel = {
                if (operation == null) {
                    restoreCandidate = null
                    message = null
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHeader(title = "Backup")
        if (settings.treeUri == null) {
            Text(
                text = "Deine Touren und Momente werden an einem Ort deiner Wahl gesichert.",
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Ink.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SpurPrimaryButton(
                label = if (operation == BackupOperation.SAVING) {
                    "Backup wird erstellt …"
                } else {
                    "Speicherort wählen"
                },
                enabled = operation == null,
                onClick = { folderLauncher.launch(null) },
            )
        } else {
            BackupDestinationRow(
                name = settings.destinationName ?: "Gewählter Ordner",
                enabled = operation == null,
                onClick = { folderLauncher.launch(settings.treeUri) },
            )
            AutomaticBackupRow(
                checked = settings.automatic,
                enabled = operation == null,
                onCheckedChange = { enabled ->
                    context.setAutomaticBackupEnabled(enabled)
                    refreshSettings()
                    message = if (enabled) {
                        "Automatische Sicherung ist eingeschaltet."
                    } else {
                        "Automatische Sicherung ist ausgeschaltet."
                    }
                },
            )
            BackupStatus(settings)
            Spacer(modifier = Modifier.height(20.dp))
            SpurPrimaryButton(
                label = if (operation == BackupOperation.SAVING) {
                    "Backup wird erstellt …"
                } else if (settings.waitingForWifi) {
                    "WLAN verbinden"
                } else {
                    "Jetzt sichern"
                },
                enabled = operation == null,
                onClick = {
                    if (settings.waitingForWifi) {
                        wifiSettingsLauncher.launch(wifiSettingsIntent())
                    } else {
                        saveNow()
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SpurSecondaryButton(
            label = if (operation == BackupOperation.READING) {
                "Backup wird geprüft …"
            } else {
                "Backup wiederherstellen"
            },
            enabled = operation == null,
            onClick = ::chooseRestore,
        )
        message?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = Ink.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BackupDestinationRow(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Speicherort",
                color = Ink.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        LucideIcon(
            paths = listOf("m9 18 6-6-6-6"),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AutomaticBackupRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Automatische Sicherung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Nach jeder abgeschlossenen Tour wird im Hintergrund gesichert.",
                color = Ink.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun BackupStatus(settings: SpurBackupSettings) {
    Text(
        text = backupStatusText(settings),
        modifier = Modifier.fillMaxWidth(),
        color = Ink.copy(alpha = 0.62f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

internal fun backupStatusText(settings: SpurBackupSettings): String = when {
    settings.waitingForWifi -> "Backup ausstehend – WLAN verbinden."
    settings.lastError != null -> settings.lastError
    settings.lastSuccessAt != null && settings.treeUri?.isGoogleDriveDestination() == true ->
        "Zuletzt an Drive übergeben: ${formatBackupDate(settings.lastSuccessAt)}"
    settings.lastSuccessAt != null -> "Zuletzt gesichert: ${formatBackupDate(settings.lastSuccessAt)}"
    else -> "Noch kein Backup gespeichert."
}

private fun backupStoredMessage(
    settings: SpurBackupSettings,
    destinationSelected: Boolean = false,
): String = if (settings.treeUri?.isGoogleDriveDestination() == true) {
    if (destinationSelected) {
        "Speicherort gewählt und Backup an Drive übergeben. Drive synchronisiert im Hintergrund."
    } else {
        "Backup an Drive übergeben. Drive synchronisiert im Hintergrund."
    }
} else if (destinationSelected) {
    "Speicherort gewählt und Backup gespeichert."
} else {
    "Backup gespeichert."
}

@Composable
private fun RestoreConfirmation(
    info: SpurBackupInfo,
    restoring: Boolean,
    message: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHeader(title = "Backup wiederherstellen")
        Text(
            text = "Backup vom ${formatBackupDate(info.createdAt)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${info.tourCount} ${if (info.tourCount == 1) "Tour" else "Touren"}\n" +
                "${info.momentCount} ${if (info.momentCount == 1) "Moment" else "Momente"}\n" +
                "Aufgenommen mit Spur ${info.appVersion}",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Der aktuelle Spur-Bestand wird ersetzt.",
            color = Ink.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SpurPrimaryButton(
            label = if (restoring) "Backup wird wiederhergestellt …" else "Backup wiederherstellen",
            destructive = true,
            enabled = !restoring,
            onClick = onConfirm,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SpurSecondaryButton(
            label = "Abbrechen",
            enabled = !restoring,
            onClick = onCancel,
        )
        message?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = StopRed,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatBackupDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun Context.backupUiError(error: Throwable): String = when {
    error.message?.startsWith("Während einer laufenden Tour") == true ->
        requireNotNull(error.message)
    error is SecurityException -> "Der Speicherort ist nicht mehr verfügbar."
    error is BackupWaitingForWifiException ->
        "Google Drive wird nur über eine aktive WLAN-Verbindung gesichert."
    else -> "Das Backup konnte nicht verarbeitet werden."
}

private fun wifiSettingsIntent(): Intent = Intent(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Settings.Panel.ACTION_WIFI
    } else {
        Settings.ACTION_WIFI_SETTINGS
    },
)

private tailrec fun Context.findBackupActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findBackupActivity()
    else -> null
}
