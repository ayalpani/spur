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
                trackPointId = 7,
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
        assertEquals(
            MapMoment(
                id = "photo-1500",
                type = MomentType.PHOTO,
                latitude = 52.52,
                longitude = 13.405,
                payload = "/data/photo.jpg",
                tourId = 42,
            ),
            decodeMapMoment("photo-1500|PHOTO|52.52|13.405|42|/data/photo.jpg"),
        )
    }

    @Test
    fun personaUsesItsOwnExactMapCoordinate() {
        assertNull(personaFeature(null))

        val feature = personaFeature(
            SpurCoordinate(latitude = 52.52, longitude = 13.405),
        )!!
        val point = feature.geometry() as org.maplibre.geojson.Point

        assertEquals(13.405, point.longitude(), 0.0)
        assertEquals(52.52, point.latitude(), 0.0)
        assertEquals(1, feature.getNumberProperty(MapPersonaProperty))
    }

    @Test
    fun fileBackedMomentsExposeTheirCaptureTime() {
        MomentType.entries
            .filterNot { it == MomentType.EMOJI }
            .forEach { type ->
                val moment = MapMoment(
                    id = "${type.name.lowercase()}-1700000000000",
                    type = type,
                    latitude = 0.0,
                    longitude = 0.0,
                    payload = "/moment",
                )
                assertEquals(1_700_000_000_000L, moment.captureTimeMillis())
            }
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
    fun tourVisualsIncludePhotosAndVideos() {
        val tour = Tour(
            id = 7,
            startedAt = 1_000,
            endedAt = 2_000,
            distanceMeters = 0.0,
            pointCount = 0,
        )
        val photo = MapMoment(
            id = "photo-1200",
            type = MomentType.PHOTO,
            latitude = 0.0,
            longitude = 0.0,
            payload = "/photo.jpg",
            tourId = 7,
        )
        val video = photo.copy(
            id = "video-1400",
            type = MomentType.VIDEO,
            payload = "/video.mp4",
        )
        val voice = photo.copy(
            id = "voice-1600",
            type = MomentType.VOICE,
            payload = "/voice.m4a",
        )

        assertEquals(
            listOf(voice, video, photo).filter { it.type != MomentType.VOICE },
            visualMomentsForTour(listOf(photo, video, voice), tour),
        )
        assertEquals(
            listOf(voice, video, photo),
            mapMomentsForTour(listOf(photo, video, voice), tour),
        )
    }

    @Test
    fun clusterStackShowsAtMostThreeMarkers() {
        assertEquals(listOf(12f), clusterStackOffsets(1))
        assertEquals(listOf(6f, 12f), clusterStackOffsets(2))
        assertEquals(listOf(0f, 6f, 12f), clusterStackOffsets(3))
        assertEquals(listOf(0f, 6f, 12f), clusterStackOffsets(12))
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

    @Test
    fun photoViewerUsesChronologicalOrder() {
        val newest = MapMoment(
            id = "photo-3000",
            type = MomentType.PHOTO,
            latitude = 0.0,
            longitude = 0.0,
            payload = "/newest.jpg",
        )
        val oldest = newest.copy(id = "photo-1000", payload = "/oldest.jpg")
        val middle = newest.copy(id = "photo-2000", payload = "/middle.jpg")
        val voice = newest.copy(id = "voice-1500", type = MomentType.VOICE)

        assertEquals(
            listOf(oldest, middle, newest),
            orderedPhotoMoments(listOf(newest, voice, oldest, middle)),
        )
    }

    @Test
    fun photoMetadataUsesCaptureTimestampAndShortPlaceDescription() {
        val photo = MapMoment(
            id = "photo-1700000000000",
            type = MomentType.PHOTO,
            latitude = 52.52,
            longitude = -13.405,
            payload = "/photo.jpg",
        )

        assertEquals(1_700_000_000_000, photo.captureTimeMillis())
        assertEquals(
            "Metzer Straße 12, Prenzlauer Berg",
            shortPlaceDescription(
                thoroughfare = "Metzer Straße",
                streetNumber = "12",
                district = "Prenzlauer Berg",
                locality = "Berlin",
                region = "Berlin",
                featureName = null,
            ),
        )
        assertEquals(
            "Berlin",
            shortPlaceDescription(
                thoroughfare = null,
                streetNumber = null,
                district = null,
                locality = "Berlin",
                region = "Berlin",
                featureName = null,
            ),
        )
    }
}
