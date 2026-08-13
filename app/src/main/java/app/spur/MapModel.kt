package app.spur

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.selected
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.geojson.Feature
import kotlinx.coroutines.flow.filter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

internal object SpurRoute {
    const val MAP = "map"
    const val HOME = "home"
}

internal fun shouldRevealHomeBeforeDeletingTour(
    deletedTourId: Long,
    displayedTour: Tour?,
    previousRoute: String?,
): Boolean = displayedTour?.id == deletedTourId &&
    displayedTour.endedAt != null &&
    previousRoute == SpurRoute.HOME

internal const val StreetMapStyle = "https://tiles.openfreemap.org/styles/liberty"
internal const val SatelliteSource = "satellite-source"
internal const val SatelliteLayer = "satellite-layer"
internal const val SatelliteTileUrl =
    "https://services.arcgisonline.com/ArcGIS/rest/services/" +
        "World_Imagery/MapServer/tile/{z}/{y}/{x}"
internal const val SatelliteMapStyleJson =
    """{"version":8,"glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf","sources":{"satellite-source":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Esri, Maxar, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-layer","type":"raster","source":"satellite-source"}]}"""
internal const val MomentMarkerWidth = 62
internal const val MomentMarkerHeight = 62
internal const val MomentMarkerStroke = 3f
internal const val MomentMarkerEdgeWidth = MapOutlineWidthDp
internal const val MomentMarkerVerticalOffset = MomentMarkerEdgeWidth - 2f
internal const val MomentClusterStackStep = 9f
internal const val MomentClusterMaximumOffset = MomentClusterStackStep * 2f
internal const val MapPreviewPixels = 180
internal const val LocationPulseWatchdogMillis = LocationSignalPeriodMillis * 10L
internal const val LocationPuckHitTargetDp = 60f
internal const val MovementStartSpeedKilometersPerHour = 1.0
internal const val MovementStopSpeedKilometersPerHour = 0.5
internal const val TravelDirectionBearingToleranceDegrees = 10f
internal val LocationPulseEasing = Easing { fraction ->
    (cos((fraction + 1f) * PI) / 2f + 0.5f).toFloat()
}
internal const val TourRouteSource = "tour-route-source"
internal const val TourRouteBorderLayer = "tour-route-border-layer"
internal const val TourRouteLayer = "tour-route-layer"
internal const val TourWaypointLayer = "tour-waypoint-layer"
internal const val TourEndpointSource = "tour-endpoint-source"
internal const val TourEndpointRingLayer = "tour-endpoint-ring-layer"
internal const val TourEndpointEndLayer = "tour-endpoint-end-layer"
internal const val TourEndpointTypeProperty = "endpoint-type"
internal const val TourEndpointEnd = "end"
private const val TourEndpointScale = 1.25f * 1.5f
internal const val TourEndpointRadius = 7f * TourEndpointScale
internal const val TourEndpointStrokeWidth = 4f
internal const val TourEndpointEndRadius = 5f * TourEndpointScale
internal const val SelectedTrackPointSource = "selected-track-point-source"
internal const val SelectedTrackPointLayer = "selected-track-point-layer"
internal const val MapMomentSource = "map-moment-source"
internal const val MapMomentLayer = "map-moment-layer"
internal const val MapMomentClusterLayer = "map-moment-cluster-layer"
internal const val MapMomentClusterCountBadgeLayer = "map-moment-cluster-count-badge-layer"
internal const val MapMomentClusterCountLayer = "map-moment-cluster-count-layer"
internal const val MapMomentUserSpotClusterLayer = "map-moment-user-spot-cluster-layer"
internal const val MapMomentUserSpotClusterCountBadgeLayer =
    "map-moment-user-spot-cluster-count-badge-layer"
internal const val MapMomentUserSpotClusterCountLayer =
    "map-moment-user-spot-cluster-count-layer"
internal const val MapMomentClusterCountBadgeRadius = 9f
internal const val MapMomentClusterCountPositionX = 9f + MomentClusterMaximumOffset / 2f
internal const val MapMomentClusterCountPositionY = -42f
internal const val UserSpotMomentClusterOffsetX = -MomentClusterMaximumOffset / 2f
internal const val UserSpotMomentClusterOffsetY = -7f
internal const val MapPoiSourceLayer = "poi"
internal const val MapBuildingLayer = "building"
internal const val MapBuilding3dLayer = "building-3d"
internal const val MapBuildingMaxZoom = 24f
internal const val HomeBuildingSource = "home-building-source"
internal const val HomeBuildingFillLayer = "home-building-fill-layer"
internal const val HomeBuildingOutlineLayer = "home-building-outline-layer"
internal const val SelectedBuildingSource = "selected-building-source"
internal const val SelectedBuildingFillLayer = "selected-building-fill-layer"
internal const val SelectedBuildingOutlineLayer = "selected-building-outline-layer"
internal const val SelectableHomeBuildingsLayer = "selectable-home-buildings-layer"
internal const val HomeBuildingSelectionZoom = 18.5
internal const val HomeBuildingMinimumSelectionZoom = 15.0
internal const val MapMomentIdProperty = "moment-id"
internal const val MapMomentImageProperty = "moment-image"
internal const val MapMomentRepresentativeProperty = "moment-representative"
internal const val MapMomentAtUserSpotProperty = "moment-at-user-spot"
internal const val MapMomentImagePrefix = "map-moment-"
internal const val MapMomentClusterImagePrefix = "map-moment-cluster-"
internal const val MapMomentClusterMaxZoom = 16
internal const val MapMomentClusterRadius = MomentMarkerHeight / 2 - 1
internal const val MapLibreWorldSizeAtZoomZero = 512.0
internal const val GoogleMapsWorldSizeAtZoomZero = 256.0

internal enum class MapRotation(val label: String, val bearing: Double?) {
    NORTH("Norden", 0.0),
    EAST("Osten", 90.0),
    SOUTH("Süden", 180.0),
    WEST("Westen", 270.0),
    TRAVEL_DIRECTION("Fahrtrichtung", null),
}

internal data class SelectedBuilding(
    val coordinate: SpurCoordinate,
    val feature: Feature,
)

internal data class MapViewport(
    val center: SpurCoordinate,
    val zoom: Double,
    val satellite: Boolean,
)

internal fun googleMapsViewUrl(viewport: MapViewport): String =
    "https://www.google.com/maps/@?api=1&map_action=map" +
        "&center=${viewport.center.latitude}%2C${viewport.center.longitude}" +
        "&zoom=${googleMapsZoom(viewport.zoom)}" +
        "&basemap=${if (viewport.satellite) "satellite" else "roadmap"}"

internal fun googleMapsZoom(mapLibreZoom: Double): Int {
    // MapLibre Native uses a 512 px world at zoom 0; Google Maps uses 256 dp.
    // The Google Maps URL only accepts whole zoom levels, so use the nearest one.
    val worldSizeOffset = log2(MapLibreWorldSizeAtZoomZero / GoogleMapsWorldSizeAtZoomZero)
    return (mapLibreZoom + worldSizeOffset).roundToInt().coerceIn(0, 21)
}

internal fun mapRotationFromStored(value: String?): MapRotation =
    MapRotation.entries.firstOrNull { it.name == value } ?: MapRotation.NORTH

internal fun waypointEmptyText(
    hasActiveTour: Boolean,
    hasDisplayedTour: Boolean,
): String = when {
    hasActiveTour -> "Warte auf GPS-Signal …"
    hasDisplayedTour -> "Keine Wegpunkte aufgezeichnet."
    else -> ""
}

internal fun mapControlColorFromStored(
    value: String?,
    fallback: MapControlColor = MapControlColor.BLACK,
): MapControlColor =
    MapControlColor.entries.firstOrNull { it.name == value } ?: fallback

internal fun defaultMapControlForeground(background: MapControlColor): MapControlColor =
    when {
        background == MapControlColor.BLUE -> MapControlColor.YELLOW
        background.color.luminance() > 0.3f -> MapControlColor.BLACK
        else -> MapControlColor.WHITE
    }

internal fun nearestCompassRotation(current: Float, target: Float): Float {
    val delta = (target - current) % 360f
    return current + when {
        delta > 180f -> delta - 360f
        delta <= -180f -> delta + 360f
        else -> delta
    }
}

internal fun isWithinLocationHitTarget(
    clickX: Float,
    clickY: Float,
    locationX: Float,
    locationY: Float,
    hitTargetSize: Float,
): Boolean {
    val radius = hitTargetSize / 2f
    return abs(clickX - locationX) <= radius &&
        abs(clickY - locationY) <= radius
}

internal fun movingForSpeed(
    speedKilometersPerHour: Double?,
    wasMoving: Boolean,
): Boolean {
    val threshold = if (wasMoving) {
        MovementStopSpeedKilometersPerHour
    } else {
        MovementStartSpeedKilometersPerHour
    }
    return speedKilometersPerHour != null && speedKilometersPerHour >= threshold
}

internal fun movingForMapSignal(
    isAtHome: Boolean,
    speedKilometersPerHour: Double?,
    wasMoving: Boolean,
): Boolean {
    if (isAtHome) return false
    return movingForSpeed(speedKilometersPerHour, wasMoving)
}

internal fun stabilizedTravelBearing(
    current: Float?,
    candidate: Float?,
    isMoving: Boolean,
): Float? {
    if (!isMoving || candidate == null) return current
    val normalizedCandidate = ((candidate % 360f) + 360f) % 360f
    val currentBearing = current ?: return normalizedCandidate
    val difference = abs((normalizedCandidate - currentBearing + 540f) % 360f - 180f)
    return if (difference > TravelDirectionBearingToleranceDegrees) {
        normalizedCandidate
    } else {
        currentBearing
    }
}

internal fun mapRotationOptionCenterDistance(
    circleRadius: Float,
    gap: Float,
    halfWidth: Float,
    halfHeight: Float,
    angleRadians: Double,
): Float = circleRadius +
    gap +
    abs(cos(angleRadians)).toFloat() * halfWidth +
    abs(sin(angleRadians)).toFloat() * halfHeight

internal fun shouldFitTourRoute(
    tourId: Long?,
    fittedTourId: Long?,
    displayRequest: Long,
    fittedDisplayRequest: Long,
    pointCount: Int,
): Boolean = tourId != null &&
    pointCount > 0 &&
    (tourId != fittedTourId || displayRequest != fittedDisplayRequest)

internal fun shouldShowTourOverview(
    isFollowingLocation: Boolean,
    hasDisplayedTour: Boolean,
    routePointCount: Int,
): Boolean = isFollowingLocation && hasDisplayedTour && routePointCount > 0

internal fun shouldStackMapPlayer(screenWidthDp: Int): Boolean =
    screenWidthDp <
        MapControlHorizontalPaddingDp * 2 +
        MapControlSizeDp * 2 +
        MapControlGapDp * 2 +
        MapPlayerMinimumWidthDp

internal fun shouldStopFollowing(cameraMoveReason: Int): Boolean =
    cameraMoveReason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldShowTourChrome(isMapGestureActive: Boolean): Boolean =
    !isMapGestureActive

internal fun shouldRefreshAlternateMapPreview(
    isFollowingLocation: Boolean,
    hasPreviewCameraPosition: Boolean,
): Boolean = !isFollowingLocation || !hasPreviewCameraPosition

internal fun shouldShowInitialMapLoading(
    initialLoadingComplete: Boolean,
    isMapReady: Boolean,
): Boolean = !initialLoadingComplete && !isMapReady

internal fun manualLocationHoldDurationMillis(isManualLocationActive: Boolean): Long =
    if (isManualLocationActive) {
        ActiveManualLocationHoldDurationMillis
    } else {
        InitialManualLocationHoldDurationMillis
    }

internal fun mapPreviewZoom(
    mapZoom: Double,
    mapWidthPixels: Int,
    density: Float,
    previewWidthPixels: Int,
): Double = mapZoom - log2(mapWidthPixels / density / previewWidthPixels)

internal fun shouldCompleteStopSwipe(offset: Float, maximum: Float): Boolean =
    maximum > 0f && offset >= maximum * 0.82f

internal fun stopSwipeProgress(offset: Float, maximum: Float): Float =
    if (maximum <= 0f) 0f else (offset / maximum).coerceIn(0f, 1f)

internal fun stopSwipePromptAlpha(offset: Float, maximum: Float): Float {
    if (maximum <= 0f) return 1f
    val progress = stopSwipeProgress(offset, maximum)
    return ((0.82f - progress) / 0.22f).coerceIn(0f, 1f)
}

internal fun canSelectHomeBuilding(mapZoom: Double): Boolean =
    mapZoom >= HomeBuildingMinimumSelectionZoom

internal fun clusterStackOffsets(pointCount: Int): List<Float> = when {
    pointCount <= 1 -> listOf(MomentClusterMaximumOffset)
    pointCount == 2 -> listOf(MomentClusterStackStep, MomentClusterMaximumOffset)
    else -> listOf(0f, MomentClusterStackStep, MomentClusterMaximumOffset)
}

internal fun mapMomentClusterExpansionZoom(expansionZoom: Int): Double =
    expansionZoom.coerceAtMost(MapMomentClusterMaxZoom + 1).toDouble()

internal fun overlappingMomentOffsets(moments: List<MapMoment>): Map<String, Offset> =
    moments
        .groupBy { it.latitude to it.longitude }
        .values
        .filter { it.size > 1 }
        .flatMap { group ->
            val sorted = group.sortedBy(MapMoment::id)
            val spacing = MomentMarkerWidth + 8f
            val radius = spacing / (2f * sin(PI.toFloat() / sorted.size))
            val startAngle = if (sorted.size == 2) PI.toFloat() else -PI.toFloat() / 2f
            sorted.mapIndexed { index, moment ->
                val angle = startAngle + 2f * PI.toFloat() * index / sorted.size
                moment.id to Offset(
                    x = cos(angle) * radius,
                    y = sin(angle) * radius,
                )
            }
        }
        .toMap()

internal fun userSpotMomentIds(
    moments: List<MapMoment>,
    userSpot: SpurCoordinate?,
): Set<String> {
    if (userSpot == null) return emptySet()
    return moments
        .asSequence()
        .filter { moment ->
            haversineDistanceMeters(
                fromLatitude = userSpot.latitude,
                fromLongitude = userSpot.longitude,
                toLatitude = moment.latitude,
                toLongitude = moment.longitude,
            ) <= UserSpotMomentMaximumDistanceMeters
        }
        .map(MapMoment::id)
        .toSet()
}

private const val UserSpotMomentMaximumDistanceMeters = 3.0
