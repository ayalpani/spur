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
    val trackPointId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

internal fun encodeMapMoment(moment: MapMoment): String =
    listOf(
        moment.id,
        moment.type.name,
        moment.latitude,
        moment.longitude,
        moment.payload,
        moment.tourId.orEmpty(),
        moment.trackPointId.orEmpty(),
        moment.createdAt,
    ).joinToString("|")

internal fun decodeMapMoment(value: String): MapMoment? {
    val fields = value.split('|', limit = 8)
    if (fields.size != 5 && fields.size != 8) return null
    return MapMoment(
        id = fields[0],
        type = runCatching { MomentType.valueOf(fields[1]) }.getOrNull() ?: return null,
        latitude = fields[2].toDoubleOrNull() ?: return null,
        longitude = fields[3].toDoubleOrNull() ?: return null,
        payload = fields[4],
        tourId = fields.getOrNull(5)?.toLongOrNull(),
        trackPointId = fields.getOrNull(6)?.toLongOrNull(),
        createdAt = fields.getOrNull(7)?.toLongOrNull() ?: 0L,
    )
}

internal fun associateLegacyMoment(
    moment: MapMoment,
    tours: List<Tour>,
    pointsByTour: Map<Long, List<TrackPoint>>,
): MapMoment {
    if (moment.tourId != null) return moment
    val capturedAt = moment.createdAt.takeIf { it > 0 }
        ?: moment.id.substringAfterLast('-').toLongOrNull()
        ?: return moment
    val tour = tours.firstOrNull { candidate ->
        capturedAt >= candidate.startedAt &&
            capturedAt <= (candidate.endedAt ?: Long.MAX_VALUE)
    } ?: return moment.copy(createdAt = capturedAt)
    val point = pointsByTour[tour.id]
        .orEmpty()
        .minByOrNull { kotlin.math.abs(it.recordedAt - capturedAt) }
        ?: return moment.copy(createdAt = capturedAt)
    return moment.copy(
        tourId = tour.id,
        trackPointId = point.id,
        createdAt = capturedAt,
    )
}

private fun Long?.orEmpty(): String = this?.toString().orEmpty()
