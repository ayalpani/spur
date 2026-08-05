package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourRevisionTest {
    @Test
    fun initialRevisionLoadsOnlyOnceUntilItChanges() {
        val revision = revision()

        assertTrue(shouldReloadTour(null, revision))
        assertFalse(shouldReloadTour(revision, revision.copy()))
    }

    @Test
    fun pointAppendAndStationaryRepresentativeUpdateTriggerReloads() {
        val revision = revision()

        assertTrue(
            shouldReloadTour(
                revision,
                revision.copy(pointCount = 13, maximumPointId = 43),
            ),
        )
        assertTrue(
            shouldReloadTour(
                revision,
                revision.copy(maximumPointRecordedAt = 1_700_000_004_000L),
            ),
        )
    }

    @Test
    fun deletedTourClearsLoadedSnapshotWithoutReloadLoop() {
        val revision = revision()

        assertTrue(shouldReloadTour(revision, null))
        assertFalse(shouldReloadTour(null, null))
    }

    private fun revision() = TourRevision(
        id = 7L,
        startedAt = 1_700_000_000_000L,
        endedAt = null,
        distanceMeters = 1_250.0,
        pointCount = 12,
        title = null,
        maximumPointId = 42L,
        maximumPointRecordedAt = 1_700_000_003_000L,
    )
}
