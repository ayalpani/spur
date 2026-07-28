package app.spur

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun MapPagePreview() {
    MapPage(
        store = TourStore(LocalContext.current),
        tour = null,
        activeTour = null,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        isTourEditing = false,
        onEditTour = {},
        onCloseTourEditor = {},
        onRoutePointsChanged = {},
        onDeleteTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun ActiveTourPagePreview() {
    val tour = Tour(
        id = 1,
        startedAt = System.currentTimeMillis() - 754_000,
        endedAt = null,
        distanceMeters = 1_840.0,
        pointCount = 42,
    )
    MapPage(
        store = TourStore(LocalContext.current),
        tour = tour,
        activeTour = tour,
        tourDisplayRequest = 0,
        routePoints = emptyList(),
        now = System.currentTimeMillis(),
        onStartTour = {},
        onSimulatedLocation = {},
        onEndTour = {},
        onOpenHistory = {},
        isTourEditing = false,
        onEditTour = {},
        onCloseTourEditor = {},
        onRoutePointsChanged = {},
        onDeleteTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun HistoryPagePreview() {
    HistoryPage(
        store = TourStore(LocalContext.current),
        revision = 0,
        onBack = {},
        onEditTour = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun LocationOnboardingPreview() {
    LocationOnboarding(permissionRequested = false, onRequestLocation = {})
}
