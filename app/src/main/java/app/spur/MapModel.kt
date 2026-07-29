package app.spur

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.max
import kotlin.math.sin

internal object SpurRoute {
    const val MAP = "map"
}
internal const val StreetMapStyle = "https://tiles.openfreemap.org/styles/liberty"
internal const val SatelliteSource = "satellite-source"
internal const val SatelliteLayer = "satellite-layer"
internal const val SatelliteTileUrl =
    "https://services.arcgisonline.com/ArcGIS/rest/services/" +
        "World_Imagery/MapServer/tile/{z}/{y}/{x}"
internal const val SatelliteMapStyleJson =
    """{"version":8,"glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf","sources":{"satellite-source":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"attribution":"Esri, Maxar, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-layer","type":"raster","source":"satellite-source"}]}"""
internal const val MomentMarkerWidth = 62
internal const val MomentMarkerHeight = 58
internal const val MomentClusterWidth = 70
internal const val MomentClusterHeight = 66
internal const val MomentMarkerStroke = 3f
internal const val MomentMarkerEdgeWidth = 1f
internal const val MapPreviewPixels = 180
internal const val LocationPulseWatchdogMillis = LocationSignalPeriodMillis * 10L
internal const val CurrentLocationFootprintsImage = "current-location-footprints-image"
internal const val CurrentLocationFootprintsLayer = "current-location-footprints-layer"
internal const val CurrentLocationFootprintsLiftPixels = 16f
internal const val CurrentPersonaWidth = 48f
internal const val CurrentPersonaHeight = 80f
internal const val PersonaCollisionGap = 8f
internal val LocationPulseEasing = Easing { fraction ->
    (cos((fraction + 1f) * PI) / 2f + 0.5f).toFloat()
}
internal const val TourRouteSource = "tour-route-source"
internal const val TourRouteBorderLayer = "tour-route-border-layer"
internal const val TourRouteLayer = "tour-route-layer"
internal const val TourEndpointSource = "tour-endpoint-source"
internal const val TourEndpointRingLayer = "tour-endpoint-ring-layer"
internal const val TourEndpointEndLayer = "tour-endpoint-end-layer"
internal const val TourEndpointTypeProperty = "endpoint-type"
internal const val TourEndpointEnd = "end"
private const val TourEndpointScale = 1.25f * 1.5f
internal const val TourEndpointRadius = 7f * TourEndpointScale
internal const val TourEndpointStrokeWidth = TourRouteBorderPerSidePixels
internal const val TourEndpointEndRadius = 3f * TourEndpointScale
internal const val SelectedTrackPointSource = "selected-track-point-source"
internal const val SelectedTrackPointLayer = "selected-track-point-layer"
internal const val MapMomentSource = "map-moment-source"
internal const val MapMomentLayer = "map-moment-layer"
internal const val MapMomentClusterLayer = "map-moment-cluster-layer"
internal const val MapMomentClusterCountBadgeLayer = "map-moment-cluster-count-badge-layer"
internal const val MapMomentClusterCountLayer = "map-moment-cluster-count-layer"
internal const val MapMomentClusterCountBadgeRadius = 9f
internal const val MapMomentClusterCountPositionX = 15f
internal const val MapMomentClusterCountPositionY = -42f
internal const val MapPoiSourceLayer = "poi"
internal const val MapBuildingLayer = "building"
internal const val MapBuilding3dLayer = "building-3d"
internal const val MapBuildingMaxZoom = 24f
internal const val HomeBuildingSource = "home-building-source"
internal const val HomeBuildingFillLayer = "home-building-fill-layer"
internal const val HomeBuildingOutlineLayer = "home-building-outline-layer"
internal const val SelectableHomeBuildingsLayer = "selectable-home-buildings-layer"
internal const val HomeBuildingSelectionZoom = 18.5
internal const val HomeBuildingSelectionSearchRadiusDp = 64
internal const val MapMomentIdProperty = "moment-id"
internal const val MapMomentImageProperty = "moment-image"
internal const val MapMomentRepresentativeProperty = "moment-representative"
internal const val MapMomentClusterIdProperty = "cluster_id"
internal const val MapMomentImagePrefix = "map-moment-"
internal const val MapMomentClusterImagePrefix = "map-moment-cluster-"
internal const val MapMomentClusterMaxZoom = 18
internal const val MapMomentClusterRadius = MomentMarkerHeight / 2 - 1

internal enum class MapRotation(val label: String, val bearing: Double) {
    NORTH("Norden", 0.0),
    EAST("Osten", 90.0),
    SOUTH("Süden", 180.0),
    WEST("Westen", 270.0),
}

internal data class SelectedBuilding(
    val coordinate: SpurCoordinate,
    val feature: Feature,
)

internal fun mapRotationFromStored(value: String?): MapRotation =
    MapRotation.entries.firstOrNull { it.name == value } ?: MapRotation.NORTH

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
    isTourActive: Boolean,
    routePointCount: Int,
): Boolean = isFollowingLocation && isTourActive && routePointCount > 0

internal fun shouldStackMapPlayer(screenWidthDp: Int): Boolean =
    screenWidthDp <
        MapControlHorizontalPaddingDp * 2 +
        MapControlSizeDp * 2 +
        MapControlGapDp * 2 +
        MapPlayerMinimumWidthDp

internal fun shouldStopFollowing(cameraMoveReason: Int): Boolean =
    cameraMoveReason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldShowMapPreviewLoading(
    isFollowingLocation: Boolean,
    cameraMoveReason: Int,
): Boolean = !isFollowingLocation || shouldStopFollowing(cameraMoveReason)

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

internal fun clusterStackOffsets(pointCount: Int): List<Float> = when {
    pointCount <= 1 -> listOf(8f)
    pointCount == 2 -> listOf(4f, 8f)
    else -> listOf(0f, 4f, 8f)
}

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

internal data class PersonaAvoidanceItem(
    val key: String,
    val anchor: Offset,
    val offset: Offset,
    val width: Float,
    val height: Float,
)

internal fun avoidPersonaOverlaps(
    location: Offset,
    items: List<PersonaAvoidanceItem>,
): Map<String, Offset> {
    val persona = personaExclusionRect(location)
    val colliding = items.filter { itemRect(it).overlaps(persona) }
    if (colliding.isEmpty()) return emptyMap()

    val occupied = items
        .filterNot(colliding::contains)
        .map { itemRect(it).withPersonaGap() }
        .toMutableList()
    return buildMap {
        colliding.sortedBy(PersonaAvoidanceItem::key).forEach { item ->
            val originalCenter = itemRect(item).center
            val candidates = personaAvoidanceCandidates(
                persona,
                item,
                colliding.size + 8,
            )
            val target = candidates
                .filter { candidate ->
                    val padded = candidate.withPersonaGap()
                    occupied.none { it.overlaps(padded) }
                }
                .minByOrNull { candidate ->
                    val delta = candidate.center - originalCenter
                    delta.x * delta.x + delta.y * delta.y
                }
                ?: candidates.last()
            occupied += target.withPersonaGap()
            put(
                item.key,
                Offset(
                    x = target.center.x - item.anchor.x,
                    y = target.bottom - item.anchor.y,
                ),
            )
        }
    }
}

private fun personaExclusionRect(location: Offset): Rect {
    val pulseRadius = LocationPulseMaxRadius
    val personaLeft = location.x - max(CurrentPersonaWidth / 2f, pulseRadius)
    val personaTop = minOf(
        location.y - CurrentLocationFootprintsLiftPixels - CurrentPersonaHeight / 2f,
        location.y - pulseRadius,
    )
    val personaRight = location.x + max(CurrentPersonaWidth / 2f, pulseRadius)
    val personaBottom = max(
        location.y - CurrentLocationFootprintsLiftPixels + CurrentPersonaHeight / 2f,
        location.y + pulseRadius,
    )
    return Rect(
        left = personaLeft - PersonaCollisionGap,
        top = personaTop - PersonaCollisionGap,
        right = personaRight + PersonaCollisionGap,
        bottom = personaBottom + PersonaCollisionGap,
    )
}

private fun itemRect(item: PersonaAvoidanceItem): Rect = Rect(
    left = item.anchor.x + item.offset.x - item.width / 2f,
    top = item.anchor.y + item.offset.y - item.height,
    right = item.anchor.x + item.offset.x + item.width / 2f,
    bottom = item.anchor.y + item.offset.y,
)

private fun personaAvoidanceCandidates(
    persona: Rect,
    item: PersonaAvoidanceItem,
    itemCount: Int,
): List<Rect> = buildList {
    val horizontalStep = item.width + PersonaCollisionGap
    val verticalStep = item.height + PersonaCollisionGap
    val bottomY = persona.bottom + item.height / 2f
    repeat(itemCount + 1) { row ->
        val y = bottomY + row * verticalStep
        add(rectAround(Offset(persona.center.x, y), item))
        repeat(itemCount + 1) { column ->
            val x = (column + 1) * horizontalStep
            add(rectAround(Offset(persona.center.x - x, y), item))
            add(rectAround(Offset(persona.center.x + x, y), item))
        }
    }

    val sideRows = ((persona.height + PersonaCollisionGap) / verticalStep)
        .toInt()
        .coerceAtLeast(1)
    repeat(itemCount + 1) { column ->
        val leftX = persona.left - item.width / 2f - column * horizontalStep
        val rightX = persona.right + item.width / 2f + column * horizontalStep
        repeat(sideRows) { row ->
            val y = persona.bottom - item.height / 2f - row * verticalStep
            add(rectAround(Offset(leftX, y), item))
            add(rectAround(Offset(rightX, y), item))
        }
    }
}

private fun rectAround(center: Offset, item: PersonaAvoidanceItem): Rect = Rect(
    left = center.x - item.width / 2f,
    top = center.y - item.height / 2f,
    right = center.x + item.width / 2f,
    bottom = center.y + item.height / 2f,
)

private fun Rect.withPersonaGap(): Rect {
    val inset = PersonaCollisionGap / 2f
    return Rect(left - inset, top - inset, right + inset, bottom + inset)
}
