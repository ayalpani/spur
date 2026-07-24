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
)

internal fun encodeMapMoment(moment: MapMoment): String =
    "${moment.id}|${moment.type.name}|${moment.latitude}|${moment.longitude}|${moment.payload}"

internal fun decodeMapMoment(value: String): MapMoment? {
    val fields = value.split('|', limit = 5)
    if (fields.size != 5) return null
    return MapMoment(
        id = fields[0],
        type = runCatching { MomentType.valueOf(fields[1]) }.getOrNull() ?: return null,
        latitude = fields[2].toDoubleOrNull() ?: return null,
        longitude = fields[3].toDoubleOrNull() ?: return null,
        payload = fields[4],
    )
}
