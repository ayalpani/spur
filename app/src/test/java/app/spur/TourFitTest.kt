package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourFitTest {
    @Test
    fun eachOpenedTourIsFittedOnceAfterPointsArrive() {
        assertFalse(shouldFitTourRoute(null, null, 12))
        assertFalse(shouldFitTourRoute(7, null, 0))
        assertTrue(shouldFitTourRoute(7, null, 1))
        assertFalse(shouldFitTourRoute(7, 7, 20))
        assertTrue(shouldFitTourRoute(8, 7, 20))
    }
}
