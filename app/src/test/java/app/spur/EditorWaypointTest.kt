package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorWaypointTest {
    private val tour = Tour(1, 1_000L, 500_000L, 0.0, 5)

    @Test
    fun keepsStartEndDistanceTimeMomentAndSelectionWaypoints() {
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
            editorWaypoints(tour, points, listOf(moment), selectedPointId = 5)
                .map { it.point.id },
        )
    }

    private fun point(id: Long, latitude: Double, recordedAt: Long) =
        TrackPoint(id, latitude, 13.0, recordedAt)
}
