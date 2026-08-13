package app.spur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

internal class ItemsApi(
    private val baseUrl: String = "https://spur.yalpani.com/v1",
) {
    suspend fun list(bounds: ItemMapBounds, zoom: Double): PublicItemsPage = get(
        "/items?bbox=${bounds.west},${bounds.south},${bounds.east},${bounds.north}&zoom=$zoom",
    ).let(::parsePage)

    suspend fun nearby(location: ItemLocation): PublicItemsPage {
        val latitudeRadius = ItemDiscoveryRadiusMeters / 111_320.0
        val longitudeRadius = latitudeRadius /
            kotlin.math.cos(Math.toRadians(location.latitude)).coerceAtLeast(0.15)
        return list(
            ItemMapBounds(
                west = location.longitude - longitudeRadius,
                south = location.latitude - latitudeRadius,
                east = location.longitude + longitudeRadius,
                north = location.latitude + latitudeRadius,
            ),
            zoom = 18.0,
        )
    }

    suspend fun detail(id: String): PublicItem = parsePublicItem(get("/items/$id"))

    suspend fun drop(item: OwnedItem, pending: PendingItemDrop): PublicItem = post(
        path = "/items/${item.id}/drop",
        body = JSONObject()
            .put("kind", item.kind.name)
            .put("generation", item.generation)
            .put("capability_secret", item.capabilitySecret.base64Url())
            .put("provenance_capsule", item.provenance.toJson())
            .put("location", pending.location.toJson())
            .put("idempotency_id", pending.idempotencyId),
    ).let(::parsePublicItem)

    suspend fun claim(publicItem: PublicItem, pending: PendingItemClaim): OwnedItem = post(
        path = "/items/${publicItem.id}/claim",
        body = JSONObject()
            .put("new_capability_hash", pending.newCapabilitySecret.sha256().base64Url())
            .put("location", pending.location.toJson())
            .put("idempotency_id", pending.idempotencyId),
    ).let { parseOwnedItem(it, pending.newCapabilitySecret) }

    suspend fun recover(pending: PendingItemClaim): OwnedItem = post(
        path = "/items/${pending.itemId}/claim/recover",
        body = pending.continuationJson(),
    ).let { parseOwnedItem(it, pending.newCapabilitySecret) }

    suspend fun acknowledge(pending: PendingItemClaim) {
        post("/items/${pending.itemId}/claim/ack", pending.continuationJson())
    }

    private suspend fun get(path: String): JSONObject = request("GET", path, null)

    private suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                useCaches = false
                if (body != null) doOutput = true
            }
            try {
                body?.let { json ->
                    connection.outputStream.use { output ->
                        output.write(json.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { it.readBytes() }
                    ?: byteArrayOf()
                val json = if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
                if (status !in 200..299) {
                    val error = json.optJSONObject("error")
                    throw ItemsApiException(
                        status = status,
                        code = error?.optString("code").orEmpty().ifBlank { "http_$status" },
                        message = error?.optString("message").orEmpty().ifBlank { "Serveranfrage fehlgeschlagen" },
                    )
                }
                json
            } finally {
                connection.disconnect()
            }
        }

    private fun parsePage(json: JSONObject) = PublicItemsPage(
        items = json.getJSONArray("items").mapObjects(::parsePublicItem),
        clusters = json.getJSONArray("clusters").mapObjects { cluster ->
            PublicItemCluster(
                latitude = cluster.getDouble("latitude"),
                longitude = cluster.getDouble("longitude"),
                count = cluster.getInt("count"),
            )
        },
    )

    private fun parsePublicItem(json: JSONObject) = PublicItem(
        id = json.getString("id"),
        kind = ItemKind.valueOf(json.getString("kind")),
        generation = json.getInt("generation"),
        location = parseLocation(json.getJSONObject("location")),
        droppedDay = java.time.LocalDate.parse(json.getString("dropped_day")),
        publicProvenance = parseEvents(json.getJSONArray("public_provenance")),
    )

    private fun parseOwnedItem(json: JSONObject, secret: ByteArray) = OwnedItem(
        id = json.getString("id"),
        kind = ItemKind.valueOf(json.getString("kind")),
        generation = json.getInt("generation"),
        capabilitySecret = secret,
        provenance = ProvenanceCapsule(
            parseEvents(json.getJSONObject("provenance_capsule").getJSONArray("events")),
        ),
    )

    private fun parseLocation(json: JSONObject) = ItemLocation(
        latitude = json.getDouble("latitude"),
        longitude = json.getDouble("longitude"),
        accuracyMeters = json.optDouble("accuracy_m", 0.0),
    )

    private fun parseEvents(events: JSONArray): List<ProvenanceEvent> = events.mapObjects { event ->
        ProvenanceEvent(
            generation = event.getInt("generation"),
            dayUtc = java.time.LocalDate.parse(event.getString("day_utc")),
            coarseLatitude = event.getString("coarse_latitude"),
            coarseLongitude = event.getString("coarse_longitude"),
            previousHash = event.getString("previous_hash"),
            serverSignature = event.getString("server_signature"),
        )
    }

    private fun ProvenanceCapsule.toJson() = JSONObject().put(
        "events",
        JSONArray().also { array ->
            events.forEach { event ->
                array.put(
                    JSONObject()
                        .put("generation", event.generation)
                        .put("day_utc", event.dayUtc.toString())
                        .put("coarse_latitude", event.coarseLatitude)
                        .put("coarse_longitude", event.coarseLongitude)
                        .put("previous_hash", event.previousHash)
                        .put("server_signature", event.serverSignature),
                )
            }
        },
    )

    private fun ItemLocation.toJson() = JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("accuracy_m", accuracyMeters)

    private fun PendingItemClaim.continuationJson() = JSONObject()
        .put("capability_secret", newCapabilitySecret.base64Url())
        .put("idempotency_id", idempotencyId)
}

internal class ItemsApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message)

private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)

private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
