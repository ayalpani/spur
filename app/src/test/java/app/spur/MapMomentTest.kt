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
                tourId = 42,
            )
            assertEquals(moment, decodeMapMoment(encodeMapMoment(moment)))
        }
        assertNull(decodeMapMoment("broken"))
    }

    @Test
    fun legacyMomentsRemainReadable() {
        assertEquals(
            MapMoment(
                id = "photo-1500",
                type = MomentType.PHOTO,
                latitude = 52.52,
                longitude = 13.405,
                payload = "/data/photo.jpg",
            ),
            decodeMapMoment("photo-1500|PHOTO|52.52|13.405|/data/photo.jpg"),
        )
    }

    @Test
    fun photosUseStoredTourIdAndLegacyCaptureTime() {
        val tour = Tour(
            id = 7,
            startedAt = 1_000,
            endedAt = 2_000,
            distanceMeters = 0.0,
            pointCount = 0,
        )
        val explicit = MapMoment(
            id = "custom",
            type = MomentType.PHOTO,
            latitude = 0.0,
            longitude = 0.0,
            payload = "/explicit.jpg",
            tourId = 7,
        )
        val legacy = explicit.copy(id = "photo-1500", payload = "/legacy.jpg", tourId = null)
        val outside = explicit.copy(id = "photo-2500", payload = "/outside.jpg", tourId = null)

        assertEquals(listOf(legacy, explicit), photoMomentsForTour(listOf(explicit, outside, legacy), tour))
    }
}
