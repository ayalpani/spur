package app.spur

import org.maplibre.geojson.FeatureCollection

internal data class RoadCoverageLayerSnapshot(
    val contextKey: String?,
    val revision: Long,
    val segments: List<List<SpurCoordinate>>,
    val features: FeatureCollection,
)

/**
 * Owns the prepared GeoJSON shown by the single global road-coverage layer.
 * Camera-cell preparation may only append to the current context; replacing a
 * context is reserved for startup and history invalidation.
 */
internal class RoadCoverageLayerCache {
    private var revision = 0L
    private var snapshot = snapshot(contextKey = null, segments = emptyList())

    @Synchronized
    fun current(): RoadCoverageLayerSnapshot = snapshot

    @Synchronized
    fun replace(
        contextKey: String,
        segments: List<List<SpurCoordinate>>,
    ): RoadCoverageLayerSnapshot {
        revision++
        return snapshot(contextKey, segments).also { snapshot = it }
    }

    @Synchronized
    fun append(
        contextKey: String,
        additions: List<List<SpurCoordinate>>,
    ): RoadCoverageLayerSnapshot? {
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
    ) = RoadCoverageLayerSnapshot(
        contextKey = contextKey,
        revision = revision,
        segments = segments,
        features = roadProgressOverviewFeatureCollection(segments),
    )
}

internal data class RoadCoveragePreparationKey(
    val cacheKey: String,
    val viewportKey: RoadNetworkViewportKey,
    val roadGeometrySignature: Long,
)

internal class RoadCoveragePreparationCache(
    private val maximumEntries: Int = 128,
) {
    private val entries = object : LinkedHashMap<RoadCoveragePreparationKey, Unit>(
        maximumEntries,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<RoadCoveragePreparationKey, Unit>?,
        ): Boolean = size > maximumEntries
    }

    @Synchronized
    fun contains(key: RoadCoveragePreparationKey): Boolean = entries[key] != null

    @Synchronized
    fun add(key: RoadCoveragePreparationKey) {
        entries[key] = Unit
    }

    @Synchronized
    fun clear() = entries.clear()
}

internal fun roadGeometrySignature(roads: List<RenderedRoadSegment>): Long =
    roads.asSequence()
        .map(RenderedRoadSegment::key)
        .sorted()
        .fold(RoadGeometrySignatureOffset) { signature, key ->
            key.fold(signature) { hash, character ->
                (hash xor character.code.toLong()) * RoadGeometrySignaturePrime
            }
        }

private const val RoadGeometrySignatureOffset = -3750763034362895579L
private const val RoadGeometrySignaturePrime = 1099511628211L
