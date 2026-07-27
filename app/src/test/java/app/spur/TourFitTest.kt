package app.spur

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
}
