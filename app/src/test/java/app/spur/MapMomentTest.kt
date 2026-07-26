package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun clusterStackShowsAtMostThreeMarkers() {
        assertEquals(listOf(8f), clusterStackOffsets(1))
        assertEquals(listOf(4f, 8f), clusterStackOffsets(2))
        assertEquals(listOf(0f, 4f, 8f), clusterStackOffsets(3))
        assertEquals(listOf(0f, 4f, 8f), clusterStackOffsets(12))
    }

    @Test
    fun momentsAtTheSameLocationFanOutAroundTheirLocation() {
        val moments = (1..4).map { index ->
            MapMoment(
                id = "moment-$index",
                type = MomentType.PHOTO,
                latitude = 52.52,
                longitude = 13.405,
                payload = "/photo-$index.jpg",
            )
        } + MapMoment(
            id = "somewhere-else",
            type = MomentType.PHOTO,
            latitude = 52.53,
            longitude = 13.406,
            payload = "/other.jpg",
        )

        val offsets = overlappingMomentOffsets(moments)

        assertEquals(moments.take(4).map(MapMoment::id).toSet(), offsets.keys)
        offsets.values.forEach { offset ->
            assertTrue(offset.getDistance() > MomentMarkerWidth / 2f)
        }
    }
}
