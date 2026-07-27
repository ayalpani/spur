package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapMomentTest {
    @Test
    fun mapMomentRoundTripsThroughLocalStorageFormat() {
        MomentType.entries.forEach { type ->
            val moment = MapMoment(
                id = "moment-${type.name}",
                type = type,
                latitude = 52.52,
                longitude = 13.405,
                payload = "/data/moment",
            )
            assertEquals(moment, decodeMapMoment(encodeMapMoment(moment)))
        }
        assertNull(decodeMapMoment("broken"))
    }

    @Test
    fun legacyPhotoIsAssociatedByCaptureTime() {
        val tour = Tour(4, 1_000L, 5_000L, 0.0, 2)
        val points = listOf(
            TrackPoint(10, 52.0, 13.0, 1_500L),
            TrackPoint(11, 52.1, 13.1, 4_000L),
        )
        val legacy = MapMoment(
            id = "photo-3900",
            type = MomentType.PHOTO,
            latitude = 52.1,
            longitude = 13.1,
            payload = "/photo.jpg",
            createdAt = 0L,
        )

        val associated = associateLegacyMoment(
            legacy,
            tours = listOf(tour),
            pointsByTour = mapOf(tour.id to points),
        )

        assertEquals(4L, associated.tourId)
        assertEquals(11L, associated.trackPointId)
        assertEquals(3_900L, associated.createdAt)
    }
}
