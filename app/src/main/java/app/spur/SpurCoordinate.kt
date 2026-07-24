package app.spur

internal data class SpurCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal fun resolveSpurCoordinate(
    manual: SpurCoordinate?,
    gps: SpurCoordinate?,
): SpurCoordinate? = manual ?: gps
