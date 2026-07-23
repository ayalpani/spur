package app.spur

internal fun unwrapHeading(previous: Double, next: Double): Double {
    val previousNormalized = (previous % 360.0 + 360.0) % 360.0
    val shortestTurn = (next - previousNormalized + 540.0) % 360.0 - 180.0
    return previous + shortestTurn
}
