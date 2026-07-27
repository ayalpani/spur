package app.spur

internal enum class MomentType {
    PHOTO,
    VIDEO,
    VOICE,
    EMOJI,
}

internal data class MapMoment(
    val id: String,
    val type: MomentType,
    val latitude: Double,
    val longitude: Double,
    val payload: String,
    val tourId: Long? = null,
)

internal fun encodeMapMoment(moment: MapMoment): String =
    "${moment.id}|${moment.type.name}|${moment.latitude}|${moment.longitude}|" +
        "${moment.tourId.orEmpty()}|${moment.payload}"

internal fun decodeMapMoment(value: String): MapMoment? {
    val fields = value.split('|', limit = 6)
    if (fields.size !in 5..6) return null
    val hasTourId = fields.size == 6
    return MapMoment(
        id = fields[0],
        type = runCatching { MomentType.valueOf(fields[1]) }.getOrNull() ?: return null,
        latitude = fields[2].toDoubleOrNull() ?: return null,
        longitude = fields[3].toDoubleOrNull() ?: return null,
        payload = fields[if (hasTourId) 5 else 4],
        tourId = fields.getOrNull(4)?.takeIf { hasTourId && it.isNotEmpty() }?.toLongOrNull(),
    )
}

private fun Long?.orEmpty(): String = this?.toString().orEmpty()

internal fun photoMomentsForTour(
    moments: List<MapMoment>,
    tour: Tour,
): List<MapMoment> = momentsForTour(moments, tour) { it.type == MomentType.PHOTO }

internal fun visualMomentsForTour(
    moments: List<MapMoment>,
    tour: Tour,
): List<MapMoment> = momentsForTour(moments, tour) {
    it.type == MomentType.PHOTO || it.type == MomentType.VIDEO
}

internal fun mapMomentsForTour(
    moments: List<MapMoment>,
    tour: Tour,
): List<MapMoment> = momentsForTour(moments, tour) { true }

private fun momentsForTour(
    moments: List<MapMoment>,
    tour: Tour,
    accepts: (MapMoment) -> Boolean,
): List<MapMoment> = moments
    .asSequence()
    .filter(accepts)
    .filter { moment ->
        moment.tourId == tour.id ||
            moment.tourId == null && moment.captureTimeMillis()?.let { capturedAt ->
                capturedAt >= tour.startedAt && capturedAt <= (tour.endedAt ?: Long.MAX_VALUE)
            } == true
    }
    .sortedByDescending(MapMoment::captureTimeMillis)
    .toList()

internal fun orderedPhotoMoments(moments: List<MapMoment>): List<MapMoment> =
    moments
        .filter { it.type == MomentType.PHOTO }
        .sortedWith(
            compareBy<MapMoment> { it.captureTimeMillis() ?: Long.MAX_VALUE }
                .thenBy(MapMoment::id),
        )

internal fun MapMoment.captureTimeMillis(): Long? =
    id.removePrefix("${type.name.lowercase()}-")
        .takeIf { it != id }
        ?.toLongOrNull()
