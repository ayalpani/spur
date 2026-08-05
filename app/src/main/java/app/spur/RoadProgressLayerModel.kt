package app.spur

import org.maplibre.geojson.FeatureCollection

internal data class RoadProgressLayerSnapshot(
    val contextKey: String?,
    val revision: Long,
    val segments: List<List<SpurCoordinate>>,
    val features: FeatureCollection,
)

/**
 * Owns the prepared GeoJSON shown by the single global road-progress layer.
 * Camera-cell preparation may only append to the current context; replacing a
 * context is reserved for startup and history invalidation.
 */
internal class RoadProgressLayerModel {
    private var revision = 0L
    private var snapshot = snapshot(contextKey = null, segments = emptyList())

    @Synchronized
    fun current(): RoadProgressLayerSnapshot = snapshot

    @Synchronized
    fun replace(
        contextKey: String,
        segments: List<List<SpurCoordinate>>,
    ): RoadProgressLayerSnapshot {
        revision++
        return snapshot(contextKey, segments).also { snapshot = it }
    }

    @Synchronized
    fun append(
        contextKey: String,
        additions: List<List<SpurCoordinate>>,
    ): RoadProgressLayerSnapshot? {
        if (snapshot.contextKey != contextKey) return null
        val segments = appendOverviewRoadCoverageSegments(
            existing = snapshot.segments,
            additions = additions,
        )
        if (segments == snapshot.segments) return snapshot
        revision++
        return snapshot(contextKey, segments).also { snapshot = it }
    }

    private fun snapshot(
        contextKey: String?,
        segments: List<List<SpurCoordinate>>,
    ) = RoadProgressLayerSnapshot(
        contextKey = contextKey,
        revision = revision,
        segments = segments,
        features = roadProgressOverviewFeatureCollection(segments),
    )
}
