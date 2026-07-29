package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedTrackPointPositionTest {
    private val position = SelectedTrackPointScreenPosition(
        pointId = 12L,
        x = 100f,
        y = 200f,
    )

    @Test
    fun `stale screen position is hidden when selection changes`() {
        assertNull(
            visibleSelectedTrackPointPosition(
                position = position,
                selectedPointId = 13L,
                firstPointId = 10L,
                lastPointId = 20L,
            ),
        )
    }

    @Test
    fun `matching non-endpoint position is shown`() {
        assertEquals(
            position,
            visibleSelectedTrackPointPosition(
                position = position,
                selectedPointId = 12L,
                firstPointId = 10L,
                lastPointId = 20L,
            ),
        )
    }
}
