package app.spur

import android.location.Location
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class LocalArOffset(
    val rightMeters: Double,
    val forwardMeters: Double,
)

internal data class ArVector3(
    val x: Double,
    val y: Double,
    val z: Double,
)

internal data class ItemLocationFixQuality(
    val accuracyMeters: Double,
    val ageMillis: Long,
)

internal fun nearbyItemPresentation(
    distanceMeters: Double,
    accuracyMeters: Double,
): NearbyItemPresentation = when {
    distanceMeters > ItemDiscoveryRadiusMeters -> NearbyItemPresentation.HIDDEN
    distanceMeters > ItemSpatialRadiusMeters -> NearbyItemPresentation.DIRECTION_GUIDE
    distanceMeters > ItemClaimRadiusMeters -> NearbyItemPresentation.SPATIAL
    accuracyMeters > ItemMaximumLocationAccuracyMeters -> NearbyItemPresentation.SPATIAL
    else -> NearbyItemPresentation.CLAIMABLE
}

internal fun distanceMeters(a: ItemLocation, b: ItemLocation): Double {
    val earthRadius = 6_371_000.0
    val latitudeA = a.latitude.toRadians()
    val latitudeB = b.latitude.toRadians()
    val deltaLatitude = (b.latitude - a.latitude).toRadians()
    val deltaLongitude = (b.longitude - a.longitude).toRadians()
    val latitudeTerm = sin(deltaLatitude / 2)
    val longitudeTerm = sin(deltaLongitude / 2)
    val haversine = latitudeTerm * latitudeTerm +
        cos(latitudeA) * cos(latitudeB) * longitudeTerm * longitudeTerm
    return earthRadius * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
}

internal fun bearingDegrees(from: ItemLocation, to: ItemLocation): Double {
    val fromLatitude = from.latitude.toRadians()
    val toLatitude = to.latitude.toRadians()
    val longitudeDelta = (to.longitude - from.longitude).toRadians()
    val y = sin(longitudeDelta) * cos(toLatitude)
    val x = cos(fromLatitude) * sin(toLatitude) -
        sin(fromLatitude) * cos(toLatitude) * cos(longitudeDelta)
    return normalizeDegrees(atan2(y, x) * 180 / PI)
}

internal fun smoothBearingDegrees(current: Double?, candidate: Double, factor: Double = 0.18): Double {
    if (current == null) return normalizeDegrees(candidate)
    val shortestDelta = ((candidate - current + 540) % 360) - 180
    return normalizeDegrees(current + shortestDelta * factor.coerceIn(0.0, 1.0))
}

internal fun projectArOffset(
    origin: ItemLocation,
    northBearingDegrees: Double,
    offset: LocalArOffset,
): ItemLocation {
    val distance = sqrt(offset.rightMeters * offset.rightMeters + offset.forwardMeters * offset.forwardMeters)
    val relativeBearing = atan2(offset.rightMeters, offset.forwardMeters) * 180 / PI
    return destination(origin, northBearingDegrees + relativeBearing, distance)
}

internal fun localAnchorOffset(
    device: ItemLocation,
    item: ItemLocation,
    deviceNorthBearingDegrees: Double,
): LocalArOffset {
    val actualDistance = distanceMeters(device, item)
    val renderedDistance = min(actualDistance, ItemMaximumLocalAnchorMeters)
    val relativeBearing = (bearingDegrees(device, item) - deviceNorthBearingDegrees).toRadians()
    return LocalArOffset(
        rightMeters = sin(relativeBearing) * renderedDistance,
        forwardMeters = cos(relativeBearing) * renderedDistance,
    )
}

internal fun horizontalAnchorPosition(
    cameraPosition: ArVector3,
    cameraForward: ArVector3,
    cameraRight: ArVector3,
    offset: LocalArOffset,
): ArVector3 {
    val forwardLength = sqrt(
        cameraForward.x * cameraForward.x + cameraForward.z * cameraForward.z,
    )
    val (forwardX, forwardZ) = if (forwardLength > 0.0001) {
        cameraForward.x / forwardLength to cameraForward.z / forwardLength
    } else {
        val rightLength = sqrt(cameraRight.x * cameraRight.x + cameraRight.z * cameraRight.z)
        val rightX = cameraRight.x / rightLength
        val rightZ = cameraRight.z / rightLength
        rightZ to -rightX
    }
    val rightX = -forwardZ
    val rightZ = forwardX
    return ArVector3(
        x = cameraPosition.x + rightX * offset.rightMeters + forwardX * offset.forwardMeters,
        y = cameraPosition.y,
        z = cameraPosition.z + rightZ * offset.rightMeters + forwardZ * offset.forwardMeters,
    )
}

internal fun approximatePlacementAnchorPosition(
    cameraPosition: ArVector3,
    cameraForward: ArVector3,
    cameraRight: ArVector3,
): ArVector3 = horizontalAnchorPosition(
    cameraPosition = cameraPosition,
    cameraForward = cameraForward,
    cameraRight = cameraRight,
    offset = LocalArOffset(rightMeters = 0.0, forwardMeters = 2.0),
)

internal fun elevatedPlacementAnchorPosition(floorPosition: ArVector3): ArVector3 =
    floorPosition.copy(y = floorPosition.y + ItemPlacementHeightMeters)

internal fun isPublishableLocation(quality: ItemLocationFixQuality): Boolean =
    quality.accuracyMeters in 0.0..ItemMaximumLocationAccuracyMeters &&
        max(0L, quality.ageMillis) <= ItemMaximumLocationAgeMillis

internal fun android.location.Location.isPublishableItemLocation(nowMillis: Long): Boolean =
    hasAccuracy() && isPublishableLocation(
        ItemLocationFixQuality(
            accuracyMeters = accuracy.toDouble(),
            ageMillis = nowMillis - time,
        ),
    )

internal fun Location.toItemLocation() = ItemLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
)

private fun destination(origin: ItemLocation, bearingDegrees: Double, distanceMeters: Double): ItemLocation {
    val earthRadius = 6_371_000.0
    val angularDistance = distanceMeters / earthRadius
    val bearing = bearingDegrees.toRadians()
    val latitude = origin.latitude.toRadians()
    val longitude = origin.longitude.toRadians()
    val resultLatitude = kotlin.math.asin(
        sin(latitude) * cos(angularDistance) +
            cos(latitude) * sin(angularDistance) * cos(bearing),
    )
    val resultLongitude = longitude + atan2(
        sin(bearing) * sin(angularDistance) * cos(latitude),
        cos(angularDistance) - sin(latitude) * sin(resultLatitude),
    )
    return ItemLocation(
        latitude = resultLatitude * 180 / PI,
        longitude = ((resultLongitude * 180 / PI + 540) % 360) - 180,
        accuracyMeters = origin.accuracyMeters,
    )
}

private fun Double.toRadians() = this * PI / 180

private fun normalizeDegrees(value: Double) = (value % 360 + 360) % 360
