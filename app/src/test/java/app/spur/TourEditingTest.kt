package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TourEditingTest {
    private val points = listOf(
        TrackPoint(1, 52.5200, 13.4050, 1_000),
        TrackPoint(2, 52.5205, 13.4055, 2_000),
        TrackPoint(3, 52.5210, 13.4060, 3_000),
        TrackPoint(4, 52.5215, 13.4065, 4_000),
    )

    @Test
    fun trimmingKeepsOnlyPointsBetweenBothHandles() {
        assertEquals(setOf(2L, 3L), retainedPointIds(points, 1, 2))
    }

    @Test
    fun distanceIsRecalculatedFromTheRemainingRoute() {
        val fullDistance = trackDistanceMeters(points)
        val trimmedDistance = trackDistanceMeters(points.subList(1, 3))

        assertEquals(true, fullDistance > trimmedDistance)
        assertEquals(65.1, trimmedDistance, 2.0)
    }

    @Test
    fun deletingPointReassignsItsMomentsAndSelectsTheFollowingPoint() {
        val moment = MapMoment(
            id = "emoji-1",
            type = MomentType.EMOJI,
            latitude = points[2].latitude,
            longitude = points[2].longitude,
            payload = "🙂",
            trackPointId = points[1].id,
        )

        val deletion = trackPointDeletion(
            points = points,
            moments = listOf(moment),
            deletedPointId = points[1].id,
        )

        requireNotNull(deletion)
        assertEquals(listOf(1L, 3L, 4L), deletion.retainedPoints.map(TrackPoint::id))
        assertEquals(setOf(1L, 3L, 4L), deletion.retainedPointIds)
        assertEquals(3L, deletion.updatedMoments.single().trackPointId)
        assertEquals(3L, deletion.selectedPointId)
    }

    @Test
    fun deletingMissingPointIsANoOp() {
        assertNull(trackPointDeletion(points, emptyList(), deletedPointId = 99L))
    }
}
