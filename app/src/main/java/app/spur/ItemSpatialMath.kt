package app.spur

import android.location.Location
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ItemLocationFixQuality(
    val accuracyMeters: Double,
    val ageMillis: Long,
)

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

private fun Double.toRadians() = this * PI / 180

private fun normalizeDegrees(value: Double) = (value % 360 + 360) % 360
