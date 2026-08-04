package app.spur

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun MapPagePreview() {
    MapPage(
        tour = null,
        activeTour = null,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        onCloseDisplayedTour = {},
        onDeleteTour = {},
        onDeleteWaypoint = { _, _ -> true },
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ActiveTourPagePreview() {
    val tour = Tour(
        id = 1,
        startedAt = System.currentTimeMillis() - 754_000,
        endedAt = null,
        distanceMeters = 1_840.0,
        pointCount = 42,
    )
    MapPage(
        tour = tour,
        activeTour = tour,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        onCloseDisplayedTour = {},
        onDeleteTour = {},
        onDeleteWaypoint = { _, _ -> true },
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HistoryBottomSheetPreview() {
    HistoryBottomSheet(
        store = TourStore(LocalContext.current),
        revision = 0,
        onOpenTour = {},
        onOpenPhoto = { _, _ -> },
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun StartTourBottomSheetPreview() {
    CompositionLocalProvider(
        LocalMapControlColors provides MapControlColors(
            background = MapControlColor.BLACK.color,
            foreground = MapControlColor.WHITE.color,
        ),
    ) {
        StartTourBottomSheet(onStartTour = {})
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun VoiceRecorderBottomSheetPreview() {
    CompositionLocalProvider(
        LocalMapControlColors provides MapControlColors(
            background = MapControlColor.BLACK.color,
            foreground = MapControlColor.WHITE.color,
        ),
    ) {
        VoiceRecorderBottomSheet(
            startRecordingRequest = 0,
            hasRecordPermission = true,
            onRequestPermission = {},
            onRecordingAccepted = {},
            onDismiss = {},
        )
    }
}
