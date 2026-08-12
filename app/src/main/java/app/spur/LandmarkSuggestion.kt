package app.spur

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.json.JSONObject

internal data class LandmarkTitleSuggestionRequest(
    val id: Long,
    val coordinate: SpurCoordinate,
)

internal data class NearbyLandmarkCandidate(
    val title: String,
    val coordinate: SpurCoordinate,
    val rank: Int,
)

internal fun MapLibreMap.nearbyLandmarkTitle(
    coordinate: SpurCoordinate,
): String? {
    val poiSources = style
        ?.layers
        ?.filterIsInstance<SymbolLayer>()
        ?.filter { it.sourceLayer == MapPoiSourceLayer }
        ?.mapNotNull { style?.getSourceAs<VectorSource>(it.sourceId) }
        ?.distinctBy(VectorSource::getId)
        .orEmpty()
    val candidates = poiSources.flatMap(VectorSource::nearbyLandmarkCandidates)
    return bestNearbyLandmarkTitle(candidates, coordinate)
}

internal suspend fun Context.fetchNearbyLandmarkTitle(
    coordinate: SpurCoordinate,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val query = """
            [out:json][timeout:5];
            nwr(around:100,${coordinate.latitude},${coordinate.longitude})[name];
            out tags center 80;
        """.trimIndent()
        val connection = (URL("$LandmarkSuggestionOverpassUrl?data=${query.urlEncoded()}")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = LandmarkSuggestionNetworkTimeoutMillis
            readTimeout = LandmarkSuggestionNetworkTimeoutMillis
            requestMethod = "GET"
            setRequestProperty("User-Agent", LandmarkSuggestionUserAgent)
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val elements = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getJSONArray("elements")
            val candidates = buildList {
                repeat(elements.length()) { index ->
                    val element = elements.getJSONObject(index)
                    val tags = element.optJSONObject("tags") ?: return@repeat
                    val title = LandmarkSuggestionNameProperties.firstNotNullOfOrNull { key ->
                        tags.optString(key).trim().takeIf(String::isNotEmpty)
                    } ?: return@repeat
                    val center = element.optJSONObject("center")
                    val latitude = center?.optDouble("lat") ?: element.optDouble("lat")
                    val longitude = center?.optDouble("lon") ?: element.optDouble("lon")
                    if (!latitude.isFinite() || !longitude.isFinite()) return@repeat
                    add(
                        NearbyLandmarkCandidate(
                            title = title,
                            coordinate = SpurCoordinate(latitude, longitude),
                            rank = overpassLandmarkRank(tags),
                        ),
                    )
                }
            }
            bestNearbyLandmarkTitle(candidates, coordinate)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

internal fun bestNearbyLandmarkTitle(
    candidates: List<NearbyLandmarkCandidate>,
    origin: SpurCoordinate,
): String? = candidates
    .asSequence()
    .map { candidate ->
        candidate to haversineDistanceMeters(
            fromLatitude = origin.latitude,
            fromLongitude = origin.longitude,
            toLatitude = candidate.coordinate.latitude,
            toLongitude = candidate.coordinate.longitude,
        )
    }
    .filter { (_, distance) -> distance <= LandmarkSuggestionFallbackRadiusMeters }
    .groupBy { (candidate) -> candidate.title }
    .values
    .map { duplicates -> duplicates.minBy { it.second } }
    .minWithOrNull(
        compareBy<Pair<NearbyLandmarkCandidate, Double>> {
            if (it.second <= LandmarkSuggestionPreferredRadiusMeters) 0 else 1
        }
            .thenBy { it.first.rank }
            .thenBy { it.second },
    )
    ?.first
    ?.title

private fun Feature.toNearbyLandmarkCandidate(): NearbyLandmarkCandidate? {
    val point = geometry() as? Point ?: return null
    val title = LandmarkSuggestionNameProperties.firstNotNullOfOrNull { property ->
        takeIf { hasProperty(property) }
            ?.getStringProperty(property)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    } ?: return null
    val rank = takeIf { hasProperty(LandmarkSuggestionRankProperty) }
        ?.getNumberProperty(LandmarkSuggestionRankProperty)
        ?.toInt()
        ?: Int.MAX_VALUE
    return NearbyLandmarkCandidate(
        title = title,
        coordinate = SpurCoordinate(
            latitude = point.latitude(),
            longitude = point.longitude(),
        ),
        rank = rank,
    )
}

private fun VectorSource.nearbyLandmarkCandidates(): List<NearbyLandmarkCandidate> =
    querySourceFeatures(arrayOf(MapPoiSourceLayer), null)
        .mapNotNull(Feature::toNearbyLandmarkCandidate)

private fun overpassLandmarkRank(tags: JSONObject): Int = when {
    tags.has("wikipedia") || tags.has("wikidata") || tags.has("seamark:type") -> 0
    tags.has("tourism") || tags.has("historic") || tags.has("heritage") -> 1
    tags.has("amenity") || tags.has("leisure") || tags.has("natural") -> 2
    tags.has("shop") || tags.has("office") -> 3
    else -> 4
}

private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

private val LandmarkSuggestionNameProperties = listOf("name_de", "name", "name_en")
private const val LandmarkSuggestionRankProperty = "rank"
private const val LandmarkSuggestionPreferredRadiusMeters = 50.0
private const val LandmarkSuggestionFallbackRadiusMeters = 100.0
private const val LandmarkSuggestionOverpassUrl = "https://overpass-api.de/api/interpreter"
private const val LandmarkSuggestionNetworkTimeoutMillis = 6_000
private const val LandmarkSuggestionUserAgent = "Spur Android (app.spur)"
