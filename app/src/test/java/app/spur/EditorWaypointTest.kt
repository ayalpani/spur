package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorWaypointTest {
    private val tour = Tour(
        id = 1,
        startedAt = 1_000L,
        endedAt = 1_000_000L,
        distanceMeters = 0.0,
        pointCount = 0,
    )

    @Test
    fun includesStartEndDistanceAndElapsedMilestones() {
        val points = listOf(
            point(1, 52.0, 13.0, 1_000L),
            point(2, 52.00045, 13.0, 60_000L),
            point(3, 52.00090, 13.0, 120_000L),
            point(4, 52.00091, 13.0, 421_000L),
            point(5, 52.00092, 13.0, 422_000L),
        )

        assertEquals(
            listOf(1L, 3L, 4L, 5L),
            buildEditorWaypoints(tour, points, emptyList()).map { it.trackPoint.id },
        )
    }

    @Test
    fun keepsMomentAndSelectedPoints() {
        val points = listOf(
            point(1, 52.0, 13.0, 1_000L),
            point(2, 52.0001, 13.0, 2_000L),
            point(3, 52.0002, 13.0, 3_000L),
            point(4, 52.0003, 13.0, 4_000L),
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
            listOf(1L, 2L, 3L, 4L),
            buildEditorWaypoints(tour, points, listOf(moment), selectedTrackPointId = 3)
                .map { it.trackPoint.id },
        )
    }

    private fun point(id: Long, latitude: Double, longitude: Double, at: Long) =
        TrackPoint(id, latitude, longitude, at)
}
