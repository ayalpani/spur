package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourFitTest {
    @Test
    fun eachOpenedTourIsFittedOnceAfterPointsArrive() {
        assertFalse(shouldFitTourRoute(null, null, 0, -1, 12))
        assertFalse(shouldFitTourRoute(7, null, 0, -1, 0))
        assertTrue(shouldFitTourRoute(7, null, 0, -1, 1))
        assertFalse(shouldFitTourRoute(7, 7, 0, 0, 20))
        assertTrue(shouldFitTourRoute(7, 7, 1, 0, 20))
        assertTrue(shouldFitTourRoute(8, 7, 1, 1, 20))
    }

    @Test
    fun tourEntryStartsTwoZoomLevelsOutsideItsFittedTarget() {
        assertEquals(15.5, tourEntryStartZoom(17.5), 0.0001)
        assertEquals(MapZoomMinimum, tourEntryStartZoom(2.0, 5.0), 0.0001)
    }

    @Test
    fun tourEntryWaitsForPresentationBuiltFromCurrentInputs() {
        val points = mutableListOf<TrackPoint>()
        val moments = mutableListOf<MapMoment>()

        assertTrue(
            isTourPresentationReadyForEntry(
                presentedTourId = 7L,
                tourId = 7L,
                presentedRoutePoints = points,
                routePoints = points,
                presentedMapMoments = moments,
                mapMoments = moments,
            ),
        )
        assertFalse(
            isTourPresentationReadyForEntry(
                presentedTourId = 7L,
                tourId = 7L,
                presentedRoutePoints = mutableListOf<TrackPoint>(),
                routePoints = points,
                presentedMapMoments = moments,
                mapMoments = moments,
            ),
        )
        assertFalse(
            isTourPresentationReadyForEntry(
                presentedTourId = 7L,
                tourId = 7L,
                presentedRoutePoints = points,
                routePoints = points,
                presentedMapMoments = mutableListOf<MapMoment>(),
                mapMoments = moments,
            ),
        )
        assertFalse(
            isTourPresentationReadyForEntry(
                presentedTourId = 8L,
                tourId = 7L,
                presentedRoutePoints = points,
                routePoints = points,
                presentedMapMoments = moments,
                mapMoments = moments,
            ),
        )
    }
}
