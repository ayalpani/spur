package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLocationTest {
    private val tour = Tour(1, 1_000L, 500_000L, 0.0, 5)

    @Test
    fun exposesEveryRecordedGpsPointAndItsMoments() {
        val points = listOf(
            point(1, 52.0, 1_000L),
            point(2, 52.00045, 2_000L),
            point(3, 52.00090, 3_000L),
            point(4, 52.00091, 304_000L),
            point(5, 52.00092, 305_000L),
            point(6, 52.00093, 306_000L),
        )
        val moment = MapMoment(
            id = "photo",
            type = MomentType.PHOTO,
            latitude = points[1].latitude,
            longitude = points[1].longitude,
            payload = "/photo",
            tourId = 1,
            trackPointId = 2,
        )

        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L, 6L),
            editorLocations(tour, points, listOf(moment))
                .map { it.point.id },
        )
        assertEquals(
            listOf(moment),
            editorLocations(tour, points, listOf(moment))[1].moments,
        )
    }

    @Test
    fun momentFromCollapsedPointMovesToNearestClusterPoint() {
        val points = listOf(
            point(1, 52.0, 1_000L),
            point(3, 52.00020, 3_000L),
        )
        val moment = MapMoment(
            id = "emoji",
            type = MomentType.EMOJI,
            latitude = 52.00018,
            longitude = 13.0,
            payload = "🙂",
            tourId = 1,
            trackPointId = 2,
        )

        assertEquals(
            listOf(moment),
            editorLocations(tour, points, listOf(moment)).last().moments,
        )
    }

    private fun point(id: Long, latitude: Double, recordedAt: Long) =
        TrackPoint(id, latitude, 13.0, recordedAt)
}
