package app.spur

import androidx.compose.runtime.Composable

internal fun openArMode(current: WorldMode, arClosing: Boolean): WorldMode =
    if (arClosing) current else WorldMode.AR

internal fun closeArMode(current: WorldMode): WorldMode =
    if (current == WorldMode.AR) WorldMode.MAP else current

@Composable
internal fun WorldModeToggle(
    mode: WorldMode,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    MapIconButton(
        contentDescription = if (mode == WorldMode.MAP) "AR öffnen" else "Karte öffnen",
        onClick = onClick,
        enabled = enabled,
        secondary = true,
    ) {
        if (mode == WorldMode.MAP) LucideScanIcon() else LucideMapIcon()
    }
}
